package shipeditor.components.instrument.ship.shared;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import lombok.Getter;
import lombok.Setter;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.utility.components.containers.SortableList;
import shipeditor.utility.components.rendering.InstalledFeatureCellRenderer;
import shipeditor.utility.overseers.StaticController;
import javax.swing.DefaultListModel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import shipeditor.communication.events.components.ComponentEvents.SelectWeaponDataEntry;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;

public class InstalledFeatureList extends SortableList<InstalledFeature> {

    private boolean propagationBlock;

    @Getter @Setter
    private boolean belongsToBaseHullBuiltIns;

    private final Consumer<InstalledFeature> uninstaller;

    private final Consumer<InstalledFeature> selectionAction;

    private final Consumer<Map<String, InstalledFeature>> sorter;

    protected static final DataFlavor FEATURE_FLAVOR = new DataFlavor(InstalledFeature.class,
            StringManager.getString("INSTALLED_FEATURE"));

    public InstalledFeatureList(ListModel<InstalledFeature> dataModel,
                                Consumer<InstalledFeature> removeAction,
                                Consumer<Map<String, InstalledFeature>> sortAction,
                                Consumer<InstalledFeature> selectAction) {
        super(dataModel);
        this.uninstaller = removeAction;
        this.sorter = sortAction;
        this.selectionAction = selectAction;
        this.addListSelectionListener(e -> {
            this.actOnSelectedEntry(this::handleEntrySelection);
            if (propagationBlock) {
                propagationBlock = false;
                return;
            }
            this.actOnSelectedEntry(InstalledFeatureList::selectSlotPoint);
        });
        this.addMouseListener(new FeatureContextMenuListener());
        this.setCellRenderer(InstalledFeatureList.createCellRenderer());
        int margin = 3;
        this.setBorder(new EmptyBorder(margin, margin, margin, margin));
        this.setDragEnabled(true);
    }

    public static WeaponSlotPainter getSlotPainter() {
        return StaticController.getSelectedSlotPainter();
    }

    protected void handleEntrySelection(InstalledFeature feature) {
        if (feature == null) return;
        if (this.selectionAction == null) return;
        this.selectionAction.accept(feature);
    }

    private static ListCellRenderer<InstalledFeature> createCellRenderer() {
        return new InstalledFeatureCellRenderer();
    }

    /**
     * existing slot point in active layer.
     */
    private static void selectSlotPoint(InstalledFeature feature) {
        var slotPainter = InstalledFeatureList.getSlotPainter();
        if (slotPainter != null) {
            var slotPoint = slotPainter.getSlotByID(feature.getSlotID());
            if (slotPoint == null) return;
            EventBus.publish(new PointSelectQueued(slotPoint));
        }
    }

    public void selectEntryByPoint(SlotData slotPoint) {
        DefaultListModel<InstalledFeature> model = (DefaultListModel<InstalledFeature>) this.getModel();
        InstalledFeature target = null;

        for (int i = 0; i < model.size(); i++) {
            InstalledFeature feature = model.get(i);
            String slotID = feature.getSlotID();
            if (slotID.equals(slotPoint.getId())) {
                target = feature;
            }
        }

        propagationBlock = true;
        this.setSelectedValue(target, true);
    }

    protected JMenuItem getSelectEntryOption(InstalledFeature selected) {
        JMenuItem selectEntry = new JMenuItem(StringManager.getString("SELECT_WEAPON_ENTRY"));
        selectEntry.addActionListener(event -> actOnSelectedEntry(feature -> {
            CSVEntry dataEntry = feature.getDataEntry();
            if (dataEntry instanceof WeaponCSVEntry weaponEntry) {
                EventBus.publish(new SelectWeaponDataEntry(weaponEntry));
            }
        }));
        if (!(selected.getDataEntry() instanceof WeaponCSVEntry)) {
            selectEntry.setEnabled(false);
        }
        return selectEntry;
    }

    @Override
    protected void sortListModel() {
        ListModel<InstalledFeature> model = this.getModel();
        Map<String, InstalledFeature> rearranged = new LinkedHashMap<>(model.getSize());
        for (int i = 0; i < model.getSize(); i++) {
            InstalledFeature feature = model.getElementAt(i);
            rearranged.put(feature.getSlotID(), feature);
        }
        this.sorter.accept(rearranged);
    }

    @Override
    protected Transferable createTransferableFromEntry(InstalledFeature entry) {

        return new Transferable() {
            private final InstalledFeature feature = entry;

            private final DataFlavor sourceFlavor = new DataFlavor(InstalledFeatureList.this.getClass(),
                    String.valueOf(InstalledFeatureList.this.hashCode()));

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {FEATURE_FLAVOR, sourceFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(FEATURE_FLAVOR);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return feature;
            }
        };
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        DataFlavor[] dataFlavors = transferable.getTransferDataFlavors();
        if (dataFlavors == null || dataFlavors.length == 0) return false;
        return dataFlavors[0].equals(FEATURE_FLAVOR);
    }

    protected JPopupMenu getContextMenu() {
        InstalledFeature selected = getSelectedValue();
        if (selected == null) return null;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem removePoint = new JMenuItem(StringManager.getString("UNINSTALL_FEATURE"));
        removePoint.addActionListener(event -> actOnSelectedEntry(uninstaller));
        menu.add(removePoint);

        JMenuItem selectEntry = getSelectEntryOption(selected);
        menu.add(selectEntry);

        shipeditor.components.datafiles.entities.WeaponCSVEntry pickedForInstall = null;
        if (shipeditor.utility.overseers.StaticController.getActiveLayer() instanceof shipeditor.components.viewer.layers.ship.ShipLayer layer) {
            pickedForInstall = layer.getFeaturesOverseer().getWeaponForInstall();
        }
        if (pickedForInstall != null) {
            JMenuItem replacePoint = new JMenuItem(StringManager.getString("REPLACE_WITH_PICKED_WEAPON"));
            replacePoint.addActionListener(event -> actOnSelectedEntry(feature -> {
                var slotPainter = InstalledFeatureList.getSlotPainter();
                if (slotPainter != null) {
                    var slotPoint = slotPainter.getSlotByID(feature.getSlotID());
                    if (slotPoint != null) {
                        EventBus.publish(new shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued(slotPoint));
                        EventBus.publish(new shipeditor.communication.events.viewer.control.ControlEvents.FeatureInstallQueued(null));
                    }
                }
            }));
            menu.add(replacePoint);
        }

        return menu;
    }

    private class FeatureContextMenuListener extends MouseAdapter {

        public void mousePressed(MouseEvent e) {
            if ( SwingUtilities.isRightMouseButton(e) ) {
                setSelectedIndex(locationToIndex(e.getPoint()));
                JPopupMenu menu = getContextMenu();
                if (menu != null) {
                    menu.show(InstalledFeatureList.this, e.getPoint().x, e.getPoint().y);
                }
            }
        }
    }

}
