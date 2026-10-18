package shipeditor.persistence.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import shipeditor.utility.text.StringConstants;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("DatabaseManager")
public class DataReferenceQueriesTest {

    @TempDir
    Path tempDir;

    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_reference_queries.sqlite");
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
    public void testHullmodsExist() throws SQLException {
        String modId = "test_mod_hullmod";
        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO mods (id, name, folder_path, last_scanned) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, modId);
                pstmt.setString(2, "Test Mod");
                pstmt.setString(3, tempDir.toAbsolutePath().toString());
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            }

            String insertSql = """
                    INSERT INTO indexed_files (uuid, mod_id, entity_id, entity_name, entity_type, file_name, file_path, last_modified)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, UUID.randomUUID().toString());
                pstmt.setString(2, modId);
                pstmt.setString(3, "test_hullmod");
                pstmt.setString(4, "test_hullmod");
                pstmt.setString(5, StringConstants.HULLMOD_CSV_TYPE);
                pstmt.setString(6, "hull_mods.csv");
                pstmt.setString(7, tempDir.resolve("hull_mods.csv").toAbsolutePath().toString());
                pstmt.setLong(8, 1000L);
                pstmt.executeUpdate();
            }
        }

        Map<String, List<IndexedFile>> hullmods = DatabaseQueryService.getFilesByTypeGroupedByMod(StringConstants.HULLMOD_CSV_TYPE);
        assertNotNull(hullmods);
        assertTrue(hullmods.containsKey(modId));
        assertEquals(1, hullmods.get(modId).size());
        assertEquals("test_hullmod", hullmods.get(modId).get(0).getEntityId());
    }
}
