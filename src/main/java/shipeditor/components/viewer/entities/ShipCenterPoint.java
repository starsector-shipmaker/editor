package shipeditor.components.viewer.entities;

import shipeditor.components.viewer.painters.GlColors;
import shipeditor.utility.text.StringManager;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.CenterPointPainter;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.ColorUtilities;
import java.awt.Color;
import java.awt.geom.AffineTransform;

import java.awt.geom.Point2D;
import shipeditor.utility.graphics.GraphicConstants;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShipCenterPoint extends BaseWorldPoint {

    @Getter @Setter
    private float collisionRadius;

    private final CenterPointPainter parentPainter;

    private final Color collisionCircleColor = new Color(0xFFDCDC40, true);

    public ShipCenterPoint(Point2D pointPosition, float radius, ShipPainter layer, CenterPointPainter parent) {
        super(pointPosition, layer);
        this.collisionRadius = radius;
        this.parentPainter = parent;
    }

    @Override
    protected boolean isInteractable() {
        LayerPainter shipPainter = super.getParent();
        if (shipPainter instanceof ShipPainter checkedLayer) {
            CenterPointPainter painter = checkedLayer.getCenterPointPainter();
            return StaticController.getEditorMode() == getAssociatedMode() && painter.isInteractionEnabled();
        } else {
            throw new IllegalStateException("Illegal parent layer of ship center point!");
        }
    }

    @Override
    public EditorInstrument getAssociatedMode() {
        return EditorInstrument.COLLISION;
    }

    @Override
    public String getNameForLabel() {
        return StringManager.getString("SHIP_CENTER");
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();

        // 1. Draw Collision Circle (World Space)
        shapeRenderer.end();
        shapeRenderer.begin(projection, view);

        Point2D position = getPosition();
        org.joml.Vector2f centerWorld = new org.joml.Vector2f((float) position.getX(), (float) position.getY());

        float painterOpacity = parentPainter.getPaintOpacity();

        // Interior fill: same opacity as shield fill (using painterOpacity directly)
        org.joml.Vector4f glCollisionFill = GlColors.of(collisionCircleColor,
                (collisionCircleColor.getAlpha() / 255.0f) * painterOpacity);
        shapeRenderer.drawCircle(centerWorld, getCollisionRadius(), glCollisionFill, true);

        // Border ring outline: same distinct opacity as shield ring
        org.joml.Vector4f glCollisionRing = GlColors.of(collisionCircleColor, 0.5f);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_NORMAL);
        shapeRenderer.drawCircle(centerWorld, getCollisionRadius(), glCollisionRing, false);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);

        shapeRenderer.end();

        // 2. Draw Center Cross (Screen Space)
        shapeRenderer.begin(projection, new org.joml.Matrix4f());

        Color crossColor = getCurrentColor();
        org.joml.Vector4f glCrossColor = GlColors.of(crossColor, 1.0f);
        Point2D screenLoc = worldToScreen.transform(position, null);
        org.joml.Vector2f screenPos = new org.joml.Vector2f(Math.round(screenLoc.getX()), Math.round(screenLoc.getY()));

        float halfLen = 6.0f;
        shapeRenderer.drawLine(new org.joml.Vector2f(screenPos.x - halfLen, screenPos.y), new org.joml.Vector2f(screenPos.x + halfLen, screenPos.y), glCrossColor);
        shapeRenderer.drawLine(new org.joml.Vector2f(screenPos.x, screenPos.y - halfLen), new org.joml.Vector2f(screenPos.x, screenPos.y + halfLen), glCrossColor);
    }

    @Override
    protected Color createBaseColor() {
        return new Color(250, 200, 30);
    }

    @Override
    protected Color createHoverColor() {
        return ColorUtilities.getBlendedColor(createBaseColor(),
                createSelectColor(), 0.5f);
    }

    @Override
    @SuppressWarnings("WeakerAccess")
    protected Color createSelectColor() {
        return createBaseColor();
    }



    @Override
    public String toString() {
        return "ShipCenter";
    }

}
