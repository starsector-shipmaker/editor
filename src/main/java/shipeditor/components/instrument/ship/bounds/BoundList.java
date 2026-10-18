package shipeditor.components.instrument.ship.bounds;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.BoundPointsSorted;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.utility.components.containers.PointList;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;

@Log4j2
final class BoundList extends PointList<BoundPoint> {

    private static final DataFlavor BOUND_FLAVOR = new DataFlavor(BoundPoint.class, StringManager.getString("BOUND"));

    private final Runnable selectionRefresher;

    BoundList(ListModel<BoundPoint> dataModel, Runnable refresher) {
        super(dataModel);
        this.selectionRefresher = refresher;
    }

    @Override
    protected void publishPointsSorted(List<BoundPoint> rearrangedPoints) {
        EventBus.publish(new BoundPointsSorted(rearrangedPoints));
    }

    @Override
    protected void handlePointSelection(BoundPoint point) {
        if (this.selectionRefresher != null) {
            selectionRefresher.run();
        }
    }

    @Override
    protected Transferable createTransferableFromEntry(BoundPoint entry) {
        return createSingleEntryTransferable(entry, BOUND_FLAVOR);
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        return transferable.isDataFlavorSupported(BOUND_FLAVOR);
    }

}
