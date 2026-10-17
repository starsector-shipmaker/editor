package shipeditor.components.instrument.ship.shared;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.datafiles.entities.transferable.Transferables.TransferableEntry;
import shipeditor.components.datafiles.entities.transferable.Transferables.TransferableWing;
import shipeditor.utility.components.containers.OrdnancedEntryList;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WingsList extends OrdnancedEntryList<WingCSVEntry> {

    private final BiConsumer<Integer, WingCSVEntry> removeAction;

    public WingsList(BiConsumer<Integer, WingCSVEntry> removeSetter, ListModel<WingCSVEntry> dataModel,
                     Consumer<List<WingCSVEntry>> sortAction) {
        super(dataModel, sortAction);
        this.removeAction = removeSetter;
    }

    @Override
    protected Consumer<WingCSVEntry> getRemoveAction() {
        return wingCSVEntry -> actOnSelectedWing(removeAction);
    }

    private void actOnSelectedWing(BiConsumer<Integer, WingCSVEntry> action) {
        int index = this.getSelectedIndex();
        if (index != -1) {
            ListModel<WingCSVEntry> listModel = this.getModel();
            WingCSVEntry feature = listModel.getElementAt(index);
            action.accept(index, feature);
        }
    }

    protected JPopupMenu getContextMenu() {
        WingCSVEntry selected = getSelectedValue();
        if (selected == null) return null;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem remove = new JMenuItem(StringManager.getString("REMOVE_WING"));
        remove.addActionListener(event -> actOnSelectedWing(removeAction));
        menu.add(remove);

        return menu;
    }

    @Override
    protected Transferable createTransferableFromEntry(WingCSVEntry entry) {
        return new TransferableWing(entry, this);
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        DataFlavor[] dataFlavors = transferable.getTransferDataFlavors();
        if (dataFlavors == null || dataFlavors.length == 0) return false;
        return dataFlavors[0].equals(TransferableEntry.TRANSFERABLE_WING);
    }

}
