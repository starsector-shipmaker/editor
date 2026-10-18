package shipeditor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.WindowContentPanes;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.menubar.PrimaryMenuBar;
import shipeditor.parsing.saving.SaveCoordinator;
import shipeditor.parsing.saving.CSVIDSyncHandler;
import shipeditor.persistence.Initializations;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.components.ToastManager;
import shipeditor.utility.objects.SimpleRectangle;
import shipeditor.utility.overseers.StaticController;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;

import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.*;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import shipeditor.communication.events.components.ComponentEvents.WindowGUIShowConfirmed;
import shipeditor.communication.events.components.ComponentEvents.WindowRepaintQueued;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class PrimaryWindow extends JFrame {

    public static final String SHIP_EDITOR = "Ship Editor";

    @Getter
    private static PrimaryWindow instance;

    @Getter
    private final PrimaryMenuBar primaryMenu;

    private final WindowContentPanes contentPanes;

    @Getter
    private boolean initialized;

    private static final Dimension MINIMUM_WINDOW_SIZE = new Dimension(800, 600);

    private PrimaryWindow() {
        log.info("Application start: creating window.");
        this.setTitle(SHIP_EDITOR);
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        PrimaryWindow.performStaticInits();

        this.setMinimumSize(MINIMUM_WINDOW_SIZE);

        this.initListeners();

        Container contentPane = this.getContentPane();
        contentPane.setLayout(new BorderLayout());
        primaryMenu = new PrimaryMenuBar();
        this.setJMenuBar(primaryMenu);

        this.contentPanes = new WindowContentPanes(this.getContentPane());
        contentPanes.loadShipView();
        contentPanes.loadLayerHandling();
        contentPanes.loadEditingPanes();

        PrimaryWindow.configureTooltips();
        this.addComponentListener(new ResizingPersistenceListener());

        this.pack();
        // This centers the frame.
        this.setLocationRelativeTo(null);
    }

    private static void performStaticInits() {
        Initializations.selectGameFolder();
        SaveCoordinator.init();
        CSVIDSyncHandler.init();
        StaticController.init();

        ControlPredicates.initSelectionModeListening();
    }

    public static PrimaryWindow create() {
        PrimaryWindow primaryWindow = new PrimaryWindow();
        instance = primaryWindow;

        primaryWindow.addWindowListener(new WindowAdapter() {
            private final KeyEventDispatcher hotkeyDispatcher = e -> {
                if (e.getKeyCode() == KeyEvent.VK_ALT) {
                    return true;
                }
                switch (e.getID()) {
                    case java.awt.event.KeyEvent.KEY_PRESSED -> shipeditor.communication.EventBus.publish(new shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed(e));
                    case java.awt.event.KeyEvent.KEY_RELEASED -> shipeditor.communication.EventBus.publish(new shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyReleased(e));
                    default -> {}
                }
                return false;
            };

            @Override
            public void windowActivated(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeyDispatcher);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(hotkeyDispatcher);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (shipeditor.utility.overseers.StaticController.getViewer() != null) {
                    java.util.List<String> unsaved = shipeditor.utility.overseers.StaticController.getViewer().getLayerManager().getUnsavedLayerNames();
                    if (!unsaved.isEmpty()) {
                        String message = "You have unsaved changes in the following layers:\n\n- " + String.join("\n- ", unsaved) + "\n\nAre you sure you want to exit without saving?";
                        int result = javax.swing.JOptionPane.showConfirmDialog(primaryWindow, message, "Unsaved Changes", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
                        if (result != javax.swing.JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                }
                primaryWindow.dispose();
                System.exit(0);
            }
        });

        primaryWindow.restoreSize();
        return primaryWindow;
    }

    private static void configureTooltips() {
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        toolTipManager.setInitialDelay(10);
        toolTipManager.setDismissDelay(90000);
    }

    private void restoreSize() {
        Settings settings = SettingsManager.getSettings();
        SimpleRectangle saved = settings.getWindowBounds();

        if (saved != null) {
            int x = Math.max(0, saved.getX());
            int y = Math.max(0, saved.getY());

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

            int clampedWidth = Math.min(saved.getWidth(), screenSize.width - x - 50);
            int clampedHeight = Math.min(saved.getHeight(), screenSize.height - y - 50);

            int width = Math.max(MINIMUM_WINDOW_SIZE.width, clampedWidth);
            int height = Math.max(MINIMUM_WINDOW_SIZE.height, clampedHeight);

            this.setBounds(new Rectangle(x, y, width, height));
        } else {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int defaultWidth = Math.min(1440, screenSize.width - 100);
            int defaultHeight = Math.min(900, screenSize.height - 100);
            this.setSize(new Dimension(defaultWidth, defaultHeight));
            this.setLocationRelativeTo(null);
        }

        if (settings.isWindowMaximized()) {
            this.setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
    }

    private void initListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof WindowRepaintQueued) {
                contentPanes.refreshContent();
            }
        });
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });
    }

    private void confirmExit() {
        PrimaryViewer viewer = StaticController.getViewer();
        if (viewer != null) {
            LayerManager layerManager = viewer.getLayerManager();
            if (layerManager != null) {
                java.util.List<ViewerLayer> layersCopy = new java.util.ArrayList<>(layerManager.getLayers());
                for (ViewerLayer layer : layersCopy) {
                    if (layer instanceof ShipLayer shipLayer) {
                        if (layerManager.isLayerDirty(shipLayer)) {
                            layerManager.setActiveLayer(shipLayer);
                            boolean confirmed = shipeditor.utility.components.dialog.DialogHelper.confirmLayerRemoval(layerManager, shipLayer);
                            if (!confirmed) {
                                return; // Abort exit
                            }
                        }
                    }
                }
            }
        }
        this.dispose();
        System.exit(0);
    }

    void showGUI() {
        this.setVisible(true);
        ToastManager.attach(this);
        EventBus.publish(new WindowGUIShowConfirmed());
        this.initialized = true;
    }

    private class ResizingPersistenceListener extends ComponentAdapter {

        private boolean saveScheduled;
        private final Timer resizeTimer;

        ResizingPersistenceListener() {
            ActionListener savingAction = e -> {
                if (saveScheduled) {
                    saveBounds();
                    saveScheduled = false;
                }
            };
            resizeTimer = new Timer(2500, savingAction);
            resizeTimer.setRepeats(false);
        }

        private void scheduleSave() {
            if (!PrimaryWindow.this.initialized) return;
            saveScheduled = true;
            resizeTimer.restart();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            scheduleSave();
        }

        @Override
        public void componentResized(ComponentEvent e) {
            scheduleSave();
        }

        private void saveBounds() {
            log.info("Saving modified window bounds...");
            var bounds = PrimaryWindow.this.getBounds();
            SimpleRectangle serializable = new SimpleRectangle(bounds.x, bounds.y, bounds.width, bounds.height);
            Settings settings = SettingsManager.getSettings();
            settings.setWindowBounds(serializable);

            int state = PrimaryWindow.this.getExtendedState();
            boolean maximized = (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
            settings.setWindowMaximized(maximized);

            SettingsManager.updateFileFromRuntime();
        }

    }

}
