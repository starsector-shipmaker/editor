package shipeditor.components.instrument.ship.engines;

import javax.swing.ListModel;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.EnginePointsSorted;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.utility.components.containers.PointList;
import shipeditor.utility.components.rendering.EngineCellRenderer;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.function.Consumer;

public class EngineList extends PointList<EnginePoint> {

    private static final DataFlavor ENGINE_FLAVOR = new DataFlavor(EnginePoint.class, "Engine");

    private final Consumer<EnginePoint> selectAction;

    EngineList(ListModel<EnginePoint> dataModel, Consumer<EnginePoint> pointSelectAction) {
        super(dataModel);
        this.setCellRenderer(new EngineCellRenderer());
        this.selectAction = pointSelectAction;
    }

    @Override
    protected void handlePointSelection(EnginePoint point) {
        this.selectAction.accept(point);
    }

    @Override
    protected void publishPointsSorted(List<EnginePoint> rearrangedPoints) {
        EventBus.publish(new EnginePointsSorted(rearrangedPoints));
    }

    @Override
    protected Transferable createTransferableFromEntry(EnginePoint entry) {
        return createSingleEntryTransferable(entry, ENGINE_FLAVOR);
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        return transferable.isDataFlavorSupported(ENGINE_FLAVOR);
    }

}
