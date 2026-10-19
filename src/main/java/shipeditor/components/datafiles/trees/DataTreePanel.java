package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.OpenDataTarget;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SuppressWarnings("ClassWithTooManyMethods")
@Log4j2
@SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP" })
public abstract class DataTreePanel extends JPanel {

    @Getter
    private DefaultMutableTreeNode rootNode;

    @lombok.Setter
    @Getter
    private DefaultMutableTreeNode cachedSelectForMenu;

    @Getter
    private JTree tree;

    private boolean reloadQueued = true;
    private int batchGeneration;

    protected DataTreePanel(String rootName) {
        this.setLayout(new BorderLayout());
        JPanel topContainer = createTopPanel();
        if (topContainer != null) {
            topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Themes.getBorderColor()));
            this.add(topContainer, BorderLayout.PAGE_START);
        }
        javax.swing.JComponent contentSplitter = createContentSplitter(rootName);
        this.add(contentSplitter, BorderLayout.CENTER);

        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (this.isShowing()) {
                    if (!this.isDataLoaded() && !shipeditor.parsing.loading.FileLoading.isLoadingInProgress()) {
                        javax.swing.Action loadAction = this.getLoadDataAction();
                        if (loadAction != null) {
                            loadAction.actionPerformed(null);
                        }
                    } else if (this.reloadQueued) {
                        this.reload();
                        this.reloadQueued = false;
                    }
                }
            }
        });
        
        shipeditor.communication.EventBus.subscribe(this, event -> {
            if (event instanceof shipeditor.communication.events.components.ComponentEvents.LoadingActionFired checked) {
                if (!checked.started()) {
                    javax.swing.SwingUtilities.invokeLater(() -> this.queueReload());
                }
            }
        });
    }

    protected abstract boolean isDataLoaded();

    protected javax.swing.Action getLoadDataAction() {
        return new javax.swing.AbstractAction("Reload") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                queueReload();
            }
        };
    }

    public void queueReload() {
        if (this.isDataLoaded()) {
            if (!this.isShowing()) {
                this.reloadQueued = true;
                return;
            }
            try {
                this.reload();
            } catch (RuntimeException ex) {
                log.error("Silent exception intercepted during tree reload!", ex);
                debuggerHook();
                throw ex;
            }
            this.reloadQueued = false;
        } else {
            this.reloadQueued = true;
        }
    }

    public static void debuggerHook() {
        if (SettingsManager.isDeveloperModeEnabled()) {
            java.lang.System.nanoTime();
        }
    }

    static boolean isCurrentSkinNotEligible() {
        var activeLayer = StaticController.getActiveLayer();
        var isShipLayer = activeLayer instanceof ShipLayer;
        ShipLayer shipLayer;
        if (isShipLayer) {
            shipLayer = (ShipLayer) activeLayer;
        } else
            return true;

        ShipPainter shipPainter = shipLayer.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized())
            return true;
        var skin = shipPainter.getActiveSkin();
        return skin == null || skin.isBase();
    }

    protected JPanel createTopPanel() {
        return new JPanel();
    }

    void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    @Getter
    private JPanel leftInfoPanel;

    void resetInfoPanel() {
        if (leftInfoPanel != null) {
            leftInfoPanel.removeAll();
            leftInfoPanel.add(new JLabel(StringManager.getString("NO_ENTRY_SELECTED")));
            leftInfoPanel.revalidate();
            leftInfoPanel.repaint();
        }
        JPanel consolePanel = getConsolePanel();
        consolePanel.removeAll();
        consolePanel.add(new JLabel(StringManager.getString("NO_ENTRY_SELECTED")));
        consolePanel.revalidate();
        consolePanel.repaint();
    }

    public JPanel getConsolePanel() {
        return shipeditor.components.datafiles.trees.InfoConsolePanel.getInstance().getContentPanel();
    }

    public javax.swing.JComponent createContentSplitter(String rootName) {
        JPanel treeContainer = this.createTreePanel(rootName);
        treeContainer.setMinimumSize(new Dimension(120, 100));

        leftInfoPanel = new shipeditor.utility.components.containers.TextScrollPanel(new BorderLayout());
        leftInfoPanel.add(new JLabel(StringManager.getString("NO_ENTRY_SELECTED")), BorderLayout.NORTH);
        
        JScrollPane leftInfoScroll = new JScrollPane(leftInfoPanel);
        leftInfoScroll.setBorder(null);
        leftInfoScroll.setMinimumSize(new Dimension(120, 100));

        JSplitPane treeSplitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        treeSplitter.setOneTouchExpandable(true);
        treeSplitter.setResizeWeight(0.6f);
        treeSplitter.setLeftComponent(treeContainer);
        treeSplitter.setRightComponent(leftInfoScroll);
        return treeSplitter;
    }

    protected float getSplitterResizeWeight() {
        return 0.4f;
    }

    private JPanel createTreePanel(String rootName) {
        JPanel createdTreePanel = new JPanel();
        rootNode = new DefaultMutableTreeNode(rootName);
        tree = createCustomTree();
        ToolTipManager.sharedInstance().registerComponent(tree);
        JScrollPane scrollContainer = new JScrollPane(tree);
        createdTreePanel.setLayout(new BorderLayout());
        JPanel searchContainer = createSearchContainer();
        createdTreePanel.add(searchContainer, BorderLayout.PAGE_START);
        createdTreePanel.add(scrollContainer, BorderLayout.CENTER);
        this.initTreePanelListeners(createdTreePanel);
        return createdTreePanel;
    }

    @SuppressWarnings("WeakerAccess")
    protected JPanel createSearchContainer() {
        return new DataTreeSearchController(this, tree).createSearchContainer();
    }

    static GridBagConstraints getDefaultConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.PAGE_START;
        constraints.weighty = 0.0;
        constraints.insets = new Insets(0, 0, 0, 0);
        return constraints;
    }

    protected abstract void initTreePanelListeners(JPanel passedTreePanel);

    DefaultMutableTreeNode getNodeOfEntry(CSVEntry entry) {
        DefaultMutableTreeNode root = this.getRootNode();
        Enumeration<TreeNode> allNodes = root.depthFirstEnumeration();
        Spliterator<TreeNode> spliterator = Spliterators.spliteratorUnknownSize(
                allNodes.asIterator(), Spliterator.ORDERED);
        Stream<TreeNode> stream = StreamSupport.stream(spliterator, false);
        Optional<DefaultMutableTreeNode> treeNode = stream
                .filter(node -> node instanceof DefaultMutableTreeNode)
                .map(node -> (DefaultMutableTreeNode) node)
                .filter(node -> {
                    Object userObject = node.getUserObject();
                    if (userObject instanceof CSVEntry csvEntry) {
                        String entryID = csvEntry.getID();
                        return entryID.equals(entry.getID());
                    }
                    return false;
                }).findFirst();
        return treeNode.orElse(null);
    }

    JTree createCustomTree() {
        JTree customTree = new JTree(getRootNode()) {
            @Override
            public String getToolTipText(MouseEvent event) {
                if (getRowForLocation(event.getX(), event.getY()) == -1)
                    return null;
                TreePath currPath = getPathForLocation(event.getX(), event.getY());
                if (currPath == null)
                    return null;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) currPath.getLastPathComponent();
                Object entry = node.getUserObject();
                return getTooltipForEntry(entry);
            }

            @Override
            protected boolean removeDescendantSelectedPaths(TreePath path, boolean includePath) {
                return false;
            }
        };
        customTree.setToggleClickCount(1);
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            DragSource dragSource = DragSource.getDefaultDragSource();
            DragGestureListener gestureListener = new TreeDataGestureListener(customTree);
            dragSource.createDefaultDragGestureRecognizer(customTree, DnDConstants.ACTION_COPY,
                    gestureListener);
        }
        return customTree;
    }

    public void reload() {
        batchGeneration++;
        DefaultMutableTreeNode root = getRootNode();
        root.removeAllChildren();
        root.add(new DefaultMutableTreeNode("Loading..."));
        if (getTree().getModel() instanceof javax.swing.tree.DefaultTreeModel model) {
            model.nodeStructureChanged(root);
        }

        final int currentGeneration = batchGeneration;
        java.util.concurrent.CompletableFuture.supplyAsync(this::buildTreeNodesBackground)
                .thenAccept(packageRoots -> javax.swing.SwingUtilities.invokeLater(() -> {
                    if (currentGeneration != batchGeneration) return;
                    root.removeAllChildren();
                    if (packageRoots != null) {
                        for (DefaultMutableTreeNode node : packageRoots) {
                            root.add(node);
                        }
                    }
                    sortAndExpandTree();
                    onTreePopulated();
                    getTree().repaint();
                }));
    }

    protected abstract List<DefaultMutableTreeNode> buildTreeNodesBackground();

    protected void onTreePopulated() {}

    void sortAndExpandTree() {
        Enumeration<TreeNode> children = rootNode.children();
        List<DefaultMutableTreeNode> nodeList = new ArrayList<>();

        while (children.hasMoreElements()) {
            TreeNode folder = children.nextElement();
            if (folder instanceof DefaultMutableTreeNode packageNode) {
                nodeList.add(packageNode);
            }
        }

        nodeList.sort((firstNode, secondNode) -> {
            Object firstNodeUserObject = firstNode.getUserObject();
            Object secondNodeUserObject = secondNode.getUserObject();
            if (!(firstNodeUserObject instanceof GameDataPackage) || !(secondNodeUserObject instanceof GameDataPackage)) {
                return 0;
            }
            GameDataPackage firstDataPackage = (GameDataPackage) firstNodeUserObject;
            GameDataPackage secondDataPackage = (GameDataPackage) secondNodeUserObject;

            if (SettingsManager.isCoreFolder(firstDataPackage)) {
                return -1;
            } else if (SettingsManager.isCoreFolder(secondDataPackage)) {
                return 1;
            }

            if (firstDataPackage.isPinned() && !secondDataPackage.isPinned()) {
                return -1;
            } else if (!firstDataPackage.isPinned() && secondDataPackage.isPinned()) {
                return 1;
            }

            String firstFolderName = firstDataPackage.getFolderName();
            String secondFolderName = secondDataPackage.getFolderName();
            return firstFolderName.compareToIgnoreCase(secondFolderName);
        });

        rootNode.removeAllChildren();
        for (DefaultMutableTreeNode packageNode : nodeList) {
            rootNode.add(packageNode);
        }

        Enumeration<TreeNode> updatedPackages = rootNode.children();
        while (updatedPackages.hasMoreElements()) {
            TreeNode folder = updatedPackages.nextElement();
            DataTreePanel.sortFolderNode(folder, (node1, node2) -> {
                String name1 = node1.toString();
                String name2 = node2.toString();
                return name1.compareToIgnoreCase(name2);
            });
        }

        if (tree.getModel() instanceof DefaultTreeModel checked) {
            checked.nodeStructureChanged(rootNode);
        }

        tree.expandPath(new TreePath(rootNode));
        tree.repaint();
    }

    @SuppressWarnings("ConstantConditions")
    private static void sortFolderNode(TreeNode folder, Comparator<DefaultMutableTreeNode> comparator) {
        Enumeration<? extends TreeNode> children = folder.children();
        List<DefaultMutableTreeNode> nodeList = new ArrayList<>();
        while (children.hasMoreElements()) {
            if (children.nextElement() instanceof DefaultMutableTreeNode checked) {
                nodeList.add(checked);
            }
        }
        nodeList.sort(comparator);
        DefaultMutableTreeNode casted = (DefaultMutableTreeNode) folder;
        casted.removeAllChildren();
        for (MutableTreeNode node : nodeList) {
            casted.add(node);
        }
    }

    static String getTooltipForPackage(GameDataPackage dataPackage) {
        String corePackageLine = "";
        boolean isCoreFolder = SettingsManager.isCoreFolder(dataPackage);
        if (isCoreFolder) {
            corePackageLine = "<p>" + "Is a core package" + "</p>";
        }
        String pinnedPackageLine = "";
        boolean isPinned = dataPackage.isPinned();
        if (isPinned) {
            pinnedPackageLine = "<p>" + "Is pinned" + "</p>";
        }
        if (isCoreFolder || isPinned) {
            return "<html>" + corePackageLine + pinnedPackageLine + "</html>";
        }
        return null;
    }

    void createRightPanelDataTable(CSVEntry entry) {
        Map<String, String> data = entry.getRowData();
        JScrollPane tableContainer = DataTreeTableBuilder.createTableFromMap(data, entry);
        this.addContentToRightPanel(tableContainer, entry);
    }

    private void addContentToRightPanel(JComponent component, CSVEntry entry) {
        JPanel tableContainer = new JPanel();
        tableContainer.setLayout(new BorderLayout());
        tableContainer.setAlignmentY(java.awt.Component.TOP_ALIGNMENT);

        JPanel buttonsContainer = DataTreeTableBuilder.createTableButtons(entry);

        ComponentUtilities.outfitPanelWithTitle(buttonsContainer,
                new Insets(1, 0, 0, 0), "CSV Data");

        tableContainer.add(component, BorderLayout.CENTER);
        tableContainer.add(buttonsContainer, BorderLayout.PAGE_START);

        JPanel consolePanel = getConsolePanel();
        consolePanel.removeAll();
        consolePanel.add(tableContainer, BorderLayout.CENTER);
        consolePanel.revalidate();
        consolePanel.repaint();
    }

    static void configureCellRendererColors(Object userObject, JLabel stamp) {
        Color textColor = Themes.getTextColor();
        stamp.setForeground(textColor);
        if (userObject instanceof GameDataPackage dataPackage) {
            stamp.setText(dataPackage.getFolderName());
            if (SettingsManager.isCoreFolder(dataPackage)) {
                stamp.setForeground(Themes.getCorePackageTextColor());
            } else if (dataPackage.isPinned()) {
                stamp.setForeground(Themes.getPinnedPackageTextColor());
            }
        }
    }

    protected abstract String getTooltipForEntry(Object entry);

    protected abstract Class<?> getEntryClass();

    public java.awt.event.MouseAdapter createContextMenuListener() {
        return new DataTreeContextMenuController(this, tree, getEntryClass());
    }

    JPopupMenu getContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem collapsePackage = new JMenuItem(StringManager.getString("COLLAPSE_PACKAGE"));
        collapsePackage.addActionListener(getCollapseAction());
        menu.add(collapsePackage);
        menu.addSeparator();
        JMenuItem openSourceFile = new JMenuItem(StringManager.getString("OPEN_SOURCE_FILE"));
        openSourceFile.addActionListener(e -> openEntryPath(OpenDataTarget.FILE));
        menu.add(openSourceFile);
        JMenuItem openInExplorer = new JMenuItem(StringManager.getString("OPEN_CONTAINING_FOLDER"));
        openInExplorer.addActionListener(e -> openEntryPath(OpenDataTarget.CONTAINER));
        menu.add(openInExplorer);
        JMenuItem openPackage = new JMenuItem(StringManager.getString("OPEN_DATA_PACKAGE"));
        openPackage.addActionListener(e -> openEntryPath(OpenDataTarget.PACKAGE));
        menu.add(openPackage);
        return menu;
    }

    protected abstract void openEntryPath(OpenDataTarget target);

    private ActionListener getCollapseAction() {
        return e -> {
            Class<?> entryClass = getEntryClass();
            if (entryClass.isInstance(this.cachedSelectForMenu.getUserObject())) {
                TreePath selected = this.tree.getSelectionPath();
                if (selected != null) {
                    this.tree.collapsePath(selected.getParentPath());
                }
            }
        };
    }

}
