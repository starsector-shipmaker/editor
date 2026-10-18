package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.WingDataLoaded;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.VariantFile;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WingsTreePanel extends CSVDataTreePanel<WingCSVEntry>{

    public WingsTreePanel() {
        super("Wing entry packages");
    }

    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isWingDataLoaded();
    }

    @Override
    protected JTree createCustomTree() {
        JTree custom = super.createCustomTree();
        custom.setCellRenderer(new WingsTreeCellRenderer());
        return custom;
    }

    @Override
    protected String getEntryTypeName() {
        return "wing";
    }

    @Override
    protected Map<String, WingCSVEntry> getRepository() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getAllWingEntries();
    }

    @Override
    protected Map<Path, List<WingCSVEntry>> getPackageList() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getWingEntriesByPackage();
    }

    @Override
    protected void setLoadedStatus() {
        GameDataRepository gameData = SettingsManager.getGameData();
        gameData.setWingDataLoaded(true);
    }

    @Override
    protected void initTreePanelListeners(JPanel passedTreePanel) {
        super.initTreePanelListeners(passedTreePanel);
        JTree tree = getTree();
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                DefaultMutableTreeNode node = DataTreeSupport.getDoubleClickedNode(tree, e);
                if (node == null) {
                    return;
                }
                WingCSVEntry entry = getObjectFromNode(node);
                if (entry != null) {
                    WingsTreePanel.handleDoubleClickOnWing(entry);
                }
            }
        });
    }

    @Override
    protected void initWalkerListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof WingDataLoaded) {
                this.queueReload();
            }
        });
    }

    private static void handleDoubleClickOnWing(WingCSVEntry entry) {
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        if (!(activeLayer instanceof ShipLayer checkedLayer)) {
            return;
        }
        EditorInstrument mode = StaticController.getEditorMode();
        if (mode == EditorInstrument.BUILT_IN_WINGS) {
            var skin = checkedLayer.getActiveSkin();
            if (skin != null && !skin.isBase()) {
                var skinAdded = skin.getBuiltInWings();
                if (skinAdded != null && WingsTreePanel.isPushEntryToListSuccessful(skinAdded, checkedLayer, entry)) {
                    EventBus.publish(new ActiveLayerUpdated(checkedLayer));
                }
            } else {
                ShipHull hull = checkedLayer.getHull();
                if (hull != null && hull.getBuiltInWings() != null) {
                    if (WingsTreePanel.isPushEntryToListSuccessful(hull.getBuiltInWings(), checkedLayer, entry)) {
                        EventBus.publish(new ActiveLayerUpdated(checkedLayer));
                    }
                }
            }
        } else if (mode != null && ("VARIANT_WINGS".equals(mode.name()) || mode == EditorInstrument.VARIANT_DATA)) {
            WingsTreePanel.addWingToVariant(checkedLayer, entry);
        }
    }

    @Override
    protected void updateEntryPanel(WingCSVEntry selected) {
        JPanel infoPanel = DataTreeSupport.prepareInfoPanel(getLeftInfoPanel());

        ComponentUtilities.InfoPanelBuilder builder = new ComponentUtilities.InfoPanelBuilder("Wing Info");

        JLabel spriteIcon = selected.getIconLabel(128);
        if (spriteIcon != null) {
            builder.addCustomComponent(ComponentUtilities.createCenteredPanel(spriteIcon));
        }

        VariantFile variantFile = selected.retrieveMemberVariant();
        if (variantFile != null) {
            if (variantFile.getVariantFilePath() != null) {
                builder.addWrappingPathLabel("Variant file: ", variantFile.getVariantFilePath());
            }

            JPanel variantWrapper = new JPanel();
            variantWrapper.setLayout(new BoxLayout(variantWrapper, BoxLayout.PAGE_AXIS));

            List<VariantFile> memberVariantFile = Collections.singletonList(variantFile);
            JPanel variantPanel = DataTreeVariantPanelBuilder.createVariantsPanel(memberVariantFile, false);
            variantWrapper.add(variantPanel);

            builder.addCustomComponent(variantWrapper);
        }

        infoPanel.add(builder.getPanel());
        infoPanel.add(javax.swing.Box.createVerticalStrut(20));

        createRightPanelDataTable(selected);
        
        infoPanel.revalidate();
        infoPanel.repaint();
    }

    @Override
    JPopupMenu getContextMenu() {
        JPopupMenu menu = super.getContextMenu();
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof WingCSVEntry checked) {
            EditorInstrument mode = StaticController.getEditorMode();
            if (mode == EditorInstrument.BUILT_IN_WINGS) {
                menu.addSeparator();
                WingsTreePanel.populateBuiltInOptions(menu, checked);
            }
            if (WingsTreePanel.isCurrentLayerVariantEligible()
                    || (mode != null && mode.name().startsWith("VARIANT"))) {
                menu.addSeparator();
                WingsTreePanel.populateVariantOptions(menu, checked);
            }
        }
        return menu;
    }

    private static void populateVariantOptions(JPopupMenu menu, WingCSVEntry entry) {
        JMenuItem addToVariantWings = new JMenuItem(StringManager.getString("ADD_TO_VARIANT_WINGS"));
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        addToVariantWings.addActionListener(e -> {
            if (activeLayer instanceof ShipLayer checkedLayer) {
                WingsTreePanel.addWingToVariant(checkedLayer, entry);
            }
        });
        if (!WingsTreePanel.isCurrentLayerVariantEligible()) {
            addToVariantWings.setEnabled(false);
        }
        menu.add(addToVariantWings);
    }

    private static void addWingToVariant(ShipLayer checkedLayer, WingCSVEntry entry) {
        ShipPainter painter = checkedLayer.getPainter();
        if (painter == null || painter.isUninitialized()) {
            return;
        }
        var variant = DataTreeSupport.getOrCreateActiveVariant(checkedLayer, painter);
        var variantWings = variant.getWings();
        if (variantWings != null && WingsTreePanel.isPushEntryToListSuccessful(variantWings, checkedLayer, entry)) {
            EventBus.publish(new ActiveLayerUpdated(checkedLayer));
        }
    }

    private static void populateBuiltInOptions(JPopupMenu menu, WingCSVEntry entry) {
        EditorInstrument targetMode = EditorInstrument.BUILT_IN_WINGS;

        JMenuItem addToHullBuiltIns = new JMenuItem(StringManager.getString("ADD_TO_HULL_BUILT_IN_WINGS"));
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        addToHullBuiltIns.addActionListener(e -> {
            if (activeLayer instanceof ShipLayer checkedLayer) {
                ShipHull hull = checkedLayer.getHull();
                if (hull == null) return;
                var builtInWings = hull.getBuiltInWings();
                if (builtInWings == null) return;
                if (WingsTreePanel.isPushEntryToListSuccessful(builtInWings, checkedLayer, entry)) {
                    EventBus.publish(new ActiveLayerUpdated(activeLayer));
                }
            }
        });
        if (!WingsTreePanel.areCurrentLayerBuiltInsEligible() || DataTreeSupport.isNotActiveInstrument(targetMode)) {
            addToHullBuiltIns.setEnabled(false);
        }
        menu.add(addToHullBuiltIns);

        JMenuItem addToSkinAdded = WingsTreePanel.getAddToSkinAdded(entry, activeLayer);
        menu.add(addToSkinAdded);
    }

    private static JMenuItem getAddToSkinAdded(WingCSVEntry checked, ViewerLayer activeLayer) {
        EditorInstrument targetMode = EditorInstrument.BUILT_IN_WINGS;
        JMenuItem addToSkinAdded = new JMenuItem(StringManager.getString("ADD_TO_SKIN_BUILT_IN_WINGS"));
        addToSkinAdded.addActionListener(e -> {
            if (activeLayer instanceof ShipLayer checkedLayer) {
                var skin = checkedLayer.getActiveSkin();
                if (skin == null) return;
                var skinAdded = skin.getBuiltInWings();
                if (WingsTreePanel.isPushEntryToListSuccessful(skinAdded, checkedLayer, checked)) {
                    EventBus.publish(new ActiveLayerUpdated(activeLayer));
                }
            }
        });
        if (DataTreePanel.isCurrentSkinNotEligible() || DataTreeSupport.isNotActiveInstrument(targetMode)) {
            addToSkinAdded.setEnabled(false);
        }
        return addToSkinAdded;
    }

    private static boolean isPushEntryToListSuccessful(List<WingCSVEntry> list, ShipLayer layer,
                                                       WingCSVEntry entry) {
        EditDispatch.postWingAdded(list, layer, entry);
        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean areCurrentLayerBuiltInsEligible() {
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        boolean isShipLayer = activeLayer instanceof ShipLayer;
        ShipLayer shipLayer;
        if (isShipLayer) {
            shipLayer = (ShipLayer) activeLayer;
        } else return false;
        ShipHull hull = shipLayer.getHull();
        return hull != null && hull.getBuiltInWings() != null;
    }

    private static boolean isCurrentLayerVariantEligible() {
        return DataTreeSupport.isLayerEligibleForVariant(StaticController.getActiveLayer());
    }

    @Override
    protected Class<?> getEntryClass() {
        return WingCSVEntry.class;
    }

    private static class WingsTreeCellRenderer extends TreeEntryRenderer {

        @SuppressWarnings("ChainOfInstanceofChecks")
        @Override
        protected void customizeEntry(Object object, boolean leaf) {
            if (object instanceof WingCSVEntry checked) {
                String name = checked.getEntryName();
                if (name == null || name.isBlank()) {
                    name = checked.getWingID();
                }
                if (name == null || name.isBlank()) {
                    name = checked.toString();
                }
                setText(name);
            } else if (object instanceof GameDataPackage dataPackage) {
                setText(dataPackage.getFolderName());
            }
        }

    }

}
