package shipeditor.persistence.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.IndexScannerTask;
import shipeditor.utility.text.StringConstants;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("DatabaseManager")
class JsonDatabaseIntegrationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper mapper;
    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        mapper = FileUtilities.getConfigured();
        dbPath = tempDir.resolve("test_ship_editor_database.sqlite");
        DatabaseQueryService.clearTypeCache();
        CoreIndexManager.reset();

        DatabaseManager.deleteDatabase();
        setDatabaseManagerField("databaseFilePath", dbPath);
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
    void testExtractShipMetadata() throws IOException {
        String json = """
                {
                    "hullName": "Test Ship",
                    "hullId": "test_ship_id",
                    "hullSize": "FRIGATE",
                    "spriteName": "graphics/ships/test_ship.png",
                    "style": "HIGH_TECH"
                }
                """;
        File shipFile = tempDir.resolve("test.ship").toFile();
        Files.writeString(shipFile.toPath(), json, StandardCharsets.UTF_8);

        IndexScannerTask.EntityMetadata metadata = IndexScannerTask.extractEntityMetadata(shipFile, StringConstants.SHIP_TYPE, mapper);

        assertNotNull(metadata);
        assertEquals("test_ship_id", metadata.id());
        assertEquals("graphics/ships/test_ship.png", metadata.spritePath());
        assertEquals("FRIGATE", metadata.metaValue());
    }

    @Test
    void testExtractWeaponMetadataWithQuirks() throws IOException {
        // Unquoted enum (ROUGH) and trailing commas
        String json = """
                {
                    "id": "test_weapon",
                    "specClass": "projectile",
                    "type": ENERGY,
                    "size": LARGE,
                    "turretSprite": "graphics/weapons/test_wpn.png",
                    "textureType": ROUGH,
                }
                """;
        File wpnFile = tempDir.resolve("test.wpn").toFile();
        Files.writeString(wpnFile.toPath(), json, StandardCharsets.UTF_8);

        IndexScannerTask.EntityMetadata metadata = IndexScannerTask.extractEntityMetadata(wpnFile, StringConstants.WEAPON_TYPE, mapper);

        assertNotNull(metadata);
        assertEquals("test_weapon", metadata.id());
        assertEquals("graphics/weapons/test_wpn.png", metadata.spritePath());
    }

    @Test
    void testInsertAndQueryIndexedFile() throws SQLException, IOException {
        String modId = "test_mod";
        String uuid = UUID.randomUUID().toString();
        String absPath = tempDir.resolve("test.ship").toAbsolutePath().toString();

        try (Connection conn = DatabaseManager.getConnection()) {
            // Insert mod
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO mods (id, name, folder_path, last_scanned) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, modId);
                pstmt.setString(2, "Test Mod");
                pstmt.setString(3, tempDir.toAbsolutePath().toString());
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            }

            // Insert indexed file
            String upsertFileSql = """
                    INSERT INTO indexed_files (uuid, mod_id, entity_id, entity_name, entity_type, file_name, file_path, last_modified, file_hash, sprite_path, designation, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement pstmt = conn.prepareStatement(upsertFileSql)) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, modId);
                pstmt.setString(3, "test_ship_id");
                pstmt.setString(4, "test_ship");
                pstmt.setString(5, StringConstants.SHIP_TYPE);
                pstmt.setString(6, "test.ship");
                pstmt.setString(7, absPath);
                pstmt.setLong(8, 1000L);
                pstmt.setString(9, "");
                pstmt.setString(10, "graphics/ships/test_ship.png");
                pstmt.setString(11, null);
                pstmt.setString(12, null);
                pstmt.executeUpdate();
            }
        }

        // Query using DatabaseQueryService
        try (Connection conn = DatabaseManager.getConnection()) {
            Map<String, DatabaseQueryService.FileDbInfo> dbInfoMap = DatabaseQueryService.getFilesDbInfoMap(conn, modId);
            assertNotNull(dbInfoMap);
            assertTrue(dbInfoMap.containsKey(absPath));
            
            DatabaseQueryService.FileDbInfo info = dbInfoMap.get(absPath);
            assertEquals(uuid, info.uuid().toString());
            assertEquals(1000L, info.lastModified());
        }
    }
}
