package shipeditor.components.instrument.ship.builtins.wings;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class BuiltInWingsPanel extends JPanel {

    private final BaseWingListPane baseBuiltInWingsList;

    private final SkinWingListPane addedBySkinList;

    private final SkinWingListPane removedBySkinList;

    private JLabel totalBayCount;

    private JLabel builtInWingsCount;

    public BuiltInWingsPanel() {
        this.setLayout(new BorderLayout());

        this.baseBuiltInWingsList = new BaseWingListPane(a -> a.getBuiltInWings(), (a, b) -> a.setBuiltInWings(b));
        this.addedBySkinList = new SkinWingListPane(a -> a.getBuiltInWings(), (a, b) -> a.setBuiltInWings(b));
        this.removedBySkinList = new SkinWingListPane(a -> a.getRemoveBuiltInWings(), (a, b) -> a.setRemoveBuiltInWings(b));

        JScrollPane scroller = ComponentUtilities.createBuiltInListsScroller(baseBuiltInWingsList, addedBySkinList,
                removedBySkinList);

        this.add(scroller, BorderLayout.CENTER);

        JPanel infoPanel = createInfoPanel();
        this.add(infoPanel, BorderLayout.PAGE_START);

        this.initLayerListeners();
    }

    private JPanel createInfoPanel() {
        JPanel container = new JPanel(new BorderLayout());

        JPanel dragHintPanel = ComponentUtilities.createDragInfoPanel();
        container.add(dragHintPanel, BorderLayout.PAGE_START);

        JPanel infoPanel = new JPanel();
        ComponentUtilities.outfitPanelWithTitle(infoPanel, "Built-in wings");
        infoPanel.setLayout(new GridBagLayout());

        JLabel totalBaysLabel = new JLabel(StringManager.getString("TOTAL_SHIP_BAYS"));
        totalBayCount = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, totalBaysLabel, totalBayCount, 0);

        JLabel totalBuiltInsLabel = new JLabel(StringManager.getString("TOTAL_BUILT_IN_WINGS"));
        builtInWingsCount = new JLabel();

        ComponentUtilities.addLabelAndComponent(infoPanel, totalBuiltInsLabel, builtInWingsCount, 1);

        container.add(infoPanel, BorderLayout.CENTER);

        return container;
    }

    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                ViewerLayer selected = checked.selected();

                refreshLayerInfo(selected);

                baseBuiltInWingsList.refreshListModel(selected);
                addedBySkinList.refreshListModel(selected);
                removedBySkinList.refreshListModel(selected);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.BUILT_IN_WINGS) {
                    this.refreshLayerInfo(StaticController.getActiveLayer());
                }
            }
        });
    }

    private void refreshLayerInfo(ViewerLayer selected) {
        String notInitialized = StringManager.getString("NOT_INITIALIZED");

        if (selected instanceof ShipLayer shipLayer) {
            String totalBays = Utility.translateIntegerValue(shipLayer::getBayCount);
            totalBayCount.setText(totalBays);
            String totalBuiltIns =  Utility.translateIntegerValue(shipLayer::getBuiltInWingsCount);
            builtInWingsCount.setText(totalBuiltIns);
        } else {
            totalBayCount.setText(notInitialized);
            builtInWingsCount.setText(notInitialized);
        }
    }

}
