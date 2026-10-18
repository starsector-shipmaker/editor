package shipeditor.undo.edits.points;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.Events;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.entities.ShieldCenterPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.bays.LaunchBay;
import shipeditor.components.viewer.entities.bays.LaunchPortPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.MirrorablePointPainter;
import shipeditor.components.viewer.painters.points.ship.BoundPointsPainter;
import shipeditor.components.viewer.painters.points.ship.LaunchBayPainter;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;

import java.awt.geom.Point2D;
import java.util.List;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class PointEdits {

    private PointEdits() {
    }

    private static void queueViewerAndCenterPanelsRepaint() {
        var repainter = StaticController.getScheduler();
        repainter.queueViewerRepaint();
        repainter.queueCenterPanelsRepaint();
    }

    public static class BoundsListEdit extends AbstractEdit {
        private final BoundPointsPainter pointPainter;
        private final List<BoundPoint> oldList;
        private final List<BoundPoint> newList;
        private final String actionName;

        public BoundsListEdit(BoundPointsPainter painter, List<BoundPoint> old, List<BoundPoint> changed, String actionName) {
            this.pointPainter = painter;
            this.oldList = old;
            this.newList = changed;
            this.actionName = actionName;
        }

        @Override
        public void undo() {
            pointPainter.setBoundPoints(oldList);
            Events.repaintShipView();
        }

        @Override
        public void redo() {
            pointPainter.setBoundPoints(newList);
            Events.repaintShipView();
        }

        @Override
        public String getName() {
            return actionName;
        }
    }

    public static class BoundsSortEdit extends BoundsListEdit {
        public BoundsSortEdit(BoundPointsPainter painter, List<BoundPoint> old, List<BoundPoint> changed) {
            super(painter, old, changed, "Sort Bounds");
        }
    }

    public static class BoundsReplaceEdit extends BoundsListEdit {
        public BoundsReplaceEdit(BoundPointsPainter painter, List<BoundPoint> old, List<BoundPoint> changed) {
            super(painter, old, changed, "Auto-Generate Bounds");
        }
    }

    public static class CollisionRadiusEdit extends AbstractEdit {
        private final ShipCenterPoint parentPoint;
        private final float oldRadius;
        private final float newRadius;

        public CollisionRadiusEdit(ShipCenterPoint point, float oldValue, float newValue) {
            this.parentPoint = point;
            this.oldRadius = oldValue;
            this.newRadius = newValue;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            parentPoint.setCollisionRadius(oldRadius);
            queueViewerAndCenterPanelsRepaint();
        }

        @Override
        public void redo() {
            parentPoint.setCollisionRadius(newRadius);
            redoSubEdits();
            queueViewerAndCenterPanelsRepaint();
        }

        @Override
        public String getName() {
            return "Set Collision";
        }
    }

    @RequiredArgsConstructor
    public static class LaunchPortsSortEdit extends AbstractEdit {
        private final LaunchPortPoint portPoint;
        private final LaunchBay targetBay;
        private final LaunchBay oldParentBay;
        private final int targetIndex;
        private final int oldIndex;
        private int cachedBayIndex;

        @Override
        public void undo() {
            this.transferPort(targetBay, oldParentBay, oldIndex);
        }

        @Override
        public void redo() {
            this.transferPort(oldParentBay, targetBay, targetIndex);
        }

        public void transferPort(LaunchBay supplier, LaunchBay recipient, int index) {
            var oldBayPoints = supplier.getPortPoints();
            oldBayPoints.remove(portPoint);

            if (oldBayPoints.isEmpty()) {
                var painter = supplier.getBayPainter();
                List<LaunchBay> baysList = painter.getBaysList();
                cachedBayIndex = baysList.indexOf(supplier);
                painter.removeBay(supplier);
            }

            portPoint.setParentBay(recipient);
            var portPoints = recipient.getPortPoints();
            if (index != -1) {
                portPoints.add(index, portPoint);

                var recipientPainter = recipient.getBayPainter();
                List<LaunchBay> baysList = recipientPainter.getBaysList();
                if (!baysList.contains(recipient)) {
                    recipientPainter.insertBay(recipient, cachedBayIndex);
                }
            }

            if (StaticController.getEditorMode() == EditorInstrument.LAUNCH_BAYS) {
                var repainter = StaticController.getScheduler();
                repainter.queueViewerRepaint();
                repainter.queueBaysPanelRepaint();
            }
        }

        @Override
        public String getName() {
            return "Sort Launch Ports";
        }
    }

    public static class PointAdditionEdit extends AbstractEdit implements PointEdit {
        private AbstractPointPainter pointPainter;
        private BaseWorldPoint point;
        private final int insertionIndex;

        public PointAdditionEdit(AbstractPointPainter painter, BaseWorldPoint toAdd) {
            this(painter, toAdd, -1);
        }

        public PointAdditionEdit(AbstractPointPainter painter, BaseWorldPoint toAdd, int index) {
            this.pointPainter = painter;
            this.point = toAdd;
            this.insertionIndex = index;
        }

        @Override
        public WorldPoint getPoint() {
            return point;
        }

        @Override
        public void undo() {
            pointPainter.removePoint(point);
            Events.repaintShipView();
        }

        @SuppressWarnings("ChainOfInstanceofChecks")
        @Override
        public void redo() {
            if (insertionIndex == -1 || pointPainter instanceof LaunchBayPainter) {
                pointPainter.addPoint(point);
            } else if (pointPainter instanceof MirrorablePointPainter checked) {
                checked.insertPoint(point, insertionIndex);
            }
            Events.repaintShipView();
        }

        @Override
        public String getName() {
            String name = "Add Point";
            if (insertionIndex != -1) {
                name = "Insert Point";
            }
            return name;
        }

        @Override
        public LayerPainter getLayerPainter() {
            return point.getParent();
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(PointAdditionEdit.class);
            this.pointPainter = null;
            this.point = null;
        }
    }

    public static final class PointDragEdit extends AbstractEdit implements PointEdit {
        private WorldPoint point;
        private final Point2D oldPosition;
        private final Point2D newPosition;

        public PointDragEdit(WorldPoint worldPoint, Point2D oldInput, Point2D newInput) {
            this.point = worldPoint;
            this.oldPosition = oldInput;
            this.newPosition = newInput;
            this.setFinished(false);
        }

        public void adjustPositionOffset(Point2D offset) {
            oldPosition.setLocation(oldPosition.getX() - offset.getX(), oldPosition.getY() - offset.getY());
            newPosition.setLocation(newPosition.getX() - offset.getX(), newPosition.getY() - offset.getY());
        }

        @Override
        public String getName() {
            return "Point Drag";
        }

        @Override
        public void undo() {
            undoSubEdits();
            point.setPosition(oldPosition);
            PointDragEdit.repaintByPointType(point);
        }

        @Override
        public void redo() {
            point.setPosition(newPosition);
            redoSubEdits();
            PointDragEdit.repaintByPointType(point);
        }

        @Override
        public WorldPoint getPoint() {
            return point;
        }

        @Override
        public LayerPainter getLayerPainter() {
            return point.getParent();
        }

        public static void repaintByPointType(WorldPoint point) {
            EventBus.publish(new ViewerRepaintQueued());
            if (point == null) return;
            var repainter = StaticController.getScheduler();
            switch (point.getAssociatedMode()) {
                case BOUNDS -> repainter.queueBoundsPanelRepaint();
                case COLLISION, SHIELD -> repainter.queueCenterPanelsRepaint();
                case WEAPON_SLOTS -> repainter.queueSlotsPanelRepaint();
                case ENGINES -> repainter.queueEnginesPanelRepaint();
                case LAUNCH_BAYS -> repainter.queueBaysPanelRepaint();
                default -> {}
            }
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(PointDragEdit.class);
            this.point = null;
        }
    }

    @Log4j2
    @AllArgsConstructor
    public static class PointRemovalEdit extends AbstractEdit implements PointEdit {
        private AbstractPointPainter painter;
        private BaseWorldPoint removed;
        private final int indexOfRemoved;

        @Override
        public void undo() {
            if (painter instanceof MirrorablePointPainter checked && !(painter instanceof LaunchBayPainter)) {
                checked.insertPoint(removed, indexOfRemoved);
            } else {
                painter.addPoint(removed);
            }
            Events.repaintShipView();
        }

        @Override
        public WorldPoint getPoint() {
            return removed;
        }

        @Override
        public LayerPainter getLayerPainter() {
            return removed.getParent();
        }

        @Override
        public void redo() {
            painter.removePoint(removed);
            Events.repaintShipView();
        }

        @Override
        public String getName() {
            return "Remove Point";
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(PointRemovalEdit.class);
            this.painter = null;
            this.removed = null;
        }
    }

    @AllArgsConstructor
    public static class PointsFlippedEdit extends AbstractEdit {
        private List<BaseWorldPoint> points;
        private BaseWorldPoint anchor;

        @Override
        public void undo() {
            for (BaseWorldPoint point : points) {
                Utility.flipPointHorizontally(point, anchor);
            }
        }

        @Override
        public void redo() {
            for (BaseWorldPoint point : points) {
                Utility.flipPointHorizontally(point, anchor);
            }
        }

        @Override
        public String getName() {
            return "Flip Ship Points";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return anchor.getParent();
        }

        @Override
        public void cleanupReferences() {
            points = null;
            anchor = null;
        }
    }

    public static class ShieldRadiusEdit extends AbstractEdit {
        private final ShieldCenterPoint parentPoint;
        private final float oldRadius;
        private final float newRadius;

        public ShieldRadiusEdit(ShieldCenterPoint point, float oldValue, float newValue) {
            this.parentPoint = point;
            this.oldRadius = oldValue;
            this.newRadius = newValue;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            parentPoint.setShieldRadius(oldRadius);
            queueViewerAndCenterPanelsRepaint();
        }

        @Override
        public void redo() {
            parentPoint.setShieldRadius(newRadius);
            redoSubEdits();
            queueViewerAndCenterPanelsRepaint();
        }

        @Override
        public String getName() {
            return "Set Shield Radius";
        }
    }
}
