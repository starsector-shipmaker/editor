package shipeditor.components.datafiles.entities;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.text.StringConstants;
import javax.swing.JLabel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;

@Log4j2
@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class HullmodCSVEntry implements OrdnancedCSVEntry {

    private final Map<String, String> rowData;

    private final Path packageFolderPath;

    private final Path tableFilePath;

    private String hullmodID;

    private final String spriteFileName;

    private File fetchedSpriteFile;

    public HullmodCSVEntry(Map<String, String> row, Path folder, Path tablePath) {
        this.rowData = row;
        packageFolderPath = folder;
        this.tableFilePath = tablePath;
        hullmodID = this.rowData.get("id");
        spriteFileName = this.rowData.get(StringConstants.SPRITE);
    }

    @Override
    public String getID() {
        return hullmodID;
    }

    public void setHullmodID(String newID) {
        this.hullmodID = newID;
        this.rowData.put("id", newID);
    }

    @Override
    public String toString() {
        return CSVEntry.nameOrUntitled(rowData);
    }

    @Override
    public String getEntryName() {
        return toString();
    }

    @Override
    public String getMultilineTooltip() {
        String entryID = "Hullmod ID: " + this.getHullmodID();
        return Utility.getWithLinebreaks(entryID);
    }

    public String getName() {
        return toString();
    }

    public String getDescription() {
        String desc = rowData.get("desc");
        if (desc == null || desc.isBlank()) {
            desc = rowData.get("short_desc");
        }
        if (desc == null || desc.isBlank()) {
            desc = rowData.get("description");
        }
        return desc != null ? desc : "";
    }

    public String getTags() {
        String tags = rowData.get("tags");
        return tags != null ? tags : "";
    }

    public boolean isHidden() {
        if (rowData == null) {
            return false;
        }
        String hidden = rowData.get("hidden");
        String hiddenEverywhere = rowData.get("hiddenEverywhere");
        return (hidden != null && "TRUE".equalsIgnoreCase(hidden.trim()))
                || (hiddenEverywhere != null && "TRUE".equalsIgnoreCase(hiddenEverywhere.trim()));
    }

    public boolean isBuiltInOnly() {
        return isHidden();
    }

    public boolean isBuiltInOnShip(shipeditor.components.viewer.layers.ship.ShipLayer shipLayer) {
        if (shipLayer == null || hullmodID == null) {
            return false;
        }
        var painter = shipLayer.getPainter();
        var activeSkin = (painter != null && !painter.isUninitialized()) ? painter.getActiveSkin() : null;
        if (activeSkin != null && !activeSkin.isBase()) {
            if (containsById(activeSkin.getBuiltInMods(), hullmodID)) {
                return true;
            }
            if (containsById(activeSkin.getRemoveBuiltInMods(), hullmodID)) {
                return false;
            }
        }
        var hull = shipLayer.getHull();
        return hull != null && containsById(hull.getBuiltInMods(), hullmodID);
    }

    public boolean isFittedOnVariant(shipeditor.components.viewer.layers.ship.data.ShipVariant variant) {
        if (variant == null || variant.isEmpty() || hullmodID == null) {
            return false;
        }
        return containsById(variant.getHullMods(), hullmodID)
                || containsById(variant.getPermaMods(), hullmodID)
                || containsById(variant.getSMods(), hullmodID);
    }

    public boolean isFittableOnVariant(shipeditor.components.viewer.layers.ship.ShipLayer shipLayer) {
        if (isBuiltInOnly()) {
            return false;
        }
        return shipLayer == null || !isBuiltInOnShip(shipLayer);
    }

    private static boolean containsById(java.util.List<HullmodCSVEntry> list, String id) {
        if (list == null || id == null) {
            return false;
        }
        for (HullmodCSVEntry entry : list) {
            if (entry != null && id.equalsIgnoreCase(entry.getID())) {
                return true;
            }
        }
        return false;
    }

    private volatile BufferedImage cachedSpriteImage;
    private volatile boolean spriteLoaded = false;

    public BufferedImage getSpriteImage() {
        if (!spriteLoaded) {
            synchronized (this) {
                if (!spriteLoaded) {
                    try {
                        File imageFile = this.fetchHullmodSpriteFile();
                        cachedSpriteImage = FileLoading.loadSpriteAsImage(imageFile);
                    } catch (Exception e) {
                        cachedSpriteImage = null;
                    }
                    spriteLoaded = true;
                }
            }
        }
        return cachedSpriteImage;
    }

    @Override
    public int getOrdnanceCost(HullSize size) {
        var csvData = this.getRowData();
        String stringValue;
        switch (size) {
            case FRIGATE -> stringValue = csvData.get("cost_frigate");
            case DESTROYER -> stringValue = csvData.get("cost_dest");
            case CRUISER -> stringValue = csvData.get("cost_cruiser");
            case CAPITAL_SHIP -> stringValue = csvData.get("cost_capital");
            default -> stringValue = "0";
        }
        return Utility.parseIntegerOrDefault(stringValue, 0);
    }

    @Override
    public JLabel getIconLabel() {
        return getIconLabel(32);
    }

    private JLabel cachedIconLabel;
    private volatile boolean isIconLoading = false;

    @Override
    public JLabel getIconLabel(int maxSize) {
        if (cachedIconLabel != null && !isIconLoading) {
            return cachedIconLabel;
        }
        if (cachedIconLabel == null) {
            cachedIconLabel = new JLabel(StringManager.getString("EMPTY_STRING"));
        }
        if (!isIconLoading) {
            isIconLoading = true;
            Map<String, String> csvData = this.getRowData();
            String name = csvData.get("name");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    File imageFile = this.fetchHullmodSpriteFile();
                    BufferedImage iconImage = FileLoading.loadSpriteAsImage(imageFile);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        JLabel newLabel = ComponentUtilities.createIconFromImage(iconImage, name, maxSize);
                        ComponentUtilities.copyLabelAppearance(cachedIconLabel, newLabel);
                        isIconLoading = false;
                        shipeditor.communication.EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.WindowRepaintQueued());
                    });
                } catch (Exception ex) {
                    log.error("Failed to load hullmod icon: " + name, ex);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        cachedIconLabel.setText(StringManager.getString("EMPTY_STRING_1"));
                        if (name != null && !name.isEmpty()) {
                            cachedIconLabel.setToolTipText(name);
                        }
                        isIconLoading = false;
                        shipeditor.communication.EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.WindowRepaintQueued());
                    });
                }
            });
        }
        return cachedIconLabel;
    }

    private File fetchHullmodSpriteFile() {
        if (fetchedSpriteFile != null) {
            return fetchedSpriteFile;
        }
        String path = this.spriteFileName;
        if (path == null || path.isEmpty()) {
            // Fallback sprite with question mark.
            path = "graphics/icons/intel/investigation.png";
        }
        Path spritePath = Path.of(path);
        fetchedSpriteFile = FileLoading.fetchDataFile(spritePath, packageFolderPath);
        return fetchedSpriteFile;
    }

}
