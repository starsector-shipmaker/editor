package shipeditor.components.viewer.entities.weapon;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import shipeditor.utility.overseers.StaticController;

import java.awt.Color;
import java.awt.geom.*;
import shipeditor.utility.graphics.GraphicConstants;

@SuppressWarnings("ClassWithTooManyFields")
@Getter
@Setter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class SlotDrawer {

    private SlotPoint parentPoint;

    private Point2D pointPosition;

    private WeaponMount mount;

    private WeaponSize size;

    private WeaponType type;

    private double angle;

    private double arc;

    private double paintSizeMultiplier = 1;

    private boolean drawArc = true;

    private boolean drawAngle = true;

    private boolean drawMount = true;

    // Pre-allocated rendering caches to prevent per-frame object allocation
    private final Point2D p0Screen = new Point2D.Double();
    private final Point2D p1Screen = new Point2D.Double();
    private final Point2D centerScreen = new Point2D.Double();
    private final org.joml.Vector4f colorGl = new org.joml.Vector4f();
    private final org.joml.Vector4f fillGl = new org.joml.Vector4f();
    private final org.joml.Vector4f arcFillGl = new org.joml.Vector4f();
    private final org.joml.Vector4f highlightGl = new org.joml.Vector4f();
    private final org.joml.Vector4f builtInGl = new org.joml.Vector4f();
    private final org.joml.Vector4f blackGl = new org.joml.Vector4f();
    private final org.joml.Vector4f whiteGl = new org.joml.Vector4f();
    private final org.joml.Vector2f centerGl = new org.joml.Vector2f();

    private final Point2D p0Cached = new Point2D.Double();
    private final Point2D p1Cached = new Point2D.Double();
    private final Point2D p2Cached = new Point2D.Double();
    private final Point2D p3Cached = new Point2D.Double();

    private final Point2D s0Cached = new Point2D.Double();
    private final Point2D s1Cached = new Point2D.Double();
    private final Point2D s2Cached = new Point2D.Double();
    private final Point2D s3Cached = new Point2D.Double();

    private final org.joml.Vector2f v0Cached = new org.joml.Vector2f();
    private final org.joml.Vector2f v1Cached = new org.joml.Vector2f();
    private final org.joml.Vector2f v2Cached = new org.joml.Vector2f();
    private final org.joml.Vector2f v3Cached = new org.joml.Vector2f();

    // Cache variables for drawArcGL
    private final Point2D arcStartEndpoint = new Point2D.Double();
    private final Point2D arcStartCirclePoint = new Point2D.Double();
    private final Point2D arcEndEndpoint = new Point2D.Double();
    private final Point2D arcEndCirclePoint = new Point2D.Double();

    private final Point2D sStartEndpoint = new Point2D.Double();
    private final Point2D sStartCirclePoint = new Point2D.Double();
    private final Point2D sEndEndpoint = new Point2D.Double();
    private final Point2D sEndCirclePoint = new Point2D.Double();

    private final org.joml.Vector2f vStartEndpoint = new org.joml.Vector2f();
    private final org.joml.Vector2f vStartCirclePoint = new org.joml.Vector2f();
    private final org.joml.Vector2f vEndEndpoint = new org.joml.Vector2f();
    private final org.joml.Vector2f vEndCirclePoint = new org.joml.Vector2f();

    // Cache variables for drawAnglePointerGL
    private final Point2D lineEndpoint = new Point2D.Double();
    private final Point2D closestIntersection = new Point2D.Double();
    private final Point2D sLineEndpoint = new Point2D.Double();
    private final Point2D sClosestIntersection = new Point2D.Double();
    private final org.joml.Vector2f vLineEndpoint = new org.joml.Vector2f();
    private final org.joml.Vector2f vClosestIntersection = new org.joml.Vector2f();

    public SlotDrawer(SlotPoint parent) {
        this.parentPoint = parent;
    }

    public void paintSlotVisuals(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        Point2D position = this.pointPosition;
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        double wtsScale = DrawerMath.worldToScreenScale(worldToScreen, p0Screen, p1Screen);

        double circleRadius = 0.10f * paintSizeMultiplier;
        double enlargedRadius = circleRadius * 1.65f;

        float alpha = 1.0f;
        boolean isSelected = false;
        boolean isHovered = false;
        boolean hasBuiltIn = false;
        if (parentPoint instanceof shipeditor.components.viewer.entities.BaseWorldPoint bwp) {
            isSelected = bwp.isPointSelected();
            isHovered = bwp.isCursorInBounds();
        }
        if (parentPoint instanceof WeaponSlotPoint weaponSlotPoint) {
            alpha = (float) weaponSlotPoint.getTransparency();
            hasBuiltIn = weaponSlotPoint.hasBuiltInWeapon();
        }

        Color mountColor = this.type.getColor();
        if (parentPoint != null) {
            mountColor = parentPoint.getCurrentColor();
        }

        float r = mountColor.getRed() / 255.0f;
        float g = mountColor.getGreen() / 255.0f;
        float b = mountColor.getBlue() / 255.0f;

        colorGl.set(r, g, b, mountColor.getAlpha() / 255.0f * alpha);

        float fillAlpha = (isSelected ? 0.40f : (isHovered ? 0.30f : 0.16f)) * alpha;
        fillGl.set(r, g, b, fillAlpha);

        float arcAlpha = (isSelected ? 0.14f : (isHovered ? 0.10f : 0.05f)) * alpha;
        arcFillGl.set(r, g, b, arcAlpha);

        highlightGl.set(1.0f, 0.95f, 0.6f, 0.85f * alpha);
        builtInGl.set(0.4f, 0.9f, 1.0f, 0.95f * alpha);

        blackGl.set(0.0f, 0.0f, 0.0f, 0.55f * alpha);
        whiteGl.set(1.0f, 1.0f, 1.0f, alpha);

        Point2D centerScreenPoint = worldToScreen.transform(position, centerScreen);
        centerGl.set((float) centerScreenPoint.getX(), (float) centerScreenPoint.getY());

        if (drawArc) {
            this.drawArcGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, colorGl, blackGl, arcFillGl, alpha);
        }

        if (drawMount) {
            this.drawMountShapeGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, enlargedRadius, colorGl, fillGl, highlightGl, blackGl, isSelected, isHovered);
        }

        if (drawAngle) {
            this.drawAnglePointerGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, colorGl, whiteGl, builtInGl, hasBuiltIn, alpha);
        }
    }

    private void drawMountShapeGL(ShapeRenderer shapeRenderer, AffineTransform worldToScreen, double wtsScale,
                                  org.joml.Vector2f centerGl, double circleRadius, double enlargedRadius,
                                  org.joml.Vector4f colorGl, org.joml.Vector4f fillGl, org.joml.Vector4f highlightGl,
                                  org.joml.Vector4f blackGl, boolean isSelected, boolean isHovered) {
        WeaponMount slotMount = this.mount;
        WeaponSize slotSize = this.size;

        this.paintMountGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, enlargedRadius, 1.0d, slotMount, colorGl, fillGl, highlightGl, blackGl, isSelected, isHovered, true);

        if (slotSize == WeaponSize.MEDIUM || slotSize == WeaponSize.LARGE) {
            double scaleMedium = 1.25d;
            this.paintMountGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, enlargedRadius, scaleMedium, slotMount, colorGl, fillGl, highlightGl, blackGl, isSelected, isHovered, false);
            if (slotSize == WeaponSize.LARGE) {
                double scaleLarge = 1.5d;
                this.paintMountGL(shapeRenderer, worldToScreen, wtsScale, centerGl, circleRadius, enlargedRadius, scaleLarge, slotMount, colorGl, fillGl, highlightGl, blackGl, isSelected, isHovered, false);
            }
        }
    }

    private void paintMountGL(ShapeRenderer shapeRenderer, AffineTransform worldToScreen, double wtsScale,
                              org.joml.Vector2f centerGl, double circleRadius, double enlargedRadius,
                              double scale, WeaponMount slotMount,
                              org.joml.Vector4f colorGl, org.joml.Vector4f fillGl, org.joml.Vector4f highlightGl,
                              org.joml.Vector4f blackGl, boolean isSelected, boolean isHovered, boolean drawFill) {
        
        Point2D centerScreenPoint = this.centerScreen;
        centerScreenPoint.setLocation(centerGl.x, centerGl.y);
        
        double effectiveHalfExtent = enlargedRadius * scale;
        double effectiveHalfExtentPixels = effectiveHalfExtent * wtsScale;
        
        double minRadiusPixels = 12.0 * paintSizeMultiplier;
        if (Double.compare(scale, 1.25d) == 0) minRadiusPixels = 14.0 * paintSizeMultiplier;
        if (Double.compare(scale, 1.5d) == 0) minRadiusPixels = 16.0 * paintSizeMultiplier;
        
        double finalHalfExtentPixels = Math.max(effectiveHalfExtentPixels, minRadiusPixels);
        double pixelScale = finalHalfExtentPixels / effectiveHalfExtentPixels;

        switch (slotMount) {
            case TURRET -> {
                double transformedAngle = Utility.transformAngle(this.angle);
                double halfArc = this.arc * 0.5d;
                double arcStartAngle = Math.toRadians(transformedAngle - halfArc);
                double arcRads = Math.toRadians(this.arc);

                if (drawFill) {
                    shapeRenderer.drawPartialCircle(centerGl, (float) finalHalfExtentPixels, fillGl, true, arcStartAngle, arcRads);
                }

                org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                shapeRenderer.drawPartialCircle(centerGl, (float) finalHalfExtentPixels, blackGl, false, arcStartAngle, arcRads);

                float lineWidth = isSelected ? GraphicConstants.LINE_WIDTH_NORMAL : (isHovered ? 2.2f : GraphicConstants.LINE_WIDTH_DEFAULT);
                org.lwjgl.opengl.GL11.glLineWidth(lineWidth);
                shapeRenderer.drawPartialCircle(centerGl, (float) finalHalfExtentPixels, colorGl, false, arcStartAngle, arcRads);

                if (isSelected && drawFill) {
                    org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                    shapeRenderer.drawCircle(centerGl, (float) (finalHalfExtentPixels + 3.0f), highlightGl, false);
                }
            }
            case HARDPOINT -> {
                double transformedAngle = Utility.transformAngle(this.angle);
                double diagDist = effectiveHalfExtent * Math.sqrt(2);
                DrawerMath.pointInDirection(pointPosition, transformedAngle + 225, diagDist, p0Cached);
                DrawerMath.pointInDirection(pointPosition, transformedAngle + 315, diagDist, p1Cached);
                DrawerMath.pointInDirection(pointPosition, transformedAngle + 45, diagDist, p2Cached);
                DrawerMath.pointInDirection(pointPosition, transformedAngle + 135, diagDist, p3Cached);

                worldToScreen.transform(p0Cached, s0Cached);
                worldToScreen.transform(p1Cached, s1Cached);
                worldToScreen.transform(p2Cached, s2Cached);
                worldToScreen.transform(p3Cached, s3Cached);

                v0Cached.set(centerGl.x + (float)((s0Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s0Cached.getY() - centerScreenPoint.getY()) * pixelScale));
                v1Cached.set(centerGl.x + (float)((s1Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s1Cached.getY() - centerScreenPoint.getY()) * pixelScale));
                v2Cached.set(centerGl.x + (float)((s2Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s2Cached.getY() - centerScreenPoint.getY()) * pixelScale));
                v3Cached.set(centerGl.x + (float)((s3Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s3Cached.getY() - centerScreenPoint.getY()) * pixelScale));

                if (drawFill) {
                    shapeRenderer.drawQuad(v0Cached, v1Cached, v2Cached, v3Cached, fillGl, true);
                }

                org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                shapeRenderer.drawLine(v0Cached, v1Cached, blackGl);
                shapeRenderer.drawLine(v1Cached, v2Cached, blackGl);
                shapeRenderer.drawLine(v2Cached, v3Cached, blackGl);
                shapeRenderer.drawLine(v3Cached, v0Cached, blackGl);

                float lineWidth = isSelected ? GraphicConstants.LINE_WIDTH_NORMAL : (isHovered ? 2.2f : GraphicConstants.LINE_WIDTH_DEFAULT);
                org.lwjgl.opengl.GL11.glLineWidth(lineWidth);
                shapeRenderer.drawLine(v0Cached, v1Cached, colorGl);
                shapeRenderer.drawLine(v1Cached, v2Cached, colorGl);
                shapeRenderer.drawLine(v2Cached, v3Cached, colorGl);
                shapeRenderer.drawLine(v3Cached, v0Cached, colorGl);

                if (isSelected && drawFill) {
                    org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                    shapeRenderer.drawCircle(centerGl, (float) (finalHalfExtentPixels + 3.0f), highlightGl, false);
                }
            }
            case HIDDEN -> {
                double transformedAngle = Utility.transformAngle(this.angle);
                double d = effectiveHalfExtent * (2.5 / 1.65);

                DrawerMath.pointInDirection(pointPosition, transformedAngle - 120, d, p0Cached);
                DrawerMath.pointInDirection(pointPosition, transformedAngle, d, p1Cached);
                DrawerMath.pointInDirection(pointPosition, transformedAngle + 120, d, p2Cached);

                worldToScreen.transform(p0Cached, s0Cached);
                worldToScreen.transform(p1Cached, s1Cached);
                worldToScreen.transform(p2Cached, s2Cached);

                v0Cached.set(centerGl.x + (float)((s0Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s0Cached.getY() - centerScreenPoint.getY()) * pixelScale));
                v1Cached.set(centerGl.x + (float)((s1Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s1Cached.getY() - centerScreenPoint.getY()) * pixelScale));
                v2Cached.set(centerGl.x + (float)((s2Cached.getX() - centerScreenPoint.getX()) * pixelScale), centerGl.y + (float)((s2Cached.getY() - centerScreenPoint.getY()) * pixelScale));

                if (drawFill) {
                    shapeRenderer.drawTriangle(v0Cached, v1Cached, v2Cached, fillGl, true);
                }

                org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                shapeRenderer.drawLine(v0Cached, v1Cached, blackGl);
                shapeRenderer.drawLine(v1Cached, v2Cached, blackGl);
                shapeRenderer.drawLine(v2Cached, v0Cached, blackGl);

                float lineWidth = isSelected ? GraphicConstants.LINE_WIDTH_NORMAL : (isHovered ? 2.2f : GraphicConstants.LINE_WIDTH_DEFAULT);
                org.lwjgl.opengl.GL11.glLineWidth(lineWidth);
                shapeRenderer.drawLine(v0Cached, v1Cached, colorGl);
                shapeRenderer.drawLine(v1Cached, v2Cached, colorGl);
                shapeRenderer.drawLine(v2Cached, v0Cached, colorGl);

                if (isSelected && drawFill) {
                    org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
                    shapeRenderer.drawCircle(centerGl, (float) (finalHalfExtentPixels + 3.0f), highlightGl, false);
                }
            }
        }
    }

    private void drawArcGL(ShapeRenderer shapeRenderer, AffineTransform worldToScreen, double wtsScale,
                           org.joml.Vector2f centerGl, double circleRadius,
                           org.joml.Vector4f colorGl, org.joml.Vector4f blackGl,
                           org.joml.Vector4f arcFillGl, float alpha) {
        Point2D position = this.pointPosition;
        double slotArc = this.arc;
        double halfArc = slotArc * 0.5d;
        double transformedAngle = Utility.transformAngle(this.angle);

        double arcStartAngle = transformedAngle - halfArc;
        double arcEndAngle = transformedAngle + halfArc;

        double lineLength = 0.55f * paintSizeMultiplier;

        double effectiveCircleRadius = circleRadius;
        double effectiveLineLength = lineLength;
        double effectiveArcRadius = 0.40f * paintSizeMultiplier;

        DrawerMath.pointInDirection(position, arcStartAngle, effectiveLineLength, arcStartEndpoint);
        DrawerMath.pointInDirection(position, arcStartAngle, effectiveCircleRadius, arcStartCirclePoint);
        DrawerMath.pointInDirection(position, arcEndAngle, effectiveLineLength, arcEndEndpoint);
        DrawerMath.pointInDirection(position, arcEndAngle, effectiveCircleRadius, arcEndCirclePoint);

        worldToScreen.transform(arcStartEndpoint, sStartEndpoint);
        worldToScreen.transform(arcStartCirclePoint, sStartCirclePoint);
        worldToScreen.transform(arcEndEndpoint, sEndEndpoint);
        worldToScreen.transform(arcEndCirclePoint, sEndCirclePoint);

        vStartEndpoint.set((float) sStartEndpoint.getX(), (float) sStartEndpoint.getY());
        vStartCirclePoint.set((float) sStartCirclePoint.getX(), (float) sStartCirclePoint.getY());
        vEndEndpoint.set((float) sEndEndpoint.getX(), (float) sEndEndpoint.getY());
        vEndCirclePoint.set((float) sEndCirclePoint.getX(), (float) sEndCirclePoint.getY());

        double startAngleRads = Math.toRadians(transformedAngle - halfArc);
        double arcRads = Math.toRadians(slotArc);
        float screenArcRadius = (float) (effectiveArcRadius * wtsScale);

        // Subtle filled arc sector
        shapeRenderer.drawPartialCircle(centerGl, screenArcRadius, arcFillGl, true, startAngleRads, arcRads);

        // Arc radial boundary lines
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawLine(vStartEndpoint, vStartCirclePoint, blackGl);
        shapeRenderer.drawLine(vEndEndpoint, vEndCirclePoint, blackGl);

        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawLine(vStartEndpoint, vStartCirclePoint, colorGl);
        shapeRenderer.drawLine(vEndEndpoint, vEndCirclePoint, colorGl);

        // Outer curved rim
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawPartialCircle(centerGl, screenArcRadius, blackGl, false, startAngleRads, arcRads);

        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawPartialCircle(centerGl, screenArcRadius, colorGl, false, startAngleRads, arcRads);
    }

    private void drawAnglePointerGL(ShapeRenderer shapeRenderer, AffineTransform worldToScreen, double wtsScale,
                                     org.joml.Vector2f centerGl, double circleRadius,
                                     org.joml.Vector4f colorGl, org.joml.Vector4f whiteGl,
                                     org.joml.Vector4f builtInGl, boolean hasBuiltIn, float alpha) {
        Point2D position = this.pointPosition;
        double transformedAngle = Utility.transformAngle(this.angle);

        double effectiveRadius = circleRadius;
        double effectivePointerStart = effectiveRadius * 5.0;

        DrawerMath.pointInDirection(position, transformedAngle, effectivePointerStart, lineEndpoint);
        DrawerMath.pointInDirection(position, transformedAngle, effectiveRadius, closestIntersection);

        worldToScreen.transform(lineEndpoint, sLineEndpoint);
        worldToScreen.transform(closestIntersection, sClosestIntersection);

        vLineEndpoint.set((float) sLineEndpoint.getX(), (float) sLineEndpoint.getY());
        vClosestIntersection.set((float) sClosestIntersection.getX(), (float) sClosestIntersection.getY());

        blackGl.set(0.0f, 0.0f, 0.0f, 0.4f * alpha);

        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawLine(vLineEndpoint, vClosestIntersection, blackGl);

        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawLine(vLineEndpoint, vClosestIntersection, whiteGl);

        float centerRadiusPixels = (float) (effectiveRadius * wtsScale);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
        shapeRenderer.drawCircle(centerGl, centerRadiusPixels, blackGl, false);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        shapeRenderer.drawCircle(centerGl, centerRadiusPixels, colorGl, false);

        // Center dot / Built-in indicator
        if (hasBuiltIn) {
            float diamondSize = Math.max(centerRadiusPixels * 0.7f, 4.0f);
            shapeRenderer.drawCircle(centerGl, diamondSize, builtInGl, true);
            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THIN);
            shapeRenderer.drawCircle(centerGl, diamondSize, whiteGl, false);
        } else {
            shapeRenderer.drawCircle(centerGl, Math.max(centerRadiusPixels * 0.45f, 2.0f), colorGl, true);
        }
    }

}
