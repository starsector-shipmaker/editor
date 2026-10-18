package shipeditor.components.viewer.entities.weapon;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import shipeditor.components.viewer.painters.GlColors;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.GraphicConstants;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.overseers.StaticController;


import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Dedicated renderer for weapon offset (barrel) points.
 * Analogous to {@link SlotDrawer} for weapon slot points.
 * <p>
 * Renders a circle marker at the offset position and a barrel-direction line
 * extending outward from the point. Uses double-pass rendering (black outline
 * beneath colored interior) for visual clarity, consistent with SlotDrawer.
 */
@Getter
@Setter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class OffsetDrawer {

    /**
     * World-space circle radius for the offset marker. Matches SlotDrawer proportions.
     */
    private static final double CIRCLE_RADIUS = 0.10;

    /**
     * Barrel direction line extends this many times the circle radius from the point.
     * Matches SlotDrawer's angle pointer length ratio.
     */
    private static final double BARREL_LENGTH_MULTIPLIER = 5.0;

    /**
     * Minimum screen-space circle radius in pixels.
     * Ensures the marker stays visible when zoomed far out.
     */
    private static final double MIN_CIRCLE_PIXELS = 5.0;

    private final OffsetPoint parentPoint;

    private Point2D pointPosition;
    private double angle;
    private double paintSizeMultiplier = 1;

    // Pre-allocated cache variables to avoid per-frame allocations
    private final Point2D p0Screen = new Point2D.Double();
    private final Point2D p1Screen = new Point2D.Double();
    private final Point2D centerScreen = new Point2D.Double();
    private final Vector4f colorGl = new Vector4f();
    private final Vector4f blackGl = new Vector4f(0.0f, 0.0f, 0.0f, 0.4f);
    private final Vector4f whiteGl = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private final Vector2f centerGl = new Vector2f();

    private final Point2D closestIntersection = new Point2D.Double();
    private final Point2D barrelTip = new Point2D.Double();
    private final Point2D sClosest = new Point2D.Double();
    private final Point2D sBarrelTip = new Point2D.Double();
    private final Vector2f vClosest = new Vector2f();
    private final Vector2f vBarrelTip = new Vector2f();

    public OffsetDrawer(OffsetPoint parent) {
        this.parentPoint = parent;
    }

    /**
     * Renders the offset point marker and barrel direction line.
     * Must be called within an active {@code ShapeRenderer.begin()/end()} block.
     */
    public void paintOffsetVisuals(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer,
                                    Matrix4f projection, Matrix4f view) {
        Point2D position = this.pointPosition;
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        
        LayerPainter parent = parentPoint.getParent();
        if (parent != null) {
            worldToScreen = parent.getWithRotation(worldToScreen);
        }

        // Compute world-to-screen scale for minimum size enforcement.
        double wtsScale = DrawerMath.worldToScreenScale(worldToScreen, p0Screen, p1Screen);

        Color pointColor = parentPoint.getCurrentColor();
        GlColors.set(colorGl, pointColor);

        Point2D centerScreenPoint = worldToScreen.transform(position, centerScreen);
        centerGl.set((float) centerScreenPoint.getX(), (float) centerScreenPoint.getY());

        double effectiveRadius = CIRCLE_RADIUS * paintSizeMultiplier;

        drawMarkerCircle(shapeRenderer, wtsScale, centerGl, effectiveRadius, colorGl, blackGl);
        drawBarrelLine(shapeRenderer, worldToScreen, wtsScale, centerGl, effectiveRadius, colorGl, whiteGl, blackGl);
    }

    /**
     * Draws the offset marker: a circle outline with a semi-transparent fill.
     * Uses double-pass rendering: thick black outline first, then thinner colored ring.
     */
    private void drawMarkerCircle(ShapeRenderer shapeRenderer, double wtsScale,
                                   Vector2f centerGl, double effectiveRadius,
                                   Vector4f colorGl, Vector4f blackGl) {
        double radiusPixels = Math.max(effectiveRadius * wtsScale, MIN_CIRCLE_PIXELS * paintSizeMultiplier);

        // Pass 1: Black outline ring.
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawCircle(centerGl, (float) radiusPixels, blackGl, false);

        // Pass 2: Colored outline ring.
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawCircle(centerGl, (float) radiusPixels, colorGl, false);
    }

    /**
     * Draws the barrel direction line extending outward from the circle edge.
     * Follows the same double-pass pattern as SlotDrawer's angle pointer:
     * thick black outline beneath a thinner white/colored line.
     */
    private void drawBarrelLine(ShapeRenderer shapeRenderer, AffineTransform worldToScreen,
                                 double wtsScale, Vector2f centerGl, double effectiveRadius,
                                 Vector4f colorGl, Vector4f whiteGl, Vector4f blackGl) {
        Point2D position = this.pointPosition;
        double transformedAngle = Utility.transformAngle(this.angle);

        double barrelEndDist = effectiveRadius * BARREL_LENGTH_MULTIPLIER;

        // Compute line endpoints in world space, then transform to screen.
        DrawerMath.pointInDirection(position, transformedAngle, effectiveRadius, closestIntersection);
        DrawerMath.pointInDirection(position, transformedAngle, barrelEndDist, barrelTip);

        worldToScreen.transform(closestIntersection, sClosest);
        worldToScreen.transform(barrelTip, sBarrelTip);

        vClosest.set((float) sClosest.getX(), (float) sClosest.getY());
        vBarrelTip.set((float) sBarrelTip.getX(), (float) sBarrelTip.getY());

        // Pass 1: Black outline.
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawLine(vBarrelTip, vClosest, blackGl);

        // Pass 2: White/colored line.
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawLine(vBarrelTip, vClosest, whiteGl);
    }

}
