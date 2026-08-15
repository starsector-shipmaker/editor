package shipeditor.undo;
import shipeditor.utility.UtilityEnums.EditCategory;


/**
 * Entity interface for the Undo/Redo functionality; this implements the Command Pattern.
 * <p>
 * In Starsector Ship Editor, any user action that mutates the project state (e.g., dragging an anchor,
 * rotating a layer, modifying a ship hull) must be encapsulated as an {@code Edit}.
 * These edits are managed globally by the {@link UndoOverseer}, which maintains the undo/redo stacks.
 */
public interface Edit {

    void setFinished(boolean state);

    void add(Edit edit);

    void undo();

    void redo();

    String getName();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isFinished();

    default EditCategory getCategory() {
        return EditCategory.HULL;
    }

    /**
     * Gets the target variant (ShipVariant or similar) that this edit applies to.
     * Overridden by edits that operate on specific modules rather than the base layer.
     */
    default Object getTargetEntity() {
        return null;
    }

}
