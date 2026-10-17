package shipeditor.components.instrument.ship.engines;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.EngineStylesLoaded;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.LayerPropertiesPanel;
import shipeditor.components.viewer.entities.engine.EngineData;
import shipeditor.components.viewer.entities.engine.EngineDataOverride;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.EngineSlotPainter;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.widgets.Spinners;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.objects.Size2D;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class EngineDataPanel extends LayerPropertiesPanel {

    EngineDataPanel() {
        EventBus.subscribe(this, event -> {
            if (event instanceof EngineStylesLoaded) {
                this.populateContent();
                refresh(this.getCachedLayerPainter());
            }
        });
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (layerPainter == null || layerPainter.isUninitialized()) {
            fireClearingListeners(null);
            return;
        }

        fireRefresherListeners(layerPainter);
    }

    @Override
    protected void addWidgetRow(JPanel contentContainer, JLabel label, JComponent component, int ordering) {
        ComponentUtilities.addLabelAndComponent(contentContainer,
                label, component, 2, 2, 0, ordering);
    }

    private static EnginePoint getSelectedFromLayer(LayerPainter layerPainter) {
        if (layerPainter instanceof ShipPainter shipPainter) {
            if (shipPainter.isUninitialized()) return null;
            var enginePainter = shipPainter.getEnginePainter();

            return enginePainter.getSelected();
        }
        return null;
    }

    @Override
    public ShipPainter getCachedLayerPainter() {
        return (ShipPainter) super.getCachedLayerPainter();
    }

    @Override
    protected void populateContent() {
        this.removeAll();
        clearAllWidgetListeners();

        this.setLayout(new BorderLayout());

        Map<JLabel, JComponent> widgets = new LinkedHashMap<>();

        var angleController = createAngleController();
        widgets.put(angleController.getFirst(), angleController.getSecond());

        var widthController = createWidthController();
        widgets.put(widthController.getFirst(), widthController.getSecond());

        var lengthController = createLengthController();
        widgets.put(lengthController.getFirst(), lengthController.getSecond());

        var contrailController = createContrailController();
        widgets.put(contrailController.getFirst(), contrailController.getSecond());

        var styleSelector = createStyleSelector();
        widgets.put(styleSelector.getFirst(), styleSelector.getSecond());

        JPanel widgetsPanel = createWidgetsPanel(widgets);
        this.add(widgetsPanel, BorderLayout.CENTER);
        this.revalidate();
        this.repaint();
    }

    private Pair<JLabel, JComponent> createAngleController() {
        String text = StringManager.getString("ENGINE_ANGLE");
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(0.0d,
                -360.0d, 360.0d, 0.5d);
        Function<EngineData, Double> getter = a -> a.getAngleBoxed();
        Consumer<Double> setter = degrees -> {
            ShipPainter shipPainter = getCachedLayerPainter();
            EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
            EnginePoint selected = enginePainter.getSelected();
            if (selected != null) {
                enginePainter.changePointAngleWithMirrorCheck(selected, degrees);
            }
        };
        return createValueWidget(text, spinnerNumberModel, getter, setter);
    }

    private Pair<JLabel, JComponent> createWidthController() {
        String text = StringManager.getString("ENGINE_WIDTH");
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(0.0d,
                0.0d, Double.MAX_VALUE, 0.5d);
        Function<EngineData, Double> getter = a -> a.getWidthBoxed();
        Consumer<Double> setter = width -> {
            ShipPainter shipPainter = getCachedLayerPainter();
            EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
            EnginePoint selected = enginePainter.getSelected();
            if (selected != null) {
                double currentLength = selected.getLength();
                Size2D newSize = new Size2D(width, currentLength);
                enginePainter.changeEngineSizeWithMirrorCheck(selected, newSize);
            }
        };
        return createValueWidget(text, spinnerNumberModel, getter, setter);
    }

    private Pair<JLabel, JComponent> createLengthController() {
        String text = StringManager.getString("ENGINE_LENGTH");
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(0.0d,
                0.0d, Double.MAX_VALUE, 0.5d);
        Function<EngineData, Double> getter = a -> a.getLengthBoxed();
        Consumer<Double> setter = length -> {
            ShipPainter shipPainter = getCachedLayerPainter();
            EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
            EnginePoint selected = enginePainter.getSelected();
            if (selected != null) {
                double selectedWidth = selected.getWidth();
                Size2D newSize = new Size2D(selectedWidth, length);
                enginePainter.changeEngineSizeWithMirrorCheck(selected, newSize);
            }
        };
        return createValueWidget(text, spinnerNumberModel, getter, setter);
    }

    private Pair<JLabel, JComponent> createContrailController() {
        String text = StringManager.getString("CONTRAIL_SIZE");
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(0.0d,
                0.0d, 128.0d, 1.0d);
        Function<EngineData, Double> getter = a -> a.getContrailSizeBoxed();
        Consumer<Double> setter = contrailValue -> {
            ShipPainter shipPainter = getCachedLayerPainter();
            EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
            EnginePoint selected = enginePainter.getSelected();
            if (selected != null) {
                enginePainter.changeEngineContrailWithMirrorCheck(selected, (int) Math.round(contrailValue));
            }
        };
        return createValueWidget(text, spinnerNumberModel, getter, setter);
    }

    private Pair<JLabel, JComponent> createStyleSelector() {
        JLabel selectorLabel = new JLabel(StringManager.getString("ENGINE_STYLE_LABEL"));
        selectorLabel.setToolTipText(StringManager.getString("CHANGE_APPLIES_TO_FIRST_SELECTED_SLOT"));

        JComponent widget;

        GameDataRepository gameData = SettingsManager.getGameData();
        Map<String, EngineStyle> allEngineStyles = gameData.getAllEngineStyles();
        if (allEngineStyles != null) {
            Collection<EngineStyle> styleCollection = allEngineStyles.values();
            JComboBox<EngineStyle> styleSelector  = new JComboBox<>(styleCollection.toArray(new EngineStyle[0]));

            styleSelector.addActionListener(e -> {
                if (isWidgetsReadyForInput()) {
                    ShipPainter slotParent = getCachedLayerPainter();
                    EngineSlotPainter enginePainter = slotParent.getEnginePainter();
                    EnginePoint selected = enginePainter.getSelected();
                    if (selected != null) {
                        EngineStyle selectedValue = (EngineStyle) styleSelector.getSelectedItem();
                        enginePainter.changeEngineStyleWithMirrorCheck(selected, selectedValue);
                    }
                }
            });

            widget = styleSelector;

            registerWidgetListeners(styleSelector, layerPainter -> {
                styleSelector.setSelectedItem(null);
                styleSelector.setToolTipText(null);
                styleSelector.setEnabled(false);
            }, layerPainter -> {
                var selectedEngine = EngineDataPanel.getSelectedFromLayer(layerPainter);

                if (selectedEngine != null) {
                    EngineDataOverride skinOverride = selectedEngine.getSkinOverride();
                    if (skinOverride != null && (skinOverride.getStyle() != null || skinOverride.getStyleID() != null)) {
                        styleSelector.setToolTipText(StringManager.getString("LOCKED_STYLE_OVERRIDDEN_BY_SKIN"));
                        styleSelector.setEnabled(false);
                    } else {
                        styleSelector.setSelectedItem(selectedEngine.getStyle());
                        styleSelector.setEnabled(true);
                    }
                } else {
                    styleSelector.setSelectedItem(null);
                    styleSelector.setToolTipText(null);
                    styleSelector.setEnabled(false);
                }
            });
        } else {
            JTextField idField = new JTextField();
            idField.setToolTipText(StringManager.getString("STYLES_NOT_LOADED_DEFAULTED_TO_ID_TEXT"));

            idField.addActionListener(e -> {
                String textValue = idField.getText();
                ShipPainter slotParent = getCachedLayerPainter();
                EngineSlotPainter enginePainter = slotParent.getEnginePainter();
                EnginePoint selected = enginePainter.getSelected();
                if (selected != null) {
                    selected.setStyleID(textValue);
                    EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.ENGINES));
                }
            });

            widget = idField;

            registerWidgetListeners(idField, layerPainter -> {
                idField.setText("");
                idField.setToolTipText(null);
                idField.setEnabled(false);
            }, layerPainter -> {
                var selectedEngine = EngineDataPanel.getSelectedFromLayer(layerPainter);

                if (selectedEngine != null) {
                    EngineDataOverride skinOverride = selectedEngine.getSkinOverride();
                    if (skinOverride != null && (skinOverride.getStyle() != null || skinOverride.getStyleID() != null)) {
                        idField.setToolTipText(StringManager.getString("LOCKED_STYLE_OVERRIDDEN_BY_SKIN"));
                        idField.setEnabled(false);
                    } else {
                        idField.setText(selectedEngine.getStyleID());
                        idField.setEnabled(true);
                    }
                } else {
                    idField.setText("");
                    idField.setToolTipText(null);
                    idField.setEnabled(false);
                }
            });
        }

        return new Pair<>(selectorLabel, widget);
    }

    private Pair<JLabel, JComponent> createValueWidget(String labelText, SpinnerNumberModel model,
                                                       Function<EngineData, Double> getter, Consumer<Double> setter) {
        JLabel label = new JLabel(labelText);

        String changeApplies = StringManager.getString("CHANGE_APPLIES_TO_FIRST_SELECTED_SLOT");
        String tooltip = Utility.getWithLinebreaks(changeApplies, StringManager.getString("MOUSEWHEEL_TO_CHANGE"));
        label.setToolTipText(tooltip);

        JSpinner spinner =  Spinners.createWheelable(model);

        spinner.addChangeListener(e -> {
            if (isWidgetsReadyForInput()) {
                Number modelNumber = model.getNumber();
                double current = modelNumber.doubleValue();
                setter.accept(current);
            }
        });

        registerWidgetListeners(spinner, layerPainter -> {
            spinner.setValue(0.0d);
            spinner.setToolTipText(null);
            spinner.setEnabled(false);
        }, layerPainter -> {
            var selectedEngine = EngineDataPanel.getSelectedFromLayer(layerPainter);

            if (selectedEngine != null) {
                EngineDataOverride skinOverride = selectedEngine.getSkinOverride();

                if (skinOverride != null && getter.apply(skinOverride) != null) {
                    spinner.setToolTipText(StringManager.getString("LOCKED_OVERRIDDEN_BY_SKIN"));
                    spinner.setEnabled(false);
                } else {
                    spinner.setValue(getter.apply(selectedEngine));
                    spinner.setEnabled(true);
                }
            } else {
                spinner.setValue(0.0d);
                spinner.setToolTipText(null);
                spinner.setEnabled(false);
            }
        });

        return new Pair<>(label, spinner);
    }

}
