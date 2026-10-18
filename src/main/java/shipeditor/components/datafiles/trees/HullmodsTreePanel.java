package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullmodFoldersWalked;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Log4j2
public
class HullmodsTreePanel extends CSVDataTreePanel<HullmodCSVEntry>{

    public HullmodsTreePanel() {
        super("Hullmod file packages");
    }

    @Override
    protected String getEntryTypeName() {
        return "hullmod";
    }

    @Override
    protected Map<String, HullmodCSVEntry> getRepository() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getAllHullmodEntries();
    }

    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isHullmodDataLoaded();
    }

    @Override
    protected Map<Path, List<HullmodCSVEntry>> getPackageList() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getHullmodEntriesByPackage();
    }

    @Override
    protected void setLoadedStatus() {
        GameDataRepository gameData = SettingsManager.getGameData();
        gameData.setHullmodDataLoaded(true);
    }

    @Override
    protected void initWalkerListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof HullmodFoldersWalked) {
                this.queueReload();
            }
        });
    }

    protected void updateEntryPanel(HullmodCSVEntry selected) {
        JPanel infoPanel = DataTreeSupport.prepareInfoPanel(getLeftInfoPanel());

        ComponentUtilities.InfoPanelBuilder builder = new ComponentUtilities.InfoPanelBuilder("Hullmod Info");

        String spriteFileName = selected.getSpriteFileName();
        if (spriteFileName != null && !spriteFileName.isEmpty()) {
            JPanel hullmodIconPanel = ComponentUtilities.createCenteredPanel(selected.getIconLabel());
            builder.addCustomComponent(hullmodIconPanel);
            
            java.io.File spriteFile = shipeditor.parsing.loading.FileLoading.fetchDataFile(java.nio.file.Path.of(spriteFileName), selected.getPackageFolderPath());
            if (spriteFile != null) {
                builder.addWrappingPathLabel("Sprite file: ", spriteFile.toPath());
            } else {
                builder.addWrappingPathLabel("Sprite path: ", java.nio.file.Path.of(spriteFileName));
            }
        }
        
        String script = selected.getRowData().get("script");
        if (script != null && !script.isEmpty()) {
            builder.addWrappingPathLabel("Script class: ", java.nio.file.Path.of(script));
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
        if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof HullmodCSVEntry checked) {
            EditorInstrument mode = StaticController.getEditorMode();
            if (mode == EditorInstrument.BUILT_IN_MODS) {
                menu.addSeparator();
                populateBuiltInOptions(menu, checked);
            }
            if (DataTreeSupport.isLayerEligibleForVariant(StaticController.getActiveLayer())
                    || (mode != null && mode.name().startsWith("VARIANT"))) {
                menu.addSeparator();
                populateVariantOptions(menu, checked);
            }
        }
        return menu;
    }

    private void populateVariantOptions(JPopupMenu menu, HullmodCSVEntry entry) {
        ViewerLayer activeLayer = StaticController.getActiveLayer();

        JMenuItem addToNormal = createVariantOption("Add to variant hullmods",
                activeLayer, entry, variant -> variant.getHullMods());
        menu.add(addToNormal);

        JMenuItem addToPermanent = createVariantOption("Add to variant perma-mods",
                activeLayer, entry, variant -> variant.getPermaMods());
        menu.add(addToPermanent);

        JMenuItem addToSMods = createVariantOption("Add to variant S-mods",
                activeLayer, entry, variant -> variant.getSMods());
        menu.add(addToSMods);
    }

    private JMenuItem createVariantOption(String text, ViewerLayer activeLayer,
                                          HullmodCSVEntry entry, Function<ShipVariant, List<HullmodCSVEntry>> getter) {
        JMenuItem option = new JMenuItem(text);
        option.addActionListener(e -> {
            var modsList = HullmodsTreePanel.getOrCreateVariantMods(activeLayer, getter);
            if (modsList == null) return;
            commenceModAddition(modsList, (ShipLayer) activeLayer, entry);
        });
        if (!DataTreeSupport.isLayerEligibleForVariant(activeLayer)) {
            option.setEnabled(false);
        }
        return option;
    }

    private static List<HullmodCSVEntry> getOrCreateVariantMods(
            ViewerLayer activeLayer, Function<ShipVariant, List<HullmodCSVEntry>> getter) {
        if (activeLayer instanceof ShipLayer checkedLayer) {
            ShipPainter painter = checkedLayer.getPainter();
            if (painter == null || painter.isUninitialized()) {
                return null;
            }
            return getter.apply(DataTreeSupport.getOrCreateActiveVariant(checkedLayer, painter));
        }
        return null;
    }

    private void populateBuiltInOptions(JPopupMenu menu, HullmodCSVEntry entry) {
        EditorInstrument targetMode = EditorInstrument.BUILT_IN_MODS;
        JMenuItem addToHullBuiltIns = new JMenuItem(StringManager.getString("ADD_TO_HULL_BUILT_INS"));
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        addToHullBuiltIns.addActionListener(e -> {
            if (activeLayer instanceof ShipLayer checkedLayer) {
                ShipHull hull = checkedLayer.getHull();
                if (hull == null) return;
                var builtInMods = hull.getBuiltInMods();
                if (builtInMods == null) return;
                commenceModAddition(builtInMods, checkedLayer, entry);
            }
        });
        if (!HullmodsTreePanel.isCurrentLayerDataEligible() || DataTreeSupport.isNotActiveInstrument(targetMode)) {
            addToHullBuiltIns.setEnabled(false);
        }
        menu.add(addToHullBuiltIns);

        JMenuItem addToSkinAdded = createSkinAddedOption(entry, activeLayer, targetMode);
        menu.add(addToSkinAdded);

        JMenuItem addToSkinRemoved = createSkinRemovedOption(entry, activeLayer, targetMode);
        menu.add(addToSkinRemoved);
    }

    private JMenuItem createSkinRemovedOption(HullmodCSVEntry entry,
                                              ViewerLayer activeLayer, EditorInstrument targetMode) {
        return createSkinModsOption(StringManager.getString("ADD_TO_SKIN_BUILT_IN_REMOVALS"),
                entry, activeLayer, targetMode, ShipSkin::getRemoveBuiltInMods);
    }

    private JMenuItem createSkinAddedOption(HullmodCSVEntry entry,
                                            ViewerLayer activeLayer, EditorInstrument targetMode) {
        return createSkinModsOption(StringManager.getString("ADD_TO_SKIN_BUILT_INS"),
                entry, activeLayer, targetMode, ShipSkin::getBuiltInMods);
    }

    private JMenuItem createSkinModsOption(String text, HullmodCSVEntry entry, ViewerLayer activeLayer,
                                           EditorInstrument targetMode,
                                           Function<ShipSkin, List<HullmodCSVEntry>> getter) {
        JMenuItem option = new JMenuItem(text);
        option.addActionListener(e -> {
            if (activeLayer instanceof ShipLayer checkedLayer) {

                ShipPainter shipPainter = checkedLayer.getPainter();
                if (shipPainter == null || shipPainter.isUninitialized()) return;
                var skin = shipPainter.getActiveSkin();
                if (skin == null || skin.isBase()) return;

                commenceModAddition(getter.apply(skin), checkedLayer, entry);
            }
        });
        if (DataTreePanel.isCurrentSkinNotEligible() || DataTreeSupport.isNotActiveInstrument(targetMode)) {
            option.setEnabled(false);
        }
        return option;
    }

    private void commenceModAddition(List<HullmodCSVEntry> list, ShipLayer shipLayer, HullmodCSVEntry entry) {
        if (this.isPushEntryToListSuccessful(list, shipLayer, entry)) {
            EventBus.publish(new ActiveLayerUpdated(shipLayer));
        }
    }

    private boolean isPushEntryToListSuccessful(List<HullmodCSVEntry> list, ShipLayer layer,
                                                HullmodCSVEntry entry) {
        if (list.contains(entry)) {
            JOptionPane.showMessageDialog(this,
                    StringManager.getString("CANNOT_ADD_HULLMOD_ENTRY_ALREADY_PRESENT_MSG"));
            return false;
        } else {
            EditDispatch.postHullmodAdded(list, layer, entry);
            return true;
        }
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    private static boolean isCurrentLayerDataEligible() {
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        boolean isShipLayer = activeLayer instanceof ShipLayer;
        ShipLayer shipLayer;
        if (isShipLayer) {
            shipLayer = (ShipLayer) activeLayer;
        } else return false;
        ShipHull hull = shipLayer.getHull();
        return hull != null && hull.getBuiltInMods() != null;
    }

    @Override
    protected Class<?> getEntryClass() {
        return HullmodCSVEntry.class;
    }

}
