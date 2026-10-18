package shipeditor.menubar;

import shipeditor.utility.text.StringManager;

import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.communication.events.viewer.layers.LayerEvents.ViewerLayerRemovalConfirmed;
import shipeditor.communication.events.viewer.control.ControlEvents.CursorSnappingToggled;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformsReset;
import shipeditor.components.settings.PreferencesDialog;

import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.undo.UndoOverseer;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;

import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.JToggleButton;
import java.awt.event.ActionListener;

import shipeditor.communication.events.viewer.layers.LayerEvents.ShipLayerCreationQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.WeaponLayerCreationQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerRemovalQueued;

public class MainToolBar extends JToolBar {

    public MainToolBar() {
        super();
        this.setFloatable(false);
        this.initUI();
    }

    private void initUI() {
        // --- FILE ACTIONS ---
        this.add(createToolbarButton(BoxiconsRegular.SAVE, "SAVE_ACTIVE_LAYER_CTRL_S",
                e -> shipeditor.parsing.saving.SaveCoordinator.saveActiveLayer()));

        this.addSeparator();

        // --- HISTORY ---
        JButton undoButton = new JButton();
        undoButton.setAction(UndoOverseer.getUndoAction());
        undoButton.setHideActionText(true);
        this.styleToolbarButton(undoButton, BoxiconsRegular.UNDO);
        undoButton.setToolTipText(StringManager.getString("UNDO_CTRL_Z"));
        this.add(undoButton);

        JButton redoButton = new JButton();
        redoButton.setAction(UndoOverseer.getRedoAction());
        redoButton.setHideActionText(true);
        this.styleToolbarButton(redoButton, BoxiconsRegular.REDO);
        redoButton.setToolTipText(StringManager.getString("REDO_CTRL_Y"));
        this.add(redoButton);

        this.addSeparator();

        // --- LAYER MANAGEMENT ---
        this.add(createToolbarButton(BoxiconsRegular.ROCKET, "CREATE_NEW_SHIP_LAYER_CTRL_N",
                event -> EventBus.publish(new ShipLayerCreationQueued())));
        
        this.add(createToolbarButton(BoxiconsRegular.CROSSHAIR, "CREATE_NEW_WEAPON_LAYER_CTRL_SHIFT_N",
                event -> EventBus.publish(new WeaponLayerCreationQueued())));

        JButton removeLayerButton = createToolbarButton(BoxiconsRegular.MINUS, "REMOVE_ACTIVE_LAYER_CTRL_DELETE",
                event -> EventBus.publish(new ActiveLayerRemovalQueued()));
        removeLayerButton.setEnabled(false);

        EventBus.subscribe(this, event -> {
            LayerManager layerManager = StaticController.getLayerManager();
            if (layerManager != null) {
                if (event instanceof ViewerLayerRemovalConfirmed && layerManager.isEmpty()) {
                    removeLayerButton.setEnabled(false);
                } else if (event instanceof LayerWasSelected && !layerManager.isEmpty()) {
                    removeLayerButton.setEnabled(true);
                }
            }
        });
        this.add(removeLayerButton);

        this.addSeparator();

        // --- CANVAS MODES ---
        JToggleButton snapButton = new JToggleButton();
        snapButton.setIcon(FontIcon.of(BoxiconsRegular.MAGNET, 16, Themes.getIconColor()));
        snapButton.setSelectedIcon(FontIcon.of(BoxiconsRegular.MAGNET, 16, Themes.getBrighterSelectionColor()));
        snapButton.setToolTipText(StringManager.getString("TOGGLE_CURSOR_SNAPPING"));
        snapButton.setSelected(true);
        snapButton.setFocusPainted(false);
        snapButton.addActionListener(e -> EventBus.publish(new CursorSnappingToggled(snapButton.isSelected())));
        EventBus.subscribe(this, event -> {
            if (event instanceof CursorSnappingToggled checked) {
                snapButton.setSelected(checked.toggled());
            }
        });
        this.add(snapButton);

        this.add(createToolbarButton(BoxiconsRegular.TARGET_LOCK, "RESET_VIEW_CENTER_CTRL_0",
                event -> EventBus.publish(new ViewerTransformsReset())));

        this.addSeparator();

        // --- UTILITY ---
        this.add(createToolbarButton(BoxiconsRegular.REFRESH, "RELOAD_GAME_DATA_F5",
                event -> FileLoading.loadGameData()));
        
        this.addSeparator();
        
        JButton combinedFiltersBtn = new JButton();
        this.styleToolbarButton(combinedFiltersBtn, BoxiconsRegular.FILTER);
        combinedFiltersBtn.setToolTipText(StringManager.getString("DATA_FILTERS"));
        combinedFiltersBtn.addActionListener(e -> shipeditor.components.datafiles.trees.FilterDialogs.showCombinedFilters(combinedFiltersBtn));
        this.add(combinedFiltersBtn);

        this.addSeparator();

        this.add(createToolbarButton(BoxiconsRegular.COG, "PREFERENCES_CTRL", event -> {
            PreferencesDialog dialog = new PreferencesDialog(shipeditor.PrimaryWindow.getInstance());
            dialog.setVisible(true);
        }));
    }

    private JButton createToolbarButton(org.kordamp.ikonli.Ikon icon, String tooltipKey, ActionListener action) {
        JButton button = new JButton();
        this.styleToolbarButton(button, icon);
        button.setToolTipText(StringManager.getString(tooltipKey));
        button.addActionListener(action);
        return button;
    }

    private void styleToolbarButton(JButton button, org.kordamp.ikonli.Ikon icon) {
        button.setIcon(FontIcon.of(icon, 16, Themes.getIconColor()));
        button.setRolloverIcon(FontIcon.of(icon, 16, Themes.getBrighterSelectionColor()));
        button.setPressedIcon(FontIcon.of(icon, 16, Themes.getDarkerBackgroundColor()));
        button.setDisabledIcon(FontIcon.of(icon, 16, Themes.getDisabledIconColor()));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
    }
}
