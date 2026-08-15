package shipeditor.undo;
import shipeditor.utility.UtilityEnums.EditCategory;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.undo.edits.LayerEdit;
import shipeditor.undo.edits.points.PointEdits.PointDragEdit;

import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.utility.overseers.StaticController;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;

/**
 * The central manager for the Undo/Redo system in Starsector Ship Editor.
 * <p>
 * **Mechanics:** 
 * This class enforces a strict LIFO (Last-In-First-Out) order of operations via two Deques (stacks).
 * When a user action occurs, an {@link Edit} is created (typically via {@link shipeditor.undo.EditDispatch})
 * and posted here using {@link #post(Edit)}. The system caps at {@value #MAX_UNDO_CAPACITY} edits to prevent memory leaks.
 * <p>
 * <b>Note:</b> Exposing the edit stack to the user for selective undo is strictly forbidden as it breaks 
 * the sequential integrity of coordinate mutations and state changes.
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class UndoOverseer {

    private static final UndoOverseer SEER = new UndoOverseer();

    @Getter @Setter
    private static boolean paused = false;

    private static final int MAX_UNDO_CAPACITY = 200;

    private UndoOverseer() {
        undoAction.setEnabled(false);
        redoAction.setEnabled(false);
    }

    /**
     * Isn't meant to have protective checks; the input vehicles need to be disabled if there is no edits in stack.
     */
    private final Action undoAction = new AbstractAction("Undo") {
        @Override
        public void actionPerformed(ActionEvent e) {
            Deque<Edit> undo = UndoOverseer.getUndoStack();
            if (undo.isEmpty()) {
                return;
            }
            Edit head = undo.pop();
            try {
                log.debug("Executing undo: {}", head.getName());
                head.undo();
                Deque<Edit> redo = UndoOverseer.getRedoStack();
                redo.push(head);
                markActiveLayerDirty(head);
            } catch (Exception ex) {
                log.error("Failed to undo edit: {}", head.getName(), ex);
                undo.push(head);
            } finally {
                updateActionState();
            }
        }
    };

    private final Action redoAction = new AbstractAction("Redo") {
        @Override
        public void actionPerformed(ActionEvent e) {
            Deque<Edit> redo = getRedoStack();
            if (redo.isEmpty()) {
                return;
            }
            Edit head = redo.pop();
            try {
                log.debug("Executing redo: {}", head.getName());
                head.redo();
                Deque<Edit> undo = getUndoStack();
                undo.push(head);
                markActiveLayerDirty(head);
            } catch (Exception ex) {
                log.error("Failed to redo edit: {}", head.getName(), ex);
                redo.push(head);
            } finally {
                updateActionState();
            }
        }
    };

    private final Deque<Edit> undoStack = new ArrayDeque<>();

    private final Deque<Edit> redoStack = new ArrayDeque<>();

    private void updateActionState() {
        Deque<Edit> undo = UndoOverseer.getUndoStack();
        String undoName = "Undo";
        if (undo.isEmpty()) {
            undoAction.setEnabled(false);
        } else {
            undoAction.setEnabled(true);
            Edit nextUndoable = UndoOverseer.getNextUndoable();
            undoName = undoName + " " + nextUndoable.getName();
        }
        undoAction.putValue(Action.NAME, undoName);
        undoAction.putValue(Action.SHORT_DESCRIPTION, undoName);

        Deque<Edit> redo = UndoOverseer.getRedoStack();
        String redoName = "Redo";
        if (redo.isEmpty()) {
            redoAction.setEnabled(false);
        } else {
            redoAction.setEnabled(true);
            Edit nextUndoable = UndoOverseer.getNextRedoable();
            redoName = redoName + " " + nextUndoable.getName();
        }
        redoAction.putValue(Action.NAME, redoName);
        redoAction.putValue(Action.SHORT_DESCRIPTION, redoName);
    }

    public static Action getUndoAction() {
        return SEER.undoAction;
    }

    public static Action getRedoAction() {
        return SEER.redoAction;
    }

    public static Deque<Edit> getUndoStack() {
        return SEER.undoStack;
    }

    public static Deque<Edit> getRedoStack() {
        return SEER.redoStack;
    }

    public static Edit getNextUndoable() {
        Deque<Edit> stack = UndoOverseer.getUndoStack();
        return stack.peek();
    }

    private static Edit getNextRedoable() {
        Deque<Edit> stack = UndoOverseer.getRedoStack();
        return stack.peek();
    }

    public static void post(Edit edit) {
        if (paused) return;
        Deque<Edit> stack = UndoOverseer.getUndoStack();
        stack.addFirst(edit);
        
        while (stack.size() > MAX_UNDO_CAPACITY) {
            stack.removeLast();
        }
        
        UndoOverseer.clearRedoStack();
        markActiveLayerDirty(edit);
        SEER.updateActionState();
    }

    private static void clearRedoStack() {
        SEER.redoStack.clear();
    }

    private static Collection<Edit> getAllEdits() {
        Collection<Edit> allEdits = new ArrayList<>(SEER.undoStack);
        allEdits.addAll(SEER.redoStack);
        return allEdits;
    }

    public static void adjustPointEditsOffset(BaseWorldPoint point, Point2D offset) {
        var allEdits = UndoOverseer.getAllEdits();
        allEdits.forEach(edit -> {
            if (edit instanceof PointDragEdit dragEdit && dragEdit.getPoint() == point)  {
                dragEdit.adjustPositionOffset(offset);
            }
        });
    }

    public static void finishAllEdits() {
        var allEdits = UndoOverseer.getAllEdits();
        allEdits.forEach(edit -> edit.setFinished(true));
    }

    public static void cleanupRemovedLayer(LayerPainter painter) {
        UndoOverseer.cleanupStack(painter,  SEER.undoStack);
        UndoOverseer.cleanupStack(painter,  SEER.redoStack);
        SEER.updateActionState();
    }

    private static void cleanupStack(LayerPainter painter, Collection<Edit> stack) {
        stack.removeIf(edit -> {
            if (edit instanceof LayerEdit checked) {
                LayerPainter layerPainter = checked.getLayerPainter();
                if (layerPainter != null && layerPainter == painter) {
                    checked.cleanupReferences();
                    return true;
                }
            }
            return false;
        });
    }

    private static void markActiveLayerDirty(Edit edit) {
        EditCategory category = edit.getCategory();
        if (category == EditCategory.NONE) return;

        Object targetEntity = edit.getTargetEntity();
        if (targetEntity instanceof shipeditor.components.viewer.layers.ship.data.ShipVariant variant) {
            String type = category == EditCategory.VARIANT ? "variant" : "hull";
            // Find which layer holds this variant. 
            // If it's a module, maybe it's not directly in a layer but we want to mark the variant dirty.
            // But LayerManager tracks unsaved by ViewerLayer. We might need to track it differently,
            // or we just mark the active layer as dirty, AND the variant as dirty internally.
            // Wait, LayerManager currently tracks by ViewerLayer. We should update LayerManager
            // to be able to track ShipVariants as well, or we just keep it simple for now.
        }

        // Determine the target layer from the edit itself, not the currently active layer.
        ViewerLayer target = null;
        if (edit instanceof LayerEdit layerEdit) {
            LayerPainter painter = layerEdit.getLayerPainter();
            if (painter != null) {
                target = painter.getParentLayer();
            }
        }
        // Fallback to active layer for edits that don't implement LayerEdit.
        if (target == null) {
            target = StaticController.getActiveLayer();
        }

        if (target instanceof ShipLayer) {
            String type = category == EditCategory.VARIANT ? "variant" : "hull";
            StaticController.getViewer().getLayerManager().markUnsaved(target, type);
        }
    }

}
