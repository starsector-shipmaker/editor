package shipeditor.components.instrument.ship.bays;


import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.viewer.entities.bays.LaunchPortPoint;
import shipeditor.components.viewer.entities.weapon.SlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.LaunchBayPainter;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointAddConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.LaunchBayRemoveConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemovedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.LaunchBayAddConfirmed;

public class LaunchBaysPanel extends AbstractShipPropertiesPanel {

    private LaunchBaysTree baysTree;

    private BayDataControlPane bayDataPanel;

    public LaunchBaysPanel() {
        this.initPointListener();
    }

    private void refreshPointDataPane(SlotPoint slotPoint) {
        ShipPainter painter = (ShipPainter) getCachedLayerPainter();
        if (slotPoint instanceof LaunchPortPoint portPoint) {
            painter = portPoint.getParent();
        }
        this.bayDataPanel.refresh(painter);
    }

    private void initPointListener() {
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.LAUNCH_BAYS) {
                    this.baysTree.repaint();
                    this.refreshPointDataPane(null);
                }
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof LaunchBayAddConfirmed checked) {
                var added = checked.added();
                var index = checked.index();

                if (index == -1) {
                    baysTree.addBay(added);
                } else {
                    baysTree.insertBay(added, index);
                }
                this.refreshPointDataPane(null);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof LaunchBayRemoveConfirmed checked) {
                baysTree.removeBay(checked.removed());
                this.refreshPointDataPane(null);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointAddConfirmed checked && checked.point() instanceof LaunchPortPoint point) {
                baysTree.addPort(point);
                this.refreshPointDataPane(point);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointRemovedConfirmed checked && checked.point() instanceof LaunchPortPoint point) {
                baysTree.removePort(point);
                this.refreshPointDataPane(point);
            }
        });
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        baysTree.clearRoot();
        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            fireClearingListeners(layerPainter);
            refreshPointDataPane(null);
            baysTree.setEnabled(false);
            return;
        }

        baysTree.setEnabled(true);
        LaunchBayPainter bayPainter = shipPainter.getBayPainter();
        baysTree.repopulateTree(bayPainter);
        baysTree.repaint();

        fireRefresherListeners(layerPainter);
        refreshPointDataPane(bayPainter.getSelected());
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Bays");
        baysTree = new LaunchBaysTree(root, this::refreshPointDataPane);

        JScrollPane scrollableContainer = new JScrollPane(baysTree);

        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.setBorder(new EmptyBorder(0, 0, 4, 0));
        JPanel visibilityWidgetContainer = createShipVisibilityPanel(ShipPainter::getBayPainter, "BAYS_VIEW");
        northContainer.add(visibilityWidgetContainer, BorderLayout.PAGE_START);

        this.bayDataPanel = new BayDataControlPane();
        ComponentUtilities.outfitPanelWithTitle(bayDataPanel, "Bay Data");
        northContainer.add(bayDataPanel, BorderLayout.CENTER);

        this.add(northContainer, BorderLayout.PAGE_START);

        this.add(scrollableContainer, BorderLayout.CENTER);
    }

}
