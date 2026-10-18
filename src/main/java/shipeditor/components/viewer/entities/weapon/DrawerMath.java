package shipeditor.components.viewer.entities.weapon;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Allocation-free geometry helpers shared by {@link SlotDrawer} and {@link OffsetDrawer}.
 */
final class DrawerMath {

    private DrawerMath() {
    }

    /**
     * Writes into {@code target} the point located {@code length} away from {@code startPoint}
     * in the direction of {@code angleDegrees}.
     */
    static void pointInDirection(Point2D startPoint, double angleDegrees, double length, Point2D target) {
        double angleRadians = Math.toRadians(angleDegrees);
        double deltaX = length * Math.cos(angleRadians);
        double deltaY = length * Math.sin(angleRadians);
        target.setLocation(startPoint.getX() + deltaX, startPoint.getY() + deltaY);
    }

    /**
     * Computes how many screen pixels a single world unit spans, using the two scratch points
     * (which are overwritten) to avoid per-frame allocations.
     */
    static double worldToScreenScale(AffineTransform worldToScreen, Point2D scratch0, Point2D scratch1) {
        scratch0.setLocation(0, 0);
        scratch1.setLocation(1, 0);
        Point2D transformedP0 = worldToScreen.transform(scratch0, scratch0);
        Point2D transformedP1 = worldToScreen.transform(scratch1, scratch1);
        return transformedP0.distance(transformedP1);
    }

}
