package shipeditor.components.instrument.ship.engines;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.EngineSlotPainter;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.objects.Pair;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.EngineInsertedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointAddConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemovedConfirmed;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class EnginesPanel extends AbstractShipPropertiesPanel {

    @Getter
    private EngineList enginesContainer;

    private EngineDataPanel dataPanel;

    private JCheckBox reorderCheckbox;

    private DefaultListModel<EnginePoint> model;

    public EnginesPanel() {
        this.initPointListener();
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        DefaultListModel<EnginePoint> newModel = new DefaultListModel<>();

        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            this.model = newModel;
            this.enginesContainer.setModel(newModel);

            fireClearingListeners(layerPainter);
            refreshEngineControlPane(null);

            this.enginesContainer.setEnabled(false);
            this.reorderCheckbox.setEnabled(false);
            return;
        }

        EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
        newModel.addAll(enginePainter.getPointsIndex());

        this.model = newModel;
        this.enginesContainer.setModel(newModel);
        this.enginesContainer.setEnabled(true);
        this.reorderCheckbox.setEnabled(true);

        fireRefresherListeners(layerPainter);
        refreshEngineControlPane(enginePainter.getSelected());
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        this.model = new DefaultListModel<>();
        this.enginesContainer = new EngineList(model, this::refreshEngineControlPane);
        this.dataPanel = new EngineDataPanel();

        JPanel northContainer = new JPanel(new BorderLayout());
        JPanel visibilityWidgetContainer = createShipVisibilityPanel(ShipPainter::getEnginePainter, "ENGINES_VIEW");
        northContainer.add(visibilityWidgetContainer, BorderLayout.PAGE_START);

        ComponentUtilities.outfitPanelWithTitle(dataPanel, "Engine Data");
        northContainer.add(dataPanel, BorderLayout.CENTER);

        this.refreshEngineControlPane(null);

        JScrollPane scrollableContainer = new JScrollPane(enginesContainer);

        Pair<JPanel, JCheckBox> reorderWidget = ComponentUtilities.createReorderCheckboxPanel(enginesContainer);
        reorderCheckbox = reorderWidget.getSecond();
        northContainer.add(reorderWidget.getFirst(), BorderLayout.PAGE_END);

        this.add(northContainer, BorderLayout.PAGE_START);

        this.add(scrollableContainer, BorderLayout.CENTER);
    }

    private void refreshEngineControlPane(EnginePoint engine) {
        ShipPainter painter = (ShipPainter) getCachedLayerPainter();
        if (engine != null) {
            painter = (ShipPainter) engine.getParent();
        }
        this.dataPanel.refresh(painter);
    }

    private EngineSlotPainter getCachedEnginePainter() {
        LayerPainter cachedLayerPainter = getCachedLayerPainter();
        if (cachedLayerPainter instanceof ShipPainter shipPainter && !shipPainter.isUninitialized()) {
            return shipPainter.getEnginePainter();
        }
        return null;
    }

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() != EditorInstrument.ENGINES) {
                    return;
                }
                EngineSlotPainter cachedEnginePainter = getCachedEnginePainter();
                if (cachedEnginePainter != null) {
                    this.model = this.enginesContainer.synchronizeModel(this.model,
                            cachedEnginePainter.getPointsIndex());
                }

                this.refreshEngineControlPane(null);
            }
        });
    }

    private void initPointListener() {
        EventBus.subscribe(this, event -> {
            if (event instanceof PointRemovedConfirmed checked && checked.point() instanceof EnginePoint point) {
                model.removeElement(point);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof EngineInsertedConfirmed checked) {
                model.insertElementAt(checked.toInsert(), checked.precedingIndex());
                enginesContainer.setSelectedIndex(model.indexOf(checked.toInsert()));
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointAddConfirmed checked && checked.point() instanceof EnginePoint point) {
                model.addElement(point);
                enginesContainer.setSelectedIndex(model.indexOf(point));
            }
        });
    }

}
