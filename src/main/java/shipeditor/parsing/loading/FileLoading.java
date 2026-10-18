package shipeditor.parsing.loading;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.representation.ship.HullStyle;
import shipeditor.utility.text.StringConstants;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
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
        shipeditor.utility.DataProblemsRegistry.clear();
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
                    CompletableFuture<Runnable> shipCsv = preloadCsvTask("TASK_SHIPS", () -> {
                        SettingsManager.getGameData().getShipEntriesByPackage();
                        SettingsManager.getGameData().getAllShipEntries();
                    });
                    CompletableFuture<Runnable> weaponCsv = preloadCsvTask("TASK_WEAPONS", () -> {
                        SettingsManager.getGameData().getWeaponEntriesByPackage();
                        SettingsManager.getGameData().getAllWeaponEntries();
                    });
                    CompletableFuture<Runnable> hullmodCsv = preloadCsvTask("TASK_HULLMODS", () -> {
                        SettingsManager.getGameData().getHullmodEntriesByPackage();
                        SettingsManager.getGameData().getAllHullmodEntries();
                    });
                    CompletableFuture<Runnable> systemCsv = preloadCsvTask("TASK_SHIP_SYSTEMS", () -> {
                        SettingsManager.getGameData().getShipSystemEntriesByPackage();
                        SettingsManager.getGameData().getAllShipsystemEntries();
                    });
                    CompletableFuture<Runnable> wingCsv = preloadCsvTask("TASK_WINGS", () -> {
                        SettingsManager.getGameData().getWingEntriesByPackage();
                        SettingsManager.getGameData().getAllWingEntries();
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

    /**
     * Runs a CSV pre-load on a background thread, publishing started/completed task events around it.
     *
     * @param taskNameKey string-table key of the task label shown while loading.
     * @param loader the CSV pre-loading work.
     */
    private static CompletableFuture<Runnable> preloadCsvTask(String taskNameKey, Runnable loader) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Pre-loading CSV data for task: {}", taskNameKey);
            SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskStarted(StringManager.getString(taskNameKey))));
            loader.run();
            SwingUtilities.invokeLater(() -> EventBus.publish(new LoadingTaskCompleted(StringManager.getString(taskNameKey))));
            return () -> {};
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

    /**
     * Assigns the id and source file onto a freshly deserialized style entry.
     */
    @FunctionalInterface
    private interface StyleIdentifier<T> {
        void identify(T style, String styleId, Path filePath);
    }

    public static Runnable loadHullStyles() {
        Map<String, HullStyle> collectedHullStyles = collectStyles(StringConstants.HULL_STYLE_JSON_TYPE,
                "hull styles", HullStyle.class,
                (style, styleId, filePath) -> {
                    style.setHullStyleID(styleId);
                    style.setFilePath(filePath);
                },
                HullStyle::setContainingPackage);

        return () -> {
            GameDataRepository gameData = SettingsManager.getGameData();
            gameData.setAllHullStyles(collectedHullStyles);
            EventBus.publish(new FileEvents.HullStylesLoaded(collectedHullStyles));
        };
    }

    public static Runnable loadEngineStyles() {
        Map<String, EngineStyle> collectedEngineStyles = collectStyles(StringConstants.ENGINE_STYLE_JSON_TYPE,
                "engine styles", EngineStyle.class,
                (style, styleId, filePath) -> {
                    style.setEngineStyleID(styleId);
                    style.setFilePath(filePath);
                },
                EngineStyle::setContainingPackage);

        return () -> {
            GameDataRepository gameData = SettingsManager.getGameData();
            gameData.setAllEngineStyles(collectedEngineStyles);
            EventBus.publish(new FileEvents.EngineStylesLoaded(collectedEngineStyles));
        };
    }

    private static <T> Map<String, T> collectStyles(String jsonType, String styleLabel, Class<T> styleClass,
                                                    StyleIdentifier<T> identifier,
                                                    BiConsumer<T, Path> packageSetter) {
        log.debug("Collecting {} from indexed files of type: {}", styleLabel, jsonType);
        List<IndexedFile> dbFiles = DatabaseQueryService.getFilesByType(jsonType);

        Map<String, T> collectedStyles = new LinkedHashMap<>();
        for (IndexedFile dbFile : dbFiles) {
            Path folderPath = SettingsManager.getFolderForModId(dbFile.getModId());
            if (folderPath == null) {
                log.warn("No folder found for mod_id '{}', skipping {}", dbFile.getModId(), styleLabel);
                continue;
            }

            Settings settings = SettingsManager.getSettings();
            if (settings == null) {
                continue;
            }
            Path folderName = folderPath.getFileName();
            if (folderName == null) {
                continue;
            }
            GameDataPackage dataPackage = settings.getPackage(folderName.toString());
            if (dataPackage != null && dataPackage.isDisabled()) {
                continue;
            }
            if (LibModFilter.isLibMod(folderPath)) {
                continue;
            }

            File styleFile = dbFile.getFilePath().toFile();
            log.trace("Data file for {} found in mod directory: {}", styleLabel, folderPath);
            Map<String, T> stylesFromFile = loadStyleFile(styleFile, styleClass, styleLabel, identifier);
            for (T style : stylesFromFile.values()) {
                packageSetter.accept(style, folderPath);
            }
            collectedStyles.putAll(stylesFromFile);
        }
        return collectedStyles;
    }

    private static <T> Map<String, T> loadStyleFile(File styleFile, Class<T> styleClass, String styleLabel,
                                                    StyleIdentifier<T> identifier) {
        ObjectMapper mapper = FileUtilities.getConfigured();
        Map<String, T> styles = null;
        log.trace("Fetching {} data at: {}..", styleLabel, styleFile.toPath());
        MapType mapType = null;
        try {
            TypeFactory typeFactory = mapper.getTypeFactory();
            mapType = typeFactory.constructMapType(HashMap.class, String.class, styleClass);
            styles = mapper.readValue(styleFile, mapType);
        } catch (IOException e) {
            log.trace("File loading failed for {}, retrying with correction: {}", styleLabel, styleFile.getName());
            styles = FileLoading.parseCorrectableJSON(styleFile, mapType);
        }

        if (styles == null) {
            log.error("File loading failed conclusively for {}: {}", styleLabel, styleFile.getName());
            return new HashMap<>();
        }

        for (Map.Entry<String, T> entry : styles.entrySet()) {
            T style = entry.getValue();
            if (style != null) {
                identifier.identify(style, entry.getKey(), styleFile.toPath());
            }
        }
        return styles;
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
