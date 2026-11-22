package shipeditor.menubar;

import shipeditor.utility.text.StringManager;

import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.communication.EventBus;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.ViewerEnums.PointSelectionMode;
import shipeditor.undo.UndoOverseer;
import shipeditor.utility.themes.Themes;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import shipeditor.communication.events.viewer.control.ControlEvents.RotationRoundingToggled;
import shipeditor.communication.events.viewer.control.ControlEvents.PointSelectionModeChange;
import shipeditor.communication.events.viewer.control.ControlEvents.CursorSnappingToggled;

class EditMenu extends JMenu {

    private JCheckBoxMenuItem toggleCursorSnap;
    private JCheckBoxMenuItem toggleRotationRounding;

    EditMenu() {
        super(StringManager.getString("MENU_EDIT"));
        this.setMnemonic(KeyEvent.VK_E);
    }

    void initialize() {
        JMenuItem undo = new JMenuItem(StringManager.getString("UNDO"));
        undo.setAction(UndoOverseer.getUndoAction());
        undo.setIcon(FontIcon.of(BoxiconsRegular.UNDO, 16, Themes.getIconColor()));
        KeyStroke keyStrokeToUndo = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
        undo.setAccelerator(keyStrokeToUndo);
        this.add(undo);

        JMenuItem redo = new JMenuItem(StringManager.getString("REDO"));
        redo.setAction(UndoOverseer.getRedoAction());
        redo.setIcon(FontIcon.of(BoxiconsRegular.REDO, 16, Themes.getIconColor()));
        KeyStroke keyStrokeToRedo = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
        redo.setAccelerator(keyStrokeToRedo);
        this.add(redo);

        this.addSeparator();

        JMenuItem pointSelectionMode = this.createPointSelectionModeOptions();
        this.add(pointSelectionMode);

        JMenu snappingMenu = new JMenu(StringManager.getString("SNAPPING_ROUNDING"));
        snappingMenu.setIcon(FontIcon.of(BoxiconsRegular.MAGNET, 16, Themes.getIconColor()));

        JCheckBoxMenuItem toggleSelectionHold = new JCheckBoxMenuItem(StringManager.getString("ENABLE_SELECTION_HOLDING"));
        toggleSelectionHold.setSelected(true);
        toggleSelectionHold.setToolTipText(StringManager.getString("ENABLES_CTRL_HOLD_TO_PREVENT_MOUSE_MOTION_FROM_CHANGING_SELECTION"));
        toggleSelectionHold.addActionListener(event ->
                ControlPredicates.setSelectionHoldingEnabled(toggleSelectionHold.isSelected())
        );
        snappingMenu.add(toggleSelectionHold);

        toggleCursorSnap = new JCheckBoxMenuItem(StringManager.getString("ENABLE_CURSOR_SNAPPING"));
        toggleCursorSnap.setSelected(true);
        toggleCursorSnap.addActionListener(event ->
                EventBus.publish(new CursorSnappingToggled(toggleCursorSnap.isSelected()))
        );
        EventBus.subscribe(this, event -> {
            if (event instanceof CursorSnappingToggled checked) {
                toggleCursorSnap.setSelected(checked.toggled());
            }
        });
        snappingMenu.add(toggleCursorSnap);

        toggleRotationRounding = new JCheckBoxMenuItem(StringManager.getString("ENABLE_ROTATION_ROUNDING"));
        toggleRotationRounding.setSelected(true);
        toggleRotationRounding.addActionListener(event ->
                EventBus.publish(new RotationRoundingToggled(toggleRotationRounding.isSelected()))
        );
        EventBus.subscribe(this, event -> {
            if (event instanceof RotationRoundingToggled checked) {
                toggleRotationRounding.setSelected(checked.toggled());
            }
        });
        snappingMenu.add(toggleRotationRounding);

        this.add(snappingMenu);
    }

    private JMenu createPointSelectionModeOptions() {
        JMenu newSubmenu = new JMenu(StringManager.getString("POINT_SELECTION_MODE"));
        newSubmenu.setIcon(FontIcon.of(BoxiconsRegular.POINTER, 16, Themes.getIconColor()));

        JMenuItem selectHovered = new JRadioButtonMenuItem(StringManager.getString("SELECT_CLICKED_POINT"));
        selectHovered.addActionListener(e ->
                EventBus.publish(new PointSelectionModeChange(PointSelectionMode.STRICT)));
        newSubmenu.add(selectHovered);

        JMenuItem selectClosest = new JRadioButtonMenuItem(StringManager.getString("SELECT_CLOSEST_POINT"));
        selectClosest.addActionListener(e ->
                EventBus.publish(new PointSelectionModeChange(PointSelectionMode.CLOSEST)));
        newSubmenu.add(selectClosest);
        selectClosest.setSelected(true);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(selectHovered);
        buttonGroup.add(selectClosest);

        EventBus.subscribe(this, event -> {
            if (event instanceof PointSelectionModeChange checked) {
                if (checked.newMode() == PointSelectionMode.STRICT && !selectHovered.isSelected()) {
                    selectHovered.setSelected(true);
                } else if (checked.newMode() == PointSelectionMode.CLOSEST && !selectClosest.isSelected()) {
                    selectClosest.setSelected(true);
                }
            }
        });

        return newSubmenu;
    }

}
