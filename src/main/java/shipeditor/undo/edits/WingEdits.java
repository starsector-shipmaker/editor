package shipeditor.undo.edits;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.UtilityEnums.EditCategory;
import shipeditor.utility.overseers.StaticController;

import java.util.List;
import java.util.function.Consumer;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class WingEdits {

    private WingEdits() {
    }

    private static EditCategory resolveCategory(ShipLayer layer, List<WingCSVEntry> targetList) {
        if (layer != null) {
            if (layer.getHull() != null && targetList == layer.getHull().getBuiltInWings()) {
                return EditCategory.HULL;
            }
            if (layer.getPainter() != null) {
                ShipSkin skin = layer.getPainter().getActiveSkin();
                if (skin != null && !skin.isBase() && targetList == skin.getBuiltInWings()) {
                    return EditCategory.SKIN;
                }
            }
        }
        EditorInstrument mode = StaticController.getEditorMode();
        if (mode == EditorInstrument.BUILT_IN_WINGS) {
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

    public static class WingAddEdit extends AbstractEdit {
        private final List<WingCSVEntry> wingIndex;
        private ShipLayer layer;
        private final WingCSVEntry entry;
        private final EditCategory category;

        public WingAddEdit(List<WingCSVEntry> index, ShipLayer shipLayer, WingCSVEntry wing) {
            this.wingIndex = index;
            this.layer = shipLayer;
            this.entry = wing;
            this.category = resolveCategory(shipLayer, index);
        }

        @Override
        public void undo() {
            wingIndex.remove(entry);
            refreshIfActive(layer);
        }

        @Override
        public void redo() {
            wingIndex.add(entry);
            refreshIfActive(layer);
        }

        @Override
        public String getName() {
            return "Add Wing";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return layer != null ? layer.getPainter() : null;
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

    public static class WingRemoveEdit extends AbstractEdit {
        private final List<WingCSVEntry> wingIndex;
        private ShipLayer layer;
        private final WingCSVEntry entry;
        private final int positionIndex;
        private final EditCategory category;

        public WingRemoveEdit(List<WingCSVEntry> index, ShipLayer shipLayer, WingCSVEntry wing, int entryIndex) {
            this.wingIndex = index;
            this.layer = shipLayer;
            this.entry = wing;
            this.positionIndex = entryIndex;
            this.category = resolveCategory(shipLayer, index);
        }

        @Override
        public void undo() {
            wingIndex.add(positionIndex, entry);
            refreshIfActive(layer);
        }

        @Override
        public void redo() {
            wingIndex.remove(positionIndex);
            refreshIfActive(layer);
        }

        @Override
        public String getName() {
            return "Remove Wing";
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

    public static class WingsSortEdit extends AbstractEdit {
        private final List<WingCSVEntry> oldList;
        private final List<WingCSVEntry> newList;
        private ShipLayer layer;
        private final Consumer<List<WingCSVEntry>> sortSetter;
        private final EditCategory category;

        public WingsSortEdit(List<WingCSVEntry> old, List<WingCSVEntry> updated, ShipLayer shipLayer,
                             Consumer<List<WingCSVEntry>> setter) {
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
            return "Sort Wings";
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
