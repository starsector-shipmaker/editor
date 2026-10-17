package shipeditor.menubar;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerBackgroundChanged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerGuidesToggled;
import shipeditor.components.viewer.PaintOrderController;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.graphics.ColorUtilities;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.utility.themes.Themes;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRotationToggled;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformsReset;

class ViewMenu extends JMenu {

    private JMenuItem toggleRotate;

    private JMenuItem toggleCursorGuides;
    private JMenuItem toggleBorders;
    private JMenuItem toggleSpriteCenter;

    ViewMenu() {
        super(StringManager.getString("MENU_VIEW"));
        this.setMnemonic(KeyEvent.VK_V);
    }

    void initialize() {
        JMenuItem centerView = new JMenuItem(StringManager.getString("RESET_VIEW_CENTER"));
        centerView.setIcon(FontIcon.of(BoxiconsRegular.TARGET_LOCK, 16, Themes.getIconColor()));
        centerView.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        centerView.addActionListener(event -> EventBus.publish(new ViewerTransformsReset()));
        this.add(centerView);

        JMenuItem cycleFrame = new JMenuItem(StringManager.getString("CYCLE_WEAPON_FRAME"));
        cycleFrame.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        cycleFrame.addActionListener(event -> EventBus.publish(
                new shipeditor.communication.events.viewer.control.ControlEvents.CycleWeaponFrameQueued()));
        this.add(cycleFrame);

        this.addSeparator();

        toggleRotate = new JCheckBoxMenuItem(StringManager.getString("ENABLE_VIEW_ROTATION"));
        toggleRotate.setSelected(true);
        toggleRotate.addActionListener(event ->
                EventBus.publish(new ViewerRotationToggled(toggleRotate.isSelected(), true))
        );
        EventBus.subscribe(this, event -> {
            if (event instanceof ViewerRotationToggled checked) {
                toggleRotate.setSelected(checked.isSelected());
                toggleRotate.setEnabled(checked.isEnabled());
            }
        });
        this.add(toggleRotate);

        this.addSeparator();

        JMenu bgSubmenu = new JMenu(StringManager.getString("BACKGROUND_SETTINGS"));
        bgSubmenu.setIcon(FontIcon.of(BoxiconsRegular.IMAGE, 16, Themes.getIconColor()));

        JMenuItem changeBackground = new JMenuItem(StringManager.getString("CHANGE_BACKGROUND_COLOR"));
        changeBackground.addActionListener(event -> {
            Settings settings = SettingsManager.getSettings();
            Color chosen = ColorUtilities.showColorChooser(settings.getBackgroundColor());
            if (chosen != null) {
                settings.setBackgroundColor(chosen);
                EventBus.publish(new ViewerBackgroundChanged(chosen));
            }
        });
        bgSubmenu.add(changeBackground);

        JMenuItem displayBackgroundImage = new JCheckBoxMenuItem(StringManager.getString("SHOW_BACKGROUND_GRID_IMAGE"));
        displayBackgroundImage.setSelected(true);
        displayBackgroundImage.addActionListener(e ->
                PaintOrderController.setShowBackgroundImage(displayBackgroundImage.isSelected()));
        bgSubmenu.add(displayBackgroundImage);

        this.add(bgSubmenu);

        JMenu guidesSubmenu = this.createGuidesSubmenu();
        this.add(guidesSubmenu);

        this.addSeparator();

        JMenuItem hideNonBuiltIn = new JCheckBoxMenuItem(StringManager.getString("HIDE_NON_BUILT_IN_WEAPONS"));
        hideNonBuiltIn.setSelected(false);
        hideNonBuiltIn.addActionListener(e -> {
            PaintOrderController.setHideNonBuiltInWeapons(hideNonBuiltIn.isSelected());
            shipeditor.utility.overseers.StaticController.getViewer().setRepaintQueued();
        });
        this.add(hideNonBuiltIn);
    }

    private void notifyGuidesToggled() {
        EventBus.publish(new ViewerGuidesToggled(toggleCursorGuides.isSelected(),
                toggleBorders.isSelected(), toggleSpriteCenter.isSelected()));
    }

    private JMenu createGuidesSubmenu() {
        JMenu guidesSubmenu = new JMenu(StringManager.getString("TOGGLE_GUIDES"));
        guidesSubmenu.setIcon(FontIcon.of(BoxiconsRegular.GRID_ALT, 16, Themes.getIconColor()));

        toggleCursorGuides = new JCheckBoxMenuItem(StringManager.getString("SHOW_CURSOR_GUIDES"));
        toggleCursorGuides.setSelected(true);
        toggleCursorGuides.addActionListener(e -> this.notifyGuidesToggled());
        guidesSubmenu.add(toggleCursorGuides);

        toggleBorders = new JCheckBoxMenuItem(StringManager.getString("SHOW_SPRITE_BOUNDS"));
        toggleBorders.setSelected(true);
        toggleBorders.addActionListener(e -> this.notifyGuidesToggled());
        guidesSubmenu.add(toggleBorders);

        toggleSpriteCenter = new JCheckBoxMenuItem(StringManager.getString("SHOW_SPRITE_CENTER_MARKER"));
        toggleSpriteCenter.setSelected(true);
        toggleSpriteCenter.addActionListener(e -> this.notifyGuidesToggled());
        guidesSubmenu.add(toggleSpriteCenter);

        return guidesSubmenu;
    }

}
