package shipeditor.components.instrument;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import shipeditor.components.instrument.weapon.WeaponFirePanelUtilities;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.objects.Pair;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** * This class is the preferred way of implementing various property panels like hull/skin data values.
 * Handles refresh and population of values on layer selection.*/
@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class LayerPropertiesPanel extends JPanel {

    private final Map<JComponent, Consumer<LayerPainter>> clearingListeners;

    private final Map<JComponent, Consumer<LayerPainter>> refresherListeners;

    private boolean widgetsReadyForInput;

    @Setter
    private LayerPainter cachedLayerPainter;

    protected LayerPropertiesPanel() {
        clearingListeners = new LinkedHashMap<>();
        refresherListeners = new LinkedHashMap<>();
        this.populateContent();
    }

    /**
     * Default method for populating widgets.
     * @return panel with added widgets, which uses GridBagLayout.
     */
    protected JPanel createWidgetsPanel(Map<JLabel, JComponent> widgets) {
        JPanel contentContainer = new JPanel();
        contentContainer.setLayout(new GridBagLayout());

        int ordering = 0;
        for (Map.Entry<JLabel, JComponent> entry : widgets.entrySet()) {
            JLabel label = entry.getKey();
            JComponent widget = entry.getValue();
            addWidgetRow(contentContainer, label, widget, ordering);
            ordering++;
        }

        return contentContainer;
    }

    public void refresh(LayerPainter layer) {
        cachedLayerPainter = layer;
        widgetsReadyForInput = false;
        this.refreshContent(layer);
        widgetsReadyForInput = true;
    }

    protected void addWidgetRow(JPanel contentContainer, JLabel label, JComponent component, int ordering) {
        ComponentUtilities.addLabelAndComponent(contentContainer, label, component, ordering);
    }

    /**
     * To be called at any significant layer change, like selection. Not expected to add or remove components.
     */
    public abstract void refreshContent(LayerPainter layerPainter);

    /**
     * Called once at panel creation to install all sub-components.
     */
    protected abstract void populateContent();

    protected void processChange() {
        this.refresh(cachedLayerPainter);
    }

    protected void fireClearingListeners(LayerPainter layer) {
        clearingListeners.forEach((widget, clearer) -> clearer.accept(layer));
    }

    protected void fireRefresherListeners(LayerPainter layer) {
        refresherListeners.forEach((widget, refresher) -> refresher.accept(layer));
    }

    protected void registerWidgetListeners(JComponent widget,
                                           Consumer<LayerPainter> clearer,
                                           Consumer<LayerPainter> refresher) {
        registerWidgetClearer(widget, clearer);
        registerWidgetRefresher(widget, refresher);
    }

    protected void registerWidgetClearer(JComponent widget,
                                           Consumer<LayerPainter> clearer) {
        clearingListeners.put(widget, clearer);
    }

    protected void registerWidgetRefresher(JComponent widget,
                                         Consumer<LayerPainter> refresher) {
        refresherListeners.put(widget, refresher);
    }

    protected void clearAllWidgetListeners() {
        clearingListeners.clear();
        refresherListeners.clear();
    }

    protected Pair<JLabel, JComboBox<PainterVisibility>> createVisibilityWidget(Function<LayerPainter,
            AbstractPointPainter> getter) {
        return createVisibilityWidget(getter, null);
    }

    protected Pair<JLabel, JComboBox<PainterVisibility>> createVisibilityWidget(Function<LayerPainter,
            AbstractPointPainter> getter, Consumer<PainterVisibility> additionalAction) {
        BooleanSupplier readinessChecker = this::isWidgetsReadyForInput;
        Consumer<PainterVisibility> visibilitySetter = changedValue -> {
            LayerPainter layerPainter = getCachedLayerPainter();
            if (layerPainter != null) {
                AbstractPointPainter pointPainter = getter.apply(layerPainter);
                if (pointPainter != null) {
                    pointPainter.setVisibilityMode(changedValue);
                    processChange();
                } else {
                    throw new IllegalStateException("Invalid usage of visibility widget!");
                }
            }
            if (additionalAction != null) {
                additionalAction.accept(changedValue);
            }
        };

        BiConsumer<JComponent, Consumer<LayerPainter>> clearerListener = this::registerWidgetClearer;
        BiConsumer<JComponent, Consumer<LayerPainter>> refresherListener = this::registerWidgetRefresher;

        Function<LayerPainter, PainterVisibility> visibilityGetter = layerPainter -> {
            AbstractPointPainter pointPainter = getter.apply(cachedLayerPainter);
            return pointPainter.getVisibilityMode();
        };

        return PainterVisibility.createVisibilityWidget(
                readinessChecker, visibilityGetter,
                visibilitySetter, clearerListener, refresherListener
        );
    }

    protected javax.swing.JTextField createTextField(Consumer<String> setter) {
        return WeaponFirePanelUtilities.createTextField(null, this::isWidgetsReadyForInput, setter,
                this::processChange);
    }

    protected javax.swing.JTextField createDoubleField(Consumer<Double> setter) {
        return WeaponFirePanelUtilities.createDoubleField(null, this::isWidgetsReadyForInput, setter,
                this::processChange);
    }

    protected javax.swing.JTextField createIntField(Consumer<Integer> setter) {
        return WeaponFirePanelUtilities.createIntField(null, this::isWidgetsReadyForInput, setter,
                this::processChange);
    }

    protected javax.swing.JTextField createListField(Consumer<java.util.List<String>> setter) {
        javax.swing.JTextField textField = new javax.swing.JTextField();
        textField.setColumns(10);
        textField.addActionListener(e -> {
            if (isWidgetsReadyForInput()) {
                String text = textField.getText();
                if (text.isEmpty()) {
                    setter.accept(null);
                } else {
                    setter.accept(java.util.Arrays.asList(text.split("\\s*,\\s*")));
                }
                processChange();
            }
        });
        return textField;
    }

    protected javax.swing.JCheckBox createCheckBox(String text, Consumer<Boolean> setter) {
        return WeaponFirePanelUtilities.createCheckBox(text, null, this::isWidgetsReadyForInput, setter,
                this::processChange);
    }

    protected Pair<JLabel, JSlider> createOpacityWidget(Function<LayerPainter, Float> opacityGetter,
                                                        Consumer<Float> opacitySetter) {
        BooleanSupplier readinessChecker = this::isWidgetsReadyForInput;

        BiConsumer<JComponent, Consumer<LayerPainter>> clearerListener = this::registerWidgetClearer;
        BiConsumer<JComponent, Consumer<LayerPainter>> refresherListener = this::registerWidgetRefresher;

        Pair<JLabel, JSlider> opacityWidget = ComponentUtilities.createOpacityWidget(readinessChecker,
                opacityGetter, opacitySetter, clearerListener, refresherListener);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("SPRITE_OPACITY"));

        return opacityWidget;
    }

}
