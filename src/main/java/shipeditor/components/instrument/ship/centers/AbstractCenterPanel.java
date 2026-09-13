package shipeditor.components.instrument.ship.centers;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.UtilityEnums.IncrementType;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.widgets.PointLocationWidget;
import shipeditor.utility.components.widgets.Spinners;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.geom.Point2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractCenterPanel extends AbstractShipPropertiesPanel {

    private PointLocationWidget centerLocationWidget;

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == getMode()) {
                    this.handleRefreshFromLayer(StaticController.getActiveLayer());
                }
            }
        });
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            fireClearingListeners(layerPainter);
            if (centerLocationWidget != null) {
                centerLocationWidget.refresh(null);
            }
            refreshExtraComponents(null);
            return;
        }

        fireRefresherListeners(layerPainter);
        if (centerLocationWidget != null) {
            centerLocationWidget.refresh(layerPainter);
        }
        refreshExtraComponents(shipPainter);
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        JPanel topContainer = new JPanel(new BorderLayout());

        Map<JLabel, JComponent> topWidgets = new LinkedHashMap<>();

        var opacityWidget = createCenterOpacityWidget();
        topWidgets.put(opacityWidget.getFirst(), opacityWidget.getSecond());

        var visibilityWidget = createCenterVisibilityWidget();
        topWidgets.put(visibilityWidget.getFirst(), visibilityWidget.getSecond());

        Border bottomPadding = new EmptyBorder(0, 0, 4, 0);

        JPanel topWidgetsPanel = createWidgetsPanel(topWidgets);
        topWidgetsPanel.setBorder(bottomPadding);
        topContainer.add(topWidgetsPanel, BorderLayout.PAGE_START);
        centerLocationWidget = createCenterLocationWidget();
        topContainer.add(centerLocationWidget, BorderLayout.CENTER);
        this.add(topContainer, BorderLayout.PAGE_START);

        JPanel centerContainer = new JPanel(new BorderLayout());

        var radiusWidget = createCenterRadiusSpinner();
        Map<JLabel, JComponent> centerWidgets = Map.of(
                radiusWidget.getFirst(), radiusWidget.getSecond()
        );

        JPanel centerWidgetsPanel = createWidgetsPanel(centerWidgets);
        centerWidgetsPanel.setBorder(bottomPadding);
        centerContainer.add(centerWidgetsPanel, BorderLayout.PAGE_START);

        JComponent extraComponent = createCenterExtraComponent();
        if (extraComponent != null) {
            centerContainer.add(extraComponent, BorderLayout.CENTER);
        }

        JPanel buttonsPanel = new JPanel(new GridLayout(0, 2, 4, 4));
        buttonsPanel.setBorder(new EmptyBorder(4, 0, 4, 0));
        JButton autoCalcBtn = new JButton("<html><center>Auto-Calculate<br>Radius</center></html>",
                FontIcon.of(BoxiconsRegular.RADAR, 16, Themes.getIconColor()));
        autoCalcBtn.addActionListener(e -> autoCalculateRadius());

        JButton spriteCenterBtn = new JButton("<html><center>Set to<br>Sprite Center</center></html>",
                FontIcon.of(BoxiconsRegular.TARGET_LOCK, 16, Themes.getIconColor()));
        spriteCenterBtn.addActionListener(e -> setToSpriteCenter());

        buttonsPanel.add(autoCalcBtn);
        buttonsPanel.add(spriteCenterBtn);
        centerContainer.add(buttonsPanel, BorderLayout.PAGE_END);

        this.add(centerContainer, BorderLayout.CENTER);
    }

    protected void autoCalculateRadius() {
        LayerPainter layerPainter = getCachedLayerPainter();
        if (!(layerPainter instanceof ShipPainter shipPainter)) return;

        BaseWorldPoint centerPoint = getTargetCenterPoint(shipPainter);
        if (centerPoint == null) return;
        Point2D center = centerPoint.getPosition();

        List<BoundPoint> bounds = shipPainter.getBoundsPainter().getPointsIndex();
        float maxDistSq = 0;
        if (!bounds.isEmpty()) {
            for (BoundPoint bp : bounds) {
                float distSq = (float) center.distanceSq(bp.getPosition());
                if (distSq > maxDistSq) {
                    maxDistSq = distSq;
                }
            }
            float radius = (float) Math.ceil(Math.sqrt(maxDistSq));
            postRadiusChanged(centerPoint, radius);
            processChange();
        } else if (shipPainter.getSprite() != null && shipPainter.getSprite().getImage() != null) {
            float w = shipPainter.getSprite().getImage().getWidth();
            float h = shipPainter.getSprite().getImage().getHeight();
            float radius = (float) Math.ceil(Math.hypot(w, h) / 2.0);
            postRadiusChanged(centerPoint, radius);
            processChange();
        }
    }

    protected void setToSpriteCenter() {
        LayerPainter layerPainter = getCachedLayerPainter();
        if (!(layerPainter instanceof ShipPainter shipPainter)) return;
        if (shipPainter.getSprite() == null || shipPainter.getSprite().getImage() == null) return;

        float w = shipPainter.getSprite().getImage().getWidth();
        float h = shipPainter.getSprite().getImage().getHeight();
        Point2D anchor = shipPainter.getAnchor();

        Point2D newCenter = new Point2D.Double(anchor.getX() + w / 2.0, anchor.getY() + h / 2.0);

        BaseWorldPoint centerPoint = getTargetCenterPoint(shipPainter);
        if (centerPoint != null) {
            EditDispatch.postPointDragged(centerPoint, newCenter);
            processChange();
        }
    }

    private Pair<JLabel, JSlider> createCenterOpacityWidget() {
        BooleanSupplier readinessChecker = this::isWidgetsReadyForInput;
        Consumer<Float> opacitySetter = changedValue -> {
            LayerPainter cachedLayerPainter = getCachedLayerPainter();
            if (cachedLayerPainter instanceof ShipPainter shipPainter) {
                AbstractPointPainter pointPainter = getPointPainter(shipPainter);
                if (pointPainter != null) {
                    pointPainter.setPaintOpacity(changedValue);
                    processChange();
                }
            }
        };

        BiConsumer<JComponent, Consumer<LayerPainter>> clearerListener = this::registerWidgetClearer;
        BiConsumer<JComponent, Consumer<LayerPainter>> refresherListener = this::registerWidgetRefresher;

        Function<LayerPainter, Float> opacityGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                AbstractPointPainter pointPainter = getPointPainter(shipPainter);
                return pointPainter != null ? pointPainter.getPaintOpacity() : 1.0f;
            }
            return 1.0f;
        };

        Pair<JLabel, JSlider> opacityWidget = ComponentUtilities.createOpacityWidget(readinessChecker,
                opacityGetter, opacitySetter, clearerListener, refresherListener);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString(getOpacityLabelKey()));

        return opacityWidget;
    }

    private Pair<JLabel, JComboBox<PainterVisibility>> createCenterVisibilityWidget() {
        Function<LayerPainter, AbstractPointPainter> painterGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return getPointPainter(shipPainter);
            }
            return null;
        };

        var visibilityWidget = createVisibilityWidget(painterGetter);

        JLabel visibilityLabel = visibilityWidget.getFirst();
        visibilityLabel.setText(StringManager.getString(getVisibilityLabelKey()));

        return visibilityWidget;
    }

    private Pair<JLabel, JSpinner> createCenterRadiusSpinner() {
        double minimum = 0.0d;
        double maximum = Double.MAX_VALUE;
        double initial = 0.0d;
        SpinnerNumberModel numberModel = new SpinnerNumberModel(initial, minimum, maximum, 1.0d);

        JSpinner radiusSpinner = Spinners.createWheelable(numberModel, IncrementType.CHUNK);
        radiusSpinner.setEnabled(false);
        JLabel radiusLabel = new JLabel(StringManager.getString(getRadiusLabelKey()));

        radiusSpinner.addChangeListener(e -> {
            if (!isWidgetsReadyForInput()) return;
            Number modelNumber = numberModel.getNumber();
            double newRadius = modelNumber.doubleValue();

            LayerPainter layerPainter = getCachedLayerPainter();
            if (layerPainter instanceof ShipPainter shipPainter) {
                BaseWorldPoint centerPoint = getTargetCenterPoint(shipPainter);
                if (centerPoint != null) {
                    postRadiusChanged(centerPoint, (float) newRadius);
                    processChange();
                }
            }
        });

        registerWidgetListeners(radiusSpinner, layer -> {
            numberModel.setValue(0.0d);
            radiusSpinner.setEnabled(false);
        }, layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                BaseWorldPoint centerPoint = getTargetCenterPoint(shipPainter);
                if (centerPoint != null) {
                    double currentRadius = getCurrentRadius(centerPoint);
                    numberModel.setValue(currentRadius);
                    radiusSpinner.setEnabled(true);
                }
            }
        });

        return new Pair<>(radiusLabel, radiusSpinner);
    }

    protected JComponent createCenterExtraComponent() {
        return null;
    }

    protected void refreshExtraComponents(ShipPainter shipPainter) {
    }

    protected abstract EditorInstrument getMode();

    protected abstract String getOpacityLabelKey();

    protected abstract String getVisibilityLabelKey();

    protected abstract String getRadiusLabelKey();

    protected abstract AbstractPointPainter getPointPainter(ShipPainter shipPainter);

    protected abstract BaseWorldPoint getTargetCenterPoint(ShipPainter shipPainter);

    protected abstract double getCurrentRadius(BaseWorldPoint centerPoint);

    protected abstract void postRadiusChanged(BaseWorldPoint centerPoint, float radius);

    protected abstract PointLocationWidget createCenterLocationWidget();
}
