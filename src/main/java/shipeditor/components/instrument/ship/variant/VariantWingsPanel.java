package shipeditor.components.instrument.ship.variant;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.shared.WingsList;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class VariantWingsPanel extends JPanel {

    private final WingsList wingsList;

    private DefaultListModel<WingCSVEntry> wingsModel;

    private final Function<ShipVariant, List<WingCSVEntry>> wingsGetter;

    private final JButton removeButton;

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

        this.add(infoPanel, BorderLayout.PAGE_START);
        this.add(scroller, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.PAGE_END);

        this.initLayerListeners();
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
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.VARIANT_DATA) {
                    this.refreshLayerInfo(StaticController.getActiveLayer());
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
            String totalBuiltIns =  Utility.translateIntegerValue(shipLayer::getBuiltInWingsCount);
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
        }
    }

    private void refreshListModel(ViewerLayer selected) {
        DefaultListModel<WingCSVEntry> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.wingsModel = newModel;
            this.wingsList.setModel(newModel);
            this.wingsList.setEnabled(false);
            return;
        }
        ShipPainter painter = checkedLayer.getPainter();
        if (painter != null && !painter.isUninitialized()) {
            ShipVariant active = painter.getActiveVariant();
            if (active != null && !active.isEmpty()) {
                List<WingCSVEntry> entries = wingsGetter.apply(active);
                if (entries != null) {
                    newModel.addAll(entries);
                }
                this.wingsList.setEnabled(true);
            } else {
                this.wingsList.setEnabled(false);
            }
        } else {
            this.wingsList.setEnabled(false);
        }
        if (removeButton != null) {
            removeButton.setEnabled(false);
        }
        this.wingsModel = newModel;
        this.wingsList.setModel(newModel);
    }

}
