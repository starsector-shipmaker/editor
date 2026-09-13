package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.OpenDataTarget;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.utility.overseers.StaticController;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import shipeditor.utility.components.UIConstants;
import shipeditor.communication.events.components.ComponentEvents.SelectShipDataEntry;
import shipeditor.communication.events.components.ComponentEvents.GameDataPanelResized;
import shipeditor.communication.events.files.FileEvents.HullTreeReloadQueued;
import shipeditor.persistence.database.IndexedFile;

@Log4j2
public class HullsTreePanel extends DataTreePanel {

    public HullsTreePanel() {
        super("Hull file packages");
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    protected String getTooltipForEntry(Object entry) {
        if (entry instanceof IndexedFile file) {
            String dragHint = "(Double-click or drag to load sprite)";
            if (StaticController.getEditorMode() == EditorInstrument.VARIANT_MODULES) {
                dragHint = "(Drag to install as module)";
            }
            shipeditor.components.datafiles.entities.ShipCSVEntry shipEntry = SettingsManager.getGameData().getOrCreateShipEntry(file);
            String displayName = shipEntry != null ? shipEntry.toString() : "Hull ID: " + file.getEntityId();
            return displayName + "\n" + dragHint;
        } else if (entry instanceof GameDataPackage dataPackage) {
            return DataTreePanel.getTooltipForPackage(dataPackage);
        }
        return null;
    }

    @Override
    protected void initTreePanelListeners(JPanel passedTreePanel) {
        this.initBusListening();
        this.initComponentListeners();
    }

    private void initBusListening() {
        JTree tree = getTree();
        EventBus.subscribe(this, event -> {
            if (event instanceof HullTreeReloadQueued) {
                this.queueReload();
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof SelectShipDataEntry checked) {
                ShipCSVEntry entry = checked.entry();
                // Find node by entity ID
                DefaultMutableTreeNode root = getRootNode();
                DefaultMutableTreeNode foundNode = null;
                for (int i = 0; i < root.getChildCount(); i++) {
                    DefaultMutableTreeNode packageNode = (DefaultMutableTreeNode) root.getChildAt(i);
                    for (int j = 0; j < packageNode.getChildCount(); j++) {
                        DefaultMutableTreeNode shipNode = (DefaultMutableTreeNode) packageNode.getChildAt(j);
                        if (shipNode.getUserObject() instanceof IndexedFile file && file.getEntityId().equals(entry.getHullID())) {
                            foundNode = shipNode;
                            break;
                        }
                    }
                    if (foundNode != null) break;
                }
                if (foundNode != null) {
                    TreePath path = new TreePath(foundNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                }
            }
        });
    }

    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isShipDataLoaded();
    }


    protected JPanel createSearchContainer() {
        JPanel searchContainer = new JPanel(new GridBagLayout());
        searchContainer.setBorder(UIConstants.EMPTY_BORDER);
        JTextField searchField = this.getSearchField();
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(0, 0, 0, 0);
        searchContainer.add(searchField, gridBagConstraints);
        return searchContainer;
    }

    private JTextField getSearchField() {
        JTextField searchField = new JTextField();
        searchField.setToolTipText(StringManager.getString("SEARCH_BY_SHIP_NAME_HULL_ID_OR_FILENAME"));
        searchField.putClientProperty("JTextField.placeholderText", "Filter hulls...");
        javax.swing.Timer timer = new javax.swing.Timer(300, e -> {
            ShipFilterPanel.setCurrentTextFilter(searchField.getText());
            this.reload();
        });
        timer.setRepeats(false);

        Document document = searchField.getDocument();
        document.addDocumentListener(new SearchFieldDocumentListener(timer));
        return searchField;
    }

    private void initComponentListeners() {
        JTree tree = getTree();
        tree.addMouseListener(createContextMenuListener());
        tree.addTreeSelectionListener(e -> {
            TreePath selectedNode = e.getNewLeadSelectionPath();
            if (selectedNode == null)
                return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedNode.getLastPathComponent();
            if (node.getUserObject() instanceof shipeditor.persistence.database.IndexedFile file) {
                JPanel consolePanel = getConsolePanel();
                consolePanel.removeAll();
                consolePanel.add(new javax.swing.JLabel("Loading..."));
                consolePanel.revalidate();
                consolePanel.repaint();

                JPanel leftPanel = getLeftInfoPanel();
                leftPanel.removeAll();
                leftPanel.add(new javax.swing.JLabel("Loading..."));
                leftPanel.revalidate();
                leftPanel.repaint();

                ShipCSVEntry checked = null;
                var gameData = SettingsManager.getGameData();
                if (gameData != null) {
                    checked = gameData.getOrCreateShipEntry(file);
                }

                if (checked != null) {
                    ShipCSVEntry finalChecked = checked;
                    checked.lazyLoadSpecAndSkins().thenAccept(v -> {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            updateEntryPanel(finalChecked);
                            EventBus.publish(new GameDataPanelResized(this.getMinimumSize()));
                        });
                    });
                } else {
                    resetInfoPanel();
                }
            } else {
                resetInfoPanel();
            }
        });
        tree.addMouseListener(new DoubleClickLayerLoader());
    }

    void updateEntryPanel(ShipCSVEntry selected) {
        JPanel infoPanel = getLeftInfoPanel();
        infoPanel.removeAll();
        infoPanel.setLayout(new javax.swing.BoxLayout(infoPanel, javax.swing.BoxLayout.Y_AXIS));

        ShipFilesSubpanel shipFilesSubpanel = new ShipFilesSubpanel(infoPanel);
        JPanel shipFilesPanel = shipFilesSubpanel.createShipFilesPanel(selected, this);
        shipFilesPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        infoPanel.add(shipFilesPanel);
        infoPanel.add(javax.swing.Box.createVerticalStrut(20));

        createRightPanelDataTable(selected);

        infoPanel.revalidate();
        infoPanel.repaint();
    }

    /**
     * Loads a ship into the editor from an IndexedFile entry.
     * First attempts to use the ShipCSVEntry path (which includes skin support).
     * Falls back to direct .ship file loading for ships not in ship_data.csv
     * (e.g. station modules, drones, boss ships).
     */
    private void loadShipFromIndexedFile(shipeditor.persistence.database.IndexedFile file) {
        var gameData = SettingsManager.getGameData();
        ShipCSVEntry checked = gameData != null ? gameData.getOrCreateShipEntry(file) : null;
        if (checked != null) {
            log.debug("Loading hull via entry: {}", file.getEntityId());
            checked.loadLayerFromEntry();
            return;
        }
        // Direct fallback: load from .ship file if repository not initialized
        java.io.File shipFile = file.getFilePath().toFile();
        shipeditor.representation.ship.HullSpecFile hullSpec =
                shipeditor.parsing.loading.FileLoading.loadHullFile(shipFile);
        if (hullSpec == null) {
            log.error("Failed to load hull spec from file: {}", file.getFilePath());
            javax.swing.JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("FAILED_TO_LOAD_HULL_SPEC_FILE_MSG") + file.getFilePath(),
                    shipeditor.utility.text.StringManager.getString("FILE_LOADING_ERROR"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        String spriteName = hullSpec.getSpriteName();
        Path spriteFilePath = Path.of(spriteName);
        // Resolve the mod folder from the IndexedFile to search for sprites.
        Path modFolder = SettingsManager.getFolderForModId(file.getModId());
        java.io.File spriteFile = shipeditor.parsing.loading.FileLoading.fetchDataFile(spriteFilePath, modFolder);
        if (spriteFile == null) {
            log.error("Sprite file for ship not found: {}", spriteFilePath);
            javax.swing.JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("SPRITE_FILE_FOR_SHIP_NOT_FOUND_LAYER_NOT_MSG") + spriteFilePath,
                    shipeditor.utility.text.StringManager.getString("FILE_LOADING_ERROR"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        shipeditor.components.viewer.layers.ship.ShipLayer newLayer =
                FileUtilities.createShipLayerWithSprite(spriteFile);
        if (newLayer != null) {
            newLayer.initializeHullData(hullSpec);
        }
    }

    @Override
    protected java.util.List<DefaultMutableTreeNode> buildTreeNodesBackground() {
        Map<String, List<shipeditor.persistence.database.IndexedFile>> shipEntries = ShipFilterPanel.getFilteredEntries();
        if (shipEntries == null || shipEntries.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<DefaultMutableTreeNode> packageRoots = new java.util.ArrayList<>();
        for (Map.Entry<String, List<shipeditor.persistence.database.IndexedFile>> hullFolder : shipEntries.entrySet()) {
            String modId = hullFolder.getKey();
            if (SettingsManager.isModActive(modId)) {
                packageRoots.add(createPackageNode(hullFolder));
            }
        }
        return packageRoots;
    }

    private static DefaultMutableTreeNode createPackageNode(Map.Entry<String, List<shipeditor.persistence.database.IndexedFile>> hullFolder) {
        String packageName = hullFolder.getKey();

        Settings settings = SettingsManager.getSettings();

        DefaultMutableTreeNode result;
        if ("starsector-core".equals(packageName)) {
            GameDataPackage corePackage = SettingsManager.getCorePackage();
            result = new DefaultMutableTreeNode(corePackage);
            for (shipeditor.persistence.database.IndexedFile entry : hullFolder.getValue()) {
                MutableTreeNode shipNode = new DefaultMutableTreeNode(entry);
                result.add(shipNode);
            }
        } else {
            GameDataPackage dataPackage = settings.getPackage(packageName);
            if (dataPackage == null) {
                dataPackage = new GameDataPackage(packageName, false, false);
            }
            result = new DefaultMutableTreeNode(dataPackage);

            for (shipeditor.persistence.database.IndexedFile entry : hullFolder.getValue()) {
                MutableTreeNode shipNode = new DefaultMutableTreeNode(entry);
                result.add(shipNode);
            }
        }

        return result;
    }

    private class LoadLayerFromTree extends AbstractAction {
        @Override
        public boolean isEnabled() {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            return super.isEnabled() && cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            if (cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile file) {
                loadShipFromIndexedFile(file);
            }
        }
    }

    @Override
    protected Class<?> getEntryClass() {
        return shipeditor.persistence.database.IndexedFile.class;
    }

    @Override
    protected JTree createCustomTree() {
        JTree custom = super.createCustomTree();
        custom.setCellRenderer(new HullsTreeCellRenderer());
        return custom;
    }

    @Override
    JPopupMenu getContextMenu() {
        JPopupMenu menu = super.getContextMenu();
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        JMenuItem loadAsLayer = new JMenuItem(StringManager.getString("LOAD_AS_SHIP_LAYER"));
        loadAsLayer.addActionListener(new HullsTreePanel.LoadLayerFromTree());
        menu.insert(loadAsLayer, 0);
        menu.insert(new JPopupMenu.Separator(), 1);

        if (cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile file) {
            var shipEntries = SettingsManager.getGameData().getAllShipEntries();
            if (shipEntries != null) {
                ShipCSVEntry checked = shipEntries.get(file.getEntityId());
                if (checked != null) {
                    JMenuItem openSkin = HullsTreePanel.addOpenSkinOption(checked);
                    if (openSkin != null) {
                        menu.addSeparator();
                        menu.add(openSkin);
                    }
                }
            }
        }
        return menu;
    }

    private static JMenuItem addOpenSkinOption(ShipCSVEntry checked) {
        SkinSpecFile activeSkinSpecFile = checked.getActiveSkinSpecFile();
        if (activeSkinSpecFile == null || activeSkinSpecFile.isBase())
            return null;
        JMenuItem openSkin = new JMenuItem(StringManager.getString("OPEN_SKIN_FILE"));
        openSkin.addActionListener(e -> {
            Path toOpen = activeSkinSpecFile.getFilePath();
            FileUtilities.openPathInDesktop(toOpen);
        });
        return openSkin;
    }

    @Override
    protected void openEntryPath(OpenDataTarget target) {
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (!(cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile file))
            return;
            
        Path toOpen;
        switch (target) {
            case FILE -> toOpen = file.getFilePath();
            case CONTAINER -> {
                toOpen = file.getFilePath().getParent();
                if (toOpen == null)
                    return;
            }
            default -> {
                toOpen = SettingsManager.getFolderForModId(file.getModId());
            }
        }
        FileUtilities.openPathInDesktop(toOpen);
    }

    private static class HullsTreeCellRenderer extends DefaultTreeCellRenderer {

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
            setIcon(null);
            if (object instanceof shipeditor.persistence.database.IndexedFile file) {
                var gameData = SettingsManager.getGameData();
                ShipCSVEntry entry = gameData != null ? gameData.getOrCreateShipEntry(file) : null;
                String title = null;
                if (entry != null) {
                    title = entry.getShipName();
                    if (title == null || title.isBlank()) {
                        title = entry.toString();
                    }
                    if (title == null || title.isBlank()) {
                        title = file.getEntityName() != null && !file.getEntityName().isBlank() ? file.getEntityName() : file.getEntityId();
                    }

                    shipeditor.representation.RepresentationEnums.HullSize hullSize = entry.getSize();
                    if (hullSize != null && hullSize != shipeditor.representation.RepresentationEnums.HullSize.DEFAULT) {
                        title = "[" + hullSize.getDisplayedName() + "] " + title;
                    }
                } else {
                    title = file.getEntityName() != null && !file.getEntityName().isBlank() ? file.getEntityName() : file.getEntityId();
                }
                if (title == null || title.isBlank()) {
                    title = file.getFileName();
                }
                setText(title);
            }
            return this;
        }

    }

    private class DoubleClickLayerLoader extends MouseAdapter {

        @SuppressWarnings("ChainOfInstanceofChecks")
        @Override
        public void mousePressed(MouseEvent e) {
            // Check for double-click.
            if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 2)
                return;
            JTree tree = getTree();
            Point eventPoint = e.getPoint();
            TreePath pathForLocation = tree.getPathForLocation(eventPoint.x, eventPoint.y);
            TreePath selectionPath = tree.getSelectionPath();
            TreePath targetPath = pathForLocation != null ? pathForLocation : selectionPath;
            
            if (targetPath == null)
                return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
            if (node.getUserObject() instanceof shipeditor.persistence.database.IndexedFile file) {
                loadShipFromIndexedFile(file);
            }
        }
    }

    private static class SearchFieldDocumentListener implements DocumentListener {
        private final javax.swing.Timer timer;

        SearchFieldDocumentListener(javax.swing.Timer timer) {
            this.timer = timer;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            timer.restart();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            timer.restart();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            timer.restart();
        }
    }

}
