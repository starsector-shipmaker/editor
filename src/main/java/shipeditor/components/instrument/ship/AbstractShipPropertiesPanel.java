package shipeditor.components.instrument.ship;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.instrument.AbstractDataPropertiesPanel;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.text.StringManager;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

@Log4j2
public abstract class AbstractShipPropertiesPanel extends AbstractDataPropertiesPanel<ShipPainter> {

    protected AbstractShipPropertiesPanel() {
        super(ShipPainter.class);
    }

    /**
     * Creates a visibility selector bound to one of the ship's point painters.
     *
     * @param painterGetter resolves the point painter to control from the ship painter.
     * @param labelKey      {@link StringManager} key for the widget label.
     */
    protected Pair<JLabel, JComboBox<PainterVisibility>> createShipVisibilityWidget(
            Function<ShipPainter, AbstractPointPainter> painterGetter, String labelKey) {
        Function<LayerPainter, AbstractPointPainter> getter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return painterGetter.apply(shipPainter);
            }
            return null;
        };

        var visibilityWidget = createVisibilityWidget(getter);

        JLabel visibilityLabel = visibilityWidget.getFirst();
        visibilityLabel.setText(StringManager.getString(labelKey));

        return visibilityWidget;
    }

    /**
     * Wraps a single visibility selector into the standard padded widgets panel used above point lists.
     */
    protected JPanel createShipVisibilityPanel(Function<ShipPainter, AbstractPointPainter> painterGetter,
                                               String labelKey) {
        log.debug("Creating visibility panel for label key {}", labelKey);
        var visibilityWidget = createShipVisibilityWidget(painterGetter, labelKey);
        Map<JLabel, JComponent> visibilityWidgetMap = Map.of(visibilityWidget.getFirst(), visibilityWidget.getSecond());
        JPanel visibilityWidgetContainer = this.createWidgetsPanel(visibilityWidgetMap);
        visibilityWidgetContainer.setBorder(new EmptyBorder(4, 0, 3, 0));
        return visibilityWidgetContainer;
    }

    /**
     * Creates an opacity slider bound to one of the ship's point painters.
     *
     * @param painterGetter resolves the point painter to control from the ship painter.
     * @param labelKey      {@link StringManager} key for the widget label.
     */
    protected Pair<JLabel, JSlider> createShipOpacityWidget(
            Function<ShipPainter, AbstractPointPainter> painterGetter, String labelKey) {
        BooleanSupplier readinessChecker = this::isWidgetsReadyForInput;
        Consumer<Float> opacitySetter = changedValue -> {
            LayerPainter cachedLayerPainter = getCachedLayerPainter();
            if (cachedLayerPainter instanceof ShipPainter shipPainter) {
                AbstractPointPainter pointPainter = painterGetter.apply(shipPainter);
                if (pointPainter != null) {
                    pointPainter.setPaintOpacity(changedValue != null ? changedValue : 1.0f);
                    processChange();
                }
            }
        };

        BiConsumer<JComponent, Consumer<LayerPainter>> clearerListener = this::registerWidgetClearer;
        BiConsumer<JComponent, Consumer<LayerPainter>> refresherListener = this::registerWidgetRefresher;

        Function<LayerPainter, Float> opacityGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                AbstractPointPainter pointPainter = painterGetter.apply(shipPainter);
                return pointPainter != null ? pointPainter.getPaintOpacity() : 1.0f;
            }
            return 1.0f;
        };

        Pair<JLabel, JSlider> opacityWidget = ComponentUtilities.createOpacityWidget(readinessChecker,
                opacityGetter, opacitySetter, clearerListener, refresherListener);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString(labelKey));

        return opacityWidget;
    }

}
