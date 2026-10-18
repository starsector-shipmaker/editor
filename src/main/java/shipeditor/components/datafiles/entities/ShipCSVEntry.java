package shipeditor.components.datafiles.entities;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.RepresentationEnums.ShipTypeHints;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.DrawUtilities;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.text.StringConstants;
import shipeditor.persistence.SettingsManager;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.persistence.database.IndexedFile;

import javax.swing.JOptionPane;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShipCSVEntry implements LayerableEntry, InstallableEntry {

    @Getter
    private final Map<String, String> rowData;

    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter
    private HullSpecFile hullSpecFile;

    /**
     * Keys are simple names of skin files, e.g.: legion_xiv.skin.
     */
    @lombok.Getter(lombok.AccessLevel.NONE)
    private Map<String, SkinSpecFile> skins;

    @Getter
    private SkinSpecFile activeSkinSpecFile;

    @Getter
    private final String hullFileName;

    @Getter
    private String hullID;

    @Getter
    private final Path packageFolderPath;

    private final Path tableFilePath;

    private Sprite entrySprite;

    private List<ShipTypeHints> cachedBaseHullHints;

    private HullSize cachedHullSize;

    private String fallbackHullName;

    public ShipCSVEntry(Map<String, String> row, Map.Entry<HullSpecFile, Map<String, SkinSpecFile>> hullWithSkins,
                        Path folder, String fileName) {
        this(row, hullWithSkins, folder, fileName, null);
    }

    public ShipCSVEntry(Map<String, String> row, Map.Entry<HullSpecFile, Map<String, SkinSpecFile>> hullWithSkins,
                        Path folder, String fileName, Path tablePath) {
        this.packageFolderPath = folder;
        this.hullSpecFile = hullWithSkins != null ? hullWithSkins.getKey() : null;
        this.skins = hullWithSkins != null ? hullWithSkins.getValue() : null;
        this.rowData = row;
        this.hullID = row.get(StringConstants.ID);
        this.hullFileName = fileName;
        this.tableFilePath = tablePath;
        this.activeSkinSpecFile = SkinSpecFile.empty();
        if (this.skins != null) {
            this.skins.put(SkinSpecFile.DEFAULT, activeSkinSpecFile);
        }
    }

    public void enrichFromIndexedFile(IndexedFile file) {
        if (file == null) {
            return;
        }
        if ((fallbackHullName == null || fallbackHullName.isBlank())
                && file.getEntityName() != null && !file.getEntityName().isBlank()) {
            this.fallbackHullName = file.getEntityName();
        }
        if (cachedHullSize == null && file.getMetadataJson() != null && !file.getMetadataJson().isBlank()) {
            try {
                this.cachedHullSize = HullSize.valueOf(file.getMetadataJson().trim());
            } catch (IllegalArgumentException ignored) {
                // Unknown hull size string in metadata; ignore.
            }
        }
    }

    public HullSpecFile getHullSpecFile() {
        if (hullSpecFile == null) {
            lazyLoadSpecAndSkins();
        }
        return hullSpecFile;
    }

    public Map<String, SkinSpecFile> getSkins() {
        if (skins == null) {
            lazyLoadSpecAndSkins();
        }
        return skins;
    }

    private java.util.concurrent.CompletableFuture<Void> loadingFuture;

    public synchronized java.util.concurrent.CompletableFuture<Void> lazyLoadSpecAndSkins() {
        if (loadingFuture != null) return loadingFuture;
        if (hullSpecFile != null && skins != null) return java.util.concurrent.CompletableFuture.completedFuture(null);

        log.debug("Lazily loading spec and skins for base hull ID: {}", hullID);
        Path hullPath = DatabaseQueryService.getFilePathForEntity(this.hullID, StringConstants.SHIP_TYPE);
        if (hullPath != null) {
            this.hullSpecFile = FileLoading.loadHullFile(hullPath.toFile());
        } else {
            log.error("Failed to locate hull file path for ID: {}", this.hullID);
        }

        this.skins = new java.util.concurrent.ConcurrentHashMap<>();
        this.activeSkinSpecFile = SkinSpecFile.empty();
        this.skins.put(SkinSpecFile.DEFAULT, this.activeSkinSpecFile);

        Path fileNamePath = this.packageFolderPath != null ? this.packageFolderPath.getFileName() : null;
        String modId = fileNamePath != null ? fileNamePath.toString() : "";
        if (this.packageFolderPath != null && SettingsManager.isCoreFolder(this.packageFolderPath)) {
            modId = "starsector-core";
        }

        loadingFuture = DatabaseQueryService.getFilesByModAndTypeAsync(modId, StringConstants.SKIN_TYPE).thenAcceptAsync(skinFiles -> {
            for (IndexedFile skinFile : skinFiles) {
                String indexedBaseHullId = skinFile.getMetadataJson();
                if (indexedBaseHullId != null && !indexedBaseHullId.isEmpty()
                        && !Objects.equals(indexedBaseHullId, this.hullID)) {
                    continue;
                }
                SkinSpecFile skinSpec = FileLoading.loadSkinFile(skinFile.getFilePath().toFile());
                if (skinSpec != null && Objects.equals(skinSpec.getBaseHullId(), this.hullID)) {
                    this.skins.put(skinFile.getFileName(), skinSpec);
                }
            }
        });
        return loadingFuture;
    }

    @SuppressWarnings("WeakerAccess")
    public List<ShipTypeHints> getBaseHullHints() {
        if (cachedBaseHullHints != null) {
            return cachedBaseHullHints;
        }
        List<ShipTypeHints> result = new ArrayList<>();
        String cellData = rowData.get(StringConstants.HINTS);
        if (cellData != null && !cellData.isEmpty()) {
            Iterable<String> hintsText = new ArrayList<>(Arrays.asList(Utility.SPLIT_BY_COMMA.split(cellData)));
            hintsText.forEach(hintText -> {
                try {
                    ShipTypeHints typeHint = ShipTypeHints.valueOf(hintText.trim());
                    result.add(typeHint);
                } catch (IllegalArgumentException ignored) {
                    // Unknown hint from mod data; skip silently.
                }
            });
        }
        cachedBaseHullHints = result;
        return result;
    }

    public HullSize getSize() {
        if (hullSpecFile != null) {
            String hullSize = hullSpecFile.getHullSize();
            if (hullSize == null) {
                return HullSize.DEFAULT;
            }
            try {
                return HullSize.valueOf(hullSize);
            } catch (IllegalArgumentException e) {
                return HullSize.DEFAULT;
            }
        }
        if (cachedHullSize != null) {
            return cachedHullSize;
        }
        if (hullID != null) {
            IndexedFile indexed = DatabaseQueryService.getFileByEntityId(hullID, StringConstants.SHIP_TYPE);
            if (indexed != null) {
                enrichFromIndexedFile(indexed);
                if (cachedHullSize != null) {
                    return cachedHullSize;
                }
                if (indexed.getFilePath() != null) {
                    var meta = shipeditor.parsing.loading.IndexScannerTask.extractEntityMetadata(
                            indexed.getFilePath().toFile(), StringConstants.SHIP_TYPE, FileUtilities.getConfigured());
                    if (meta.metaValue() != null && !meta.metaValue().isBlank()) {
                        try {
                            cachedHullSize = HullSize.valueOf(meta.metaValue().trim());
                            return cachedHullSize;
                        } catch (IllegalArgumentException ignored) {
                            // Fall through to DEFAULT.
                        }
                    }
                }
            }
        }
        cachedHullSize = HullSize.DEFAULT;
        return cachedHullSize;
    }

    public String getShipID() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            return activeSkinSpecFile.getSkinHullId();
        }
        return getHullID();
    }

    @Override
    public String getMultilineTooltip() {
        return this.getMultilineTooltip(new String[0]);
    }

    @SuppressWarnings("OverloadedVarargsMethod")
    public String getMultilineTooltip(String... additional) {
        List<String> lines = new ArrayList<>(2);
        String entryID = "Hull ID: " + this.getHullID();
        String size =  "Hull size: " +  this.getSize();
        lines.add(entryID);
        lines.add(size);
        if (additional != null && additional.length > 0) {
            lines.addAll(List.of(additional));
        }
        return Utility.getWithLinebreaks(lines.toArray(new String[0]));
    }

    public String getShipName() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            String skinName = activeSkinSpecFile.getHullName();
            if (skinName != null && !skinName.isBlank()) {
                return skinName;
            }
        }
        String name = rowData.get(StringConstants.NAME);
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (hullSpecFile != null && hullSpecFile.getHullName() != null && !hullSpecFile.getHullName().isBlank()) {
            return hullSpecFile.getHullName();
        }
        if (fallbackHullName != null && !fallbackHullName.isBlank()) {
            return fallbackHullName;
        }
        if (hullID != null) {
            IndexedFile indexed = DatabaseQueryService.getFileByEntityId(hullID, StringConstants.SHIP_TYPE);
            if (indexed != null) {
                enrichFromIndexedFile(indexed);
                if (fallbackHullName != null && !fallbackHullName.isBlank()) {
                    return fallbackHullName;
                }
            }
        }
        return name != null ? name : "";
    }

    public String getShipDesignation() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            String skinDesignation = activeSkinSpecFile.getHullDesignation();
            if (skinDesignation != null && !skinDesignation.isEmpty()) {
                return skinDesignation;
            }
        }
        String designation = rowData.get(StringConstants.DESIGNATION);
        return designation != null ? designation : "";
    }

    public int getTotalOPWithSkin() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            return activeSkinSpecFile.getOrdnancePoints();
        }
        String ordnancePoints = rowData.get(StringConstants.ORDNANCE_POINTS_SPACED);
        return Utility.parseIntegerOrDefault(ordnancePoints, 0);
    }

    public int getBaseTotalOP() {
        String ordnancePoints = rowData.get(StringConstants.ORDNANCE_POINTS_SPACED);
        return Utility.parseIntegerOrDefault(ordnancePoints, -1);
    }

    public int getBaseFluxCapacity() {
        String fluxCapacity = rowData.get("flux capacity");
        return Utility.parseIntegerOrDefault(fluxCapacity, 0);
    }

    public int getBaseFluxDissipation() {
        String fluxDissipation = rowData.get("flux dissipation");
        return Utility.parseIntegerOrDefault(fluxDissipation, 0);
    }

    public int getBayCount() {
        var entryRowData = this.getRowData();
        String fighterBays = entryRowData.get("fighter bays");
        return Utility.parseIntegerOrDefault(fighterBays, 0);
    }

    public String getShipSpriteName() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            String skinSpecFileSpriteName = activeSkinSpecFile.getSpriteName();
            if (skinSpecFileSpriteName != null && !skinSpecFileSpriteName.isEmpty()) {
                return skinSpecFileSpriteName;
            }
        }
        HullSpecFile spec = this.getHullSpecFile();
        return spec != null ? spec.getSpriteName() : "";
    }

    @Override
    public String getID() {
        return hullID;
    }

    public void setHullID(String newID) {
        this.hullID = newID;
        this.rowData.put(StringConstants.ID, newID);
    }

    private final Map<SkinSpecFile, Sprite> skinSpriteCache = new HashMap<>();

    public Sprite getEntrySprite() {
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            Sprite skinSprite = skinSpriteCache.get(activeSkinSpecFile);
            if (skinSprite == null) {
                String spriteFileName = this.getShipSpriteName();
                File spriteFile = FileLoading.fetchDataFile(Path.of(spriteFileName), activeSkinSpecFile.getContainingPackage());
                if (spriteFile != null) {
                    skinSprite = FileLoading.loadSprite(spriteFile);
                    skinSpriteCache.put(activeSkinSpecFile, skinSprite);
                }
            }
            if (skinSprite != null) {
                return skinSprite;
            }
        }
        if (entrySprite == null) {
            String spriteFileName = this.getShipSpriteName();
            File spriteFile = FileLoading.fetchDataFile(Path.of(spriteFileName), this.getPackageFolderPath());

            if (spriteFile != null) {
                entrySprite = FileLoading.loadSprite(spriteFile);
            }
        }
        return entrySprite;
    }

    /**
     * @param rotation - in degrees.
     */
    public void paintEntry(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer,
                           Matrix4f projection, Matrix4f view,
                           double rotation, Point2D targetLocation) {
        DrawUtilities.paintInstallableGhostGL(spriteRenderer, projection, view,
                rotation, targetLocation, getEntrySprite());
    }


    @Override
    public Path getTableFilePath() {
        if (tableFilePath != null) {
            return tableFilePath;
        }
        return hullSpecFile != null ? hullSpecFile.getTableFilePath() : null;
    }

    public void setActiveSkinSpecFile(SkinSpecFile input) {
        if (!skins.containsValue(input)) {
            throw new RuntimeException("Attempt to set incompatible skin on ship entry!");
        }
        this.activeSkinSpecFile = input;
    }

    @Override
    public String toString() {
        String displayedName = getShipName();
        if (displayedName == null || displayedName.isBlank()) {
            displayedName = getShipDesignation();
        }
        if (displayedName == null || displayedName.isBlank()) {
            displayedName = this.hullID;
        }
        return (displayedName != null && !displayedName.isBlank()) ? displayedName : "Unknown Ship";
    }

    @SuppressWarnings("MethodWithMultipleReturnPoints")
    @Override
    public ShipLayer loadLayerFromEntry() {
        HullSpecFile spec = this.getHullSpecFile();
        if (spec == null) {
            log.error("Hull spec file not loaded, layer not created: {}", this.hullID);
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("HULL_SPEC_FILE_NOT_LOADED_LAYER_NOT_CREA_MSG") + this.hullID,
                    StringManager.getString("FILE_LOADING_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        String spriteName = spec.getSpriteName();
        Path spriteFilePath = Path.of(spriteName);
        File spriteFile = FileLoading.fetchDataFile(spriteFilePath, this.packageFolderPath);

        if (spriteFile == null) {
            log.error("Sprite file for ship not found: {}", spriteFilePath.toString());
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("SPRITE_FILE_FOR_SHIP_NOT_FOUND_LAYER_NOT_MSG") + spriteFilePath,
                    StringManager.getString("FILE_LOADING_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        ShipLayer newLayer = FileUtilities.createShipLayerWithSprite(spriteFile);
        newLayer.initializeHullData(spec);

        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.join();
        }
        if (skins == null || skins.isEmpty()) return newLayer;

        Map<String, SkinSpecFile> eligibleSkins = new HashMap<>(skins);
        eligibleSkins.remove(SkinSpecFile.DEFAULT);
        if (eligibleSkins.isEmpty()) return newLayer;
        for (SkinSpecFile skinSpecFile : eligibleSkins.values()) {
            if (skinSpecFile == null || skinSpecFile.isBase()) continue;

            ShipHull data = newLayer.getHull();
            boolean setAsActive = skinSpecFile == this.activeSkinSpecFile;
            LayerManager.openSkinFile(newLayer, data, skinSpecFile, setAsActive);
        }
        return newLayer;
    }

    /**
     * @param layer can be null.
     */
    public ShipPainter createPainterFromEntry(ShipLayer layer) {
        HullSpecFile spec = this.getHullSpecFile();
        if (spec == null) {
            log.error("Hull spec file not loaded, painter not created: {}", this.hullID);
            return null;
        }
        ShipPainter shipPainter = new ShipPainter(layer);

        String spriteName = spec.getSpriteName();
        Path spriteFilePath = Path.of(spriteName);
        File spriteFile = FileLoading.fetchDataFile(spriteFilePath, this.packageFolderPath);
        Sprite sprite = FileLoading.loadSprite(spriteFile);
        shipPainter.setSprite(sprite);
        shipPainter.setBaseHullSprite(sprite);

        shipPainter.initFromHullSpec(this.getHullSpecFile());

        return shipPainter;
    }

    public List<String> getBuiltInHullmods() {
        List<String> hullmodIDs = new ArrayList<>();
        HullSpecFile spec = this.getHullSpecFile();
        if (spec != null) {
            String[] fromHull = spec.getBuiltInMods();
            if (fromHull != null) {
                hullmodIDs.addAll(List.of(fromHull));
            }
        }
        SkinSpecFile skinSpecFile = this.activeSkinSpecFile;
        if (skinSpecFile != null && !skinSpecFile.isBase()) {
            List<String> builtInMods = skinSpecFile.getBuiltInMods();
            if (builtInMods != null) {
                hullmodIDs.addAll(builtInMods);
            }
        }
        return hullmodIDs;
    }

}
