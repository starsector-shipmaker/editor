package shipeditor.components.viewer.painters;

import org.joml.Matrix4f;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.TextRenderer;
import shipeditor.utility.overseers.StaticController;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;

/**
 * World-anchored coordinate label that fades in once the viewer is zoomed in far enough.
 */
public final class ZoomFadedLabel {

    private static final double MIN_ZOOM = 20.0;
    private static final double FADE_RANGE = 20.0;
    private static final int FONT_SIZE = 14;

    private ZoomFadedLabel() {
    }

    /**
     * @return true when the current zoom level is high enough for the label to be drawn.
     */
    public static boolean isVisible() {
        return StaticController.getZoomLevel() > MIN_ZOOM;
    }

    /**
     * Draws the label at the world position with an alpha derived from the zoom level.
     * Does nothing below the minimum zoom. The caller is responsible for ending any active
     * ShapeRenderer batch before calling this.
     */
    public static void draw(SpriteRenderer spriteRenderer, Matrix4f projection, String text, Point2D worldAnchor) {
        double zoomLevel = StaticController.getZoomLevel();
        if (zoomLevel > MIN_ZOOM) {
            float alpha = (float) ((zoomLevel - MIN_ZOOM) / FADE_RANGE);
            alpha = Math.min(alpha, 1.0f);
            Font font = Utility.getOrbitron(FONT_SIZE);
            TextRenderer.drawTextGL(spriteRenderer, projection, text, font, Color.WHITE, worldAnchor, alpha);
        }
    }

}
