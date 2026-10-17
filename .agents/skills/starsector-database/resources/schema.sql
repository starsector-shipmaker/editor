-- mods Table
CREATE TABLE IF NOT EXISTS mods (
    id TEXT PRIMARY KEY,          -- Internal mod ID or "starsector-core"
    name TEXT NOT NULL,           -- Friendly display name
    folder_path TEXT NOT NULL,    -- Absolute path to the mod folder
    last_scanned INTEGER NOT NULL -- Epoch milliseconds of last full scan
);

-- indexed_files Table
CREATE TABLE IF NOT EXISTS indexed_files (
    uuid TEXT PRIMARY KEY,          -- UUID as string
    mod_id TEXT,                    -- FK -> mods.id
    entity_id TEXT,                 -- hullId / skinHullId / variantId / weapon id
    entity_name TEXT,               -- Friendly name (hullName / displayName or filename without extension)
    entity_type TEXT NOT NULL,      -- SHIP, SKIN, WEAPON, VARIANT, PROJECTILE, etc.
    file_name TEXT NOT NULL,        -- Just the filename
    file_path TEXT NOT NULL,        -- Absolute path on disk
    last_modified INTEGER NOT NULL, -- File's lastModified epoch ms
    file_hash TEXT,                 -- Optional content hash
    sprite_path TEXT,               -- Relative sprite path
    designation TEXT,               -- Hull/skin designation
    metadata_json TEXT,             -- Serialized CSV metadata map
    FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
);

-- csv_cache Table
CREATE TABLE IF NOT EXISTS csv_cache (
    csv_path TEXT PRIMARY KEY,
    mod_id TEXT NOT NULL,
    last_modified INTEGER NOT NULL,
    rows_json TEXT NOT NULL,
    FOREIGN KEY(mod_id) REFERENCES mods(id) ON DELETE CASCADE
);

-- Indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_entity_id ON indexed_files(entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_type ON indexed_files(entity_type);
CREATE UNIQUE INDEX IF NOT EXISTS idx_file_path ON indexed_files(file_path);
CREATE INDEX IF NOT EXISTS idx_mod_id ON indexed_files(mod_id);
CREATE INDEX IF NOT EXISTS idx_mod_type ON indexed_files(mod_id, entity_type);
