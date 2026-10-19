package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.layers.weapon.WeaponSprites;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.Sprite;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.utility.overseers.StaticController;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
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
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import shipeditor.utility.components.UIConstants;
import shipeditor.communication.events.components.ComponentEvents.SelectWeaponDataEntry;
import shipeditor.communication.events.files.FileEvents.WeaponTreeReloadQueued;

@Log4j2
public class WeaponsTreePanel extends DataTreePanel {

    private boolean autoExpandNodes;
    public WeaponsTreePanel() {
        super("Weapon file packages");
    }


    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isWeaponsDataLoaded();
    }

    @Override
    protected JTree createCustomTree() {
        JTree custom = super.createCustomTree();
        custom.setCellRenderer(new WeaponsTreeCellRenderer());
        return custom;
    }

    private static class WeaponsTreeCellRenderer extends DefaultTreeCellRenderer {
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
            if (object instanceof IndexedFile file && leaf) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(file) : null;
                if (entry != null) {
                    WeaponType weaponType = entry.getLazyType();
                    if (weaponType != null && weaponType.getColor() != null) {
                        setIcon(ComponentUtilities.createIconFromColor(weaponType.getColor(), 10, 10));
                    } else {
                        setIcon(null);
                    }
                    String title = entry.toString();
                    var size = entry.getSize();
                    if (size != null) {
                        title = "[" + size.getDisplayedName() + "] " + title;
                    }
                    setText(title);
                } else {
                    setIcon(null);
                    setText(file.getEntityName() != null ? file.getEntityName() : file.getEntityId());
                }
            }
            return this;
        }
    }

    @Override
    protected void initTreePanelListeners(JPanel passedTreePanel) {
        EventBus.subscribe(this, event -> {
            if (event instanceof WeaponTreeReloadQueued) {
                this.queueReload();
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof SelectWeaponDataEntry checked) {
                WeaponCSVEntry entry = checked.entry();
                DefaultMutableTreeNode root = getRootNode();
                DefaultMutableTreeNode foundNode = null;
                for (int i = 0; i < root.getChildCount(); i++) {
                    DefaultMutableTreeNode packageNode = (DefaultMutableTreeNode) root.getChildAt(i);
                    for (int j = 0; j < packageNode.getChildCount(); j++) {
                        DefaultMutableTreeNode weaponNode = (DefaultMutableTreeNode) packageNode.getChildAt(j);
                        if (weaponNode.getUserObject() instanceof IndexedFile file && file.getEntityId().equals(entry.getWeaponID())) {
                            foundNode = weaponNode;
                            break;
                        }
                    }
                    if (foundNode != null) break;
                }
                if (foundNode != null) {
                    JTree tree = this.getTree();
                    TreePath path = new TreePath(foundNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                }
            }
        });
        JTree tree = getTree();
        tree.addTreeSelectionListener(e -> {
            TreePath selectedNode = e.getNewLeadSelectionPath();
            if (selectedNode == null)
                return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedNode.getLastPathComponent();
            if (node.getUserObject() instanceof IndexedFile file) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(file) : null;
                if (entry != null) {
                    JPanel infoPanel = getLeftInfoPanel();
                    infoPanel.removeAll();
                    infoPanel.add(new JLabel(StringManager.getString("LOADING_1")));
                    infoPanel.revalidate();
                    infoPanel.repaint();

                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        entry.getWeaponImage(); // pre-load sprite off EDT
                        return entry;
                    }).thenAccept(loaded -> javax.swing.SwingUtilities.invokeLater(() -> updateEntryPanel(loaded)));
                } else {
                    resetInfoPanel();
                }
            } else {
                resetInfoPanel();
            }
        });
        tree.addMouseListener(new DoubleClickLayerLoader());
    }

    @Override
    protected Class<?> getEntryClass() {
        return IndexedFile.class;
    }

    @Override
    protected java.util.List<DefaultMutableTreeNode> buildTreeNodesBackground() {
        Map<String, List<IndexedFile>> entries = WeaponFilterPanel.getFilteredEntries();
        if (entries == null || entries.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<DefaultMutableTreeNode> packageRoots = new java.util.ArrayList<>();
        for (Map.Entry<String, List<IndexedFile>> entryFolder : entries.entrySet()) {
            String modId = entryFolder.getKey();
            if (SettingsManager.isModActive(modId)) {
                DefaultMutableTreeNode packageRoot;
                if (SettingsManager.isCoreFolder(modId)) {
                    GameDataPackage corePackage = SettingsManager.getCorePackage();
                    packageRoot = new DefaultMutableTreeNode(corePackage);
                } else {
                    Settings settings = SettingsManager.getSettings();
                    GameDataPackage modPackage = settings.getPackage(modId);
                    if (modPackage == null) {
                        modPackage = new GameDataPackage(modId, false, false);
                    }
                    packageRoot = new DefaultMutableTreeNode(modPackage);
                }
                for (IndexedFile file : entryFolder.getValue()) {
                    MutableTreeNode entryNode = new DefaultMutableTreeNode(file);
                    packageRoot.add(entryNode);
                }
                packageRoots.add(packageRoot);
            }
        }
        return packageRoots;
    }

    @Override
    protected void onTreePopulated() {
        if (autoExpandNodes) {
            this.expandAllNodes();
        }
    }

    @Override
    void resetInfoPanel() {
        JPanel infoPanel = getLeftInfoPanel();
        infoPanel.removeAll();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.add(new JLabel(StringManager.getString("SELECT_A_WEAPON_TO_VIEW_INFORMATION")));
        infoPanel.revalidate();
        infoPanel.repaint();
    }

    protected JPanel createSearchContainer() {
        JPanel searchContainer = new JPanel(new GridBagLayout());
        searchContainer.setBorder(UIConstants.EMPTY_BORDER);
        JTextField searchField = this.getTextField();
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new Insets(0, 0, 0, 0);
        searchContainer.add(searchField, gridBagConstraints);
        return searchContainer;
    }

    private JTextField getTextField() {
        JTextField searchField = new JTextField();
        searchField.setToolTipText(StringManager.getString("INPUT_IS_CHECKED_AGAINST_DISPLAYED_FILENAME_AND_WEAPON_ID_AS_A_SUBSTRING"));
        javax.swing.Timer timer = new javax.swing.Timer(300, e -> {
            WeaponFilterPanel.setCurrentTextFilter(searchField.getText());
            this.reload();
        });
        timer.setRepeats(false);
        Document document = searchField.getDocument();
        document.addDocumentListener(new SearchFieldDocumentListener(timer));
        return searchField;
    }

    @Override
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        JCheckBox expandNodes = new JCheckBox(StringManager.getString("AUTO_EXPAND_NODES"));
        expandNodes.addActionListener(e -> this.autoExpandNodes = expandNodes.isSelected());
        topPanel.add(expandNodes);
        return topPanel;
    }


    @Override
    protected String getTooltipForEntry(Object entry) {
        if (entry instanceof IndexedFile file) {
            String dragHint = "(Double-click or drag to load weapon sprite)";
            var gameData = SettingsManager.getGameData();
            WeaponCSVEntry weaponEntry = gameData != null ? gameData.getOrCreateWeaponEntry(file) : null;
            String displayName = weaponEntry != null ? weaponEntry.toString() : "Weapon ID: " + file.getEntityId();
            return displayName + "\n" + dragHint;
        } else if (entry instanceof GameDataPackage dataPackage) {
            return DataTreePanel.getTooltipForPackage(dataPackage);
        }
        return null;
    }

    private void updateEntryPanel(WeaponCSVEntry selected) {
        JPanel infoPanel = getLeftInfoPanel();
        infoPanel.removeAll();
        infoPanel.setLayout(new javax.swing.BoxLayout(infoPanel, javax.swing.BoxLayout.Y_AXIS));
        ComponentUtilities.InfoPanelBuilder builder = new ComponentUtilities.InfoPanelBuilder("Weapon Info");
        builder.addSpritePreview(selected.getWeaponImage());

        javax.swing.JTextArea nameLabel = ComponentUtilities.createWrappingLabel("Weapon name: " + selected.toString());
        builder.addCustomComponent(nameLabel);

        shipeditor.representation.weapon.WeaponEnums.WeaponSize size = selected.getSize();
        if (size != null) {
            javax.swing.JTextArea sizeLabel = ComponentUtilities.createWrappingLabel("Weapon size: " + size.getDisplayedName());
            builder.addCustomComponent(sizeLabel);
        }

        WeaponSpecFile specFile = selected.getSpecFile();
        builder.addWrappingPathLabel("Weapon file: ", specFile.getWeaponSpecFilePath());
        var projectileSpecFile = GameDataRepository.getProjectileByID(specFile.getProjectileSpecId());
        if (projectileSpecFile != null) {
            builder.addWrappingPathLabel("Projectile file: ", projectileSpecFile.getProjectileSpecFilePath());
        }
        WeaponSprites sprites = selected.getSprites();
        populateSpriteFileLabels(builder, sprites);
        builder.addCustomComponent(createInstallableSlotsLabel(selected));
        infoPanel.add(builder.getPanel());
        infoPanel.add(javax.swing.Box.createVerticalStrut(20));
        
        EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.WeaponEntryPicked(selected));
        infoPanel.revalidate();
        infoPanel.repaint();
    }

    private static void populateSpriteFileLabels(ComponentUtilities.InfoPanelBuilder builder, WeaponSprites sprites) {
        addSpriteLabel(builder, sprites.getTurretSprite(), "Turret sprite: ");
        addSpriteLabel(builder, sprites.getTurretGunSprite(), "Turret gun sprite: ");
        addSpriteLabel(builder, sprites.getTurretGlowSprite(), "Turret glow sprite: ");
        addSpriteLabel(builder, sprites.getTurretUnderSprite(), "Turret under sprite: ");
        addSpriteLabel(builder, sprites.getHardpointSprite(), "Hardpoint sprite: ");
        addSpriteLabel(builder, sprites.getHardpointGunSprite(), "Hardpoint gun sprite: ");
        addSpriteLabel(builder, sprites.getHardpointGlowSprite(), "Hardpoint glow sprite: ");
        addSpriteLabel(builder, sprites.getHardpointUnderSprite(), "Hardpoint under sprite: ");
    }

    private static void addSpriteLabel(ComponentUtilities.InfoPanelBuilder builder, Sprite sprite, String description) {
        if (sprite != null) {
            builder.addWrappingPathLabel(description, sprite.getPath());
        }
    }

    private static JLabel createInstallableSlotsLabel(WeaponCSVEntry weapon) {
        WeaponType type = weapon.getType();
        List<String> installableIn = new java.util.ArrayList<>();
        if (type == WeaponType.BALLISTIC || type == WeaponType.HYBRID || type == WeaponType.COMPOSITE) {
            installableIn.add("Ballistic");
        }
        if (type == WeaponType.ENERGY || type == WeaponType.HYBRID || type == WeaponType.SYNERGY) {
            installableIn.add("Energy");
        }
        if (type == WeaponType.MISSILE || type == WeaponType.COMPOSITE || type == WeaponType.SYNERGY) {
            installableIn.add("Missile");
        }
        if (type == WeaponType.UNIVERSAL || (type != WeaponType.LAUNCH_BAY && type != WeaponType.BUILT_IN && type != WeaponType.DECORATIVE && type != WeaponType.SYSTEM && type != WeaponType.STATION_MODULE)) {
            installableIn.add("Universal");
        }
        if (type == WeaponType.SYNERGY || type == WeaponType.ENERGY || type == WeaponType.MISSILE) {
            installableIn.add("Synergy");
        }
        if (type == WeaponType.HYBRID || type == WeaponType.BALLISTIC || type == WeaponType.ENERGY) {
            installableIn.add("Hybrid");
        }
        if (type == WeaponType.COMPOSITE || type == WeaponType.BALLISTIC || type == WeaponType.MISSILE) {
            installableIn.add("Composite");
        }
        String text = installableIn.isEmpty() ? "Installable in slots: None" : "Installable in slots: " + String.join(", ", installableIn);
        return new JLabel(text);
    }

    @Override
    JPopupMenu getContextMenu() {
        JPopupMenu menu = super.getContextMenu();
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof IndexedFile) {
            int insertIndex = 0;
            var layer = StaticController.getActiveLayer();
            if (layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer) {
                var slotPainter = shipLayer.getPainter() != null ? shipLayer.getPainter().getWeaponSlotPainter() : null;
                var selectedSlot = slotPainter != null ? slotPainter.getSelected() : null;
                if (selectedSlot != null) {
                    JMenuItem installBuiltIn = new JMenuItem(StringManager.getString("INSTALL_AS_BUILT_IN_ON") + selectedSlot.getId());
                    installBuiltIn.addActionListener(new InstallAsBuiltInAction());
                    menu.insert(installBuiltIn, insertIndex++);

                    JMenuItem fitVariant = new JMenuItem(StringManager.getString("FIT_ON") + selectedSlot.getId() + " (Variant)");
                    fitVariant.addActionListener(new FitOnSlotAction());
                    menu.insert(fitVariant, insertIndex++);

                    menu.insert(new JPopupMenu.Separator(), insertIndex++);
                }
            }

            JMenuItem loadAsLayer = new JMenuItem(StringManager.getString("LOAD_AS_WEAPON_LAYER"));
            loadAsLayer.addActionListener(new LoadWeaponLayerFromTree());
            menu.insert(loadAsLayer, insertIndex++);
            menu.insert(new JPopupMenu.Separator(), insertIndex);
        }
        return menu;
    }

    private class InstallAsBuiltInAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof IndexedFile checked) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(checked) : null;
                if (entry != null) {
                    var layer = StaticController.getActiveLayer();
                    if (layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer) {
                        var slotPainter = shipLayer.getPainter() != null ? shipLayer.getPainter().getWeaponSlotPainter() : null;
                        var selectedSlot = slotPainter != null ? slotPainter.getSelected() : null;
                        if (selectedSlot != null) {
                            shipLayer.getFeaturesOverseer().installBuiltIn(selectedSlot, entry);
                        }
                    }
                }
            }
        }
    }

    private class FitOnSlotAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof IndexedFile checked) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(checked) : null;
                if (entry != null) {
                    var layer = StaticController.getActiveLayer();
                    if (layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer) {
                        var slotPainter = shipLayer.getPainter() != null ? shipLayer.getPainter().getWeaponSlotPainter() : null;
                        var selectedSlot = slotPainter != null ? slotPainter.getSelected() : null;
                        if (selectedSlot != null
                                && shipeditor.representation.weapon.WeaponEnums.WeaponType.isWeaponFitting(selectedSlot, entry)) {
                            if (StaticController.getEditorMode() == shipeditor.components.ComponentEnums.EditorInstrument.WEAPON_SLOTS
                                    && selectedSlot.isBuiltIn()) {
                                shipLayer.getFeaturesOverseer().installBuiltIn(selectedSlot, entry);
                            } else if (selectedSlot.isFittable()) {
                                shipLayer.getFeaturesOverseer().installWeapon(selectedSlot, entry);
                            }
                        }
                    }
                }
            }
        }
    }

    private class LoadWeaponLayerFromTree extends AbstractAction {
        @Override
        public boolean isEnabled() {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            return super.isEnabled() && cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof IndexedFile;
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof IndexedFile checked) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(checked) : null;
                if (entry != null) {
                    entry.loadLayerFromEntry();
                }
            }
        }
    }

    @Override
    protected void openEntryPath(shipeditor.components.ComponentEnums.OpenDataTarget target) {
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (cachedSelectForMenu == null || !(cachedSelectForMenu.getUserObject() instanceof IndexedFile checked))
            return;
        Path toOpen = checked.getFilePath();
        if (target == shipeditor.components.ComponentEnums.OpenDataTarget.CONTAINER && toOpen != null) {
            toOpen = toOpen.getParent();
        }
        if (toOpen != null) {
            FileUtilities.openPathInDesktop(toOpen);
        }
    }

    private class DoubleClickLayerLoader extends java.awt.event.MouseAdapter {
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if (e.getButton() != java.awt.event.MouseEvent.BUTTON1 || e.getClickCount() != 2) return;
            JTree tree = getTree();
            java.awt.Point eventPoint = e.getPoint();
            TreePath pathForLocation = tree.getPathForLocation(eventPoint.x, eventPoint.y);
            TreePath selectionPath = tree.getSelectionPath();
            TreePath targetPath = pathForLocation != null ? pathForLocation : selectionPath;
            if (targetPath == null) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) targetPath.getLastPathComponent();
            if (node.getUserObject() instanceof IndexedFile checked) {
                var gameData = SettingsManager.getGameData();
                WeaponCSVEntry entry = gameData != null ? gameData.getOrCreateWeaponEntry(checked) : null;
                if (entry != null) {
                    entry.loadLayerFromEntry();
                }
            }
        }
    }

    private static class SearchFieldDocumentListener implements DocumentListener {
        private final javax.swing.Timer timer;
        SearchFieldDocumentListener(javax.swing.Timer timer) { this.timer = timer; }
        @Override public void insertUpdate(DocumentEvent e) { timer.restart(); }
        @Override public void removeUpdate(DocumentEvent e) { timer.restart(); }
        @Override public void changedUpdate(DocumentEvent e) { timer.restart(); }
    }
}
