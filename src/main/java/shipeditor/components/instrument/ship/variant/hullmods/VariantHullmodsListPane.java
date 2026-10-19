package shipeditor.components.instrument.ship.variant.hullmods;

import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.instrument.ship.shared.AbstractHullmodsListPane;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.utility.overseers.StaticController;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

class VariantHullmodsListPane extends AbstractHullmodsListPane<ShipVariant> {

    VariantHullmodsListPane(Function<ShipVariant, List<HullmodCSVEntry>> getter,
                            BiConsumer<ShipVariant, List<HullmodCSVEntry>> sortSetter) {
        super(getter, sortSetter);
    }

    @Override
    protected void actOnTarget(BiConsumer<ShipLayer, ShipVariant> action) {
        StaticController.actOnCurrentVariant(action);
    }

    @Override
    protected ShipVariant getTarget(ShipLayer checkedLayer) {
        if (checkedLayer.getPainter() != null && !checkedLayer.getPainter().isUninitialized()) {
            return checkedLayer.getPainter().getActiveVariant();
        }
        return null;
    }

    @Override
    protected boolean isValidTarget(ShipVariant target) {
        return target != null;
    }

    @Override
    protected boolean shouldExcludeBuiltInHullmods() {
        return true;
    }
}
