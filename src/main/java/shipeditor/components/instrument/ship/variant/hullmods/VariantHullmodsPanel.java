package shipeditor.components.instrument.ship.variant.hullmods;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class VariantHullmodsPanel extends JPanel {

    private final VariantHullmodsListPane normalModsPanel;

    private final VariantHullmodsListPane permaModsPanel;

    private final VariantHullmodsListPane sModsPanel;

    private final SuppressedModsPanel suppressedModsPanel;

    private JLabel shipOPCap;

    private JLabel usedOPTotal;

    private JLabel usedOPInHullmods;

    public VariantHullmodsPanel() {
        this.setLayout(new BorderLayout());

        this.normalModsPanel = new VariantHullmodsListPane(a -> a.getHullMods(), (a, b) -> a.setHullMods(b));
        ComponentUtilities.outfitPanelWithTitle(normalModsPanel, "Normal");
        this.permaModsPanel = new VariantHullmodsListPane(a -> a.getPermaMods(), (a, b) -> a.setPermaMods(b));
        ComponentUtilities.outfitPanelWithTitle(permaModsPanel, "Permanent");
        this.sModsPanel = new VariantHullmodsListPane(a -> a.getSMods(), (a, b) -> a.setSMods(b));
        ComponentUtilities.outfitPanelWithTitle(sModsPanel, "S-Mods");

        this.suppressedModsPanel = new SuppressedModsPanel();

        JPanel container = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 0.25;
        constraints.weightx = 1;
        constraints.ipady = 40;
        constraints.gridy = 0;

        container.add(normalModsPanel, constraints);
        constraints.gridy = 1;
        container.add(permaModsPanel, constraints);
        constraints.gridy = 2;
        container.add(sModsPanel, constraints);
        constraints.gridy = 3;
        container.add(suppressedModsPanel, constraints);

        JScrollPane scroller = new JScrollPane(container);
        JScrollBar verticalScrollBar = scroller.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(16);

        this.add(scroller, BorderLayout.CENTER);

        JPanel infoPanel = createInfoPanel();
        this.add(infoPanel, BorderLayout.PAGE_START);

        this.initLayerListeners();
        this.refreshAll(StaticController.getActiveLayer());
    }

    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel();
        ComponentUtilities.outfitPanelWithTitle(infoPanel, "Fitted hullmods");
        infoPanel.setLayout(new GridBagLayout());

        JLabel shipOPCapLabel = new JLabel(StringManager.getString("TOTAL_OP_CAPACITY"));
        shipOPCap = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, shipOPCapLabel, shipOPCap, 0);

        JLabel usedOPTotalLabel = new JLabel(StringManager.getString("USED_OP_FOR_SHIP"));
        usedOPTotal = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, usedOPTotalLabel, usedOPTotal, 1);

        JLabel usedOPLabel = new JLabel(StringManager.getString("USED_OP_IN_HULLMODS"));
        usedOPInHullmods = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, usedOPLabel, usedOPInHullmods, 2);

        return infoPanel;
    }

    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                refreshAll(checked.selected());
            } else if (event instanceof ActiveLayerUpdated checked) {
                refreshAll(checked.updated());
            } else if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.VARIANT_DATA) {
                    refreshAll(StaticController.getActiveLayer());
                }
            }
        });
    }

    private void refreshAll(ViewerLayer selected) {
        normalModsPanel.refreshListModel(selected);
        permaModsPanel.refreshListModel(selected);
        sModsPanel.refreshListModel(selected);
        suppressedModsPanel.refreshListModel(selected);

        refreshLayerInfo(selected);
    }

    private void refreshLayerInfo(ViewerLayer selected) {
        String notInitialized = StringManager.getString("NOT_INITIALIZED");

        if (selected instanceof ShipLayer shipLayer) {
            String totalOP = Utility.translateIntegerValue(shipLayer::getTotalOP);
            shipOPCap.setText(totalOP);

            var activeVariant = shipLayer.getActiveVariant();
            if (activeVariant == null) {
                usedOPTotal.setText(notInitialized);
                usedOPInHullmods.setText(notInitialized);
                return;
            }

            int totalUsedOP = shipLayer.getTotalUsedOP();
            usedOPTotal.setText(String.valueOf(totalUsedOP));

            int totalOPInMods = activeVariant.getTotalOPInHullmods(shipLayer);
            usedOPInHullmods.setText(String.valueOf(totalOPInMods));

        } else {
            shipOPCap.setText(notInitialized);
            usedOPTotal.setText(notInitialized);
            usedOPInHullmods.setText(notInitialized);
        }
    }

}
