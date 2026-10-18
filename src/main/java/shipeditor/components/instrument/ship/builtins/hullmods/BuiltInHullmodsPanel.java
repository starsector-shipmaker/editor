package shipeditor.components.instrument.ship.builtins.hullmods;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.utility.components.ComponentUtilities;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;

public class BuiltInHullmodsPanel extends JPanel {

    private final BaseHullmodsListPane baseBuiltInModsList;

    private final SkinHullmodsListPane addedBySkinList;

    private final SkinHullmodsListPane removedBySkinList;

    public BuiltInHullmodsPanel() {
        this.setLayout(new BorderLayout());

        this.baseBuiltInModsList = new BaseHullmodsListPane(a -> a.getBuiltInMods(), (a, b) -> a.setBuiltInMods(b));
        this.addedBySkinList = new SkinHullmodsListPane(a -> a.getBuiltInMods(), (a, b) -> a.setBuiltInMods(b));
        this.removedBySkinList = new SkinHullmodsListPane(a -> a.getRemoveBuiltInMods(), (a, b) -> a.setRemoveBuiltInMods(b));

        JScrollPane scroller = ComponentUtilities.createBuiltInListsScroller(baseBuiltInModsList, addedBySkinList,
                removedBySkinList);

        this.add(scroller, BorderLayout.CENTER);

        JPanel infoPanel = ComponentUtilities.createDragInfoPanel();
        this.add(infoPanel, BorderLayout.PAGE_START);

        this.initLayerListeners();
    }

    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                ViewerLayer selected = checked.selected();
                baseBuiltInModsList.refreshListModel(selected);
                addedBySkinList.refreshListModel(selected);
                removedBySkinList.refreshListModel(selected);
            }
        });
    }

}
