package shipeditor.components.viewer.control;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;

import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Decides what a canvas click, double-click or key press means in the current
 * editor mode. Contains only decision logic (no Swing or event bus calls) so it
 * can be unit tested without a running viewer.
 */
@Log4j2
public final class CanvasInteractionPolicy {

    /**
     * What a left-button press should do, given what is under the cursor.
     */
    public enum PressAction {
        /** Nothing to do for the selection (other handlers may still run). */
        NONE,
        /** Shift/Ctrl press on a point: add it to or remove it from the selection. */
        TOGGLE_SELECTION,
        /** Plain press on a point: make it the primary selection. */
        SELECT_POINT,
        /** Press on empty space: remember the start of a marquee selection. */
        START_MARQUEE
    }

    /**
     * What a double-click should do.
     */
    public enum DoubleClickAction {
        NONE,
        OPEN_WEAPON_PICKER
    }

    /**
     * What a key press should do on the canvas.
     */
    public enum KeyAction {
        NONE,
        OPEN_WEAPON_PICKER,
        CLEAR_WEAPONS,
        NEXT_SLOT,
        PREVIOUS_SLOT
    }

    private CanvasInteractionPolicy() {
    }

    /**
     * @param hasPointUnderCursor whether a selectable point is under the cursor.
     * @param shiftDown           whether Shift is held.
     * @param controlDown         whether Ctrl is held.
     * @param mode                current editor mode.
     * @return what a left-button press should do for selection.
     */
    public static PressAction resolveLeftPress(boolean hasPointUnderCursor, boolean shiftDown,
                                               boolean controlDown, EditorInstrument mode) {
        if (hasPointUnderCursor) {
            if (shiftDown || controlDown) {
                return PressAction.TOGGLE_SELECTION;
            }
            return PressAction.SELECT_POINT;
        }
        boolean marqueeMode = mode != EditorInstrument.COLLISION && mode != EditorInstrument.SHIELD;
        if (marqueeMode && !controlDown) {
            return PressAction.START_MARQUEE;
        }
        return PressAction.NONE;
    }

    /**
     * A point can be dragged only when a point is under the cursor and its painter
     * has interaction enabled. Painters that only allow selection (for example
     * weapon slots in VARIANT_WEAPONS mode) therefore select without dragging.
     *
     * @param hasPointUnderCursor whether a selectable point is under the cursor.
     * @param interactionEnabled  whether the owning painter allows interaction.
     * @return whether a drag gesture may begin.
     */
    public static boolean isPointDragAllowed(boolean hasPointUnderCursor, boolean interactionEnabled) {
        return hasPointUnderCursor && interactionEnabled;
    }

    /**
     * @param painter painter owning the hovered point, may be null.
     * @return whether points of this painter can be selected by clicking.
     */
    public static boolean canSelectPoints(AbstractPointPainter painter) {
        return painter != null && painter.isSelectionEnabled();
    }

    /**
     * @param painter painter owning the hovered point, may be null.
     * @return whether a press on a point of this painter may start a drag.
     */
    public static boolean canDragPoints(AbstractPointPainter painter) {
        return painter != null && isPointDragAllowed(true, painter.isInteractionEnabled());
    }

    /**
     * @param selectionEnabled whether the painter has selection enabled.
     * @param shiftDown        whether Shift is held.
     * @param controlDown      whether Ctrl is held.
     * @param altDown          whether Alt is held.
     * @param leftButton       whether the release came from the left button.
     * @return whether a clean click on empty space should clear that painter's selection.
     */
    public static boolean shouldClearSelectionOnEmptyClick(boolean selectionEnabled, boolean shiftDown,
                                                           boolean controlDown, boolean altDown,
                                                           boolean leftButton) {
        return selectionEnabled && !shiftDown && !controlDown && !altDown && leftButton;
    }

    /**
     * @param mode        current editor mode.
     * @param leftButton  whether the click came from the left button.
     * @param clickCount  click count of the event.
     * @return what a double-click means.
     */
    public static DoubleClickAction resolveDoubleClick(EditorInstrument mode, boolean leftButton,
                                                       int clickCount) {
        if (clickCount == 2 && leftButton && mode == EditorInstrument.VARIANT_WEAPONS) {
            return DoubleClickAction.OPEN_WEAPON_PICKER;
        }
        return DoubleClickAction.NONE;
    }

    /**
     * @param mode          current editor mode.
     * @param keyCode       {@link KeyEvent} key code.
     * @param shiftDown     whether Shift is held.
     * @param modifierDown  whether Ctrl, Alt or Meta is held.
     * @param canvasFocused whether the viewer canvas owns keyboard focus.
     * @return what the key press means for the canvas.
     */
    public static KeyAction resolveKey(EditorInstrument mode, int keyCode, boolean shiftDown,
                                       boolean modifierDown, boolean canvasFocused) {
        if (mode != EditorInstrument.VARIANT_WEAPONS || !canvasFocused || modifierDown) {
            return KeyAction.NONE;
        }
        return switch (keyCode) {
            case KeyEvent.VK_ENTER -> KeyAction.OPEN_WEAPON_PICKER;
            case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> KeyAction.CLEAR_WEAPONS;
            case KeyEvent.VK_TAB -> shiftDown ? KeyAction.PREVIOUS_SLOT : KeyAction.NEXT_SLOT;
            default -> KeyAction.NONE;
        };
    }

    /**
     * @param mode                 current editor mode.
     * @param hoveringFittableSlot whether the cursor is over a slot that can hold a weapon.
     * @return whether the hand cursor should be shown.
     */
    public static boolean shouldShowHandCursor(EditorInstrument mode, boolean hoveringFittableSlot) {
        return mode == EditorInstrument.VARIANT_WEAPONS && hoveringFittableSlot;
    }

    /**
     * Picks the next or previous item, wrapping around. When the current item is
     * missing from the list, forward starts at the first item and backward at the last.
     *
     * @param items     candidates in cycling order.
     * @param current   currently selected item, may be null.
     * @param backwards true to move to the previous item.
     * @param <T>       item type.
     * @return the item to select, or null when there are no candidates.
     */
    public static <T> T cycle(List<? extends T> items, T current, boolean backwards) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        int size = items.size();
        int index = items.indexOf(current);
        int target;
        if (index < 0) {
            target = backwards ? size - 1 : 0;
        } else if (backwards) {
            target = (index - 1 + size) % size;
        } else {
            target = (index + 1) % size;
        }
        log.debug("Cycling selection: size={}, currentIndex={}, backwards={}, target={}",
                size, index, backwards, target);
        return items.get(target);
    }

}
