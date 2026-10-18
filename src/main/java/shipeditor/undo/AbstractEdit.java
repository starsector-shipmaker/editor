package shipeditor.undo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class AbstractEdit implements Edit, shipeditor.undo.edits.LayerEdit {

    /**
     * These are meant to be consequential after the parent edit, meaning that first undo() of sub-edits is invoked.
     * Sub-edits are undone from head to tail, after that the parent layer is undone.
     */
    private final Deque<Edit> subEdits = new ArrayDeque<>();

    @Setter
    private boolean finished = true;

    @Setter
    private Object targetEntity = null;

    @Override
    public Object getTargetEntity() {
        return targetEntity;
    }

    @Override
    public void add(Edit edit) {
        subEdits.addFirst(edit);
    }

    protected void undoSubEdits() {
        subEdits.forEach(a -> a.undo());
    }

    protected void redoSubEdits() {
        List<Edit> editsList = new ArrayList<>(subEdits);
        Collections.reverse(editsList);
        editsList.forEach(a -> a.redo());
    }

    /**
     * Releases references held by the sub-edits of the given type; sub-edits of other types are left untouched.
     */
    protected void cleanupSubEditsOfType(Class<? extends shipeditor.undo.edits.LayerEdit> type) {
        subEdits.forEach(edit -> {
            if (type.isInstance(edit)) {
                ((shipeditor.undo.edits.LayerEdit) edit).cleanupReferences();
            }
        });
    }

    /**
     * Notifies listeners that the layer was updated, but only if it is the currently active layer.
     */
    protected static void refreshIfActive(shipeditor.components.viewer.layers.ViewerLayer layer) {
        if (shipeditor.utility.overseers.StaticController.getActiveLayer() == layer) {
            shipeditor.communication.EventBus.publish(
                    new shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated(layer));
        }
    }

    @Override
    public String toString() {
        Class<? extends AbstractEdit> identity = this.getClass();
        return identity.getSimpleName() + " " + hashCode();
    }

}
