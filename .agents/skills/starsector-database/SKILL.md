---
name: starsector-database
description: SQLite database design, indexing engine, transaction strategies, and query service in Starsector Ship Editor.
---

# Starsector Ship Editor — Database Design

## Skill Directory Structure

This skill is organized as follows:
- **`SKILL.md`**: Main instructions (this file).
- **`resources/`**: Configurations and schemas.
  - [schema.sql](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-database/resources/schema.sql): Raw DDL SQL script defining the DB tables.
- **`examples/`**: Code references.
  - [TransactionExample.java.txt](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-database/examples/TransactionExample.java.txt): Reference implementation of batch updates in a transaction block.
- **`scripts/`**: Tooling.
  - [sqlite_query.sh](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-database/scripts/sqlite_query.sh): Command-line helper to execute sqlite3 queries.

## 1. Overview

The editor uses an embedded **SQLite** database (`ship_editor_database.sqlite`) managed by [DatabaseManager.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/persistence/database/DatabaseManager.java). The database indexes all game and mod files by entity type and ID, enabling instant lookups without full-disk rescans on every launch.

---

## 2. Schema (DDL)

### `mods` Table
```sql
CREATE TABLE IF NOT EXISTS mods (
    id TEXT PRIMARY KEY,          -- Internal mod ID or "starsector-core"
    name TEXT NOT NULL,           -- Friendly display name
    folder_path TEXT NOT NULL,    -- Absolute path to the mod folder
    last_scanned INTEGER NOT NULL -- Epoch milliseconds of last full scan
);
```

### `indexed_files` Table
```sql
CREATE TABLE IF NOT EXISTS indexed_files (
    uuid TEXT PRIMARY KEY,          -- UUID as string (not native SQLite UUID)
    mod_id TEXT,                    -- FK → mods.id
    entity_id TEXT,                 -- hullId / skinHullId / variantId / weapon id
    entity_name TEXT,               -- Friendly name (hullName / displayName or filename without extension)
    entity_type TEXT NOT NULL,      -- SHIP, SKIN, WEAPON, VARIANT, PROJECTILE, *_CSV, *_JSON
    file_name TEXT NOT NULL,        -- Just the filename
    file_path TEXT NOT NULL,        -- Absolute path on disk
    last_modified INTEGER NOT NULL, -- File's lastModified epoch ms
    file_hash TEXT,                 -- Optional content hash
    sprite_path TEXT,               -- Relative sprite path extracted from JSON/CSV
    designation TEXT,               -- Hull/skin designation
    metadata_json TEXT,             -- Serialized CSV metadata map for fast tree hydration
    FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
);
```

### `csv_cache` Table
```sql
CREATE TABLE IF NOT EXISTS csv_cache (
    csv_path TEXT PRIMARY KEY,
    mod_id TEXT NOT NULL,
    last_modified INTEGER NOT NULL,
    rows_json TEXT NOT NULL,
    FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
);
```

### Indexes
```sql
CREATE INDEX IF NOT EXISTS idx_entity_id ON indexed_files(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_type ON indexed_files(entity_type);
CREATE UNIQUE INDEX IF NOT EXISTS idx_file_path ON indexed_files(file_path);
CREATE INDEX IF NOT EXISTS idx_mod_id ON indexed_files(mod_id);
CREATE INDEX IF NOT EXISTS idx_mod_type ON indexed_files(mod_id, entity_type);
```

### Entity Types
| `entity_type` | Source Files |
|---|---|
| `SHIP` | `.ship` files |
| `SKIN` | `.skin` files |
| `WEAPON` | `.wpn` files |
| `VARIANT` | `.variant` files |
| `PROJECTILE` | `.proj` files |
| `SHIP_CSV` | `data/hulls/ship_data.csv` |
| `WEAPON_CSV` | `data/weapons/weapon_data.csv` |
| `HULLMOD_CSV` | `data/hullmods/hull_mods.csv` |
| `SHIPSYSTEM_CSV` | `data/shipsystems/ship_systems.csv` |
| `WING_CSV` | `data/hulls/wing_data.csv` |
| `ENGINE_STYLE_JSON` | `data/config/engine_styles.json` |
| `HULL_STYLE_JSON` | `data/config/hull_styles.json` |

---

## 3. Connection Management (HikariCP Pooling & WAL)

```java
public static Connection getConnection() throws SQLException {
    if (dataSource == null) {
        initDataSource();
    }
    return dataSource.getConnection();
}
```

The database utilizes **HikariCP** (`HikariDataSource`) connection pooling (`SQLite-Pool` with maximum pool size 10) configured with SQLite PRAGMAs:
- `PRAGMA foreign_keys = ON;`
- `PRAGMA journal_mode = WAL;` (Write-Ahead Logging for non-blocking concurrent reads during indexing)
- `PRAGMA synchronous = NORMAL;`
- `PRAGMA cache_size = -64000;` (64 MB page cache)
- `PRAGMA temp_store = MEMORY;`
- `busyTimeout = 5000;`

### Path Forward-Slash Normalization
```java
"jdbc:sqlite:" + getDatabaseFilePath().toAbsolutePath().toString().replace("\\", "/")
```
SQLite JDBC URLs require forward slashes even on Windows to prevent path escape errors.

---

## 4. In-Memory Core Index & Query Deduplication

### `CoreIndexManager`
- Manages an in-memory cache of `starsector-core` files (`CORE_FILES_BY_TYPE`, `CORE_FILES_BY_ENTITY_ID`, `CORE_FILES_BY_PATH`) so that core data remains accessible instantly without full SQLite round-trips.
- **Cache Accumulation Safeguard**: All collections (`CORE_FILES_BY_TYPE`, `CORE_FILES_BY_ENTITY_ID`, `CORE_FILES_BY_PATH`) must be cleared at the start of `loadCoreData()` under `synchronized (LOCK)` before loading from DB cache or scanning from disk.
- `persistToDatabase()` delegates to `DatabaseQueryService.deleteFilesByModId("starsector-core")`, `upsertMod(...)`, and `upsertFiles(...)`.

