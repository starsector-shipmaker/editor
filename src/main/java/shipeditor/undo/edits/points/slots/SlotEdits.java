package shipeditor.undo.edits.points.slots;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.overseers.StaticController;

import java.util.List;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class SlotEdits {

    private SlotEdits() {
    }

    private static void queueSlotAndBayRepaint() {
        var repainter = StaticController.getScheduler();
        repainter.queueViewerRepaint();
        repainter.queueSlotsPanelRepaint();
        repainter.queueBaysPanelRepaint();
    }

    public static class RenderOrderChangeEdit extends AbstractEdit {
        private final SlotData slotPoint;
        private final int oldOrder;
        private final int updatedOrder;

        public RenderOrderChangeEdit(SlotData point, int old, int updated) {
            this.slotPoint = point;
            this.oldOrder = old;
            this.updatedOrder = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            slotPoint.setRenderOrderMod(oldOrder);
            queueSlotAndBayRepaint();
        }

        @Override
        public void redo() {
            slotPoint.setRenderOrderMod(updatedOrder);
            redoSubEdits();
            queueSlotAndBayRepaint();
        }

        @Override
        public String getName() {
            return "Change Render Order";
        }
    }

    public static class SlotAngleSet extends AbstractEdit {
        private final SlotData slotPoint;
        private final double oldAngle;
        private final double updatedAngle;

        public SlotAngleSet(SlotData point, double old, double updated) {
            this.slotPoint = point;
            this.oldAngle = old;
            this.updatedAngle = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            slotPoint.setAngle(oldAngle);
            queueSlotAndBayRepaint();
        }

        @Override
        public void redo() {
            slotPoint.setAngle(updatedAngle);
            redoSubEdits();
            queueSlotAndBayRepaint();
        }

        @Override
        public String getName() {
            return "Change Slot Angle";
        }
    }

    public static class SlotArcSet extends AbstractEdit {
        private final SlotData slotPoint;
        private final double oldArc;
        private final double updatedArc;

        public SlotArcSet(SlotData point, double old, double updated) {
            this.slotPoint = point;
            this.oldArc = old;
            this.updatedArc = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            slotPoint.setArc(oldArc);
            queueSlotAndBayRepaint();
        }

        @Override
        public void redo() {
            slotPoint.setArc(updatedArc);
            redoSubEdits();
            queueSlotAndBayRepaint();
        }

        @Override
        public String getName() {
            return "Change Slot Arc";
        }
    }

    public static class SlotIDChangeEdit extends AbstractEdit {
        private final SlotData slot;
        private final String old;
        private final String updated;

        public SlotIDChangeEdit(SlotData point, String newID, String oldID) {
            this.slot = point;
            this.old = oldID;
            this.updated = newID;
        }

        @Override
        public void undo() {
            slot.changeSlotID(old);
        }

        @Override
        public void redo() {
            slot.changeSlotID(updated);
        }

        @Override
        public String getName() {
            return "Slot ID Change";
        }
    }

    public static class SlotMountChangeEdit extends AbstractEdit {
        private final SlotData slot;
        private final WeaponMount old;
        private final WeaponMount updated;

        public SlotMountChangeEdit(SlotData point, WeaponMount oldMount, WeaponMount newMount) {
            this.slot = point;
            this.old = oldMount;
            this.updated = newMount;
        }

        @Override
        public void undo() {
            slot.setWeaponMount(old);
            queueSlotAndBayRepaint();
        }

        @Override
        public void redo() {
            slot.setWeaponMount(updated);
            queueSlotAndBayRepaint();
        }

        @Override
        public String getName() {
            return "Slot Mount Change";
        }
    }

    public static class SlotSizeChangeEdit extends AbstractEdit {
        private final SlotData slot;
        private final WeaponSize old;
        private final WeaponSize updated;

        public SlotSizeChangeEdit(SlotData point, WeaponSize oldSize, WeaponSize newSize) {
            this.slot = point;
            this.old = oldSize;
            this.updated = newSize;
        }

        @Override
        public void undo() {
            slot.setWeaponSize(old);
            queueSlotAndBayRepaint();
            StaticController.getScheduler().queueBuiltInsRepaint();
        }

        @Override
        public void redo() {
            slot.setWeaponSize(updated);
            queueSlotAndBayRepaint();
            StaticController.getScheduler().queueBuiltInsRepaint();
        }

        @Override
        public String getName() {
            return "Slot Size Change";
        }
    }

    public static class SlotTypeChangeEdit extends AbstractEdit {
        private final SlotData slot;
        private final WeaponType old;
        private final WeaponType updated;

        public SlotTypeChangeEdit(SlotData point, WeaponType oldType, WeaponType newType) {
            this.slot = point;
            this.old = oldType;
            this.updated = newType;
        }

        @Override
        public void undo() {
            slot.setWeaponType(old);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueSlotsPanelRepaint();
            repainter.queueBuiltInsRepaint();
        }

        @Override
        public void redo() {
            slot.setWeaponType(updated);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueSlotsPanelRepaint();
            repainter.queueBuiltInsRepaint();
        }

        @Override
        public String getName() {
            return "Slot Type Change";
        }
    }

    public static class WeaponSlotsSortEdit extends AbstractEdit {
        private final WeaponSlotPainter pointPainter;
        private final List<WeaponSlotPoint> oldList;
        private final List<WeaponSlotPoint> newList;

        public WeaponSlotsSortEdit(WeaponSlotPainter painter, List<WeaponSlotPoint> old, List<WeaponSlotPoint> changed) {
            this.pointPainter = painter;
            this.oldList = old;
            this.newList = changed;
        }

        @Override
        public void undo() {
            pointPainter.setSlotPoints(oldList);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueSlotsPanelRepaint();
        }

        @Override
        public void redo() {
            pointPainter.setSlotPoints(newList);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueSlotsPanelRepaint();
        }

        @Override
        public String getName() {
            return "Sort Weapon Slots";
        }
    }
}
