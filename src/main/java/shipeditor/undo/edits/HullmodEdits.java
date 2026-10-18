package shipeditor.undo.edits;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.UtilityEnums.EditCategory;
import shipeditor.utility.overseers.StaticController;

import java.util.List;
import java.util.function.Consumer;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class HullmodEdits {

    private HullmodEdits() {
    }

    private static EditCategory resolveCategory(ShipLayer layer, List<HullmodCSVEntry> targetList) {
        if (layer != null) {
            if (layer.getHull() != null && targetList == layer.getHull().getBuiltInMods()) {
                return EditCategory.HULL;
            }
            if (layer.getPainter() != null) {
                ShipSkin skin = layer.getPainter().getActiveSkin();
                if (skin != null && !skin.isBase() && targetList == skin.getBuiltInMods()) {
                    return EditCategory.SKIN;
                }
            }
        }
        EditorInstrument mode = StaticController.getEditorMode();
        if (mode == EditorInstrument.BUILT_IN_MODS) {
            if (layer != null && layer.getPainter() != null) {
                ShipSkin skin = layer.getPainter().getActiveSkin();
                if (skin != null && !skin.isBase()) {
                    return EditCategory.SKIN;
                }
            }
            return EditCategory.HULL;
        }
        if (mode == EditorInstrument.SKIN_DATA || mode == EditorInstrument.SKIN_SLOTS
                || mode == EditorInstrument.SKIN_ENGINES || mode == EditorInstrument.SKIN_REMOVALS) {
            return EditCategory.SKIN;
        }
        return EditCategory.VARIANT;
    }

    public static class HullmodAddEdit extends AbstractEdit {
        private final List<HullmodCSVEntry> hullmodIndex;
        private ShipLayer layer;
        private final HullmodCSVEntry entry;
        private final EditCategory category;

        public HullmodAddEdit(List<HullmodCSVEntry> index, ShipLayer shipLayer, HullmodCSVEntry hullmod) {
            this.hullmodIndex = index;
            this.layer = shipLayer;
            this.entry = hullmod;
            this.category = resolveCategory(shipLayer, index);
        }

        @Override
        public void undo() {
            hullmodIndex.remove(entry);
            refreshIfActive(layer);
        }

        @Override
        public void redo() {
            hullmodIndex.add(entry);
            refreshIfActive(layer);
        }

        @Override
        public String getName() {
            return "Add Hullmod";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return layer.getPainter();
        }

        @Override
        public void cleanupReferences() {
            layer = null;
        }

        @Override
        public EditCategory getCategory() {
            return category;
        }
    }

    public static class HullmodRemoveEdit extends AbstractEdit {
        private final List<HullmodCSVEntry> hullmodIndex;
        private ShipLayer layer;
        private final HullmodCSVEntry entry;
        private final int positionIndex;
        private final EditCategory category;

        public HullmodRemoveEdit(List<HullmodCSVEntry> index, ShipLayer shipLayer, HullmodCSVEntry hullmod, int positionIndex) {
            this.hullmodIndex = index;
            this.layer = shipLayer;
            this.entry = hullmod;
            this.positionIndex = positionIndex;
            this.category = resolveCategory(shipLayer, index);
        }

        @Override
        public void undo() {
            hullmodIndex.add(positionIndex, entry);
            refreshIfActive(layer);
        }

        @Override
        public void redo() {
            hullmodIndex.remove(entry);
            refreshIfActive(layer);
        }

        @Override
        public String getName() {
            return "Remove Hullmod";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return layer.getPainter();
        }

        @Override
        public void cleanupReferences() {
            layer = null;
        }

        @Override
        public EditCategory getCategory() {
            return category;
        }
    }

    public static class HullmodsSortEdit extends AbstractEdit {
        private final List<HullmodCSVEntry> oldList;
        private final List<HullmodCSVEntry> newList;
        private ShipLayer layer;
        private final Consumer<List<HullmodCSVEntry>> sortSetter;
        private final EditCategory category;

        public HullmodsSortEdit(List<HullmodCSVEntry> old, List<HullmodCSVEntry> updated, ShipLayer shipLayer,
                                Consumer<List<HullmodCSVEntry>> setter) {
            this.oldList = old;
            this.newList = updated;
            this.layer = shipLayer;
            this.sortSetter = setter;
            this.category = resolveCategory(shipLayer, old);
        }

        @Override
        public void undo() {
            sortSetter.accept(oldList);
            refreshIfActive(layer);
        }

        @Override
        public void redo() {
            sortSetter.accept(newList);
            refreshIfActive(layer);
        }

        @Override
        public String getName() {
            return "Sort Hullmods";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return layer.getPainter();
        }

        @Override
        public void cleanupReferences() {
            layer = null;
        }

        @Override
        public EditCategory getCategory() {
            return category;
        }
    }
}
