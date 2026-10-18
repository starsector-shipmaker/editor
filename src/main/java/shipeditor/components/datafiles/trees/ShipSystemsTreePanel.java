package shipeditor.components.datafiles.trees;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.ShipSystemsLoaded;
import shipeditor.components.datafiles.entities.ShipSystemCSVEntry;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;

import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Log4j2
public
class ShipSystemsTreePanel extends CSVDataTreePanel<ShipSystemCSVEntry>{

    public ShipSystemsTreePanel() {
        super("Shipsystem file packages");
    }

    @Override
    protected String getEntryTypeName() {
        return "shipsystem";
    }

    @Override
    protected Map<String, ShipSystemCSVEntry> getRepository() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getAllShipsystemEntries();
    }

    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isShipsystemDataLoaded();
    }

    @Override
    protected Map<Path, List<ShipSystemCSVEntry>> getPackageList() {
        GameDataRepository gameData = SettingsManager.getGameData();
        return gameData.getShipSystemEntriesByPackage();
    }

    @Override
    protected void setLoadedStatus() {
        GameDataRepository gameData = SettingsManager.getGameData();
        gameData.setShipsystemDataLoaded(true);
    }

    @Override
    protected void initWalkerListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof ShipSystemsLoaded) {
                this.queueReload();
            }
        });
    }

    @Override
    protected void updateEntryPanel(ShipSystemCSVEntry selected) {
        JPanel leftPanel = DataTreeSupport.prepareInfoPanel(getLeftInfoPanel());

        shipeditor.utility.components.ComponentUtilities.InfoPanelBuilder builder = new shipeditor.utility.components.ComponentUtilities.InfoPanelBuilder("Shipsystem Info");
        
        String iconPath = selected.getRowData().get("icon");
        if (iconPath != null && !iconPath.isEmpty()) {
            java.io.File iconFile = shipeditor.parsing.loading.FileLoading.fetchDataFile(Path.of(iconPath), selected.getPackageFolderPath());
            if (iconFile != null) {
                try {
                    java.awt.image.BufferedImage img = shipeditor.parsing.loading.FileLoading.loadSpriteAsImage(iconFile);
                    javax.swing.JLabel iconLabel = shipeditor.utility.components.ComponentUtilities.createIconFromImage(img, "Icon", 128);
                    builder.addCustomComponent(iconLabel);
                } catch (Exception ex) {
                    log.error("Failed to load icon for ship system", ex);
                }
                builder.addWrappingPathLabel("Icon file: ", iconFile.toPath());
            } else {
                builder.addWrappingPathLabel("Icon path: ", Path.of(iconPath));
            }
        }
        
        leftPanel.add(builder.getPanel());
        leftPanel.add(javax.swing.Box.createVerticalStrut(20));

        leftPanel.revalidate();
        leftPanel.repaint();

        createRightPanelDataTable(selected);
    }

    @Override
    protected Class<?> getEntryClass() {
        return ShipSystemCSVEntry.class;
    }

}
