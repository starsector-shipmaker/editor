package shipeditor.components.datafiles.entities;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.*;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.representation.ship.ShipSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.text.StringConstants;
import javax.swing.JLabel;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;

@Log4j2
@Getter
@SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP" })
public class WingCSVEntry implements OrdnancedCSVEntry {

    private final Map<String, String> rowData;

    private final Path packageFolderPath;

    private final Path tableFilePath;

    private String wingID;

    @Setter
    private String displayedName;

    @Getter
    private ShipSpecFile wingMemberSpec;

    private Sprite memberSprite;

    public WingCSVEntry(Map<String, String> row, Path folder, Path tablePath) {
        this.rowData = row;
        packageFolderPath = folder;
        this.tableFilePath = tablePath;
        wingID = this.rowData.get("id");
    }

    @Override
    public String getID() {
        return wingID;
    }

    public void setWingID(String newID) {
        this.wingID = newID;
        this.rowData.put("id", newID);
    }

    @Override
    public String toString() {
        String name = rowData.get(StringConstants.ID);
        if (name.isEmpty()) {
            name = StringManager.getString("UNTITLED");
        }
        return name;
    }

    public VariantFile retrieveMemberVariant() {
        String variantID = rowData.get(StringConstants.VARIANT);
        var gameData = SettingsManager.getGameData();
        var allVariants = gameData.getAllVariants();
        return allVariants.get(variantID);
    }

    @Override
    public String getMultilineTooltip() {
        String entryID = "Wing ID: " + this.getWingID();
        return Utility.getWithLinebreaks(entryID);
    }

    private ShipSpecFile retrieveSpec() {
        VariantFile variantFile = retrieveMemberVariant();
        if (variantFile == null) {
            return null;
        }

        String hullID = variantFile.getHullId();
        ShipSpecFile desiredSpec = GameDataRepository.retrieveSpecByID(hullID);

        this.wingMemberSpec = desiredSpec;
        return desiredSpec;
    }

    private Sprite getWingMemberSprite() {
        if (this.memberSprite != null) {
            return this.memberSprite;
        }

        ShipSpecFile specFile;
        if (this.wingMemberSpec == null) {
            specFile = this.retrieveSpec();
        } else {
            specFile = this.wingMemberSpec;
        }

        if (specFile != null) {
            String spriteName = specFile.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                log.warn("Wing member sprite loading warning: spriteName is null or empty for: " + this.wingID);
                return null;
            }
            Path of = Path.of(spriteName);
            File spriteFile = FileLoading.fetchDataFile(of, packageFolderPath);
            Sprite result = FileLoading.loadSprite(spriteFile);
            this.memberSprite = result;
            return result;
        } else {
            log.warn("Wing member sprite loading failed, specFile is null for: " + this.wingID);
            return null;
        }
    }

    /**
     * @param size irrelevant, should be null.
     */
    @Override
    public int getOrdnanceCost(HullSize size) {
        String tableValue = this.rowData.get("op cost");
        return Utility.parseIntegerOrDefault(tableValue, 0);
    }

    @Override
    public String getEntryName() {
        if (this.displayedName != null && !this.displayedName.isBlank()) {
            return this.displayedName;
        }

        ShipSpecFile specFile;
        if (this.wingMemberSpec == null) {
            specFile = this.retrieveSpec();
        } else {
            specFile = this.wingMemberSpec;
        }

        String result = null;
        VariantFile variant = this.retrieveMemberVariant();

        if (variant != null && variant.getShipHullId() != null) {
            ShipCSVEntry entry = GameDataRepository.retrieveShipCSVEntryByID(variant.getShipHullId());
            if (entry != null) {
                result = entry.getShipName();
                if (result == null || result.isBlank()) {
                    result = entry.toString();
                }
            }
            if ((result == null || result.isBlank()) && specFile != null) {
                result = specFile.getHullName();
            }
            if (result == null || result.isBlank()) {
                result = variant.getShipHullId();
            }

            String drone = "Drone";
            String displayName = variant.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                if (result != null && !result.isBlank() && !(result.endsWith(drone) && displayName.equals(drone))) {
                    if (!result.endsWith(displayName)) {
                        result = result + " " + displayName;
                    }
                } else if (result == null || result.isBlank()) {
                    result = displayName;
                }
            }
        }

        if (result == null || result.isBlank()) {
            if (specFile != null && specFile.getHullName() != null && !specFile.getHullName().isBlank()) {
                result = specFile.getHullName();
            }
        }

        if (result == null || result.isBlank()) {
            result = this.getWingID();
        }

        if (result == null || result.isBlank()) {
            result = StringManager.getString("UNTITLED");
        }

        this.setDisplayedName(result);
        return result;
    }

    @Override
    public JLabel getIconLabel() {
        return getIconLabel(32);
    }

    private JLabel cachedIconLabel;
    private boolean isIconLoading = false;

    @Override
    public JLabel getIconLabel(int maxSize) {
        if (cachedIconLabel != null && !isIconLoading) {
            return cachedIconLabel;
        }
        if (cachedIconLabel == null) {
            cachedIconLabel = new JLabel(StringManager.getString("EMPTY_STRING"));
        }
        if (!isIconLoading) {
            if (!SettingsManager.getGameData().isShipDataLoaded()) {
                return cachedIconLabel;
            }
            isIconLoading = true;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Sprite sprite = this.getWingMemberSprite();
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        if (sprite != null) {
                            String tooltip = Utility.getTooltipForSprite(sprite);
                            cachedIconLabel = ComponentUtilities.createIconFromImage(sprite.getImage(), tooltip, maxSize);
                        } else {
                            cachedIconLabel = new JLabel(StringManager.getString("EMPTY_STRING_1"));
                        }
                        isIconLoading = false;
                        shipeditor.communication.EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.WindowRepaintQueued());
                    });
                } catch (Exception ex) {
                    log.error("Failed to load wing icon for: " + this.wingID, ex);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        cachedIconLabel = new JLabel(StringManager.getString("EMPTY_STRING_1"));
                        isIconLoading = false;
                        shipeditor.communication.EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.WindowRepaintQueued());
                    });
                }
            });
        }
        return cachedIconLabel;
    }

}
