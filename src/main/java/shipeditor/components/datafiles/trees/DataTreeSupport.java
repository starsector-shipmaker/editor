package shipeditor.components.datafiles.trees;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.overseers.StaticController;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * Helpers shared by the data tree panels (hulls, hullmods, wings, ship systems, projectiles, weapons).
 */
@Log4j2
final class DataTreeSupport {

    private DataTreeSupport() {
    }

    /**
     * Creates a package root node for the given folder, using the core package when the folder is a core one
     * and falling back to a fresh unpinned package when settings don't know it.
     */
    static DefaultMutableTreeNode createPackageRootNode(String folderName, boolean isCoreFolder) {
        if (isCoreFolder) {
            GameDataPackage corePackage = SettingsManager.getCorePackage();
            return new DefaultMutableTreeNode(corePackage);
        }
        Settings settings = SettingsManager.getSettings();
        GameDataPackage dataPackage = settings.getPackage(folderName);
        if (dataPackage == null) {
            dataPackage = new GameDataPackage(folderName, false, false);
        }
        return new DefaultMutableTreeNode(dataPackage);
    }

    /**
     * Resolves the tree node targeted by a double left-click: the node under the pointer, or the
     * currently selected node when the pointer is not over one.
     *
     * @return the node, or null when the event is not a double left-click or nothing is targeted
     */
    static DefaultMutableTreeNode getDoubleClickedNode(JTree tree, MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 2) {
            return null;
        }
        Point eventPoint = e.getPoint();
        TreePath pathForLocation = tree.getPathForLocation(eventPoint.x, eventPoint.y);
        TreePath selectionPath = tree.getSelectionPath();
        TreePath targetPath = pathForLocation != null ? pathForLocation : selectionPath;
        if (targetPath == null) {
            return null;
        }
        return (DefaultMutableTreeNode) targetPath.getLastPathComponent();
    }

    static boolean isNotActiveInstrument(EditorInstrument target) {
        return StaticController.getEditorMode() != target;
    }

    static boolean isLayerEligibleForVariant(ViewerLayer activeLayer) {
        if (activeLayer instanceof ShipLayer checkedLayer) {
            ShipPainter painter = checkedLayer.getPainter();
            return painter != null && !painter.isUninitialized();
        }
        return false;
    }

    /**
     * Returns the layer's active variant, auto-creating and selecting a custom one when none is usable.
     */
    static ShipVariant getOrCreateActiveVariant(ShipLayer layer, ShipPainter painter) {
        return StaticController.getOrCreateEditableVariant(layer);
    }

    /**
     * Clears the info panel and gives it the vertical box layout the entry info builders expect.
     */
    static JPanel prepareInfoPanel(JPanel infoPanel) {
        infoPanel.removeAll();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        return infoPanel;
    }
}
