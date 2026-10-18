package shipeditor.undo.edits.points.engines;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.painters.points.ship.EngineSlotPainter;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.objects.Size2D;
import shipeditor.utility.overseers.StaticController;

import java.util.List;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class EngineEdits {

    private EngineEdits() {
    }

    private static void queueEngineRepaint() {
        var repainter = StaticController.getScheduler();
        repainter.queueViewerRepaint();
        repainter.queueEnginesPanelRepaint();
    }

    public static class EngineAngleSet extends AbstractEdit {
        private final EnginePoint enginePoint;
        private final double oldAngle;
        private final double updatedAngle;

        public EngineAngleSet(EnginePoint point, double old, double updated) {
            this.enginePoint = point;
            this.oldAngle = old;
            this.updatedAngle = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            enginePoint.setAngle(oldAngle);
            queueEngineRepaint();
        }

        @Override
        public void redo() {
            enginePoint.setAngle(updatedAngle);
            redoSubEdits();
            queueEngineRepaint();
        }

        @Override
        public String getName() {
            return "Change Engine Angle";
        }
    }

    public static class EngineContrailSet extends AbstractEdit {
        private final EnginePoint enginePoint;
        private final int oldContrail;
        private final int updatedContrail;

        public EngineContrailSet(EnginePoint point, int old, int updated) {
            this.enginePoint = point;
            this.oldContrail = old;
            this.updatedContrail = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            enginePoint.setContrailSize(oldContrail);
            queueEngineRepaint();
        }

        @Override
        public void redo() {
            enginePoint.setContrailSize(updatedContrail);
            redoSubEdits();
            queueEngineRepaint();
        }

        @Override
        public String getName() {
            return "Change Engine Contrail Size";
        }
    }

    public static class EngineSizeSet extends AbstractEdit {
        private final EnginePoint enginePoint;
        private final Size2D oldSize;
        private final Size2D updatedSize;

        public EngineSizeSet(EnginePoint point, Size2D old, Size2D updated) {
            this.enginePoint = point;
            this.oldSize = old;
            this.updatedSize = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            enginePoint.setSize(oldSize);
            queueEngineRepaint();
        }

        @Override
        public void redo() {
            enginePoint.setSize(updatedSize);
            redoSubEdits();
            queueEngineRepaint();
        }

        @Override
        public String getName() {
            return "Change Engine Size";
        }
    }

    public static class EngineStyleSet extends AbstractEdit {
        private final EnginePoint enginePoint;
        private final EngineStyle oldStyle;
        private final EngineStyle updatedStyle;

        public EngineStyleSet(EnginePoint point, EngineStyle old, EngineStyle updated) {
            this.enginePoint = point;
            this.oldStyle = old;
            this.updatedStyle = updated;
        }

        @Override
        public void undo() {
            enginePoint.setStyle(oldStyle);
            queueEngineRepaint();
        }

        @Override
        public void redo() {
            enginePoint.setStyle(updatedStyle);
            queueEngineRepaint();
        }

        @Override
        public String getName() {
            return "Change Engine Style";
        }
    }

    public static class EnginesSortEdit extends AbstractEdit {
        private final EngineSlotPainter pointPainter;
        private final List<EnginePoint> oldList;
        private final List<EnginePoint> newList;

        public EnginesSortEdit(EngineSlotPainter painter, List<EnginePoint> old, List<EnginePoint> changed) {
            this.pointPainter = painter;
            this.oldList = old;
            this.newList = changed;
        }

        @Override
        public void undo() {
            pointPainter.setEnginePoints(oldList);
            queueEngineRepaint();
        }

        @Override
        public void redo() {
            pointPainter.setEnginePoints(newList);
            queueEngineRepaint();
        }

        @Override
        public String getName() {
            return "Sort Engines";
        }
    }
}
