package shipeditor.persistence.database;

import lombok.extern.log4j.Log4j2;
import shipeditor.persistence.SettingsManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection, file creation, and schema initialization.
 *
 * @author Shadow
 */
@Log4j2
public final class DatabaseManager {

    private static Path databaseFilePath;
    private static volatile HikariDataSource dataSource;
    
    private static final Object STATIC_LOCK = new Object();

    private DatabaseManager() {}

    /**
     * Gets the path to the SQLite database file, located in the same directory as the settings file.
     */
    public static Path getDatabaseFilePath() {
        synchronized (STATIC_LOCK) {
            if (databaseFilePath == null) {
                Path settingsPath = SettingsManager.getSettingsPath().toPath();
                Path settingsDir = settingsPath.getParent();
                if (settingsDir == null) {
                    settingsDir = Path.of("");
                }
                databaseFilePath = settingsDir.resolve("ship_editor_database.sqlite");
            }
            return databaseFilePath;
        }
    }

    /**
     * Checks if the SQLite database file already exists.
     */
    public static boolean databaseExists() {
        return Files.exists(getDatabaseFilePath());
    }

    /**
     * Checks if the SQLite database is valid (can connect, passes integrity check, and tables exist).
     */
    public static boolean isDatabaseValid() {
        if (!databaseExists()) {
            return false;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Check integrity
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
                if (rs.next()) {
                    String result = rs.getString(1);
                    if (!"ok".equalsIgnoreCase(result)) {
                        log.warn("Database integrity check failed: {}", result);
                        return false;
                    }
                }
            }
            
            // Check table existence by querying them
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM mods;")) {
                // Table exists
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM indexed_files;")) {
                // Table exists
            }
            return true;
        } catch (SQLException e) {
            log.warn("Database validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the database is empty (no mods registered).
     */
    public static boolean isDatabaseEmpty() {
        if (!databaseExists()) {
            return true;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM mods;")) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            log.warn("Failed to check if database is empty: {}", e.getMessage());
            return true;
        }
        return true;
    }

    /**
     * Initializes the connection pool using HikariCP and SQLiteConfig.
     */
    private static void initDataSource() {
        synchronized (STATIC_LOCK) {
            if (dataSource != null) {
                return;
            }
            Path dbPath = getDatabaseFilePath();
            try {
                Path parentDir = dbPath.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                }
            } catch (IOException e) {
                log.error("Failed to create database directory: {}", dbPath.getParent(), e);
            }
            String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString().replace("\\", "/");

            SQLiteConfig sqliteConfig = new SQLiteConfig();
            sqliteConfig.enforceForeignKeys(true);
            sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            sqliteConfig.setCacheSize(-64000);
            sqliteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY);
            sqliteConfig.setBusyTimeout(5000);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setPoolName("SQLite-Pool");
            config.setMaximumPoolSize(10);
            config.setDataSourceProperties(sqliteConfig.toProperties());

            dataSource = new HikariDataSource(config);
            ensureTablesExist();
        }
    }

    /**
     * Opens and returns a new connection to the SQLite database.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initDataSource();
        }
        return dataSource.getConnection();
    }

    public static void closeDataSource() {
        synchronized (STATIC_LOCK) {
            if (dataSource != null && !dataSource.isClosed()) {
                try {
                    log.info("Closing SQLite database connection pool...");
                    dataSource.close();
                    dataSource = null;
                    log.info("SQLite database connection pool closed successfully.");
                } catch (Exception e) {
                    log.warn("Error closing SQLite connection pool: {}", e.getMessage());
                }
            }
        }
    }

    public static void deleteDatabase() {
        try {
            synchronized (STATIC_LOCK) {
                if (dataSource != null) {
                    dataSource.close();
                    dataSource = null;
                }
            }
            Path dbPath = getDatabaseFilePath();
            Files.deleteIfExists(dbPath);
            Files.deleteIfExists(Path.of(dbPath.toString() + "-wal"));
            Files.deleteIfExists(Path.of(dbPath.toString() + "-shm"));
            log.info("Successfully deleted database files.");
        } catch (IOException e) {
            log.error("Failed to delete database file at: {}", getDatabaseFilePath(), e);
        }
    }

    /**
     * Initializes the database tables and indexes if they do not exist.
     */
    public static void initializeDatabase() {
        log.info("Initializing SQLite database at: {}", getDatabaseFilePath().toAbsolutePath());

        if (databaseExists() && !isDatabaseValid()) {
            log.warn("Database file exists but is invalid or corrupted. Deleting and recreating database...");
            deleteDatabase();
        }

        if (dataSource == null) {
            initDataSource();
        } else {
            ensureTablesExist();
        }
        log.info("Database tables and indexes verified/created successfully.");
    }

    private static void ensureTablesExist() {
        try {
            Path parentDir = getDatabaseFilePath().getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            log.error("Failed to create database directory: {}", getDatabaseFilePath().getParent(), e);
        }

        String createModsTable = """
            CREATE TABLE IF NOT EXISTS mods (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                folder_path TEXT NOT NULL,
                last_scanned INTEGER NOT NULL
            );
            """;

        String createFilesTable = """
            CREATE TABLE IF NOT EXISTS indexed_files (
                uuid TEXT PRIMARY KEY,
                mod_id TEXT,
                entity_id TEXT,
                entity_name TEXT,
                entity_type TEXT NOT NULL,
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                last_modified INTEGER NOT NULL,
                file_hash TEXT,
                sprite_path TEXT,
                designation TEXT,
                metadata_json TEXT,
                FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
            );
            """;

        String createIndexEntityId = "CREATE INDEX IF NOT EXISTS idx_entity_id ON indexed_files(entity_id);";
        String createIndexEntityType = "CREATE INDEX IF NOT EXISTS idx_entity_type ON indexed_files(entity_type);";
        String createIndexFilePath = "CREATE UNIQUE INDEX IF NOT EXISTS idx_file_path ON indexed_files(file_path);";
        String createIndexModId = "CREATE INDEX IF NOT EXISTS idx_mod_id ON indexed_files(mod_id);";
        String createIndexModType = "CREATE INDEX IF NOT EXISTS idx_mod_type ON indexed_files(mod_id, entity_type);";

        String createCsvCacheTable = """
            CREATE TABLE IF NOT EXISTS csv_cache (
                csv_path TEXT PRIMARY KEY,
                mod_id TEXT NOT NULL,
                last_modified INTEGER NOT NULL,
                rows_json TEXT NOT NULL,
                FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
            );
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute(createModsTable);
            stmt.execute(createFilesTable);
            stmt.execute(createCsvCacheTable);
            stmt.execute(createIndexEntityId);
            stmt.execute(createIndexEntityType);
            stmt.execute(createIndexFilePath);
            stmt.execute(createIndexModId);
            stmt.execute(createIndexModType);

            // Add new columns to existing databases
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(indexed_files);")) {
                boolean hasFileHash = false;
                boolean hasSpritePath = false;
                boolean hasDesignation = false;
                boolean hasMetadataJson = false;

                while(rs.next()) {
                    String colName = rs.getString("name");
                    if ("file_hash".equals(colName)) hasFileHash = true;
                    if ("sprite_path".equals(colName)) hasSpritePath = true;
                    if ("designation".equals(colName)) hasDesignation = true;
                    if ("metadata_json".equals(colName)) hasMetadataJson = true;
                }
                
                if (!hasFileHash) {
                    stmt.execute("ALTER TABLE indexed_files ADD COLUMN file_hash TEXT;");
                    log.info("Added file_hash column to indexed_files table.");
                }
                if (!hasSpritePath) {
                    stmt.execute("ALTER TABLE indexed_files ADD COLUMN sprite_path TEXT;");
                    log.info("Added sprite_path column to indexed_files table.");
                }
                if (!hasDesignation) {
                    stmt.execute("ALTER TABLE indexed_files ADD COLUMN designation TEXT;");
                    log.info("Added designation column to indexed_files table.");
                }
                if (!hasMetadataJson) {
                    stmt.execute("ALTER TABLE indexed_files ADD COLUMN metadata_json TEXT;");
                    log.info("Added metadata_json column to indexed_files table.");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to initialize database tables", e);
        }
    }

}
