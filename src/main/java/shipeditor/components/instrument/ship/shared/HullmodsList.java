package shipeditor.components.instrument.ship.shared;

import javax.swing.ListModel;

import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.datafiles.entities.transferable.Transferables.TransferableEntry;
import shipeditor.components.datafiles.entities.transferable.Transferables.TransferableHullmod;
import shipeditor.utility.components.containers.OrdnancedEntryList;

import javax.swing.DefaultListModel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.function.Consumer;

public class HullmodsList extends OrdnancedEntryList<HullmodCSVEntry> {

    private final Consumer<HullmodCSVEntry> removeAction;

    public HullmodsList(Consumer<HullmodCSVEntry> removeSetter, ListModel<HullmodCSVEntry> dataModel,
                        Consumer<List<HullmodCSVEntry>> sortSetter) {
        super(dataModel, sortSetter);
        this.removeAction = removeSetter;
    }

    @Override
    protected boolean confirmDrop(int targetIndex, HullmodCSVEntry entry) {
        DefaultListModel<HullmodCSVEntry> model = this.getModel();
        if (model.contains(entry)) {
            int former = model.indexOf(entry);
            model.remove(former);
            int adjustedIndex = targetIndex > former ? targetIndex - 1 : targetIndex;
            int insertIndex = Math.max(0, Math.min(model.size(), adjustedIndex));
            model.add(insertIndex, entry);
            setSelectedIndex(insertIndex);
        } else {
            super.confirmDrop(targetIndex, entry);
        }
        return true;
    }

    @Override
    public DefaultListModel<HullmodCSVEntry> getModel() {
        return (DefaultListModel<HullmodCSVEntry>) super.getModel();
    }

    @Override
    protected Consumer<HullmodCSVEntry> getRemoveAction() {
        return removeAction;
    }

    @Override
    protected Transferable createTransferableFromEntry(HullmodCSVEntry entry) {
        return new TransferableHullmod(entry, this);
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        DataFlavor[] dataFlavors = transferable.getTransferDataFlavors();
        if (dataFlavors == null || dataFlavors.length == 0) return false;
        return dataFlavors[0].equals(TransferableEntry.TRANSFERABLE_MOD);
    }

}
