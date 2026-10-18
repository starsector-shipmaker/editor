package shipeditor.components.viewer.painters.points.ship;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.viewer.painters.GlColors;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.BoundPointsSorted;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.MirrorablePointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import shipeditor.utility.graphics.GraphicConstants;
import shipeditor.communication.events.viewer.points.PointEvents.BoundInsertedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class BoundPointsPainter extends MirrorablePointPainter {

    private static final Color BOUND_LINE = Color.WHITE;

    // Cached rendering objects to avoid per-frame allocations.
    private final org.joml.Vector2f cachedStartVec = new org.joml.Vector2f();
    private final org.joml.Vector2f cachedFinishVec = new org.joml.Vector2f();
    private final org.joml.Vector4f cachedOutlineColor = new org.joml.Vector4f();
    private final org.joml.Vector4f cachedLineColor = new org.joml.Vector4f();
    private static final org.joml.Vector2f CACHED_CIRCLE_POS = new org.joml.Vector2f();
    private static final org.joml.Vector4f CACHED_CIRCLE_OUTLINE = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
    private static final org.joml.Vector4f CACHED_CIRCLE_FILL = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    @Setter
    private List<BoundPoint> boundPoints;

    @Getter
    private static boolean appendBoundHotkeyPressed;

    @Getter
    private static boolean insertBoundHotkeyPressed;

    private static final int APPEND_BOUND_HOTKEY = KeyEvent.VK_SHIFT;
    private static final int INSERT_BOUND_HOTKEY = KeyEvent.VK_CONTROL;


    public BoundPointsPainter(ShipPainter parent) {
        super(parent);
        this.boundPoints = new ArrayList<>();
        this.initHotkeys();
        this.initPointListening();
    }

    @Override
    public BoundPoint getSelected() {
        return (BoundPoint) super.getSelected();
    }

    @Override
    protected EditorInstrument getInstrumentType() {
        return EditorInstrument.BOUNDS;
    }

    @Override
    public void cleanupListeners() {
        super.cleanupListeners();
    }

    @Override
    protected Class<BoundPoint> getTypeReference() {
        return BoundPoint.class;
    }

    @Override
    public List<BoundPoint> getPointsIndex() {
        return boundPoints;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        if (point instanceof BoundPoint checked) {
            boundPoints.add(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        if (point instanceof BoundPoint checked) {
            boundPoints.remove(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        if (point instanceof BoundPoint checked) {
            return boundPoints.indexOf(checked);
        } else {
            throwIllegalPoint();
            return -1;
        }
    }

    private void initHotkeys() {
        this.subscribeHotkeyPair(APPEND_BOUND_HOTKEY, INSERT_BOUND_HOTKEY, BoundPointsPainter::setHotkeyState);
    }

    private void initPointListening() {
        BusEventListener boundsSortingListener = event -> {
            if (event instanceof BoundPointsSorted checked) {
                if (!isInteractionEnabled()) return;
                EditDispatch.postBoundsRearranged(this, this.boundPoints, checked.rearranged());
            }
        };
        EventBus.subscribe(this, boundsSortingListener);
    }

    protected void handleCreation(PointCreationQueued event) {
        ShipPainter parentLayer = (ShipPainter) this.getParentLayer();
        Point2D position = event.position();
        boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();
        if (insertBoundHotkeyPressed) {
            if (boundPoints.size() >= 2) {
                BoundPoint preceding = getInsertBefore(position);
                BoundPoint wrapped = new BoundPoint(position, parentLayer);
                BoundPoint wrappedCounterpart = null;
                BoundPoint precedingCounterpart = null;
                if (mirrorMode) {
                    if (getMirroredCounterpart(wrapped) == null) {
                        Point2D counterpartPosition = createCounterpartPosition(position);
                        precedingCounterpart = getInsertBefore(counterpartPosition);
                        wrappedCounterpart = new BoundPoint(counterpartPosition, parentLayer);
                    }
                }
                EditDispatch.postPointInserted(this, wrapped, boundPoints.indexOf(preceding));
                if (wrappedCounterpart != null) {
                    EditDispatch.postPointInserted(this, wrappedCounterpart,
                            boundPoints.indexOf(precedingCounterpart));
                }
            } else {
                commencePointAppend(position, parentLayer, mirrorMode);
            }
        } else if (appendBoundHotkeyPressed) {
            commencePointAppend(position, parentLayer, mirrorMode);
        }
    }

    private void commencePointAppend(Point2D position, ShipPainter parentLayer, boolean mirrorMode) {
        BoundPoint wrapped = new BoundPoint(position, parentLayer);
        EditDispatch.postPointAdded(this, wrapped);
        if (mirrorMode) {
            if (getMirroredCounterpart(wrapped) == null) {
                Point2D counterpartPosition = createCounterpartPosition(position);
                BoundPoint wrappedCounterpart = new BoundPoint(counterpartPosition, parentLayer);
                EditDispatch.postPointInserted(this, wrappedCounterpart, 0);
            }
        }
    }

    private BoundPoint getInsertBefore(Point2D position) {
        List<BoundPoint> twoClosest = findClosestBoundPoints(position);
        return twoClosest.get(1);
    }

    public void insertPoint(BaseWorldPoint toInsert, int precedingIndex) {
        if (toInsert instanceof BoundPoint checked) {
            boundPoints.add(precedingIndex, checked);
            EventBus.publish(new BoundInsertedConfirmed(checked, precedingIndex));
            log.info("Bound inserted to painter: {}", checked);
        }
        else {
            throwIllegalPoint();
        }
    }

    private static void setHotkeyState(boolean isAppendHotkey, boolean state) {
        if (isAppendHotkey) {
            appendBoundHotkeyPressed = state;
        } else {
            insertBoundHotkeyPressed = state;
        }
    }

    /**
     * Returns a list of the two BoundPoints in this list that are closest to the given point.
     * @param point The point to find the closest points to.
     * @return list of the two BoundPoints in this list that are closest to the given point.
     */
    private List<BoundPoint> findClosestBoundPoints(Point2D point) {
        double minDist = Double.MAX_VALUE;
        List<BoundPoint> closestPoints = new ArrayList<>(2);
        List<BoundPoint> bounds = this.boundPoints;
        int numPoints = bounds.size();
        for (int i = 0; i < numPoints; i++) {
            BoundPoint currentPoint = bounds.get(i);
            // Wrap around to the first point if it's the last segment.
            BoundPoint nextPoint = bounds.get((i + 1) % numPoints);
            Line2D segment = new Line2D.Double(currentPoint.getPosition(), nextPoint.getPosition());
            double dist = segment.ptSegDist(point);
            if (dist < minDist) {
                minDist = dist;
                closestPoints.clear();
                closestPoints.add(currentPoint);
                closestPoints.add(nextPoint);
            }
        }
        return closestPoints;
    }

    @Override
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        List<BoundPoint> bPoints = this.boundPoints;
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        if (bPoints.isEmpty()) {
            this.paintIfBoundsEmpty(shapeRenderer, worldToScreen);
            return;
        }
        float alpha = this.getPaintOpacity();
        BoundPoint boundPoint = bPoints.get(bPoints.size() - 1);
        Point2D prev = worldToScreen.transform(boundPoint.getPosition(), null);
        for (BoundPoint p : bPoints) {
            Point2D curr = worldToScreen.transform(p.getPosition(), null);
            this.drawBoundLine(shapeRenderer, prev, curr, BOUND_LINE, alpha);
            prev = curr;
        }
        BoundPoint anotherBoundPoint = bPoints.get(0);
        Point2D first = worldToScreen.transform(anotherBoundPoint.getPosition(), null);

        this.drawBoundLine(shapeRenderer, prev, first, Color.GREEN, alpha);

        boolean hotkeyPressed = appendBoundHotkeyPressed || insertBoundHotkeyPressed;
        if (isInteractionEnabled() && hotkeyPressed) {
            this.paintCreationGuidelines(shapeRenderer, worldToScreen, prev, first, alpha);
        }
    }

    private void drawBoundLine(ShapeRenderer shapeRenderer, Point2D start, Point2D finish, Color color, float alpha) {
        cachedStartVec.set((float) start.getX(), (float) start.getY());
        cachedFinishVec.set((float) finish.getX(), (float) finish.getY());

        cachedOutlineColor.set(0.0f, 0.0f, 0.0f, alpha);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_THICK);
        shapeRenderer.drawLine(cachedStartVec, cachedFinishVec, cachedOutlineColor);

        GlColors.set(cachedLineColor, color, alpha);
        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_NORMAL);
        shapeRenderer.drawLine(cachedStartVec, cachedFinishVec, cachedLineColor);

        org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
    }

    private void paintIfBoundsEmpty(ShapeRenderer shapeRenderer, AffineTransform worldToScreen) {
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D finalWorldCursor = screenToWorld.transform(StaticController.getRawCursor(), null);
        if (ControlPredicates.isCursorSnappingEnabled()) {
            Point2D cursor = StaticController.getAdjustedCursor();
            finalWorldCursor = Utility.correctAdjustedCursor(cursor, screenToWorld);
        }
        Point2D worldCounterpart = this.createCounterpartPosition(finalWorldCursor);
        boolean hotkeyPressed = appendBoundHotkeyPressed || insertBoundHotkeyPressed;
        if (!isInteractionEnabled() || !hotkeyPressed) return;
        float alpha = this.getPaintOpacity();
        if (ControlPredicates.isMirrorModeEnabled()) {
            Point2D adjustedScreenCursor = worldToScreen.transform(finalWorldCursor, null);
            Point2D adjustedScreenCounterpart = worldToScreen.transform(worldCounterpart, null);
            this.drawBoundLine(shapeRenderer, adjustedScreenCursor, adjustedScreenCounterpart, BOUND_LINE, alpha);
            BoundPointsPainter.paintProspectiveBound(shapeRenderer, worldToScreen, worldCounterpart);
        }
        BoundPointsPainter.paintProspectiveBound(shapeRenderer, worldToScreen, finalWorldCursor);
    }

    private void paintCreationGuidelines(ShapeRenderer shapeRenderer, AffineTransform worldToScreen,
                                         Point2D prev, Point2D first, float alpha) {
        Point2D finalWorldCursor = StaticController.getFinalWorldCursor();
        Point2D finalScreenCursor = worldToScreen.transform(finalWorldCursor, null);
        Point2D worldCounterpart = this.createCounterpartPosition(finalWorldCursor);
        Point2D adjustedScreenCounterpart = worldToScreen.transform(worldCounterpart, null);
        boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();
        if (appendBoundHotkeyPressed) {
            if (mirrorMode) {
                this.drawBoundLine(shapeRenderer, prev, finalScreenCursor, BOUND_LINE, alpha);
                this.drawBoundLine(shapeRenderer, finalScreenCursor, adjustedScreenCounterpart, BOUND_LINE, alpha);
                this.drawBoundLine(shapeRenderer, adjustedScreenCounterpart, first, BOUND_LINE, alpha);
            }
            else {
                this.drawGuidelines(shapeRenderer, prev, first, finalScreenCursor, alpha);
            }
        }
        else if (insertBoundHotkeyPressed) {
            this.handleInsertionGuidelines(shapeRenderer, worldToScreen,
                    finalWorldCursor, worldCounterpart, alpha);
        }
        // Also paint dots where the points would be placed.
        BoundPointsPainter.paintProspectiveBound(shapeRenderer, worldToScreen, finalWorldCursor);
        if (mirrorMode) {
            BoundPointsPainter.paintProspectiveBound(shapeRenderer, worldToScreen, worldCounterpart);
        }
    }

    private static void paintProspectiveBound(ShapeRenderer shapeRenderer, AffineTransform worldToScreen, Point2D position) {
        Point2D screenLoc = worldToScreen.transform(position, null);
        CACHED_CIRCLE_POS.set((float) screenLoc.getX(), (float) screenLoc.getY());
        float radius = 6.0f;
        shapeRenderer.drawCircle(CACHED_CIRCLE_POS, radius, CACHED_CIRCLE_OUTLINE, true);
        shapeRenderer.drawCircle(CACHED_CIRCLE_POS, radius - 1.5f, CACHED_CIRCLE_FILL, true);
    }

    private void handleInsertionGuidelines(ShapeRenderer shapeRenderer, AffineTransform worldToScreen,
                                           Point2D adjustedWorldCursor, Point2D worldCounterpart, float alpha) {
        List<BoundPoint> closest = this.findClosestBoundPoints(adjustedWorldCursor);
        BoundPoint precedingPoint = closest.get(1);
        Point2D preceding = worldToScreen.transform(precedingPoint.getPosition(), null);
        BoundPoint subsequentPoint = closest.get(0);
        Point2D subsequent = worldToScreen.transform(subsequentPoint.getPosition(), null);
        Point2D transformed = worldToScreen.transform(adjustedWorldCursor, null);

        List<BoundPoint> closestToCounterpart = this.findClosestBoundPoints(worldCounterpart);
        BoundPoint precedingToCounterpart = closestToCounterpart.get(1);
        Point2D precedingTC = worldToScreen.transform(precedingToCounterpart.getPosition(), null);
        BoundPoint subsequentToCounterpart = closestToCounterpart.get(0);
        Point2D subsequentTC = worldToScreen.transform(subsequentToCounterpart.getPosition(), null);
        Point2D transformedCounterpart = worldToScreen.transform(worldCounterpart, null);

        boolean crossingEmerged = preceding.equals(precedingTC) || subsequent.equals(subsequentTC);

        if (ControlPredicates.isMirrorModeEnabled()) {
            if (crossingEmerged) {
                this.drawBoundLine(shapeRenderer, subsequent, transformed, BOUND_LINE, alpha);
                this.drawBoundLine(shapeRenderer, transformed, transformedCounterpart, BOUND_LINE, alpha);
                this.drawBoundLine(shapeRenderer, transformedCounterpart, preceding, BOUND_LINE, alpha);
            } else {
                this.drawGuidelines(shapeRenderer, preceding, subsequent, transformed, alpha);
                this.drawGuidelines(shapeRenderer, precedingTC, subsequentTC, transformedCounterpart, alpha);
            }
        } else {
            this.drawGuidelines(shapeRenderer, preceding, subsequent, transformed, alpha);
        }
    }

    private void drawGuidelines(ShapeRenderer shapeRenderer, Point2D preceding, Point2D subsequent, Point2D cursor, float alpha) {
        this.drawBoundLine(shapeRenderer, preceding, cursor, BOUND_LINE, alpha);
        this.drawBoundLine(shapeRenderer, subsequent, cursor, BOUND_LINE, alpha);
    }

}
