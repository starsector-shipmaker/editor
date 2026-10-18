package shipeditor.components.viewer.entities;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import shipeditor.components.viewer.painters.GlColors;
import shipeditor.utility.graphics.opengl.OpenGLPainter;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.AnchorOffsetQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.LayerPainter;

import shipeditor.undo.UndoOverseer;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.ColorUtilities;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class BaseWorldPoint implements WorldPoint, OpenGLPainter {

    @Getter @Setter
    private LayerPainter parent;

    @Getter
    private final Point2D position;

    @Getter @Setter
    private boolean cursorInBounds;

    @Getter @Setter
    private boolean pointSelected;

    @Getter
    private final AffineTransform delegateWorldToScreen;

    private BusEventListener anchorDragListener;

    @Getter @Setter
    private double paintSizeMultiplier = 1;

    public EditorInstrument getAssociatedMode() {
        return EditorInstrument.LAYER;
    }

    public BaseWorldPoint() {
        this(new Point2D.Double());
    }

    public BaseWorldPoint(Point2D pointPosition) {
        this(new Point2D.Double(pointPosition.getX(), pointPosition.getY()), null);

    }

    public BaseWorldPoint(Point2D pointPosition, LayerPainter parentPainter) {
        this.position = new Point2D.Double(pointPosition.getX(), pointPosition.getY());
        this.parent = parentPainter;
        this.delegateWorldToScreen = new AffineTransform();

        if (parentPainter != null) {
            this.initLayerListening();
        }
    }


    /**
     * Returns the lines to display in the on-hover pop-out tooltip.
     * Subclasses override this to provide richer multi-line information.
     * The first line is rendered with a larger header font.
     */
    protected String[] getHoverLines() {
        Point2D toDisplay = this.getCoordinatesForDisplay();
        return new String[] {
                getNameForLabel() + " (" + toDisplay.getX() + ", " + toDisplay.getY() + ")"
        };
    }

    public String getNameForLabel() {
        return "Point";
    }

    public String getPositionText() {
        Point2D location = this.getCoordinatesForDisplay();
        return Utility.getPointPositionText(location);
    }
    
    private void initLayerListening() {
        anchorDragListener = event -> {
            if (event instanceof AnchorOffsetQueued checked && checked.layer() == this.parent) {
                Point2D offset = checked.difference();
                Point2D oldPosition = this.getPosition();
                this.setPosition(oldPosition.getX() - offset.getX(),
                        oldPosition.getY() - offset.getY());
                UndoOverseer.adjustPointEditsOffset(this, offset);
            }
        };
        EventBus.subscribe(anchorDragListener);
    }

    public void cleanupForRemoval() {
        EventBus.unsubscribe(anchorDragListener);
    }

    Color createHoverColor() {
        return ColorUtilities.getBlendedColor(createBaseColor(), createSelectColor(),0.5);
    }

    @SuppressWarnings("WeakerAccess")
    protected Color createSelectColor() {
        return new Color(0xFFFF0000, true);
    }

    protected Color createBaseColor() {
        return new Color(0xFFFFFFFF, true);
    }

    protected boolean isInteractable() {
        LayerPainter layer = getParent();
        if (layer == null) {
            return true;
        }
        return StaticController.getEditorMode() == getAssociatedMode() && layer.isLayerActive();
    }

    @SuppressWarnings("WeakerAccess")
    public Color getCurrentColor() {
        Color result;
        if (this.pointSelected && isInteractable()) {
            result = createSelectColor();
        } else if (this.cursorInBounds && isInteractable()) {
            result = createHoverColor();
        } else {
            result = createBaseColor();
        }
        return result;
    }



    public void setPosition(double x, double y) {
        this.position.setLocation(x, y);
    }

    public void setPosition(Point2D input) {
        this.setPosition(input.getX(), input.getY());
    }

    public Point2D getCoordinatesForDisplay() {
        Point2D pointPosition = this.getPosition();
        return Utility.getPointCoordinatesForDisplay(pointPosition);
    }

    private final Point2D cachedScreenLoc = new Point2D.Double();
    private final org.joml.Vector2f cachedScreenPos = new org.joml.Vector2f();
    private final org.joml.Vector4f cachedGlColor = new org.joml.Vector4f();
    private static final org.joml.Vector4f BLACK_GL = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    public void updateCursorHitState(Point2D rawCursor, AffineTransform worldToScreen) {
        worldToScreen.transform(position, cachedScreenLoc);
        double distSq = cachedScreenLoc.distanceSq(rawCursor);
        double radius = 8.0 * paintSizeMultiplier;
        boolean withinRadius = distSq <= (radius * radius);
        this.setCursorInBounds(withinRadius);
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        worldToScreen.transform(position, cachedScreenLoc);
        cachedScreenPos.set((float) cachedScreenLoc.getX(), (float) cachedScreenLoc.getY());

        float radius = (float) (6.0 * paintSizeMultiplier);

        // Draw black border circle
        shapeRenderer.drawCircle(cachedScreenPos, radius, BLACK_GL, true);

        // Draw inner colored circle
        Color color = getCurrentColor();
        GlColors.set(cachedGlColor, color);
        shapeRenderer.drawCircle(cachedScreenPos, radius - 1.5f, cachedGlColor, true);
    }

}
