package shipeditor.components.instrument.ship.bounds;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.ship.BoundPointsPainter;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.widgets.PointLocationWidget;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.BoundInsertedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointAddConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemovedConfirmed;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class BoundsPanel extends AbstractShipPropertiesPanel {

    @Getter
    private BoundList boundList;

    private DefaultListModel<BoundPoint> model;

    private PointLocationWidget selectedBoundWidget;

    public BoundsPanel() {
        this.initPointListeners();
    }

    private void initPointListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.BOUNDS) {
                    LayerPainter cachedLayerPainter = getCachedLayerPainter();
                    if (cachedLayerPainter != null) {
                        DefaultListModel<BoundPoint> newModel = new DefaultListModel<>();
                        BoundPointsPainter boundsPainter = ((ShipPainter) cachedLayerPainter).getBoundsPainter();
                        java.util.List<BoundPoint> currentPoints = boundsPainter.getPointsIndex();
                        boolean modelsEqual = true;
                        if (this.model.getSize() == currentPoints.size()) {
                            for (int i = 0; i < this.model.getSize(); i++) {
                                if (this.model.getElementAt(i) != currentPoints.get(i)) {
                                    modelsEqual = false;
                                    break;
                                }
                            }
                        } else {
                            modelsEqual = false;
                        }

                        if (!modelsEqual) {
                            int[] cachedSelected = this.boundList.getSelectedIndices();
                            newModel.addAll(currentPoints);
                            this.model = newModel;
                            this.boundList.setModel(newModel);
                            this.boundList.setSelectedIndices(cachedSelected);
                            if (!this.model.isEmpty() && cachedSelected.length > 0) {
                                this.boundList.ensureIndexIsVisible(cachedSelected[0]);
                            }
                        } else {
                            this.boundList.repaint();
                        }
                    }
                    this.refresh(cachedLayerPainter);
                }
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointAddConfirmed checked && checked.point() instanceof BoundPoint point) {
                model.addElement(point);
                boundList.setSelectedIndex(model.indexOf(point));
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof BoundInsertedConfirmed checked) {
                model.insertElementAt(checked.toInsert(), checked.precedingIndex());
                boundList.setSelectedIndex(model.indexOf(checked.toInsert()));
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointRemovedConfirmed checked && checked.point() instanceof BoundPoint point) {
                model.removeElement(point);
            }
        });
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        DefaultListModel<BoundPoint> newModel = new DefaultListModel<>();

        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            this.model = newModel;
            this.boundList.setModel(newModel);

            fireClearingListeners(layerPainter);
            selectedBoundWidget.refresh(null);

            this.boundList.setEnabled(false);
            return;
        }

        BoundPointsPainter newBoundsPainter = shipPainter.getBoundsPainter();
        newModel.addAll(newBoundsPainter.getPointsIndex());

        this.model = newModel;
        this.boundList.setModel(newModel);
        this.boundList.setEnabled(true);

        fireRefresherListeners(layerPainter);
        selectedBoundWidget.refresh(layerPainter);
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        JPanel topContainer = new JPanel(new BorderLayout());

        Map<JLabel, JComponent> topWidgets = new LinkedHashMap<>();

        var boundsOpacityWidget = createBoundsOpacityWidget();
        topWidgets.put(boundsOpacityWidget.getFirst(), boundsOpacityWidget.getSecond());

        var boundsVisibilityWidget = createBoundsVisibilityWidget();
        topWidgets.put(boundsVisibilityWidget.getFirst(), boundsVisibilityWidget.getSecond());

        Border bottomPadding = new EmptyBorder(0, 0, 4, 0);

        JPanel topWidgetsPanel = createWidgetsPanel(topWidgets);
        topWidgetsPanel.setBorder(bottomPadding);
        topContainer.add(topWidgetsPanel, BorderLayout.PAGE_START);
        selectedBoundWidget = createSelectedBoundLocationWidget();
        topContainer.add(selectedBoundWidget, BorderLayout.CENTER);
        this.add(topContainer, BorderLayout.PAGE_START);

        JPanel centerContainer = new JPanel(new BorderLayout());

        model = new DefaultListModel<>();
        boundList = new BoundList(model, () -> selectedBoundWidget.refresh(getCachedLayerPainter()));
        JScrollPane scrollableContainer = new JScrollPane(boundList);

        var reorderWidget = ComponentUtilities.createReorderCheckboxPanel(boundList);
        var reorderCheckbox = reorderWidget.getSecond();
        registerWidgetListeners(reorderCheckbox, layerPainter -> reorderCheckbox.setEnabled(false),
                layerPainter -> reorderCheckbox.setEnabled(true));
        
        JPanel listHeaderPanel = new JPanel(new BorderLayout());
        listHeaderPanel.add(reorderWidget.getFirst(), BorderLayout.WEST);

        JButton autoGenBtn = new JButton("Auto-Generate",
                FontIcon.of(BoxiconsRegular.SHAPE_POLYGON, 16, Themes.getIconColor()));
        autoGenBtn.setToolTipText(StringManager.getString("AUTOMATICALLY_GENERATE_COLLISION_BOUNDS_BASED_ON_THE_SPRITE_S_OPAQUE_PIXELS"));
        autoGenBtn.addActionListener(e -> autoGenerateBounds());
        registerWidgetListeners(autoGenBtn, 
                layerPainter -> autoGenBtn.setEnabled(false),
                layerPainter -> {
                    boolean hasSprite = layerPainter instanceof ShipPainter shipPainter && shipPainter.getSprite() != null && shipPainter.getSprite().getImage() != null;
                    autoGenBtn.setEnabled(hasSprite);
                });
        listHeaderPanel.add(autoGenBtn, BorderLayout.EAST);

        centerContainer.add(listHeaderPanel, BorderLayout.PAGE_START);

        centerContainer.add(scrollableContainer, BorderLayout.CENTER);

        this.add(centerContainer, BorderLayout.CENTER);
    }

    private Pair<JLabel, JSlider> createBoundsOpacityWidget() {
        BooleanSupplier readinessChecker = this::isWidgetsReadyForInput;
        Consumer<Float> opacitySetter = changedValue -> {
            LayerPainter cachedLayerPainter = getCachedLayerPainter();
            if (cachedLayerPainter instanceof ShipPainter shipPainter) {
                BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
                if (boundsPainter != null) {
                    boundsPainter.setPaintOpacity(changedValue != null ? changedValue : 1.0f);
                    processChange();
                }
            }
        };

        BiConsumer<JComponent, Consumer<LayerPainter>> clearerListener = this::registerWidgetClearer;
        BiConsumer<JComponent, Consumer<LayerPainter>> refresherListener = this::registerWidgetRefresher;

        Function<LayerPainter, Float> opacityGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
                if (boundsPainter != null) {
                    return boundsPainter.getPaintOpacity();
                }
            }
            return 1.0f;
        };

        Pair<JLabel, JSlider> opacityWidget = ComponentUtilities.createOpacityWidget(readinessChecker,
                opacityGetter, opacitySetter, clearerListener, refresherListener);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("BOUNDS_OPACITY"));

        return opacityWidget;
    }

    private Pair<JLabel, JComboBox<PainterVisibility>> createBoundsVisibilityWidget() {
        Function<LayerPainter, AbstractPointPainter> painterGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return shipPainter.getBoundsPainter();
            }
            return null;
        };

        var opacityWidget = createVisibilityWidget(painterGetter);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("BOUNDS_VIEW"));

        return opacityWidget;
    }

    private PointLocationWidget createSelectedBoundLocationWidget() {
        return new BoundLocationWidget(this);
    }

    private void autoGenerateBounds() {
        LayerPainter layerPainter = getCachedLayerPainter();
        if (!(layerPainter instanceof ShipPainter shipPainter)) return;
        
        if (shipPainter.getSprite() == null || shipPainter.getSprite().getImage() == null) return;
        java.awt.image.BufferedImage image = shipPainter.getSprite().getImage();

        java.awt.geom.Point2D anchor = shipPainter.getAnchor();
        java.util.List<java.awt.geom.Point2D> generatedPoints = shipeditor.utility.graphics.CollisionHullGenerator.generateBounds(image, anchor);
        
        if (generatedPoints.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, StringManager.getString("COULD_NOT_GENERATE_BOUNDS_SPRITE_MIGHT_B_MSG"), "Warning", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
        if (boundsPainter == null) return;
        java.util.List<BoundPoint> oldBounds = new java.util.ArrayList<>(boundsPainter.getPointsIndex());
        
        java.util.List<BoundPoint> newBounds = new java.util.ArrayList<>();
        for (java.awt.geom.Point2D point : generatedPoints) {
            newBounds.add(new BoundPoint(point, shipPainter));
        }

        shipeditor.undo.EditDispatch.postBoundsReplaced(boundsPainter, oldBounds, newBounds);
    }

}
