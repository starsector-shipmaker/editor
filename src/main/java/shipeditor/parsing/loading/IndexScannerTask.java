package shipeditor.parsing.loading;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.persistence.database.DatabaseManager;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.utility.text.StringConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;


/**
 * Handles the background scanning, indexing, and synchronization of Starsector
 * core and mod files.
 * Performs a deep scan on the first run, and a fast differential timestamp
 * check on subsequent runs.
 *
 * @author Shadow
 */
@Log4j2
public final class IndexScannerTask {

    private static final Set<String> VALID_EXTENSIONS = Set.of(
        "ship", "skin", "wpn", "variant", "proj", "csv", "json"
    );

    private IndexScannerTask() {
    }

    private static boolean isRelevantFileModified(Path path, long dbLastScanned) {
        return isRelevantFileModified(path, dbLastScanned, VALID_EXTENSIONS);
    }

    /**
     * Checks whether a regular file with one of the given extensions was modified after {@code dbLastScanned}.
     */
    public static boolean isRelevantFileModified(Path path, long dbLastScanned, Set<String> validExtensions) {
        if (!Files.isRegularFile(path)) return false;
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) return false;
        String fileName = fileNamePath.toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = fileName.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT);
            if (validExtensions.contains(ext)) {
                return path.toFile().lastModified() > dbLastScanned;
            }
        }
        return false;
    }

    /**
     * Resolves the folder to walk when scanning a mod/core folder: its {@code data} subfolder when present,
     * otherwise the folder itself.
     */
    public static Path resolveDataWalkTarget(Path folder) {
        Path dataPath = folder.resolve("data");
        return Files.exists(dataPath) && Files.isDirectory(dataPath) ? dataPath : folder;
    }

    private static boolean hasNewerRelevantFile(Path folder, long dbLastScanned) throws IOException {
        Path walkTarget = resolveDataWalkTarget(folder);
        log.debug("Checking for files modified after {} under: {}", dbLastScanned, walkTarget);
        try (Stream<Path> pathStream = Files.walk(walkTarget)) {
            return pathStream.anyMatch(path -> isRelevantFileModified(path, dbLastScanned));
        }
    }

    private static UUID uuidOrNew(DatabaseQueryService.FileDbInfo dbInfo) {
        UUID uuid = dbInfo != null ? dbInfo.uuid() : null;
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        return uuid;
    }

    private static String extractCSVMetadata(File file, String type) {
        try {
            List<Map<String, String>> rows = shipeditor.parsing.loading.CsvLoader.reparseCSVForPath(file.toPath());
            if (rows == null || rows.isEmpty()) return null;
            
            com.fasterxml.jackson.databind.node.ObjectNode rootNode = SettingsManager.getMapperForSettingsFile().createObjectNode();
            
            for (Map<String, String> row : rows) {
                String id = row.get("id");
                if (id == null || id.isEmpty()) continue;
                
                com.fasterxml.jackson.databind.node.ObjectNode rowNode = rootNode.putObject(id);
                switch (type) {
                    case StringConstants.SHIP_CSV_TYPE -> {
                        String tech = row.get("tech/manufacturer");
                        if (tech != null) rowNode.put("tech/manufacturer", tech);
                        String hullSize = row.get("hull size");
                        if (hullSize != null) rowNode.put("hull size", hullSize);
                    }
                    case StringConstants.WEAPON_CSV_TYPE -> {
                        String tech = row.get("tech/manufacturer");
                        if (tech != null) rowNode.put("tech/manufacturer", tech);
                        String ops = row.get("OPs");
                        if (ops != null) rowNode.put("OPs", ops);
                        String size = row.get("size");
                        if (size != null) rowNode.put("size", size);
                        String weaponType = row.get("type");
                        if (weaponType != null) rowNode.put("type", weaponType);
                    }
                    default -> {}
                }
            }
            return SettingsManager.getMapperForSettingsFile().writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("Failed to serialize CSV metadata for {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Checks if the database is missing or if any active mod folder has changed
     * since the last scan.
     */
    private static boolean isUpdateNeeded(boolean firstRun, Settings settings) {
        if (firstRun) {
            return true;
        }

        List<Path> targetsToScan = getActiveNonLibModFolders();

        Map<String, DatabaseQueryService.ModInfo> existingModsMap = DatabaseQueryService.getScannedModsMap();

        for (Path folder : targetsToScan) {
            Path fileNamePath = folder.getFileName();
            if (fileNamePath == null) continue;
            String modId = fileNamePath.toString();
            if (SettingsManager.isCoreFolder(folder)) {
                continue;
            }

            DatabaseQueryService.ModInfo dbModInfo = existingModsMap.get(modId);

            if (dbModInfo == null) {
                return true;
            }

            if (!folder.toAbsolutePath().toString().equals(dbModInfo.folderPath())) {
                log.info("isUpdateNeeded: found folder path change for mod: {}", modId);
                return true;
            }

            try {
                if (hasNewerRelevantFile(folder, dbModInfo.lastScanned())) {
                    log.info("isUpdateNeeded: found newer file in mod folder: {}", folder);
                    return true;
                }
            } catch (IOException e) {
                log.warn("Failed to check last modified times for mod folder: {}", folder, e);
            }

            if (hasStaleShipMetadata(modId)) {
                log.info("isUpdateNeeded: found stale metadata format for mod: {}", modId);
                return true;
            }
        }

        // If the database contains extra mods that physically no longer exist, an update is needed to purge them
        for (Map.Entry<String, DatabaseQueryService.ModInfo> entry : existingModsMap.entrySet()) {
            if (!Files.exists(Path.of(entry.getValue().folderPath()))) {
                log.info("isUpdateNeeded: found physically deleted mod in DB: {}", entry.getKey());
                return true;
            }
        }

        return false;
    }

    private static boolean hasStaleShipMetadata(String modId) {
        List<shipeditor.persistence.database.IndexedFile> modShipFiles =
                DatabaseQueryService.getFilesByModAndType(modId, StringConstants.SHIP_TYPE);
        if (!modShipFiles.isEmpty()) {
            shipeditor.persistence.database.IndexedFile sample = modShipFiles.get(0);
            if (sample.getEntityName() != null && sample.getEntityId() != null
                    && sample.getEntityName().equals(sample.getEntityId())) {
                return true;
            }
            if (sample.getMetadataJson() == null) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> getActiveNonLibModFolders() {
        List<Path> targets = new ArrayList<>();
        List<Path> modFolders = SettingsManager.getAllModFolders();
        for (Path modFolder : modFolders) {
            if (LibModFilter.isLibMod(modFolder)) {
                continue;
            }
            Path fileNamePath = modFolder.getFileName();
            if (fileNamePath != null) {
                String folderName = fileNamePath.toString();
                if (!SettingsManager.isModActive(folderName)) {
                    continue;
                }
            }
            targets.add(modFolder);
        }
        return targets;
    }

    /**
     * Executes the scanning process. To be run asynchronously during startup.
     */
    public static void scanAndIndexAll() {
        scanAndIndexAll(true);
    }

    /**
     * Executes the scanning process. To be run asynchronously during startup.
     */
    public static void scanAndIndexAll(boolean promptUser) {
        boolean firstRun = !DatabaseManager.isDatabaseValid() || DatabaseManager.isDatabaseEmpty();
        log.info("Checking if background mod indexing is needed. First run: {}", firstRun);
        long scanStartTime = System.currentTimeMillis();

        // Initialize the schema and tables if they don't exist
        DatabaseManager.initializeDatabase();

        Settings settings = SettingsManager.getSettings();
        if (settings == null) {
            log.error(StringManager.getString("SETTINGS_NOT_INITIALIZED_INDEX_CANCEL"));
            return;
        }

        if (!isUpdateNeeded(firstRun, settings)) {
            log.info(StringManager.getString("DB_INDEX_UP_TO_DATE_SKIP"));
            return;
        }

        // Silently resolve and log instead of prompting the user
        log.info(firstRun ? "Initial database indexing scan starting..." : "Changes detected in mod folders. Updating database index in the background...");

        List<Path> targetsToScan = getActiveNonLibModFolders();

        Map<String, DatabaseQueryService.ModInfo> existingModsMap = DatabaseQueryService.getScannedModsMap();

        // Purge obsolete/deleted mod packages from the database index
        for (Map.Entry<String, DatabaseQueryService.ModInfo> entry : existingModsMap.entrySet()) {
            String dbModId = entry.getKey();
            if (!Files.exists(Path.of(entry.getValue().folderPath()))) {
                if (SettingsManager.isDeveloperModeEnabled()) {
                    log.info("Purging physically deleted mod from database index: {}", dbModId);
                }
                DatabaseQueryService.deleteMod(dbModId);
            }
        }

        long currentScanTime = System.currentTimeMillis();

        for (Path folder : targetsToScan) {
            Path fileNamePath = folder.getFileName();
            if (fileNamePath == null) continue;
            String modId = fileNamePath.toString();
            if (SettingsManager.isCoreFolder(folder)) {
                continue;
            }

            DatabaseQueryService.ModInfo dbModInfo = existingModsMap.get(modId);
            boolean needsScan = firstRun || dbModInfo == null || !folder.toAbsolutePath().toString().equals(dbModInfo.folderPath());
            if (!needsScan && dbModInfo != null) {
                try {
                    needsScan = hasNewerRelevantFile(folder, dbModInfo.lastScanned());
                } catch (IOException e) {
                    log.warn("Failed to check last modified times for mod folder: {}", folder, e);
                    needsScan = true;
                }
            }
            // Migration check: detect stale entity names or missing metadata_json.
            boolean staleMetadata = false;
            if (!needsScan) {
                staleMetadata = hasStaleShipMetadata(modId);
                if (staleMetadata) {
                    log.info("Detected stale entity metadata format for mod '{}', triggering re-scan.", modId);
                    needsScan = true;
                }
            }

            if (needsScan) {
                if (SettingsManager.isDeveloperModeEnabled()) {
                    log.info("Scanning mod package: {}", modId);
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    conn.setAutoCommit(false); // Enable Batch Mode for this mod
                    try {
                        // Upsert mod metadata row
                        String upsertModSql = DatabaseQueryService.UPSERT_MOD_SQL;
                        try (PreparedStatement pstmt = conn.prepareStatement(upsertModSql)) {
                            pstmt.setString(1, modId);
                            pstmt.setString(2, modId.equals("starsector-core") ? "Starsector Core" : modId);
                            pstmt.setString(3, folder.toAbsolutePath().toString());
                            pstmt.setLong(4, currentScanTime);
                            pstmt.executeUpdate();
                        }

                        scanModFolder(conn, folder, modId, staleMetadata);
                        
                        conn.commit();
                        // Invalidate CSV cache for this mod so next CSV load re-parses from disk.
                        DatabaseQueryService.deleteCsvCacheForMod(modId);
                        log.info("Successfully indexed mod package: {}", modId);
                    } catch (Exception e) {
                        conn.rollback();
                        log.error("Failed to index mod package: {}, rolled back transaction", modId, e);
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    log.error("Database connection error while indexing mod: {}", modId, e);
                }
            } else {
                log.debug("Mod package is up to date, skipping scan: {}", modId);
            }
        }
        DatabaseQueryService.clearTypeCache();
        long scanElapsed = System.currentTimeMillis() - scanStartTime;
        log.info("Background mod indexing completed in {}ms", scanElapsed);
    }

    /**
     * Scans a single mod directory recursively for ship, weapon, variant, skin, and
     * csv files.
     */
    private record ParsedFileEntry(String absPath, long diskLastModified, EntityMetadata metadata,
                                     String entityName, String type, String fileName, UUID uuid) {}

    private record FileParseRequest(File file, String ext, String type, String absPath,
                                     long diskLastModified, UUID uuid) {}

    private static void scanModFolder(Connection conn, Path modFolder, String modId, boolean forceRescan) throws SQLException {
        List<String> activePaths = new ArrayList<>();
        ObjectMapper mapper = FileUtilities.getConfigured();

        // 1. Scan and index files with specific extensions (.ship, .skin, .wpn,
        // .variant, .proj)
        Map<String, String> extensions = Map.of(
                "ship", StringConstants.SHIP_TYPE,
                "skin", StringConstants.SKIN_TYPE,
                "wpn", StringConstants.WEAPON_TYPE,
                "variant", StringConstants.VARIANT_TYPE,
                "proj", StringConstants.PROJECTILE_TYPE);

        Map<String, DatabaseQueryService.FileDbInfo> dbInfoMap = DatabaseQueryService.getFilesDbInfoMap(conn, modId);

        String upsertFileSql = DatabaseQueryService.UPSERT_INDEXED_FILE_SQL;

        Map<String, List<File>> allFiles = fetchFilesWithExtensions(modFolder, extensions.keySet());

        // Collect files that need parsing, then extract metadata in parallel.
        List<FileParseRequest> parseRequests = new ArrayList<>();
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            String ext = entry.getKey();
            String type = entry.getValue();

            List<File> files = allFiles.getOrDefault(ext, Collections.emptyList());
            for (File file : files) {
                String absPath = file.getAbsolutePath();
                activePaths.add(absPath);

                long diskLastModified = file.lastModified();
                DatabaseQueryService.FileDbInfo dbInfo = dbInfoMap.get(absPath);
                Long dbLastModified = dbInfo != null ? dbInfo.lastModified() : null;

                if (forceRescan || dbLastModified == null || diskLastModified != dbLastModified) {
                    UUID uuid = uuidOrNew(dbInfo);
                    parseRequests.add(new FileParseRequest(file, ext, type, absPath, diskLastModified, uuid));
                }
            }
        }

        // Parallel metadata extraction — ObjectMapper is thread-safe for reads.
        List<ParsedFileEntry> parsedEntries = parseRequests.parallelStream()
                .map(req -> {
                    try {
                        EntityMetadata metadata = extractEntityMetadata(req.file, req.type, mapper);
                        String fileBaseName = req.file.getName().replace("." + req.ext, "");
                        String entityName = metadata.hullName() != null && !metadata.hullName().isBlank()
                                ? metadata.hullName() : fileBaseName;
                        return new ParsedFileEntry(req.absPath, req.diskLastModified, metadata,
                                entityName, req.type, req.file.getName(), req.uuid);
                    } catch (Exception e) {
                        log.error("Failed to index file: {}", req.absPath, e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        // Sequential JDBC batch insert from pre-parsed results.
        try (PreparedStatement pstmt = conn.prepareStatement(upsertFileSql)) {
            int batchCount = 0;
            for (ParsedFileEntry parsed : parsedEntries) {
                pstmt.setString(1, parsed.uuid.toString());
                pstmt.setString(2, modId);
                pstmt.setString(3, parsed.metadata.id() != null ? parsed.metadata.id() : parsed.entityName);
                pstmt.setString(4, parsed.entityName);
                pstmt.setString(5, parsed.type);
                pstmt.setString(6, parsed.fileName);
                pstmt.setString(7, parsed.absPath);
                pstmt.setLong(8, parsed.diskLastModified);
                pstmt.setString(9, "");
                pstmt.setString(10, parsed.metadata.spritePath());
                pstmt.setString(11, parsed.metadata.designation());
                String metaValue = parsed.metadata.metaValue() != null ? parsed.metadata.metaValue() : "";
                pstmt.setString(12, metaValue);
                pstmt.addBatch();
                batchCount++;
                if (batchCount % 500 == 0) {
                    pstmt.executeBatch();
                }
            }

            // 2. Scan and index relevant data CSVs (hull_mods, ship_systems, wing_data)
            scanCSVs(modFolder, modId, pstmt, activePaths, dbInfoMap);

            // Execute batched inserts
            pstmt.executeBatch();
        }

        // Clean up any database references for files that were deleted from disk
        DatabaseQueryService.deleteOrphanedFiles(conn, modId, activePaths);
    }

    private static void scanCSVs(Path modFolder, String modId, PreparedStatement pstmt,
            List<String> activePaths, Map<String, DatabaseQueryService.FileDbInfo> dbInfoMap) throws SQLException {

        // CSV data files
        Map<Path, String> fileTargets = new LinkedHashMap<>();
        fileTargets.put(Paths.get("data", StringConstants.HULLS, "ship_data.csv"), StringConstants.SHIP_CSV_TYPE);
        fileTargets.put(Paths.get("data", "weapons", "weapon_data.csv"), StringConstants.WEAPON_CSV_TYPE);
        fileTargets.put(Paths.get("data", "hullmods", "hull_mods.csv"), StringConstants.HULLMOD_CSV_TYPE);
        fileTargets.put(Paths.get("data", "shipsystems", "ship_systems.csv"), StringConstants.SHIPSYSTEM_CSV_TYPE);
        fileTargets.put(Paths.get("data", StringConstants.HULLS, "wing_data.csv"), StringConstants.WING_CSV_TYPE);
        // Style JSON config files
        fileTargets.put(Paths.get("data", StringConstants.CONFIG, "engine_styles.json"), StringConstants.ENGINE_STYLE_JSON_TYPE);
        fileTargets.put(Paths.get("data", StringConstants.CONFIG, "hull_styles.json"), StringConstants.HULL_STYLE_JSON_TYPE);

        for (Map.Entry<Path, String> entry : fileTargets.entrySet()) {
            Path relativePath = entry.getKey();
            String type = entry.getValue();

            Path fullPath = modFolder.resolve(relativePath);
            if (Files.exists(fullPath) && Files.isRegularFile(fullPath)) {
                try {
                    String absPath = fullPath.toAbsolutePath().toString();
                    activePaths.add(absPath);

                    File file = fullPath.toFile();
                    long diskLastModified = file.lastModified();
                    DatabaseQueryService.FileDbInfo dbInfo = dbInfoMap.get(absPath);
                    Long dbLastModified = dbInfo != null ? dbInfo.lastModified() : null;

                    if (dbLastModified == null || diskLastModified != dbLastModified) {
                        UUID uuid = uuidOrNew(dbInfo);

                        pstmt.setString(1, uuid.toString());
                        pstmt.setString(2, modId);
                        pstmt.setString(3, file.getName().replace(".csv", ""));
                        pstmt.setString(4, file.getName().replace(".csv", ""));
                        pstmt.setString(5, type);
                        pstmt.setString(6, file.getName());
                        pstmt.setString(7, absPath);
                        pstmt.setLong(8, diskLastModified);
                        pstmt.setString(9, "");
                        pstmt.setString(10, null);
                        pstmt.setString(11, null);
                        
                        String metadataJson = null;
                        if (StringConstants.SHIP_CSV_TYPE.equals(type) || StringConstants.WEAPON_CSV_TYPE.equals(type)) {
                            metadataJson = extractCSVMetadata(file, type);
                        }
                        pstmt.setString(12, metadataJson);
                        pstmt.addBatch();
                    }
                } catch (Exception e) {
                    log.error("Failed to index CSV: {}", fullPath, e);
                }
            }
        }
    }

    public record EntityMetadata(String id, String spritePath, String designation, String hullName, String metaValue) {
        public EntityMetadata(String id, String spritePath, String designation, String hullName) {
            this(id, spritePath, designation, hullName, null);
        }
    }

    public static EntityMetadata extractEntityMetadata(File file, String type, ObjectMapper mapper) {
        String keyToFind;
        String spriteKey = null;
        String designationKey = null;
        String hullNameKey = null;
        String metaKey = null;

        switch (type) {
            case StringConstants.SHIP_TYPE -> {
                keyToFind = "hullId";
                spriteKey = "spriteName";
                hullNameKey = "hullName";
                metaKey = "hullSize";
            }
            case StringConstants.SKIN_TYPE -> {
                keyToFind = "skinHullId";
                spriteKey = "spriteName";
                hullNameKey = "hullName";
                designationKey = "hullDesignation";
                metaKey = "baseHullId";
            }
            case StringConstants.VARIANT_TYPE -> {
                keyToFind = "variantId";
                hullNameKey = "displayName";
                metaKey = "hullId";
            }
            case StringConstants.WEAPON_TYPE -> {
                keyToFind = "id";
                spriteKey = "turretSprite";
            }
            default -> keyToFind = "id";
        }

        String id = null;
        String spritePath = null;
        String designation = null;
        String hullName = null;
        String metaValue = null;

        try (com.fasterxml.jackson.core.JsonParser parser = mapper.getFactory().createParser(shipeditor.parsing.JsonProcessor.straightenMalformed(file))) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == com.fasterxml.jackson.core.JsonToken.FIELD_NAME
                        && parser.getParsingContext().getParent() != null
                        && parser.getParsingContext().getParent().inRoot()) {
                    String currentName = parser.currentName();
                    if (keyToFind.equals(currentName) && id == null) {
                        parser.nextToken();
                        id = parser.getText();
                    } else if (spriteKey != null && spriteKey.equals(currentName) && spritePath == null) {
                        parser.nextToken();
                        spritePath = parser.getText();
                    } else if (((designationKey != null && designationKey.equals(currentName)) || "designation".equals(currentName)) && designation == null) {
                        parser.nextToken();
                        designation = parser.getText();
                    } else if (hullNameKey != null && hullNameKey.equals(currentName) && hullName == null) {
                        parser.nextToken();
                        hullName = parser.getText();
                    } else if (metaKey != null && metaKey.equals(currentName) && metaValue == null) {
                        parser.nextToken();
                        metaValue = parser.getText();
                    }
                }
                // Early exit once all needed fields are found
                if (id != null && (spriteKey == null || spritePath != null)
                        && (designationKey == null || designation != null)
                        && (hullNameKey == null || hullName != null)
                        && (metaKey == null || metaValue != null)) {
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("Streaming JSON parse failed for id extraction: {}", file.getName(), e);
        }

        if (id == null) {
            // Fallback: extract ID using filename matching
            id = file.getName().replace(".ship", "")
                .replace(".skin", "")
                .replace(".wpn", "")
                .replace(".variant", "")
                .replace(".proj", "");
        }
        
        return new EntityMetadata(id, spritePath, designation, hullName, metaValue);
    }

    public static Map<String, List<File>> fetchFilesWithExtensions(Path target, Set<String> exts) {
        Map<String, List<File>> filesMap = new HashMap<>();
        for (String ext : exts) {
            filesMap.put(ext, new ArrayList<>());
        }

        Path walkTarget = resolveDataWalkTarget(target);
        try (Stream<Path> pathStream = Files.walk(walkTarget)) {
            pathStream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path fileNamePath = path.getFileName();
                        if (fileNamePath == null) return;
                        String name = fileNamePath.toString();
                        int dotIndex = name.lastIndexOf('.');
                        if (dotIndex > 0 && dotIndex < name.length() - 1) {
                            String fileExt = name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
                            if (exts.contains(fileExt)) {
                                filesMap.get(fileExt).add(path.toFile());
                            }
                        }
                    });
        } catch (IOException exception) {
            log.error(StringManager.getString("FAILED_WALK_FILES_INDEXING"), target, exception);
        }
        return filesMap;
    }


}
