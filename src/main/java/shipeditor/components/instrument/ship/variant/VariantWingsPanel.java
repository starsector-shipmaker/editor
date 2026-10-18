package shipeditor.components.instrument.ship.variant;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.shared.WingsList;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.persistence.SettingsManager;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class VariantWingsPanel extends JPanel {

    private final WingsList wingsList;

    private DefaultListModel<WingCSVEntry> wingsModel;

    private final Function<ShipVariant, List<WingCSVEntry>> wingsGetter;

    private final JButton addButton;

    private final JButton removeButton;

    private final JButton clearAllButton;

    private JLabel shipOPCap;

    private JLabel usedOPTotal;

    private JLabel usedOPInWings;

    private JLabel totalBayCount;

    private JLabel builtInWingsCount;

    private JLabel fittedWingsCount;

    public VariantWingsPanel() {
        this.setLayout(new BorderLayout());

        BiConsumer<ShipVariant, List<WingCSVEntry>> sortSetter = (a, b) -> a.setWings(b);
        this.wingsGetter = a -> a.getWings();

        this.wingsModel = new DefaultListModel<>();

        BiConsumer<Integer, WingCSVEntry> removeAction = (entryIndex, wingCSVEntry) ->
                StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                    var entryList = wingsGetter.apply(variant);
                    EditDispatch.postWingRemoved(entryList, shipLayer, wingCSVEntry, entryIndex);
                });

        Consumer<List<WingCSVEntry>> sortAction = updatedList ->
                StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                    var oldWings = wingsGetter.apply(variant);
                    EditDispatch.postWingsSorted(oldWings, updatedList, shipLayer,
                            list -> sortSetter.accept(variant, list));
                });

        this.wingsList = new WingsList(removeAction, wingsModel, sortAction);

        JScrollPane scroller = new JScrollPane(wingsList);
        JScrollBar verticalScrollBar = scroller.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(16);

        JPanel infoPanel = createInfoPanel();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 2));

        addButton = new JButton("Add...",
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        addButton.setEnabled(false);
        addButton.addActionListener(e -> showWingPickerAndAdd());
        buttonPanel.add(addButton);

        removeButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> {
            WingCSVEntry selected = wingsList.getSelectedValue();
            int selectedIndex = wingsList.getSelectedIndex();
            if (selected == null || selectedIndex < 0) return;
            StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                var entryList = wingsGetter.apply(variant);
                EditDispatch.postWingRemoved(entryList, shipLayer, selected, selectedIndex);
                refreshListModel(shipLayer);
                refreshLayerInfo(shipLayer);
            });
        });
        wingsList.addListSelectionListener(e -> {
            removeButton.setEnabled(wingsList.getSelectedValue() != null);
        });
        buttonPanel.add(removeButton);

        clearAllButton = new JButton("Clear All",
                FontIcon.of(BoxiconsRegular.TRASH_ALT, 16, Themes.getIconColor()));
        clearAllButton.setEnabled(false);
        clearAllButton.addActionListener(e -> {
            StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                List<WingCSVEntry> currentWings = wingsGetter.apply(variant);
                if (currentWings == null || currentWings.isEmpty()) {
                    return;
                }
                List<WingCSVEntry> oldCopy = new ArrayList<>(currentWings);
                List<WingCSVEntry> emptyList = new ArrayList<>();
                EditDispatch.postWingsSorted(oldCopy, emptyList, shipLayer,
                        list -> sortSetter.accept(variant, new ArrayList<>(list)));
                refreshListModel(shipLayer);
                refreshLayerInfo(shipLayer);
            });
        });
        buttonPanel.add(clearAllButton);

        this.add(infoPanel, BorderLayout.PAGE_START);
        this.add(scroller, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.PAGE_END);

        this.initLayerListeners();
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        this.refreshListModel(activeLayer);
        this.refreshLayerInfo(activeLayer);
    }

    private void showWingPickerAndAdd() {
        var gameData = SettingsManager.getGameData();
        Map<String, WingCSVEntry> allWingsMap = gameData != null ? gameData.getAllWingEntries() : null;
        if (allWingsMap == null || allWingsMap.isEmpty()) {
            return;
        }

        List<WingCSVEntry> allWings = new ArrayList<>(allWingsMap.values());
        allWings.sort(Comparator.comparing(w -> {
            String name = w.getEntryName();
            return name != null ? name.toLowerCase(Locale.ROOT) : "";
        }));

        DefaultListModel<WingCSVEntry> pickerModel = new DefaultListModel<>();
        pickerModel.addAll(allWings);

        JList<WingCSVEntry> pickerList = new JList<>(pickerModel);
        pickerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pickerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof WingCSVEntry entry) {
                    setText(entry.getEntryName() + " (" + entry.getWingID() + ") - "
                            + entry.getOrdnanceCost(null) + " OP");
                }
                return this;
            }
        });
        if (!pickerModel.isEmpty()) {
            pickerList.setSelectedIndex(0);
        }

        JTextField filterField = new JTextField();
        filterField.putClientProperty("JTextField.placeholderText", "Filter wings by name or ID...");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String query = filterField.getText();
                String normalized = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
                pickerModel.clear();
                for (WingCSVEntry entry : allWings) {
                    if (normalized.isEmpty()) {
                        pickerModel.addElement(entry);
                    } else {
                        String name = entry.getEntryName() != null
                                ? entry.getEntryName().toLowerCase(Locale.ROOT) : "";
                        String id = entry.getWingID() != null
                                ? entry.getWingID().toLowerCase(Locale.ROOT) : "";
                        if (name.contains(normalized) || id.contains(normalized)) {
                            pickerModel.addElement(entry);
                        }
                    }
                }
                if (!pickerModel.isEmpty()) {
                    pickerList.setSelectedIndex(0);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JPanel dialogPanel = new JPanel(new BorderLayout(0, 6));
        dialogPanel.setPreferredSize(new Dimension(380, 320));
        dialogPanel.add(filterField, BorderLayout.PAGE_START);
        dialogPanel.add(new JScrollPane(pickerList), BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(this, dialogPanel, "Add Fighter Wing",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            WingCSVEntry chosenWing = pickerList.getSelectedValue();
            if (chosenWing != null) {
                StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                    EditDispatch.postWingAdded(variant.getWings(), shipLayer, chosenWing);
                    refreshListModel(shipLayer);
                    refreshLayerInfo(shipLayer);
                });
            }
        }
    }

    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel();
        ComponentUtilities.outfitPanelWithTitle(infoPanel, "Fitted wings");
        infoPanel.setLayout(new GridBagLayout());

        JLabel shipOPCapLabel = new JLabel(StringManager.getString("TOTAL_OP_CAPACITY"));
        shipOPCap = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, shipOPCapLabel, shipOPCap, 0);

        JLabel usedOPTotalLabel = new JLabel(StringManager.getString("USED_OP_FOR_SHIP"));
        usedOPTotal = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, usedOPTotalLabel, usedOPTotal, 1);

        JLabel usedOPLabel = new JLabel(StringManager.getString("USED_OP_IN_WINGS"));
        usedOPInWings = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, usedOPLabel, usedOPInWings, 2);

        GridBagConstraints strutConstraints = new GridBagConstraints();
        strutConstraints.gridx = 0;
        strutConstraints.gridy = 3;
        strutConstraints.gridwidth = 2;
        infoPanel.add(Box.createVerticalStrut(10), strutConstraints);

        JLabel totalBaysLabel = new JLabel(StringManager.getString("TOTAL_SHIP_BAYS"));
        totalBayCount = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, totalBaysLabel, totalBayCount, 4);

        JLabel totalBuiltInsLabel = new JLabel(StringManager.getString("TOTAL_BUILT_IN_WINGS"));
        builtInWingsCount = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, totalBuiltInsLabel, builtInWingsCount, 5);

        JLabel totalFittedLabel = new JLabel(StringManager.getString("TOTAL_FITTED_WINGS"));
        fittedWingsCount = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, totalFittedLabel, fittedWingsCount, 6);

        return infoPanel;
    }

    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                ViewerLayer selected = checked.selected();
                refreshListModel(selected);
                refreshLayerInfo(selected);
            } else if (event instanceof ActiveLayerUpdated checked) {
                ViewerLayer updated = checked.updated();
                refreshListModel(updated);
                refreshLayerInfo(updated);
            } else if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.VARIANT_DATA) {
                    ViewerLayer activeLayer = StaticController.getActiveLayer();
                    refreshListModel(activeLayer);
                    refreshLayerInfo(activeLayer);
                }
            }
        });
    }

    private void refreshLayerInfo(ViewerLayer selected) {
        String notInitialized = StringManager.getString("NOT_INITIALIZED");

        if (selected instanceof ShipLayer shipLayer) {

            String totalOP = Utility.translateIntegerValue(shipLayer::getTotalOP);
            shipOPCap.setText(totalOP);
            String totalBays = Utility.translateIntegerValue(shipLayer::getBayCount);
            totalBayCount.setText(totalBays);
            String totalBuiltIns = Utility.translateIntegerValue(shipLayer::getBuiltInWingsCount);
            builtInWingsCount.setText(totalBuiltIns);

            var activeVariant = shipLayer.getActiveVariant();
            if (activeVariant == null) {
                usedOPTotal.setText(notInitialized);
                usedOPInWings.setText(notInitialized);
                fittedWingsCount.setText(notInitialized);
                return;
            }

            int totalUsedOP = shipLayer.getTotalUsedOP();
            usedOPTotal.setText(String.valueOf(totalUsedOP));

            int totalOPInWings = shipLayer.getTotalOPInWings();
            usedOPInWings.setText(String.valueOf(totalOPInWings));

            int wingsCount = activeVariant.getFittedWingsCount();
            fittedWingsCount.setText(String.valueOf(wingsCount));
        } else {
            shipOPCap.setText(notInitialized);
            usedOPTotal.setText(notInitialized);
            usedOPInWings.setText(notInitialized);
            totalBayCount.setText(notInitialized);
            builtInWingsCount.setText(notInitialized);
            fittedWingsCount.setText(notInitialized);
        }
    }

    private void refreshListModel(ViewerLayer selected) {
        DefaultListModel<WingCSVEntry> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.wingsModel = newModel;
            this.wingsList.setModel(newModel);
            this.wingsList.setEnabled(false);
            if (addButton != null) {
                addButton.setEnabled(false);
            }
            if (removeButton != null) {
                removeButton.setEnabled(false);
            }
            if (clearAllButton != null) {
                clearAllButton.setEnabled(false);
            }
            return;
        }
        ShipPainter painter = checkedLayer.getPainter();
        boolean shipReady = painter != null && !painter.isUninitialized();
        if (shipReady) {
            ShipVariant active = painter.getActiveVariant();
            if (active != null && !active.isEmpty()) {
                List<WingCSVEntry> entries = wingsGetter.apply(active);
                if (entries != null) {
                    newModel.addAll(entries);
                }
            }
            this.wingsList.setEnabled(true);
        } else {
            this.wingsList.setEnabled(false);
        }
        if (addButton != null) {
            addButton.setEnabled(shipReady);
        }
        if (removeButton != null) {
            removeButton.setEnabled(false);
        }
        if (clearAllButton != null) {
            clearAllButton.setEnabled(shipReady && !newModel.isEmpty());
        }
        this.wingsModel = newModel;
        this.wingsList.setModel(newModel);
    }

}
