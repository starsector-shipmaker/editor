package shipeditor.persistence.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.IndexScannerTask;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.text.StringConstants;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the in-memory index of starsector-core files.
 * Guarantees that the core files are always loaded regardless of SQLite database status.
 */
@Log4j2
public class CoreIndexManager {
    private static final Map<String, List<IndexedFile>> CORE_FILES_BY_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, IndexedFile>> CORE_FILES_BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<String, IndexedFile> CORE_FILES_BY_PATH = new ConcurrentHashMap<>();
    private static final Set<String> CORE_SPEC_EXTENSIONS = Set.of("ship", "skin", "wpn", "variant", "proj");
    private static final Object LOCK = new Object();
    private static volatile boolean isLoaded = false;

    public static void reset() {
        synchronized (LOCK) {
            CORE_FILES_BY_TYPE.clear();
            CORE_FILES_BY_ENTITY_ID.clear();
            CORE_FILES_BY_PATH.clear();
            isLoaded = false;
        }
    }

    public static void loadCoreData() {
        synchronized (LOCK) {
            if (isLoaded) return;
            if (SettingsManager.getSettings() == null) {
                log.warn("Settings not initialized. Cannot load core index.");
                return;
            }
        
        Path coreFolder = SettingsManager.getCoreFolderPath();
        if (coreFolder == null || !Files.exists(coreFolder)) {
            log.warn("Core folder path is null or does not exist. Cannot load core index.");
            return;
        }

            CORE_FILES_BY_TYPE.clear();
            CORE_FILES_BY_ENTITY_ID.clear();
            CORE_FILES_BY_PATH.clear();

            long startTime = System.currentTimeMillis();

            // Check if we can load from the database instead of re-scanning.
            if (tryLoadFromDatabase(coreFolder)) {
                isLoaded = true;
                log.info("Core memory index loaded from database cache in {}ms", System.currentTimeMillis() - startTime);
                return;
            }

        log.info("Scanning starsector-core into memory index...");

        Map<String, String> extensions = Map.of(
                "ship", StringConstants.SHIP_TYPE,
                "skin", StringConstants.SKIN_TYPE,
                "wpn", StringConstants.WEAPON_TYPE,
                "variant", StringConstants.VARIANT_TYPE,
                "proj", StringConstants.PROJECTILE_TYPE
        );

        // ObjectMapper is thread-safe for read operations.
        ObjectMapper mapper = FileUtilities.getConfigured();
        Map<String, List<File>> allFiles = IndexScannerTask.fetchFilesWithExtensions(coreFolder, extensions.keySet());

        List<IndexedFile> allIndexedFiles = new ArrayList<>();

        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            String ext = entry.getKey();
            String type = entry.getValue();
            List<File> files = allFiles.getOrDefault(ext, Collections.emptyList());

            List<IndexedFile> typeList = files.parallelStream()
                    .map(file -> {
                        try {
                            IndexScannerTask.EntityMetadata metadata = IndexScannerTask.extractEntityMetadata(file, type, mapper);
                            String entityId = metadata.id();
                            String fileBaseName = file.getName().replace("." + ext, "");
                            String entityName = metadata.hullName() != null && !metadata.hullName().isBlank()
                                    ? metadata.hullName() : fileBaseName;

                            return IndexedFile.builder()
                                    .uuid(UUID.randomUUID())
                                    .modId("starsector-core")
                                    .entityId(entityId != null ? entityId : fileBaseName)
                                    .entityName(entityName)
                                    .entityType(type)
                                    .fileName(file.getName())
                                    .filePath(file.toPath())
                                    .lastModified(file.lastModified())
                                    .spritePath(metadata.spritePath())
                                    .designation(metadata.designation())
                                    .metadataJson(metadata.metaValue() != null ? metadata.metaValue() : "")
                                    .build();
                        } catch (Exception e) {
                            log.error("Failed to parse core file for memory index: {}", file, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            CORE_FILES_BY_TYPE.put(type, typeList);
            allIndexedFiles.addAll(typeList);

            Map<String, IndexedFile> entityIdMap = new ConcurrentHashMap<>();
            for (IndexedFile file : typeList) {
                entityIdMap.put(file.getEntityId(), file);
                CORE_FILES_BY_PATH.put(file.getFilePath().toString(), file);
            }
            CORE_FILES_BY_ENTITY_ID.put(type, entityIdMap);
        }

        // Persist to database for faster subsequent startups.
        persistToDatabase(coreFolder, allIndexedFiles);

        isLoaded = true;
        log.info("Core memory index loaded in {}ms", System.currentTimeMillis() - startTime);
        }
    }

    private static boolean tryLoadFromDatabase(Path coreFolder) {
        try {
            shipeditor.persistence.database.DatabaseQueryService.ModInfo modInfo =
                    shipeditor.persistence.database.DatabaseQueryService.getScannedModsMap().get("starsector-core");
            if (modInfo == null) {
                return false;
            }

            // Check if any relevant file in the core folder has been modified since the last scan.
            long dbLastScanned = modInfo.lastScanned();
            Path walkTarget = IndexScannerTask.resolveDataWalkTarget(coreFolder);
            try (java.util.stream.Stream<Path> pathStream = Files.walk(walkTarget)) {
                boolean hasNewerFile = pathStream.anyMatch(
                        path -> IndexScannerTask.isRelevantFileModified(path, dbLastScanned, CORE_SPEC_EXTENSIONS));
                if (hasNewerFile) {
                    return false;
                }
            }

            // Load from database: query indexed_files for mod_id = 'starsector-core'.
            List<IndexedFile> allCoreFiles = shipeditor.persistence.database.DatabaseQueryService.getFilesByModId("starsector-core");
            if (allCoreFiles.isEmpty()) {
                return false;
            }

            for (IndexedFile file : allCoreFiles) {
                String type = file.getEntityType();
                CORE_FILES_BY_TYPE.computeIfAbsent(type, k -> new ArrayList<>()).add(file);
                
                String entityId = file.getEntityId();
                if (entityId != null) {
                    CORE_FILES_BY_ENTITY_ID.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(entityId, file);
                }
                
                CORE_FILES_BY_PATH.put(file.getFilePath().toString(), file);
            }

            // Migration check: detect stale DB entries that used filename as entityName
            // instead of hullName, or that lack metadata_json.
            List<IndexedFile> shipFiles = CORE_FILES_BY_TYPE.get(StringConstants.SHIP_TYPE);
            if (shipFiles != null && !shipFiles.isEmpty()) {
                IndexedFile sample = shipFiles.get(0);
                if ((sample.getEntityName() != null && sample.getEntityId() != null
                        && sample.getEntityName().equals(sample.getEntityId()))
                        || sample.getMetadataJson() == null) {
                    log.info("Detected stale entity metadata format in database cache, triggering re-scan.");
                    CORE_FILES_BY_TYPE.clear();
                    CORE_FILES_BY_ENTITY_ID.clear();
                    CORE_FILES_BY_PATH.clear();
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to load core index from database, will re-scan.", e);
            return false;
        }
    }

    private static void persistToDatabase(Path coreFolder, List<IndexedFile> files) {
        try {
            DatabaseQueryService.deleteFilesByModId("starsector-core");
            DatabaseQueryService.upsertMod("starsector-core", "Starsector Core", coreFolder.toAbsolutePath().toString(), System.currentTimeMillis());

            shipeditor.persistence.database.DatabaseQueryService.upsertFiles(files);
        } catch (Exception e) {
            log.warn("Failed to persist core index to database. Next startup will re-scan.", e);
        }
    }

    public static List<IndexedFile> getFilesByType(String type) {
        if (!isLoaded) {
            loadCoreData();
        }
        return CORE_FILES_BY_TYPE.getOrDefault(type, Collections.emptyList());
    }

    public static IndexedFile getFileByEntityId(String entityId, String type) {
        if (!isLoaded) {
            loadCoreData();
        }
        Map<String, IndexedFile> entityMap = CORE_FILES_BY_ENTITY_ID.get(type);
        return entityMap != null ? entityMap.get(entityId) : null;
    }

    public static IndexedFile getFileByPath(String pathStr) {
        if (!isLoaded) {
            loadCoreData();
        }
        return CORE_FILES_BY_PATH.get(pathStr);
    }
}
