package shipeditor.persistence.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("DatabaseManager")
class DatabaseQueryServiceTest {

    @TempDir
    Path tempDir;

    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_query_service.sqlite");
        DatabaseQueryService.clearTypeCache();
        CoreIndexManager.reset();

        setDatabaseManagerField("databaseFilePath", dbPath);
        DatabaseManager.deleteDatabase();
        DatabaseManager.initializeDatabase();
    }

    @AfterEach
    void tearDown() throws Exception {
        DatabaseManager.deleteDatabase();
        setDatabaseManagerField("databaseFilePath", null);
        DatabaseQueryService.clearTypeCache();
        CoreIndexManager.reset();
    }

    private void setDatabaseManagerField(String fieldName, Object value) throws Exception {
        Field field = DatabaseManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    @Test
    void testModInsertionAndDeletion() throws SQLException {
        String modId = "test_mod_query";
        String folderPath = tempDir.toAbsolutePath().toString();
        long scanTime = System.currentTimeMillis();

        // Ensure initially empty
        Map<String, DatabaseQueryService.ModInfo> initialMods = DatabaseQueryService.getScannedModsMap();
        assertFalse(initialMods.containsKey(modId));

        // Insert mod
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO mods (id, name, folder_path, last_scanned) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, modId);
                pstmt.setString(2, "Test Mod");
                pstmt.setString(3, folderPath);
                pstmt.setLong(4, scanTime);
                pstmt.executeUpdate();
            }
        }

        // Verify retrieval
        Map<String, DatabaseQueryService.ModInfo> updatedMods = DatabaseQueryService.getScannedModsMap();
        assertTrue(updatedMods.containsKey(modId));
        assertEquals(folderPath, updatedMods.get(modId).folderPath());
        assertEquals(scanTime, updatedMods.get(modId).lastScanned());

        // Delete mod
        DatabaseQueryService.deleteMod(modId);

        // Verify deletion
        Map<String, DatabaseQueryService.ModInfo> finalMods = DatabaseQueryService.getScannedModsMap();
        assertFalse(finalMods.containsKey(modId));
    }

    @Test
    void testDeleteOrphanedFiles() throws SQLException {
        String modId = "test_mod_orphans";
        String validFile = tempDir.resolve("valid.ship").toAbsolutePath().toString();
        String orphanFile = tempDir.resolve("orphan.ship").toAbsolutePath().toString();

        try (Connection conn = DatabaseManager.getConnection()) {
            // Insert mod
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO mods (id, name, folder_path, last_scanned) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, modId);
                pstmt.setString(2, "Test Mod Orphans");
                pstmt.setString(3, tempDir.toAbsolutePath().toString());
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            }

            // Insert 2 files
            String upsertFileSql = """
                    INSERT INTO indexed_files (uuid, mod_id, entity_id, entity_name, entity_type, file_name, file_path, last_modified, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(upsertFileSql)) {
                // File 1 (Valid)
                pstmt.setString(1, UUID.randomUUID().toString());
                pstmt.setString(2, modId);
                pstmt.setString(3, "valid_ship");
                pstmt.setString(4, "valid_ship");
                pstmt.setString(5, "ship");
                pstmt.setString(6, "valid.ship");
                pstmt.setString(7, validFile);
                pstmt.setLong(8, 100L);
                pstmt.setString(9, null);
                pstmt.addBatch();

                // File 2 (Orphan)
                pstmt.setString(1, UUID.randomUUID().toString());
                pstmt.setString(2, modId);
                pstmt.setString(3, "orphan_ship");
                pstmt.setString(4, "orphan_ship");
                pstmt.setString(5, "ship");
                pstmt.setString(6, "orphan.ship");
                pstmt.setString(7, orphanFile);
                pstmt.setLong(8, 100L);
                pstmt.setString(9, null);
                pstmt.addBatch();

                pstmt.executeBatch();
            }

            // Delete orphans (only passing validFile as active)
            DatabaseQueryService.deleteOrphanedFiles(conn, modId, List.of(validFile));
        }

        // Verify only valid file remains
        try (Connection conn = DatabaseManager.getConnection()) {
            Map<String, DatabaseQueryService.FileDbInfo> dbInfoMap = DatabaseQueryService.getFilesDbInfoMap(conn, modId);
            assertEquals(1, dbInfoMap.size());
            assertTrue(dbInfoMap.containsKey(validFile));
            assertFalse(dbInfoMap.containsKey(orphanFile));
        }
    }
}
