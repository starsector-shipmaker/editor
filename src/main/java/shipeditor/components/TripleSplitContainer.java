package shipeditor.components;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.datafiles.GameDataPanel;
import shipeditor.components.instrument.AbstractInstrumentsPane;
import shipeditor.components.instrument.ship.ShipInstrumentsPane;
import shipeditor.components.instrument.weapon.WeaponInstrumentsPane;
import shipeditor.components.instrument.projectile.ProjectileInstrumentsPane;
import shipeditor.components.viewer.LayerViewer;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;

import javax.swing.JSplitPane;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import shipeditor.communication.events.components.ComponentEvents.InstrumentSplitterResized;

/**
 * Main editor container.
 * Outer JSplitPane splits horizontally: GameDataPanel (left) | secondaryLevel
 * (right).
 * secondaryLevel JSplitPane splits horizontally: Canvas/Viewer (left) |
 * InstrumentsPane (right).
 *
 * @author CyberKitsune
 */
@Log4j2
final class TripleSplitContainer extends JSplitPane {

    private ShipInstrumentsPane shipInstrumentsPane;

    private WeaponInstrumentsPane weaponInstrumentsPane;

    private ProjectileInstrumentsPane projectileInstrumentsPane;

    private JSplitPane secondaryLevel;

    TripleSplitContainer() {
        super(JSplitPane.HORIZONTAL_SPLIT);
        this.setOneTouchExpandable(true);
        this.setContinuousLayout(true);
        this.initEventListeners();
    }

    private void initEventListeners() {
        BusEventListener layerSelectListener = event -> {
            if (event instanceof LayerWasSelected checked) {
                this.handleLayerChange(checked.selected());
            } else if (event instanceof ActiveLayerUpdated checked) {
                this.handleLayerChange(checked.updated());
            }
        };

        EventBus.subscribe(this, layerSelectListener);

        // Removed entrySelectListener to prevent frustrating auto-expansion of the left panel
        // when selecting entities via the canvas if the user explicitly minimized it.

        BusEventListener splitterResizeListener = event -> {
            if (event instanceof InstrumentSplitterResized checked) {
                boolean minimized = checked.minimized();
                AbstractInstrumentsPane instrumentsPane = checked.source();
                if (instrumentsPane != null) {
                    instrumentsPane.setInstrumentPaneMinimized(minimized);
                }
                relocateDivider();
            }
        };

        EventBus.subscribe(this, splitterResizeListener);
    }

    private void handleLayerChange(ViewerLayer selected) {
        Component currentRight = secondaryLevel.getRightComponent();
        Component newRight = null;

        if (selected instanceof WeaponLayer) {
            newRight = weaponInstrumentsPane;
        } else if (selected instanceof ProjectileLayer) {
            newRight = projectileInstrumentsPane;
        } else {
            newRight = shipInstrumentsPane;
        }

        if (currentRight != newRight && newRight != null) {
            secondaryLevel.setRightComponent(newRight);
            relocateDivider();
            secondaryLevel.revalidate();
            secondaryLevel.repaint();
        }
    }

    private void relocateDivider() {
        if (secondaryLevel == null)
            return;
        int width = secondaryLevel.getWidth();
        if (width <= 0)
            return;

        Component rightComp = secondaryLevel.getRightComponent();
        if (rightComp instanceof AbstractInstrumentsPane instrumentsPane) {
            boolean minimized = instrumentsPane.isInstrumentPaneMinimized();

            int remainder = 125;
            if (rightComp instanceof WeaponInstrumentsPane || rightComp instanceof ProjectileInstrumentsPane) {
                remainder = 70;
            }
            if (minimized) {
                secondaryLevel.setDividerLocation(width - remainder);
                secondaryLevel.setEnabled(false);
            } else {
                secondaryLevel.setEnabled(true);
                int targetWidth = instrumentsPane.getTargetWidth();
                if (targetWidth >= width) {
                    targetWidth = width / 3;
                }
                secondaryLevel.setDividerLocation(width - targetWidth);
            }
        }
    }

    void loadContentPanes(LayerViewer shipView) {
        this.shipInstrumentsPane = new ShipInstrumentsPane();
        this.weaponInstrumentsPane = new WeaponInstrumentsPane();
        this.projectileInstrumentsPane = new ProjectileInstrumentsPane();

        GameDataPanel gameDataPanel = new GameDataPanel();
        gameDataPanel.setMinimumSize(new Dimension(150, 100));
        gameDataPanel.setPreferredSize(new Dimension(350, 600));

        secondaryLevel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        secondaryLevel.setOneTouchExpandable(true);
        secondaryLevel.setContinuousLayout(true);
        secondaryLevel.setResizeWeight(1.0); // Canvas gets all the resize

        Component viewerComponent = (Component) shipView;
        viewerComponent.setMinimumSize(new Dimension(150, 100));

        JSplitPane centerVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        centerVerticalSplit.setTopComponent(viewerComponent);
        centerVerticalSplit.setBottomComponent(shipeditor.components.datafiles.trees.InfoConsolePanel.getInstance());
        centerVerticalSplit.setResizeWeight(0.85);
        centerVerticalSplit.setOneTouchExpandable(true);
        centerVerticalSplit.setContinuousLayout(true);
        centerVerticalSplit.setBorder(null);

        secondaryLevel.setLeftComponent(centerVerticalSplit);
        secondaryLevel.setRightComponent(shipInstrumentsPane);

        // Track divider movement to update targetWidth of instruments pane
        secondaryLevel.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            Component rightComp = secondaryLevel.getRightComponent();
            if (rightComp instanceof AbstractInstrumentsPane instrumentsPane) {
                if (!instrumentsPane.isInstrumentPaneMinimized()) {
                    int width = secondaryLevel.getWidth() - secondaryLevel.getDividerLocation();
                    if (width > 150) {
                        instrumentsPane.setTargetWidth(width);
                    }
                }
            }
        });

        // Track resize to ensure correct divider location during initial layouts
        secondaryLevel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relocateDivider();
            }
        });

        this.setLeftComponent(gameDataPanel);
        this.setRightComponent(secondaryLevel);
        this.setResizeWeight(0.0); // Sidebar stays same size, right level gets all resize
        this.setDividerLocation(350);
    }

}

