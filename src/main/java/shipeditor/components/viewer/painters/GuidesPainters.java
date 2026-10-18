package shipeditor.components.viewer.painters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import shipeditor.utility.graphics.opengl.OpenGLPainter;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import shipeditor.utility.graphics.opengl.TextRenderer;
import org.joml.Matrix4f;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerGuidesToggled;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.BoundPointsPainter;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.Utility;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import shipeditor.utility.graphics.GraphicConstants;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class GuidesPainters {

    @Getter
    private final OpenGLPainter guidesPaint;
    @Getter
    private final OpenGLPainter bordersPaint;
    @Getter
    private final OpenGLPainter centerPaint;
    @Getter
    private final OpenGLPainter pixelGridPaint;

    private boolean drawGuides;
    private boolean drawBorders;
    private boolean drawCenter;

    private final PrimaryViewer parent;

    // Pre-allocated vectors for guides painting
    private final org.joml.Vector4f guideColor = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 0.25f);
    private final org.joml.Vector4f guideLineColor = new org.joml.Vector4f(0.14f, 0.14f, 0.14f, 0.5f);
    private final org.joml.Vector4f blackColor = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
    private final org.joml.Vector4f whiteColor = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    private final org.joml.Vector2f v1 = new org.joml.Vector2f();
    private final org.joml.Vector2f v2 = new org.joml.Vector2f();

    public GuidesPainters(PrimaryViewer viewer) {
        this.parent = viewer;
        this.listenForToggling();

        this.guidesPaint = createGuidesPainter();
        this.bordersPaint = createBordersPainter();
        this.centerPaint = createSpriteCenterPainter();
        this.pixelGridPaint = new PixelGridPainter();
    }

    private void listenForToggling() {
        EventBus.subscribe(this, event -> {
            if (event instanceof ViewerGuidesToggled checked) {
                this.drawGuides = checked.guidesEnabled();
                this.drawBorders = checked.bordersEnabled();
                this.drawCenter = checked.centerEnabled();
                EventBus.publish(new ViewerRepaintQueued());
            }
        });
    }

    private OpenGLPainter createGuidesPainter() {
        return (spriteRenderer, shapeRenderer, projection, view) -> {
            if (!drawGuides) return;
            LayerPainter layer = parent.getSelectedLayer();
            if (layer == null || layer.getSprite() == null) return;
            
            Point2D anchor = layer.getAnchor();
            double spriteW = layer.getSpriteWidth();
            double spriteH = layer.getSpriteHeight();
            
            Point2D finalWorldCursor = StaticController.getFinalWorldCursor();
            double cx = finalWorldCursor.getX();
            double cy = finalWorldCursor.getY();
            
            double xLeft = anchor.getX() - 0.5;
            double yTop = anchor.getY() - 0.5;
            
            double xGuide = cx - 0.5;
            double yGuide = cy - 0.5;
            
            if (ControlPredicates.isCursorSnappingEnabled()) {
                xLeft = Math.round(xLeft * 2) / 2.0;
                yTop = Math.round(yTop * 2) / 2.0;
                xGuide = Math.round((cx - 0.5) * 2) / 2.0;
                yGuide = Math.round((cy - 0.5) * 2) / 2.0;
            }
            
            double crossCenterX = xGuide + 0.5;
            double crossCenterY = yGuide + 0.5;

            shapeRenderer.begin(projection, view);

            v1.set((float)(xLeft + 0.5), (float)yGuide);
            v2.set((float)spriteW, 1.0f);
            shapeRenderer.drawRect(v1, v2, guideColor, true);
            shapeRenderer.drawRect(v1, v2, guideLineColor, false);
            
            v1.set((float)xGuide, (float)(yTop + 0.5));
            v2.set(1.0f, (float)spriteH);
            shapeRenderer.drawRect(v1, v2, guideColor, true);
            shapeRenderer.drawRect(v1, v2, guideLineColor, false);

            v1.set((float)xGuide, (float)yGuide);
            v2.set(1.0f, 1.0f);

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_NORMAL);
            shapeRenderer.drawRect(v1, v2, blackColor, false);
            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
            shapeRenderer.drawRect(v1, v2, whiteColor, false);

            float crossSize = 0.15f;
            shapeRenderer.drawLine(v1.set((float)(crossCenterX - crossSize), (float)crossCenterY),
                                   v2.set((float)(crossCenterX + crossSize), (float)crossCenterY), blackColor);
            shapeRenderer.drawLine(v1.set((float)crossCenterX, (float)(crossCenterY - crossSize)),
                                   v2.set((float)crossCenterX, (float)(crossCenterY + crossSize)), blackColor);

            shapeRenderer.end();

            drawPointPositionHint(spriteRenderer, projection, new Point2D.Double(cx, cy), layer);
        };
    }

    private OpenGLPainter createBordersPainter() {
        return new BordersPainter();
    }

    private OpenGLPainter createSpriteCenterPainter() {
        return new SpriteCenterPainter();
    }

    private static void drawPointPositionHint(SpriteRenderer spriteRenderer, Matrix4f projection, Point2D position, LayerPainter painter) {
        if (StaticController.getEditorMode() == EditorInstrument.BOUNDS) {
            Font hintFont = Utility.getOrbitron(12);
            if (!(painter instanceof ShipPainter checkedPainter)) return;
            BoundPointsPainter boundsPainter = checkedPainter.getBoundsPainter();
            if (boundsPainter == null) return;
            WorldPoint selected = boundsPainter.getSelected();
            if (selected == null) return;
            Point2D boundPosition = selected.getCoordinatesForDisplay();

            String toDraw = boundPosition.getX() + ", " + boundPosition.getY();
            
            AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
            Point2D screenPosition = worldToScreen.transform(position, null);
            double x = screenPosition.getX();
            double y = screenPosition.getY();
            
            TextRenderer.drawTextScreenGL(
                spriteRenderer, projection, toDraw, hintFont, Color.WHITE, (float)(x + 20), (float)(y + 14)
            );
        }
    }

    private class SpriteCenterPainter implements OpenGLPainter {

        @Override
        public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
            if (!drawCenter) return;
            LayerPainter layer = parent.getSelectedLayer();
            if (layer == null || layer.getSprite() == null) return;
            Point2D spriteCenter = layer.getSpriteCenter();
            
            AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
            Point2D screenPos = worldToScreen.transform(spriteCenter, null);
            
            shapeRenderer.begin(projection, new Matrix4f());

            float sx = (float) screenPos.getX();
            float sy = (float) screenPos.getY();
            float screenSize = 6.0f; 

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_NORMAL);
            shapeRenderer.drawLine(v1.set(sx - screenSize, sy), v2.set(sx + screenSize, sy), blackColor);
            shapeRenderer.drawLine(v1.set(sx, sy - screenSize), v2.set(sx, sy + screenSize), blackColor);

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
            shapeRenderer.drawLine(v1.set(sx - screenSize, sy), v2.set(sx + screenSize, sy), whiteColor);
            shapeRenderer.drawLine(v1.set(sx, sy - screenSize), v2.set(sx, sy + screenSize), whiteColor);
            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);

            shapeRenderer.end();
            
            WorldPoint pointInput = new BaseWorldPoint(spriteCenter);
            Point2D toDisplay = pointInput.getCoordinatesForDisplay();
            String spriteCenterCoords = "Sprite Center (" + toDisplay.getX() + ", " + toDisplay.getY() + ")";
            
            ZoomFadedLabel.draw(spriteRenderer, projection, spriteCenterCoords, spriteCenter);
        }

    }

    private class BordersPainter implements OpenGLPainter {

        @Override
        public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
            if (!drawBorders) return;
            LayerPainter layer = parent.getSelectedLayer();
            if (layer == null || layer.getSprite() == null) return;
            int width = layer.getSpriteWidth();
            int height = layer.getSpriteHeight();
            Point2D layerAnchor = layer.getAnchor();
            
            v1.set((float) layerAnchor.getX(), (float) layerAnchor.getY());
            v2.set((float) width, (float) height);

            shapeRenderer.begin(projection, view);

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_NORMAL);
            shapeRenderer.drawRect(v1, v2, blackColor, false);

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
            shapeRenderer.drawRect(v1, v2, whiteColor, false);

            shapeRenderer.end();
        }

    }


    private class PixelGridPainter implements OpenGLPainter {
        private final org.joml.Vector4f gridColor = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 0.0f);
        private final org.joml.Vector2f v1 = new org.joml.Vector2f();
        private final org.joml.Vector2f v2 = new org.joml.Vector2f();

        @Override
        public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
            if (!drawGuides) return;
            double zoomLevel = StaticController.getZoomLevel();
            // Start fading in at zoom level 15, fully visible at zoom 25
            if (zoomLevel < 15.0) return;

            LayerPainter layer = parent.getSelectedLayer();
            if (layer == null || layer.getSprite() == null) return;

            double width = layer.getSpriteWidth();
            double height = layer.getSpriteHeight();
            Point2D anchor = layer.getAnchor();

            float startX = (float) anchor.getX();
            float startY = (float) anchor.getY();
            float endX = startX + (float) width;
            float endY = startY + (float) height;

            float alpha = (float) ((zoomLevel - 15.0) / 10.0);
            alpha = Math.min(alpha, 1.0f);
            gridColor.w = 0.25f * alpha; // 25% opacity max

            shapeRenderer.begin(projection, view);
            org.lwjgl.opengl.GL11.glLineWidth(1.0f);

            // Draw vertical lines
            for (int x = 0; x <= width; x++) {
                v1.set(startX + x, startY);
                v2.set(startX + x, endY);
                shapeRenderer.drawLine(v1, v2, gridColor);
            }

            // Draw horizontal lines
            for (int y = 0; y <= height; y++) {
                v1.set(startX, startY + y);
                v2.set(endX, startY + y);
                shapeRenderer.drawLine(v1, v2, gridColor);
            }

            shapeRenderer.end();
        }
    }
}
