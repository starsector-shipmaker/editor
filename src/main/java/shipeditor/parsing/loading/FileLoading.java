package shipeditor.parsing.loading;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.databind.JavaType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;

import shipeditor.persistence.SettingsManager;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.StaticController;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import shipeditor.communication.events.components.ComponentEvents.LoadingTaskCompleted;
import shipeditor.communication.events.components.ComponentEvents.LoadingTaskStarted;
import shipeditor.communication.events.components.ComponentEvents.LoadingActionFired;

/**
 * Central system responsible for parsing and loading game data files (ships, weapons, hullmods, CSV tables)
 * into memory, asynchronously or synchronously.
 * <p>
 * **Key Mechanics & Architecture:**
 * <ul>
 *   <li><b>Symlink & Package Support:</b> Uses custom recursive walkers to locate data files across core folders and mod directories.</li>
 *   <li><b>JSON Pre-Processing:</b> Handles Starsector's unconventional/malformed JSON strings via {@link shipeditor.parsing.JsonProcessor}.</li>
 *   <li><b>CSV Preservation:</b> Preserves raw row maps and schema headers for lossless re-saving.</li>
 * </ul>
 */
@SuppressWarnings({ "ClassWithTooManyFields", "OverlyCoupledClass", "ClassWithTooManyMethods" })
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class FileLoading {


    public static final Action OPEN_SPRITE = new OpenSpriteAction();
    public static Action getOpenSprite() { return OPEN_SPRITE; }
    public static final Action OPEN_SHIP = new OpenHullAction();
    public static Action getOpenShip() { return OPEN_SHIP; }
    private static final Action LOAD_HULL_AS_LAYER = new LoadHullAsLayer();
    public static Action getLoadHullAsLayer() { return LOAD_HULL_AS_LAYER; }
    private static final Action LOAD_SPRITE_AS_HULL = new LoadSpriteAsNewHull();
    public static Action getLoadSpriteAsHull() { return LOAD_SPRITE_AS_HULL; }

    @Getter @Setter
    private static volatile boolean loadingInProgress;

    public static void clearDirectoryCache() {
        FileSystemUtils.clearDirectoryCache();
    }

    private FileLoading() {
    }

    private static void initializeDatabaseInProcess() {
        try {
            if (SettingsManager.isDeveloperModeEnabled()) {
                log.info(StringManager.getString("STARTING_DB_INDEX_SCAN"));
            }
            IndexScannerTask.scanAndIndexAll(true);
            if (SettingsManager.isDeveloperModeEnabled()) {
                log.info(StringManager.getString("DB_INDEX_SCAN_COMPLETED"));
            }
        } catch (RuntimeException e) {
            log.error(StringManager.getString("DB_INDEX_SCAN_FAILED"), e);
        }
    }

    public static CompletableFuture<List<Runnable>> loadGameData() {
        return loadGameData(false);
    }

    public static CompletableFuture<List<Runnable>> forceReindexAndLoadGameData() {
        return loadGameData(true);
    }

    private static CompletableFuture<List<Runnable>> loadGameData(boolean forceReindex) {
        if (loadingInProgress) {
            log.warn("loadGameData() called while loading is already in progress. Aborting duplicate run.");
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }

        clearDirectoryCache();
        shipeditor.persistence.database.DatabaseQueryService.clearTypeCache();
        if (forceReindex) {
            shipeditor.persistence.database.DatabaseQueryService.clearCsvCache();
        }
        shipeditor.persistence.database.CoreIndexManager.reset();
        SettingsManager.getGameData().reset();
        EventBus.publish(new LoadingActionFired(true));
        FileLoading.setLoadingInProgress(true);

        return CompletableFuture.runAsync(() -> {
            if (forceReindex) {
                shipeditor.persistence.database.DatabaseManager.deleteDatabase();
            }
            FileLoading.initializeDatabaseInProcess();
        })
                .thenCompose(v -> {
                    List<CompletableFuture<Runnable>> futures = new ArrayList<>();
                    futures.add(CompletableFuture.supplyAsync(() -> loadHullStyles()));
                    futures.add(CompletableFuture.supplyAsync(() -> loadEngineStyles()));
                    futures.add(CompletableFuture.runAsync(() -> shipeditor.persistence.database.CoreIndexManager.loadCoreData()).thenApply(ignored -> () -> {}));

                    // Pre-load CSV data in parallel so caches are warm before tabs render.
                    CompletableFuture<Runnable> shipCsv = CompletableFuture.supplyAsync(() -> {
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString("TASK_SHIPS"))));
                        SettingsManager.getGameData().getShipEntriesByPackage();
                        SettingsManager.getGameData().getAllShipEntries();
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString("TASK_SHIPS"))));
                        return () -> {};
                    });
                    CompletableFuture<Runnable> weaponCsv = CompletableFuture.supplyAsync(() -> {
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString("TASK_WEAPONS"))));
                        SettingsManager.getGameData().getWeaponEntriesByPackage();
                        SettingsManager.getGameData().getAllWeaponEntries();
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString("TASK_WEAPONS"))));
                        return () -> {};
                    });
                    CompletableFuture<Runnable> hullmodCsv = CompletableFuture.supplyAsync(() -> {
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString("TASK_HULLMODS"))));
                        SettingsManager.getGameData().getHullmodEntriesByPackage();
                        SettingsManager.getGameData().getAllHullmodEntries();
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString("TASK_HULLMODS"))));
                        return () -> {};
                    });
                    CompletableFuture<Runnable> systemCsv = CompletableFuture.supplyAsync(() -> {
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString("TASK_SHIP_SYSTEMS"))));
                        SettingsManager.getGameData().getShipSystemEntriesByPackage();
                        SettingsManager.getGameData().getAllShipsystemEntries();
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString("TASK_SHIP_SYSTEMS"))));
                        return () -> {};
                    });
                    CompletableFuture<Runnable> wingCsv = CompletableFuture.supplyAsync(() -> {
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString("TASK_WINGS"))));
                        SettingsManager.getGameData().getWingEntriesByPackage();
                        SettingsManager.getGameData().getAllWingEntries();
                        SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString("TASK_WINGS"))));
                        return () -> {};
                    });

                    futures.add(shipCsv);
                    futures.add(weaponCsv);
                    futures.add(hullmodCsv);
                    futures.add(systemCsv);
                    futures.add(wingCsv);

                    // Set loaded flags only after all CSV pre-loading completes.
                    CompletableFuture<Void> csvDone = CompletableFuture.allOf(shipCsv, weaponCsv, hullmodCsv, systemCsv, wingCsv);
                    futures.add(csvDone.thenApply(val -> () -> {
                        SettingsManager.getGameData().setShipDataLoaded(true);
                        SettingsManager.getGameData().setWeaponsDataLoaded(true);
                        SettingsManager.getGameData().setHullmodDataLoaded(true);
                        SettingsManager.getGameData().setShipsystemDataLoaded(true);
                        SettingsManager.getGameData().setWingDataLoaded(true);
                    }));

                    CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    return allOf.thenApply(val -> futures.stream().map(a -> a.join()).toList());
                }).whenComplete((runnables, ex) -> {
                    if (ex != null) {
                        log.error(StringManager.getString("ERROR_LOADING_GAME_DATA"), ex);
                        FileLoading.setLoadingInProgress(false);
                        clearDirectoryCache();
                        if (java.awt.GraphicsEnvironment.isHeadless()) {
                            EventBus.publish(new LoadingActionFired(false));
                        } else {
                            SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingActionFired(false)));
                        }
                    } else if (runnables != null) {
                        Runnable completionTasks = () -> {
                            clearDirectoryCache();
                            EventBus.publish(new LoadingActionFired(false));
                            FileLoading.setLoadingInProgress(false);
                            StaticController.reselectCurrentLayer();
                            SettingsManager.updateFileFromRuntime();
                        };
                        if (java.awt.GraphicsEnvironment.isHeadless()) {
                            runnables.forEach(a -> a.run());
                            completionTasks.run();
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                EventBus.publish(new LoadingTaskStarted(StringManager.getString("UPDATING_UI")));
                                // Delay the heavy UI rebuilds by one event cycle so the label can repaint first.
                                SwingUtilities.invokeLater(() -> executeStaggered(new ArrayList<>(runnables), () -> {
                                    completionTasks.run();
                                    EventBus.publish(new LoadingTaskCompleted(StringManager.getString("UPDATING_UI")));
                                }));
                            });
                        }
                    }
                });
    }

    private static void executeStaggered(List<Runnable> tasks, Runnable onComplete) {
        if (tasks.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        Runnable task = tasks.remove(0);
        try {
            task.run();
        } catch (Throwable t) {
            log.error(StringManager.getString("STAGGERED_UI_ERROR"), t);
        }
        SwingUtilities.invokeLater(() -> executeStaggered(tasks, onComplete));
    }


    @SuppressWarnings("NestedTryStatement")
    public static BufferedImage loadImageResource(String imageFilename) {
        return SpriteLoader.loadImageResource(imageFilename);
    }

    public static BufferedImage loadSpriteAsImage(File file) {
        return SpriteLoader.loadSpriteAsImage(file);
    }

    public static Sprite loadSprite(File file) {
        return SpriteLoader.loadSprite(file);
    }

    public static File fetchDataFile(Path filePath, Path packageFolderPath) {
        return FileSystemUtils.fetchDataFile(filePath, packageFolderPath);
    }

    public static HullSpecFile loadHullFile(File file) {
        return JsonSpecLoader.loadHullFile(file);
    }

    public static WeaponSpecFile loadWeaponFile(File file) {
        return JsonSpecLoader.loadWeaponFile(file);
    }

    public static SkinSpecFile loadSkinFile(File file) {
        return JsonSpecLoader.loadSkinFile(file);
    }

    public static VariantFile loadVariantFile(File file) {
        return JsonSpecLoader.loadVariantFile(file);
    }

    public static ProjectileSpecFile loadProjectileFile(File file) {
        return JsonSpecLoader.loadProjectileFile(file);
    }

    public static Runnable loadHullStyles() {
        List<shipeditor.persistence.database.IndexedFile> dbFiles = shipeditor.persistence.database.DatabaseQueryService.getFilesByType(shipeditor.utility.text.StringConstants.HULL_STYLE_JSON_TYPE);

        Map<String, shipeditor.representation.ship.HullStyle> collectedHullStyles = new java.util.LinkedHashMap<>();
        for (shipeditor.persistence.database.IndexedFile dbFile : dbFiles) {
            Path folderPath = SettingsManager.getFolderForModId(dbFile.getModId());
            if (folderPath == null) {
                log.warn("No folder found for mod_id '{}', skipping hull styles", dbFile.getModId());
                continue;
            }

            shipeditor.persistence.Settings settings = SettingsManager.getSettings();
            if (settings == null) {
                continue;
            }
            Path folderName = folderPath.getFileName();
            if (folderName == null) {
                continue;
            }
            shipeditor.persistence.GameDataPackage dataPackage = settings.getPackage(folderName.toString());
            if (dataPackage != null && dataPackage.isDisabled()) {
                continue;
            }
            if (LibModFilter.isLibMod(folderPath)) {
                continue;
            }

            File styleFile = dbFile.getFilePath().toFile();
            log.trace("Hullstyle data file found in mod directory: {}", folderPath);
            Map<String, shipeditor.representation.ship.HullStyle> stylesFromFile = loadHullStyleFile(styleFile);
            for (shipeditor.representation.ship.HullStyle style : stylesFromFile.values()) {
                style.setContainingPackage(folderPath);
            }
            collectedHullStyles.putAll(stylesFromFile);
        }

        return () -> {
            shipeditor.representation.GameDataRepository gameData = SettingsManager.getGameData();
            gameData.setAllHullStyles(collectedHullStyles);
            EventBus.publish(new shipeditor.communication.events.files.FileEvents.HullStylesLoaded(collectedHullStyles));
        };
    }

    private static Map<String, shipeditor.representation.ship.HullStyle> loadHullStyleFile(File styleFile) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = shipeditor.parsing.FileUtilities.getConfigured();
        Map<String, shipeditor.representation.ship.HullStyle> hullStyles = null;
        log.trace("Fetching hullstyle data at: {}..", styleFile.toPath());
        com.fasterxml.jackson.databind.type.MapType mapType = null;
        try {
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = mapper.getTypeFactory();
            mapType = typeFactory.constructMapType(java.util.HashMap.class, String.class, shipeditor.representation.ship.HullStyle.class);
            hullStyles = mapper.readValue(styleFile, mapType);
        } catch (IOException e) {
            log.trace("Hull styles file loading failed, retrying with correction: {}", styleFile.getName());
            hullStyles = FileLoading.parseCorrectableJSON(styleFile, mapType);
        }

        if (hullStyles == null) {
            log.error("Hull styles file loading failed conclusively: {}", styleFile.getName());
            return new java.util.HashMap<>();
        }

        for (Map.Entry<String, shipeditor.representation.ship.HullStyle> entry : hullStyles.entrySet()) {
            String hullStyleID = entry.getKey();
            shipeditor.representation.ship.HullStyle hullStyle = entry.getValue();
            if (hullStyle != null) {
                hullStyle.setHullStyleID(hullStyleID);
                hullStyle.setFilePath(styleFile.toPath());
            }
        }
        return hullStyles;
    }

    public static Runnable loadEngineStyles() {
        List<shipeditor.persistence.database.IndexedFile> dbFiles = shipeditor.persistence.database.DatabaseQueryService.getFilesByType(shipeditor.utility.text.StringConstants.ENGINE_STYLE_JSON_TYPE);

        Map<String, shipeditor.representation.ship.EngineStyle> collectedEngineStyles = new java.util.LinkedHashMap<>();
        for (shipeditor.persistence.database.IndexedFile dbFile : dbFiles) {
            Path folderPath = SettingsManager.getFolderForModId(dbFile.getModId());
            if (folderPath == null) {
                log.warn("No folder found for mod_id '{}', skipping engine styles", dbFile.getModId());
                continue;
            }

            shipeditor.persistence.Settings settings = SettingsManager.getSettings();
            if (settings == null) {
                continue;
            }
            Path folderName = folderPath.getFileName();
            if (folderName == null) {
                continue;
            }
            shipeditor.persistence.GameDataPackage dataPackage = settings.getPackage(folderName.toString());
            if (dataPackage != null && dataPackage.isDisabled()) {
                continue;
            }
            if (LibModFilter.isLibMod(folderPath)) {
                continue;
            }

            File styleFile = dbFile.getFilePath().toFile();
            log.trace("Engine style data file found in mod directory: {}", folderPath);
            Map<String, shipeditor.representation.ship.EngineStyle> stylesFromFile = loadEngineStyleFile(styleFile);
            for (shipeditor.representation.ship.EngineStyle style : stylesFromFile.values()) {
                style.setContainingPackage(folderPath);
            }
            collectedEngineStyles.putAll(stylesFromFile);
        }

        return () -> {
            shipeditor.representation.GameDataRepository gameData = SettingsManager.getGameData();
            gameData.setAllEngineStyles(collectedEngineStyles);
            EventBus.publish(new shipeditor.communication.events.files.FileEvents.EngineStylesLoaded(collectedEngineStyles));
        };
    }

    private static Map<String, shipeditor.representation.ship.EngineStyle> loadEngineStyleFile(File styleFile) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = shipeditor.parsing.FileUtilities.getConfigured();
        Map<String, shipeditor.representation.ship.EngineStyle> engineStyles = null;
        log.trace("Fetching engine style data at: {}..", styleFile.toPath());
        com.fasterxml.jackson.databind.type.MapType mapType = null;
        try {
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = mapper.getTypeFactory();
            mapType = typeFactory.constructMapType(java.util.HashMap.class, String.class, shipeditor.representation.ship.EngineStyle.class);
            engineStyles = mapper.readValue(styleFile, mapType);
        } catch (IOException e) {
            log.trace("Engine styles file loading failed, retrying with correction: {}", styleFile.getName());
            engineStyles = FileLoading.parseCorrectableJSON(styleFile, mapType);
        }

        if (engineStyles == null) {
            log.error("Engine styles file loading failed conclusively: {}", styleFile.getName());
            return new java.util.HashMap<>();
        }

        for (Map.Entry<String, shipeditor.representation.ship.EngineStyle> entry : engineStyles.entrySet()) {
            String engineStyleID = entry.getKey();
            shipeditor.representation.ship.EngineStyle engineStyle = entry.getValue();
            if (engineStyle != null) {
                engineStyle.setEngineStyleID(engineStyleID);
                engineStyle.setFilePath(styleFile.toPath());
            }
        }
        return engineStyles;
    }

    @SuppressWarnings("AssignmentToNull")
    static <T> T parseCorrectableJSON(File file, JavaType targetType) {
        return JsonSpecLoader.parseCorrectableJSON(file, targetType);
    }

    @SuppressWarnings("WeakerAccess")
    public static List<File> fetchFilesWithExtension(Path target, String dotlessExtension) {
        return FileSystemUtils.fetchFilesWithExtension(target, dotlessExtension);
    }

    static List<Map<String, String>> parseCSVTable(Path path) {
        return FileLoading.parseCSVTable(path, FileLoading.getNormalValidationPredicate());
    }

    /**
     * Target CSV file is expected to have a header row and an ID column designated
     * in said header.
     * 
     * @param path address of the target file.
     * @return List of rows where each row is a Map of string keys and string
     *         values.
     */
    static List<Map<String, String>> parseCSVTable(Path path, Predicate<Map<String, String>> validationPredicate) {
        return CsvLoader.parseCSVTable(path, validationPredicate);
    }

    private static Predicate<Map<String, String>> getNormalValidationPredicate() {
        return CsvLoader.getNormalValidationPredicate();
    }

    static Predicate<Map<String, String>> getWingValidationPredicate() {
        return CsvLoader.getWingValidationPredicate();
    }

    /**
     * Re-parses a CSV file from disk, caching both the raw data and schema in
     * GameDataRepository.
     * Used as a fallback when SoftReferences to cached CSV data have been cleared
     * by GC.
     * 
     * @param path the path to the CSV file on disk.
     * @return the list of raw row maps, or null if re-parsing fails.
     */
    public static List<Map<String, String>> reparseCSVForPath(Path path) {
        return CsvLoader.reparseCSVForPath(path);
    }

}
