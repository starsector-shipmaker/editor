package shipeditor.components.datafiles.trees;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.OpenDataTarget;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.SettingsManager;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Log4j2
public abstract class CSVDataTreePanel<T extends CSVEntry> extends DataTreePanel {

    CSVDataTreePanel(String rootName) {
        super(rootName);
    }

    protected abstract String getEntryTypeName();

    @Override
    protected JTree createCustomTree() {
        JTree custom = super.createCustomTree();
        custom.setCellRenderer(new TreeEntryRenderer());
        return custom;
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    protected String getTooltipForEntry(Object entry) {
        if (entry instanceof GameDataPackage dataPackage) {
            return DataTreePanel.getTooltipForPackage(dataPackage);
        } else if (entry instanceof CSVEntry checked) {
            return checked.getMultilineTooltip();
        }
        return null;
    }

    @Override
    protected JPanel createTopPanel() {
        return new JPanel();
    }

    protected abstract Map<String, T> getRepository();

    protected abstract Map<Path, List<T>> getPackageList();

    protected abstract void setLoadedStatus();

    @Override
    protected java.util.List<DefaultMutableTreeNode> buildTreeNodesBackground() {
        Map<Path, List<T>> entriesByPackage = getPackageList();
        
        log.info("buildTreeNodesBackground called in {} with {} packages", this.getClass().getSimpleName(),
                entriesByPackage == null ? "null" : entriesByPackage.size());
        
        if (entriesByPackage == null || entriesByPackage.isEmpty()) {
            DataTreePanel.debuggerHook();
            return java.util.Collections.emptyList();
        }

        int totalEntries = entriesByPackage.values().stream().mapToInt(list -> list.size()).sum();
        log.info("Total entries to load: {}", totalEntries);

        Map<String, T> entriesRepository = getRepository();
        java.util.List<DefaultMutableTreeNode> packageRoots = new java.util.ArrayList<>();

        for (Map.Entry<Path, List<T>> entry : entriesByPackage.entrySet()) {
            Path path = entry.getKey();
            String folderName = FileUtilities.extractFolderName(path.toString());
            if (!SettingsManager.isModActive(folderName)) {
                continue;
            }

            for (T item : entry.getValue()) {
                if (item.getID() != null) {
                    entriesRepository.putIfAbsent(item.getID(), item);
                }
            }
            packageRoots.add(createPackageNode(entry));
        }

        setLoadedStatus();
        log.info("Total {} {} entries registered.", entriesRepository.size(), getEntryTypeName());
        
        return packageRoots;
    }

    private DefaultMutableTreeNode createPackageNode(Map.Entry<Path, List<T>> entryFolder) {
        Path path = entryFolder.getKey();
        String packagePath = path.toString();
        String folderName = FileUtilities.extractFolderName(packagePath);

        DefaultMutableTreeNode result = DataTreeSupport.createPackageRootNode(folderName,
                SettingsManager.isCoreFolder(folderName));

        List<T> entries = entryFolder.getValue();
        if (entries != null && !entries.isEmpty()) {
            for (T csvEntry : entries) {
                result.add(new DefaultMutableTreeNode(csvEntry));
            }
        }

        return result;
    }

    @Override
    protected void initTreePanelListeners(JPanel passedTreePanel) {
        initComponentListeners();
        initWalkerListening();
    }

    protected abstract void initWalkerListening();

    private void initComponentListeners() {
        JTree tree = getTree();
        tree.addMouseListener(createContextMenuListener());
        tree.addTreeSelectionListener(e -> {
            TreePath selectedNode = e.getNewLeadSelectionPath();
            if (selectedNode == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedNode.getLastPathComponent();
            T entryObject = getObjectFromNode(node);
            if (entryObject != null) {
                updateEntryPanel(entryObject);
            } else {
                resetInfoPanel();
            }
        });
    }

    protected abstract void updateEntryPanel(T selected);

    @Override
    protected Action getLoadDataAction() {
        return new AbstractAction("Reload") {
            @Override
            public void actionPerformed(ActionEvent e) {
                reload();
            }
        };
    }

    protected T getObjectFromNode(DefaultMutableTreeNode node) {
        Object userObject = node.getUserObject();
        if (!getEntryClass().isInstance(userObject)) return null;
        @SuppressWarnings("unchecked")
        T checked = (T) userObject;
        return checked;
    }

    @Override
    protected void openEntryPath(OpenDataTarget target) {
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        T entryObject = getObjectFromNode(cachedSelectForMenu);
        if (entryObject == null) return;
        Path toOpen;
        switch (target) {
            case FILE -> toOpen = entryObject.getTableFilePath();
            case CONTAINER -> {
                toOpen = entryObject.getTableFilePath().getParent();
                if (toOpen == null) return;
            }
            default -> toOpen = entryObject.getPackageFolderPath();
        }
        FileUtilities.openPathInDesktop(toOpen);
    }

}
