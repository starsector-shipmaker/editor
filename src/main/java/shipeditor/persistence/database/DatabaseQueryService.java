package shipeditor.persistence.database;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import shipeditor.persistence.SettingsManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles all SQLite queries, indexing operations, and lookups.
 * Provides both synchronous database execution and asynchronous futures.
 *
 * @author Shadow
 */
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"})
public final class DatabaseQueryService {

    public record FileInfo(long lastModified, String fileHash) {}
    public record FileDbInfo(long lastModified, String fileHash, UUID uuid) {}

    private static boolean isModActive(String modId) {
        return SettingsManager.isModActive(modId);
    }

    private static final Map<String, List<IndexedFile>> TYPE_CACHE = new ConcurrentHashMap<>();

    private DatabaseQueryService() {}

    /**
     * Clears the in-memory type query cache. Must be called after any indexing
     * operation completes or when the active mod list changes.
     */
    public static void clearTypeCache() {
        TYPE_CACHE.clear();
    }

    // --- Synchronous Modifications (Used by Background Scanner) ---

    public static void upsertMod(String id, String name, String folderPath, long lastScanned) {
        if (id == null || folderPath == null) {
            return;
        }
        String sql = """
            INSERT INTO mods (id, name, folder_path, last_scanned)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                folder_path = excluded.folder_path,
                last_scanned = excluded.last_scanned;
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            pstmt.setString(2, name != null ? name : id);
            pstmt.setString(3, folderPath);
            pstmt.setLong(4, lastScanned);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert mod: {}", id, e);
        }
    }

    public static void ensureModExists(String id, String name, String folderPath) {
        if (id == null || folderPath == null) {
            return;
        }
        String sql = """
            INSERT INTO mods (id, name, folder_path, last_scanned)
            VALUES (?, ?, ?, 0)
            ON CONFLICT(id) DO NOTHING;
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, name != null ? name : id);
            pstmt.setString(3, folderPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to ensure mod exists: {}", id, e);
        }
    }

    public static void deleteFilesByModId(String modId) {
        if (modId == null) {
            return;
        }
        String deleteFilesSql = "DELETE FROM indexed_files WHERE mod_id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteFilesSql)) {
            pstmt.setString(1, modId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete files for mod: {}", modId, e);
        }
    }

    public static void deleteMod(String id) {
        if (id == null) {
            return;
        }
        deleteFilesByModId(id);
        String sql = "DELETE FROM mods WHERE id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            log.info("Deleted mod package from database: {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete mod: {}", id, e);
        }
    }

    public record ModInfo(long lastScanned, String folderPath) {}

    public static Map<String, ModInfo> getScannedModsMap() {
        Map<String, ModInfo> mods = new HashMap<>();
        String sql = "SELECT id, last_scanned, folder_path FROM mods;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                mods.put(rs.getString("id"), new ModInfo(rs.getLong("last_scanned"), rs.getString("folder_path")));
            }
        } catch (SQLException e) {
            log.error("Failed to query mods list", e);
        }
        return mods;
    }

    public static void upsertFiles(List<IndexedFile> files) {
        String query = """
            INSERT INTO indexed_files (uuid, mod_id, entity_id, entity_name, entity_type, file_name, file_path, last_modified, file_hash, sprite_path, designation, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                mod_id = excluded.mod_id,
                entity_id = excluded.entity_id,
                entity_name = excluded.entity_name,
                entity_type = excluded.entity_type,
                file_name = excluded.file_name,
                file_path = excluded.file_path,
                last_modified = excluded.last_modified,
                file_hash = excluded.file_hash,
                sprite_path = excluded.sprite_path,
                designation = excluded.designation,
                metadata_json = excluded.metadata_json;
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            conn.setAutoCommit(false);
            try {
                for (IndexedFile file : files) {
                    pstmt.setString(1, file.getUuid().toString());
                    pstmt.setString(2, file.getModId());
                    pstmt.setString(3, file.getEntityId());
                    pstmt.setString(4, file.getEntityName());
                    pstmt.setString(5, file.getEntityType());
                    pstmt.setString(6, file.getFileName());
                    pstmt.setString(7, file.getFilePath().toString());
                    pstmt.setLong(8, file.getLastModified());
                    pstmt.setString(9, file.getFileHash());
                    pstmt.setString(10, file.getSpritePath());
                    pstmt.setString(11, file.getDesignation());
                    pstmt.setString(12, file.getMetadataJson());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Failed to upsert indexed files", e);
        }
    }

    public static void deleteIndexedFile(UUID uuid) {
        String sql = "DELETE FROM indexed_files WHERE uuid = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete indexed file: {}", uuid, e);
        }
    }

    /**
     * Purges database records for files that were deleted from disk.
     */
    public static void deleteOrphanedFiles(Connection conn, String modId, List<String> activePaths) {
        if (activePaths == null || activePaths.isEmpty()) {
            String sql = "DELETE FROM indexed_files WHERE mod_id = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, modId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                log.error("Failed to delete all files for mod: {}", modId, e);
            }
            return;
        }
        java.util.Set<String> activeSet = new java.util.HashSet<>(activePaths);
        List<String> toDelete = new ArrayList<>();

        String selectSql = "SELECT file_path FROM indexed_files WHERE mod_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, modId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("file_path");
                    if (!activeSet.contains(path)) {
                        toDelete.add(path);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query files for orphan cleanup: {}", modId, e);
            return;
        }

        if (!toDelete.isEmpty()) {
            String deleteSql = "DELETE FROM indexed_files WHERE mod_id = ? AND file_path = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                int count = 0;
                for (String path : toDelete) {
                    pstmt.setString(1, modId);
                    pstmt.setString(2, path);
                    pstmt.addBatch();
                    count++;
                    if (count % 500 == 0) {
                        pstmt.executeBatch();
                    }
                }
                pstmt.executeBatch();
            } catch (SQLException e) {
                log.error("Failed to delete orphaned files for mod: {}", modId, e);
            }
        }
    }

    // --- Synchronous Lookups (Used by GameDataRepository) ---

    private static List<String> getActiveModIds() {
        List<String> activeIds = new ArrayList<>();
        Map<String, ModInfo> allMods = getScannedModsMap();
        for (String modId : allMods.keySet()) {
            if (!"starsector-core".equals(modId) && isModActive(modId)) {
                activeIds.add(modId);
            }
        }
        return activeIds;
    }

    public static String getFileNameForEntity(String entityId, String type) {
        if (entityId == null || type == null) {
            return "";
        }
        IndexedFile file = getFileByEntityId(entityId, type);
        return file != null ? file.getFileName() : "";
    }

    public static IndexedFile getFileByEntityId(String entityId, String type) {
        if (entityId == null || type == null) {
            return null;
        }

        List<String> activeMods = getActiveModIds();
        if (!activeMods.isEmpty()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM indexed_files WHERE entity_id = ? AND entity_type = ? AND mod_id IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(activeMods.size(), "?")));
            sql.append(") LIMIT 1;");

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

                pstmt.setString(1, entityId);
                pstmt.setString(2, type);
                int paramIndex = 3;
                for (String modId : activeMods) {
                    pstmt.setString(paramIndex++, modId);
                }

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToIndexedFile(rs);
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to lookup file by entity: {} ({})", entityId, type, e);
            }
        }
        
        return CoreIndexManager.getFileByEntityId(entityId, type);
    }

    public static Path getFilePathForEntity(String entityId, String type) {
        if (entityId == null || type == null) {
            return null;
        }
        IndexedFile file = getFileByEntityId(entityId, type);
        return file != null ? file.getFilePath() : null;
    }

    public static IndexedFile getFileByPath(String path) {
        if (path == null) {
            return null;
        }
        IndexedFile coreFile = CoreIndexManager.getFileByPath(path);
        if (coreFile != null) return coreFile;

        String sql = "SELECT * FROM indexed_files WHERE file_path = ? LIMIT 1;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToIndexedFile(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to lookup file by path: {}", path, e);
        }
        return null;
    }

    public static Map<String, FileInfo> getFilesInfoMap(Connection conn, String modId) {
        Map<String, FileInfo> fileMap = new HashMap<>();
        String sql = "SELECT file_path, last_modified, file_hash FROM indexed_files WHERE mod_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, modId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    fileMap.put(rs.getString("file_path"), new FileInfo(rs.getLong("last_modified"), rs.getString("file_hash")));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query files map for mod: {}", modId, e);
        }
        return fileMap;
    }

    public static Map<String, UUID> getFilesUuidMap(Connection conn, String modId) {
        Map<String, UUID> uuidMap = new HashMap<>();
        String sql = "SELECT file_path, uuid FROM indexed_files WHERE mod_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    uuidMap.put(rs.getString("file_path"), UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query UUIDs map for mod: {}", modId, e);
        }
        return uuidMap;
    }

    public static Map<String, FileDbInfo> getFilesDbInfoMap(Connection conn, String modId) {
        Map<String, FileDbInfo> fileMap = new HashMap<>();
        String sql = "SELECT file_path, last_modified, file_hash, uuid FROM indexed_files WHERE mod_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    fileMap.put(rs.getString("file_path"), new FileDbInfo(
                        rs.getLong("last_modified"),
                        rs.getString("file_hash"),
                        UUID.fromString(rs.getString("uuid"))
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query files DB info for mod: {}", modId, e);
        }
        return fileMap;
    }

    // --- Synchronous Lookups (Used by loading actions) ---

    /**
     * Returns all indexed files of a given type across all mods.
     * Results are cached in memory after the first query; call {@link #clearTypeCache()}
     * after re-indexing to refresh.
     */
    public static List<IndexedFile> getFilesByType(String type) {
        if (type == null) {
            return new ArrayList<>();
        }
        List<IndexedFile> cached = TYPE_CACHE.get(type);
        if (cached != null) {
            return cached;
        }

        Map<String, IndexedFile> deduplicated = new LinkedHashMap<>();
        for (IndexedFile coreFile : CoreIndexManager.getFilesByType(type)) {
            if (coreFile != null && coreFile.getFilePath() != null) {
                try {
                    deduplicated.put(coreFile.getFilePath().toFile().getCanonicalPath(), coreFile);
                } catch (java.io.IOException e) {
                    deduplicated.put(coreFile.getFilePath().toAbsolutePath().normalize().toString(), coreFile);
                }
            }
        }

        List<String> activeMods = getActiveModIds();
        if (!activeMods.isEmpty()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM indexed_files WHERE entity_type = ? AND mod_id IN (");
            sql.append(String.join(",", java.util.Collections.nCopies(activeMods.size(), "?")));
            sql.append(") ORDER BY mod_id ASC, entity_id ASC;");

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

                pstmt.setString(1, type);
                int paramIndex = 2;
                for (String modId : activeMods) {
                    pstmt.setString(paramIndex++, modId);
                }

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        IndexedFile file = mapRowToIndexedFile(rs);
                        if (file != null && file.getFilePath() != null) {
                            try {
                                deduplicated.put(file.getFilePath().toFile().getCanonicalPath(), file);
                            } catch (java.io.IOException e) {
                                deduplicated.put(file.getFilePath().toAbsolutePath().normalize().toString(), file);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to query files by type: {}", type, e);
            }
        }

        List<IndexedFile> results = new ArrayList<>(deduplicated.values());
        TYPE_CACHE.put(type, results);
        return results;
    }

    /**
     * Returns all indexed files of a given type, grouped by mod_id. Useful for per-package loading.
     */
    public static Map<String, List<IndexedFile>> getFilesByTypeGroupedByMod(String type) {
        Map<String, List<IndexedFile>> grouped = new LinkedHashMap<>();
        if (type == null) {
            return grouped;
        }
        for (IndexedFile file : getFilesByType(type)) {
            if (file != null && file.getModId() != null) {
                grouped.computeIfAbsent(file.getModId(), k -> new ArrayList<>()).add(file);
            }
        }
        return grouped;
    }

    // --- Asynchronous Lookups (Used by UI for instant rendering) ---



    public static CompletableFuture<List<IndexedFile>> getFilesByTypeAsync(String type) {
        return CompletableFuture.supplyAsync(() -> getFilesByType(type));
    }

    public static List<IndexedFile> getFilesByModAndType(String modId, String type) {
        if ("starsector-core".equals(modId)) {
            return CoreIndexManager.getFilesByType(type);
        }
        if (!isModActive(modId)) return new ArrayList<>();

        List<IndexedFile> results = new ArrayList<>();
        String sql = "SELECT * FROM indexed_files WHERE mod_id = ? AND entity_type = ? ORDER BY entity_id ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, modId);
            pstmt.setString(2, type);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToIndexedFile(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query files by mod and type: {}, {}", modId, type, e);
        }
        return results;
    }

    public static CompletableFuture<List<IndexedFile>> getFilesByModAndTypeAsync(String modId, String type) {
        return CompletableFuture.supplyAsync(() -> getFilesByModAndType(modId, type));
    }

    public static List<IndexedFile> getFilesByModId(String modId) {
        List<IndexedFile> results = new ArrayList<>();
        String sql = "SELECT * FROM indexed_files WHERE mod_id = ? ORDER BY entity_type ASC, entity_id ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToIndexedFile(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query indexed_files for mod_id: {}", modId, e);
        }
        return results;
    }

    // --- Helper Methods ---

    private static IndexedFile mapRowToIndexedFile(ResultSet rs) throws SQLException {
        return IndexedFile.builder()
                .uuid(UUID.fromString(rs.getString("uuid")))
                .modId(rs.getString("mod_id"))
                .entityId(rs.getString("entity_id"))
                .entityName(rs.getString("entity_name"))
                .entityType(rs.getString("entity_type"))
                .fileName(rs.getString("file_name"))
                .filePath(Path.of(rs.getString("file_path")))
                .lastModified(rs.getLong("last_modified"))
                .fileHash(rs.getString("file_hash"))
                .spritePath(rs.getString("sprite_path"))
                .designation(rs.getString("designation"))
                .metadataJson(rs.getString("metadata_json"))
                .build();
    }

    public static Map<String, JsonNode> getAggregatedCSVMetadata(String csvType) {
        Map<String, JsonNode> aggregated = new HashMap<>();
        List<IndexedFile> csvFiles = getFilesByType(csvType);
        ObjectMapper mapper = SettingsManager.getMapperForSettingsFile();
        
        for (IndexedFile file : csvFiles) {
            String json = file.getMetadataJson();
            if (json == null || json.isEmpty()) continue;
            
            try {
                JsonNode root = mapper.readTree(json);
                if (root.isObject()) {
                    root.fields().forEachRemaining(entry -> {
                        // Mods loaded later in the active list will overwrite earlier ones 
                        aggregated.put(entry.getKey(), entry.getValue());
                    });
                }
            } catch (Exception e) {
                log.error("Failed to parse metadata JSON for {}", file.getFileName(), e);
            }
        }
        return aggregated;
    }

    // --- CSV Cache Operations ---

    public record CsvCacheRow(String csvPath, String modId, long lastModified, String rowsJson) {}

    public static CsvCacheRow getCsvCache(Path csvPath) {
        if (csvPath == null) {
            return null;
        }
        String sql = "SELECT csv_path, mod_id, last_modified, rows_json FROM csv_cache WHERE csv_path = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, csvPath.toAbsolutePath().toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new CsvCacheRow(
                            rs.getString("csv_path"),
                            rs.getString("mod_id"),
                            rs.getLong("last_modified"),
                            rs.getString("rows_json")
                    );
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query csv_cache for: {}", csvPath, e);
        }
        return null;
    }

    public static void upsertCsvCache(Path csvPath, String modId, long lastModified, String rowsJson) {
        if (csvPath == null || modId == null || rowsJson == null) {
            return;
        }
        String sql = """
                INSERT INTO csv_cache (csv_path, mod_id, last_modified, rows_json)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(csv_path) DO UPDATE SET
                    mod_id = excluded.mod_id,
                    last_modified = excluded.last_modified,
                    rows_json = excluded.rows_json;
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, csvPath.toAbsolutePath().toString());
            pstmt.setString(2, modId);
            pstmt.setLong(3, lastModified);
            pstmt.setString(4, rowsJson);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert csv_cache for: {}", csvPath, e);
        }
    }

    public static void clearCsvCache() {
        String sql = "DELETE FROM csv_cache;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to clear csv_cache", e);
        }
    }

    public static void deleteCsvCacheForMod(String modId) {
        if (modId == null) {
            return;
        }
        String sql = "DELETE FROM csv_cache WHERE mod_id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete csv_cache for mod: {}", modId, e);
        }
    }
}
