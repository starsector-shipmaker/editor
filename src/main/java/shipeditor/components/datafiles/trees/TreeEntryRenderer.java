package shipeditor.components.datafiles.trees;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * Shared base (usable as-is) for the data tree cell renderers: applies the default renderer, unwraps the node's
 * user object, applies the package colors and then lets subclasses customize the label.
 */
class TreeEntryRenderer extends DefaultTreeCellRenderer {

    @SuppressWarnings("ParameterHidesMemberVariable")
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (!(value instanceof DefaultMutableTreeNode treeNode)) {
            return this;
        }
        Object object = treeNode.getUserObject();
        if (object == null) {
            return this;
        }
        DataTreePanel.configureCellRendererColors(object, this);
        customizeEntry(object, leaf);
        return this;
    }

    /**
     * Hook for subclasses to adjust the rendered label for the given non-null user object.
     */
    protected void customizeEntry(Object userObject, boolean leaf) {
    }
}
