package shipeditor.components.instrument.ship.variant.modules;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectedConfirmed;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.instrument.ship.variant.EmptyVariantBanner;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.components.UIFactory;
import shipeditor.utility.themes.Themes;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class VariantModulesPanel extends AbstractShipPropertiesPanel {

    private ModuleList moduleList;

    private ModuleControlPanel controlPanel;

    private DefaultListModel<InstalledFeature> model;

    private javax.swing.JLabel slotSummaryLabel;

    private EmptyVariantBanner emptyBanner;

    @SuppressWarnings({"OverlyComplexBooleanExpression", "ChainedMethodCall"})
    @Override
    public void refreshContent(LayerPainter layerPainter) {
        DefaultListModel<InstalledFeature> newModel = new DefaultListModel<>();
        refreshEmptyBanner(layerPainter);

        if (!(layerPainter instanceof ShipPainter shipPainter)
                || shipPainter.isUninitialized()
                || shipPainter.getActiveVariant() == null) {
            this.model = newModel;
            this.moduleList.setModel(newModel);

            if (this.slotSummaryLabel != null) {
                this.slotSummaryLabel.setText("Modules: 0/0 slots filled");
                this.slotSummaryLabel.setForeground(Themes.getDisabledTextColor());
            }

            fireClearingListeners(layerPainter);
            this.controlPanel.refresh(null);

            this.moduleList.setEnabled(false);

            return;
        }

        this.model = newModel;
        this.moduleList.setModel(newModel);

        List<InstalledFeature> allFeatures = getAllModuleFeatures(shipPainter);
        newModel.addAll(allFeatures);

        updateSlotSummary(shipPainter);

        this.moduleList.setEnabled(true);

        fireRefresherListeners(layerPainter);
        refreshModuleControlPane();
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        this.model = new DefaultListModel<>();
        this.moduleList = createModuleList();
        this.controlPanel = new ModuleControlPanel(moduleList);

        JPanel northContainer = new JPanel(new BorderLayout());

        this.emptyBanner = new EmptyVariantBanner();
        northContainer.add(emptyBanner, BorderLayout.PAGE_START);

        JPanel buttonContainer = new JPanel(new BorderLayout());
        buttonContainer.setBorder(new EmptyBorder(4, 4, 0, 4));

        this.slotSummaryLabel = UIFactory.createLabel("Modules: 0/0 slots filled");
        this.slotSummaryLabel.setBorder(new EmptyBorder(0, 0, 4, 0));

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(slotSummaryLabel, BorderLayout.PAGE_START);
        topContainer.add(getLoadModulesButton(), BorderLayout.CENTER);

        buttonContainer.add(topContainer, BorderLayout.CENTER);

        JPanel controlsStack = new JPanel(new BorderLayout());
        controlsStack.add(buttonContainer, BorderLayout.PAGE_START);

        ComponentUtilities.outfitPanelWithTitle(controlPanel, "Selected module");
        controlsStack.add(controlPanel, BorderLayout.CENTER);
        northContainer.add(controlsStack, BorderLayout.CENTER);

        this.refreshModuleControlPane();

        JScrollPane scrollableContainer = new JScrollPane(moduleList);

        this.add(northContainer, BorderLayout.PAGE_START);
        this.add(scrollableContainer, BorderLayout.CENTER);

        this.handleRefreshFromLayer(StaticController.getActiveLayer());
    }

    private void refreshEmptyBanner(LayerPainter layerPainter) {
        if (this.emptyBanner == null) {
            return;
        }
        this.emptyBanner.refresh(layerPainter != null ? layerPainter.getParentLayer() : null);
    }

    private JButton getLoadModulesButton() {
        JButton loadModulesAsLayers = UIFactory.createButton("Load modules as layers");

        registerWidgetListeners(loadModulesAsLayers, layer ->
                loadModulesAsLayers.setEnabled(false), layer -> {
            ShipVariant currentVariant = getCurrentVariant();
            if (currentVariant != null) {
                List<InstalledFeature> fittedModulesList = currentVariant.getFittedModulesList();
                loadModulesAsLayers.setEnabled(!fittedModulesList.isEmpty());
            } else {
                loadModulesAsLayers.setEnabled(false);
            }
        });

        loadModulesAsLayers.addActionListener(event -> {
            ShipVariant currentVariant = getCurrentVariant();
            if (currentVariant != null) {
                List<InstalledFeature> fittedModulesList = currentVariant.getFittedModulesList();
                if (!fittedModulesList.isEmpty()) {
                    fittedModulesList.forEach(a -> a.loadAsSeparateLayer());
                }
            }
        });

        return loadModulesAsLayers;
    }

    @Override
    public ShipPainter getCachedLayerPainter() {
        LayerPainter cachedLayerPainter = super.getCachedLayerPainter();
        if (cachedLayerPainter instanceof ShipPainter shipPainter && !shipPainter.isUninitialized()) {
            return shipPainter;
        }
        return null;
    }

    private ShipVariant getCurrentVariant() {
        ShipPainter shipPainter = getCachedLayerPainter();
        if (shipPainter == null) return null;
        ShipVariant activeVariant = shipPainter.getActiveVariant();
        if (activeVariant != null && !activeVariant.isEmpty()) {
            return activeVariant;
        }
        return null;
    }

    private List<InstalledFeature> getAllModuleFeatures(ShipPainter shipPainter) {
        List<InstalledFeature> features = new ArrayList<>();
        ShipVariant activeVariant = shipPainter.getActiveVariant();
        if (activeVariant == null) return features;

        java.util.Map<String, InstalledFeature> fittedModules = activeVariant.getFittedModules();
        if (fittedModules == null) fittedModules = java.util.Collections.emptyMap();

        shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
        if (slotPainter != null) {
            for (shipeditor.components.viewer.entities.weapon.WeaponSlotPoint slot : slotPainter.getSlotPoints()) {
                if (slot.getWeaponType() == shipeditor.representation.weapon.WeaponEnums.WeaponType.STATION_MODULE) {
                    InstalledFeature feature = fittedModules.get(slot.getId());
                    if (feature != null) {
                        features.add(feature);
                    } else {
                        features.add(InstalledFeature.createEmpty(slot.getId()));
                    }
                }
            }
        }
        return features;
    }

    private void updateSlotSummary(ShipPainter shipPainter) {
        if (this.slotSummaryLabel == null) return;

        int totalSlots = 0;
        int filledSlots = 0;

        ShipVariant activeVariant = shipPainter.getActiveVariant();
        if (activeVariant != null) {
            java.util.Map<String, InstalledFeature> fittedModules = activeVariant.getFittedModules();
            if (fittedModules == null) fittedModules = java.util.Collections.emptyMap();

            shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
            if (slotPainter != null) {
                for (shipeditor.components.viewer.entities.weapon.WeaponSlotPoint slot : slotPainter.getSlotPoints()) {
                    if (slot.getWeaponType() == shipeditor.representation.weapon.WeaponEnums.WeaponType.STATION_MODULE) {
                        totalSlots++;
                        if (fittedModules.containsKey(slot.getId())) {
                            filledSlots++;
                        }
                    }
                }
            }
        }

        this.slotSummaryLabel.setText(String.format("Modules: %d/%d slots filled", filledSlots, totalSlots));
        if (totalSlots == 0) {
            this.slotSummaryLabel.setForeground(Themes.getDisabledTextColor());
        } else if (filledSlots == totalSlots) {
            this.slotSummaryLabel.setForeground(Themes.getSuccessColor());
        } else if (filledSlots > 0) {
            this.slotSummaryLabel.setForeground(Themes.getWarningColor());
        } else {
            this.slotSummaryLabel.setForeground(Themes.getDisabledTextColor());
        }
    }

    private void refreshModuleControlPane() {
        InstalledFeature selectedValue = moduleList.getSelectedValue();
        if (selectedValue == null) {
            this.controlPanel.refresh(null);
            return;
        }
        ShipPainter painter = (ShipPainter) selectedValue.getFeaturePainter();
        this.controlPanel.refresh(painter);
    }

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof shipeditor.communication.events.viewer.points.PointEvents.InstrumentModeChanged checked) {
                if (checked.newMode() == EditorInstrument.VARIANT_MODULES) {
                    ShipPainter shipPainter = getCachedLayerPainter();
                    if (shipPainter != null && shipPainter.getWeaponSlotPainter() != null) {
                        ShipVariant activeVariant = shipPainter.getActiveVariant();
                        java.util.Map<String, InstalledFeature> fittedModules = activeVariant != null ? activeVariant.getFittedModules() : java.util.Collections.emptyMap();
                        if (fittedModules == null) fittedModules = java.util.Collections.emptyMap();

                        for (shipeditor.components.viewer.entities.weapon.WeaponSlotPoint slot : shipPainter.getWeaponSlotPainter().getSlotPoints()) {
                            if (slot.getWeaponType() == shipeditor.representation.weapon.WeaponEnums.WeaponType.STATION_MODULE) {
                                if (!fittedModules.containsKey(slot.getId())) {
                                    EventBus.publish(new shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued(slot));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointSelectedConfirmed checked) {
                if (!(checked.point() instanceof WeaponSlotPoint slotPoint)) return;
                if (moduleList != null && StaticController.getEditorMode() == EditorInstrument.VARIANT_MODULES) {
                    moduleList.selectEntryByPoint(slotPoint);
                }
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() != EditorInstrument.VARIANT_MODULES
                        && checked.editorMode() != EditorInstrument.VARIANT_DATA) {
                    return;
                }
                ShipPainter shipPainter = getCachedLayerPainter();
                refreshEmptyBanner(shipPainter);
                if (shipPainter != null) {
                    java.util.List<InstalledFeature> currentFeatures = getAllModuleFeatures(shipPainter);
                    updateSlotSummary(shipPainter);
                    fireRefresherListeners(shipPainter);

                    boolean modelsEqual = true;
                    if (this.model.getSize() == currentFeatures.size()) {
                        for (int i = 0; i < this.model.getSize(); i++) {
                            InstalledFeature mFeat = this.model.getElementAt(i);
                            InstalledFeature cFeat = currentFeatures.get(i);
                            if (!mFeat.getSlotID().equals(cFeat.getSlotID())
                                    || mFeat.getDataEntry() != cFeat.getDataEntry()
                                    || !java.util.Objects.equals(mFeat.getFeatureID(), cFeat.getFeatureID())) {
                                modelsEqual = false;
                                break;
                            }
                        }
                    } else {
                        modelsEqual = false;
                    }

                    if (!modelsEqual) {
                        int[] cachedSelected = this.moduleList.getSelectedIndices();
                        DefaultListModel<InstalledFeature> newModel = new DefaultListModel<>();
                        newModel.addAll(currentFeatures);

                        this.model = newModel;
                        this.moduleList.setModel(newModel);
                        this.moduleList.setSelectedIndices(cachedSelected);
                        if (!this.model.isEmpty() && cachedSelected.length > 0) {
                            this.moduleList.ensureIndexIsVisible(cachedSelected[0]);
                        }
                    } else {
                        this.moduleList.repaint();
                    }
                }

                this.refreshModuleControlPane();
            }
        });
    }

    private ModuleList createModuleList() {
        Consumer<InstalledFeature> removeAction = feature ->
                StaticController.actOnCurrentVariant((shipLayer, variant) -> {
                    Map<String, InstalledFeature> fittedModules = variant.getFittedModules();
                    if (fittedModules == null) {
                        return;
                    }
                    EditDispatch.postFeatureUninstalled(fittedModules,
                            feature.getSlotID(), feature, null);
                });

        Consumer<Map<String, InstalledFeature>> sortAction = rearranged ->
                StaticController.actOnCurrentVariant((shipLayer, variant) ->
                        variant.sortModules(rearranged));

        moduleList = new ModuleList(this::refreshModuleControlPane, model, removeAction, sortAction);
        moduleList.setBorder(new LineBorder(Themes.getBorderColor()));
        return moduleList;
    }

}