### `DatabaseQueryService.getFilesByType`
- Merges results from `CoreIndexManager` and active mods querying the SQLite database, caching results in `TYPE_CACHE` (invalidated via `clearTypeCache()`).
- Results are deduplicated by canonical file path (`LinkedHashMap<String, IndexedFile>`) to prevent duplicate entries from appearing in UI tree components.

---

## 5. Integrity Checking & Self-Healing

`isDatabaseValid()` performs three checks:
1. **Connectivity**: Can a connection be opened?
2. **Integrity**: `PRAGMA integrity_check` returns `"ok"`?
3. **Schema**: Can `SELECT count(*) FROM mods` and `SELECT count(*) FROM indexed_files` execute without error?

If any check fails, `initializeDatabase()` calls `deleteDatabase()` (which closes `dataSource` and removes `.sqlite`, `-wal`, and `-shm` files) and recreates the database from scratch:
```java
if (databaseExists() && !isDatabaseValid()) {
    log.warn("Database file exists but is invalid or corrupted. Deleting and recreating...");
    deleteDatabase();
}
```

This is acceptable because the database is a **derived cache** — all data can be reconstructed by rescanning the filesystem.

---

## 6. Indexing Engine: `IndexScannerTask`

[IndexScannerTask.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/parsing/loading/IndexScannerTask.java) orchestrates the full indexing pipeline for active mods (`getActiveNonLibModFolders()`).

### Differential Update Strategy
On subsequent runs, the scanner compares the `last_modified` timestamp of each mod folder and its files against stored database values (`DatabaseQueryService.getFilesDbInfoMap(conn, modId)`).

### Within a Folder: File-Level Diffing & Parallel Metadata Extraction
For each mod folder being scanned, the scanner:
1. Queries `DatabaseQueryService.getFilesDbInfoMap(conn, modId)` to get all known files and their stored timestamps/UUIDs.
2. Walks the filesystem and compares `file.lastModified()` against the database value.
3. Files with newer timestamps are parsed in parallel (`parseRequests.parallelStream()`) via `extractEntityMetadata()` to extract `id`, `spritePath`, `designation`, and `hullName`, and upserted in batches.
4. Known CSV and style JSON targets (`scanCSVs`) extract `metadataJson` (`extractCSVMetadata` for `SHIP_CSV` and `WEAPON_CSV`) and invalidate stale `csv_cache` rows via `DatabaseQueryService.deleteCsvCacheForMod(modId)`.

### Transaction Strategy (Quirk)
The entire scan runs in a **single SQLite transaction**:
```java
conn.setAutoCommit(false);
// ... all upserts ...
conn.commit();
```
This is critical for performance — without a transaction, each `INSERT` would trigger a separate WAL flush, making a full index of ~10,000 files take minutes instead of seconds.

### Batch Size
PreparedStatement batches are flushed every **500 operations** (`batchCount % 500 == 0`), balancing memory usage against round-trip overhead.

### Orphan Cleanup
After scanning, `deleteOrphanedFiles(conn, modId, activePaths)` purges database records for files that no longer exist on disk.

### `LibModFilter` (Quirk)
Mods identified as "library mods" (e.g., LazyLib, MagicLib) via `LibModFilter.isLibMod()` are **skipped** during scanning (`getActiveNonLibModFolders()`).

### Core Game Mod ID
The core Starsector game folder is indexed with the hardcoded mod ID `"starsector-core"` via `CoreIndexManager`.

---

## 7. Query Service: `DatabaseQueryService`

[DatabaseQueryService.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/persistence/database/DatabaseQueryService.java) is a static utility class.

### Modification & Cache Methods
- `upsertMod()`, `ensureModExists()`, `deleteFilesByModId()`, `deleteMod()`, `upsertFiles(List<IndexedFile>)`, `deleteIndexedFile()`, `deleteOrphanedFiles()`
- `getCsvCache()`, `upsertCsvCache()`, `clearCsvCache()`, `deleteCsvCacheForMod()`
- `clearTypeCache()` — Clears the in-memory `TYPE_CACHE`.

### Synchronous Lookups (Data Loading)
- `getFileByEntityId(entityId, type)` — Primary entity lookup returning `IndexedFile`.
- `getFileNameForEntity(entityId, type)` / `getFilePathForEntity(entityId, type)` — Delegate to `getFileByEntityId()`.
- `getFileByPath(path)` — Reverse lookup by absolute path.
- `getFilesByType(type)` — All indexed files of a given type across `CoreIndexManager` and active mods, deduplicated by canonical path.
- `getFilesByTypeGroupedByMod(type)` — Returned as `Map<String, List<IndexedFile>>`.
- `getFilesByModAndType(modId, type)` / `getFilesByModId(modId)` — Mod-scoped queries.
- `getAggregatedCSVMetadata(csvType)` — Merges serialized `metadata_json` maps across mods.

### Asynchronous Lookups (UI)
- `getFilesByTypeAsync(type)` — Returns `CompletableFuture<List<IndexedFile>>`.
- `getFilesByModAndTypeAsync(modId, type)` — Filtered by mod.

### `IndexedFile` Record
[IndexedFile.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/persistence/database/IndexedFile.java) is a Lombok `@Builder` POJO. All fields are `final`. The `filePath` is stored as a `java.nio.file.Path`.

---

## 8. Database File Location

The database is located in the project working directory (`./ship_editor_database.sqlite`) resolved by `DatabaseManager.getDatabaseFilePath()`.
