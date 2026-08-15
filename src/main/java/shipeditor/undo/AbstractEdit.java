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

    @Override
    public String toString() {
        Class<? extends AbstractEdit> identity = this.getClass();
        return identity.getSimpleName() + " " + hashCode();
    }

}
