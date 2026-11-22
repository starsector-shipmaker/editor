package shipeditor.components.viewer.entities.engine;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.jhlabs.image.HSBAdjustFilter;
import lombok.Getter;
import lombok.Setter;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.AngledPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.representation.GameDataRepository;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.objects.Size2D;
import shipeditor.utility.Utility;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.Map;

@SuppressWarnings({"ClassWithTooManyFields", "ClassWithTooManyMethods"})
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class EnginePoint extends AngledPoint implements EngineData {

    @Setter
    private double angle;

    @Setter
    private double length;

    @Setter
    private double width;

    @Setter
    private int contrailSize;

    @Getter @Setter
    private boolean styleIsCustom;

    @Getter @Setter
    private String styleID;

    private EngineStyle style;

    /**
     * Purely for serialization compatibility purposes; supporting inline styles editing seems counterproductive,
     */
    @Getter @Setter
    private EngineStyle customStyleSpec;

    @Getter
    private EngineDataOverride skinOverride;

    private static final BufferedImage FLAME;

    private static final BufferedImage FLAME_CORE;

    private BufferedImage flameColored;

    private int flameTextureId = 0;

    private boolean flameTextureDirty = false;

    private static int baseFlameCoreTextureId = 0;

    static {
        String flameSprite = "engineflame32.png";
        FLAME = FileLoading.loadImageResource(flameSprite);
        String flameCoreSprite = "engineflamecore32.png";
        FLAME_CORE = FileLoading.loadImageResource(flameCoreSprite);
    }

    public void setSkinOverride(EngineDataOverride override) {
        this.skinOverride = override;
        if (skinOverride != null) {
            EngineStyle overrideStyle = skinOverride.getStyle();
            this.setSkinStyleOverride(overrideStyle != null ? overrideStyle : getBaseStyle());
        } else {
            handleStyleFlameImage(getBaseStyle());
        }
    }

    public static BufferedImage getBaseFlameTexture() {
        return FLAME;
    }

    public static BufferedImage getBaseFlameCoreTexture() {
        return FLAME_CORE;
    }

    public static int getBaseFlameCoreTextureId() {
        if (baseFlameCoreTextureId == 0 && FLAME_CORE != null) {
            baseFlameCoreTextureId = shipeditor.utility.graphics.opengl.TextureLoader.loadTexture(FLAME_CORE);
        }
        return baseFlameCoreTextureId;
    }

    @Override
    public Double getAngleBoxed() {
        return angle;
    }

    @Override
    public Double getLengthBoxed() {
        return length;
    }

    @Override
    public Double getWidthBoxed() {
        return width;
    }

    public EngineStyle getBaseStyle() {
        if (style != null) {
            return style;
        }
        if (styleID != null) {
            GameDataRepository gameData = SettingsManager.getGameData();
            if (gameData != null) {
                Map<String, EngineStyle> allEngineStyles = gameData.getAllEngineStyles();
                if (allEngineStyles != null) {
                    EngineStyle engineStyle = allEngineStyles.get(styleID);
                    if (engineStyle != null) {
                        this.style = engineStyle;
                        if (this.skinOverride == null || this.skinOverride.getStyle() == null) {
                            handleStyleFlameImage(engineStyle);
                        }
                    }
                }
            }
        }
        return style;
    }

    public EngineStyle getStyle() {
        if (this.skinOverride != null && skinOverride.getStyle() != null) {
            return this.skinOverride.getStyle();
        }
        return getBaseStyle();
    }

    public double getBaseAngle() {
        return angle;
    }

    public double getBaseWidth() {
        return width;
    }

    public double getBaseLength() {
        return length;
    }

    @Override
    public double getAngle() {
        if (this.skinOverride != null && skinOverride.getAngle() != null) {
            return skinOverride.getAngle();
        }
        return angle;
    }

    public double getWidth() {
        if (this.skinOverride != null && skinOverride.getWidth() != null) {
            return skinOverride.getWidth();
        }
        return width;
    }

    public double getLength() {
        if (this.skinOverride != null && skinOverride.getLength() != null) {
            return skinOverride.getLength();
        }
        return length;
    }

    public EnginePoint(Point2D pointPosition, ShipPainter layer) {
        this(pointPosition, layer, null);
    }

    public EnginePoint(Point2D pointPosition, ShipPainter layer, EnginePoint valuesSource) {
        super(pointPosition, layer);
        this.flameColored = FLAME;
        handleStyleFlameImage(null);
        if (valuesSource != null) {
            this.setAngle(valuesSource.getBaseAngle());
            this.setWidth(valuesSource.getBaseWidth());
            this.setLength(valuesSource.getBaseLength());
            this.setContrailSize((int) valuesSource.getContrailSize());
            this.restoreStyleState(valuesSource.getBaseStyle(), valuesSource.getStyleID(),
                    valuesSource.isStyleIsCustom(), valuesSource.getCustomStyleSpec());
        }
    }

    public void setSize(Size2D size) {
        this.setLength(size.getHeight());
        this.setWidth(size.getWidth());
    }

    public void changeSize(Size2D size) {
        EditDispatch.postEngineSizeChanged(this, size);
    }

    public void changeContrailSize(int contrail) {
        EditDispatch.postEngineContrailChanged(this, contrail);
    }

    public void changeStyle(EngineStyle engineStyle) {
        EditDispatch.postEngineStyleChanged(this, engineStyle);
    }

    public double getContrailSize() {
        return contrailSize;
    }

    @Override
    public Double getContrailSizeBoxed() {
        return (double) contrailSize;
    }

    public Size2D getSize() {
        return new Size2D(this.getWidth(), this.getLength());
    }

    private void setSkinStyleOverride(EngineStyle engineStyle) {
        handleStyleFlameImage(engineStyle);
    }

    public void setStyle(EngineStyle engineStyle) {
        this.style = engineStyle;
        this.customStyleSpec = null;

        if (engineStyle != null) {
            this.setStyleID(engineStyle.getEngineStyleID());
            java.nio.file.Path pkg = engineStyle.getContainingPackage();
            this.styleIsCustom = pkg != null && !SettingsManager.isCoreFolder(pkg);
        } else {
            this.styleIsCustom = false;
        }
        handleStyleFlameImage(engineStyle);
    }

    public void restoreStyleState(EngineStyle restoredStyle, String restoredStyleID,
                                  boolean restoredStyleIsCustom, EngineStyle restoredCustomStyleSpec) {
        this.style = restoredStyle;
        this.styleID = restoredStyleID;
        this.styleIsCustom = restoredStyleIsCustom;
        this.customStyleSpec = restoredCustomStyleSpec;
        handleStyleFlameImage(this.skinOverride != null && this.skinOverride.getStyle() != null
                ? this.skinOverride.getStyle()
                : restoredStyle);
    }

    private void handleStyleFlameImage(EngineStyle engineStyle) {
        Color flameColor = new Color(255, 125, 25);
        if (engineStyle != null && engineStyle.getEngineColor() != null) {
            flameColor = engineStyle.getEngineColor();
        }

        if (FLAME != null) {
            float[] hue = Color.RGBtoHSB(flameColor.getRed(), flameColor.getGreen(), flameColor.getBlue(), null);
            BufferedImageOp filter = new HSBAdjustFilter(hue[0], hue[1], hue[2]);
            flameColored = filter.filter(FLAME, null);
        }
        // Defer OpenGL texture deletion to drawFlameOpenGL() on the active GL context thread.
        flameTextureDirty = true;
    }

    public EditorInstrument getAssociatedMode() {
        return EditorInstrument.ENGINES;
    }

    @Override
    public void changeSlotAngle(double degrees) {
        EditDispatch.postEngineAngleSet(this,this.angle,degrees);
    }

    @Override
    protected String[] getHoverLines() {
        Point2D toDisplay = this.getCoordinatesForDisplay();
        String headerLine = "Engine";
        String styleLine = "Style: " + (styleID != null ? styleID : "None");
        String sizeLine = "Size: " + Utility.round(getWidth(), 1) + " x " + Utility.round(getLength(), 1);
        String angleLine = "Angle: " + Utility.round(getAngle(), 1) + "\u00B0";
        String coords = "(" + toDisplay.getX() + ", " + toDisplay.getY() + ")";
        return new String[] { headerLine, styleLine, sizeLine, angleLine, coords };
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        shapeRenderer.end();
        this.drawRectangleOpenGL(shapeRenderer, projection, view);
        this.drawFlameOpenGL(spriteRenderer, projection, view);
        shapeRenderer.begin(projection, new Matrix4f());
        super.paint(spriteRenderer, shapeRenderer, projection, view);
    }

    public void drawFlameOpenGL(SpriteRenderer spriteRenderer, Matrix4f projection, Matrix4f view) {
        if (flameTextureDirty) {
            if (flameTextureId != 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(flameTextureId);
                flameTextureId = 0;
            }
            flameTextureDirty = false;
        }
        BufferedImage currentFlame = flameColored;
        if (flameTextureId == 0 && currentFlame != null) {
            flameTextureId = shipeditor.utility.graphics.opengl.TextureLoader.loadTexture(currentFlame);
        }
        int coreTextureId = getBaseFlameCoreTextureId();

        Point2D position = this.getPosition();
        double rawAngle = this.getAngle();
        EnginePoint.drawFlameStaticallyGL(spriteRenderer, projection, view, position, rawAngle, this.getWidth(), this.getLength(), flameTextureId, coreTextureId);
    }

    public static void drawFlameStaticallyGL(SpriteRenderer spriteRenderer, Matrix4f projection, Matrix4f view, Point2D position,
                                             double rawAngle, double engineWidth, double engineLength,
                                             int flameTextureId, int coreTextureId) {
        double transformedAngle = Utility.transformAngle(rawAngle);
        float rotationRadians = (float) Math.toRadians(transformedAngle);
        Point2D topLeft = EnginePoint.getTopLeftOffset(position, engineWidth);

        org.joml.Vector2f pos = new org.joml.Vector2f((float) topLeft.getX(), (float) topLeft.getY());
        org.joml.Vector2f size = new org.joml.Vector2f((float) engineLength, (float) engineWidth);
        org.joml.Vector2f rotationAnchor = new org.joml.Vector2f((float) position.getX(), (float) position.getY());
        org.joml.Vector4f color = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

        if (flameTextureId != 0) {
            spriteRenderer.drawSprite(flameTextureId, pos, size, rotationAnchor, rotationRadians, color, projection, view);
        }
        if (coreTextureId != 0) {
            spriteRenderer.drawSprite(coreTextureId, pos, size, rotationAnchor, rotationRadians, color, projection, view);
        }
    }

    public void drawRectangleOpenGL(ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        Point2D position = this.getPosition();
        double rawAngle = this.getAngle();
        EnginePoint.drawRectangleStaticallyGL(shapeRenderer, projection, view, position, rawAngle, this.getWidth(), this.getLength());
    }

    public static void drawRectangleStaticallyGL(ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view, Point2D position,
                                                 double rawAngle, double engineWidth, double engineLength) {
        double transformedAngle = Utility.transformAngle(rawAngle);
        float rotationRadians = (float) Math.toRadians(transformedAngle);
        Point2D topLeft = EnginePoint.getTopLeftOffset(position, engineWidth);

        Matrix4f rotatedView = new Matrix4f(view)
            .translate((float) position.getX(), (float) position.getY(), 0.0f)
            .rotate(rotationRadians, 0.0f, 0.0f, 1.0f)
            .translate((float) -position.getX(), (float) -position.getY(), 0.0f);

        shapeRenderer.begin(projection, rotatedView);

        org.joml.Vector2f pos = new org.joml.Vector2f((float) topLeft.getX(), (float) topLeft.getY());
        org.joml.Vector2f size = new org.joml.Vector2f((float) engineLength, (float) engineWidth);

        org.joml.Vector4f fillColor = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.08f);
        shapeRenderer.drawRect(pos, size, fillColor, true);

        org.joml.Vector4f outlineColor = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        
        AffineTransform worldToScreen = shipeditor.utility.overseers.StaticController.getViewer().getWorldToScreen();
        Point2D p0Screen = worldToScreen.transform(new Point2D.Double(0, 0), null);
        Point2D p1Screen = worldToScreen.transform(new Point2D.Double(1, 0), null);
        double wtsScale = p0Screen.distance(p1Screen);
        float lineWidth = (float) Math.max(1.0, 0.05 * wtsScale);
        
        org.lwjgl.opengl.GL11.glLineWidth(lineWidth);
        shapeRenderer.drawRect(pos, size, outlineColor, false);

        shapeRenderer.end();
    }

    private static Point2D getTopLeftOffset(Point2D position, double engineWidth) {
        double halfWidth = engineWidth * 0.5f;
        return new Point2D.Double(position.getX(), position.getY() - halfWidth);
    }

}
