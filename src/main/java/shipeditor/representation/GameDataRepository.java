package shipeditor.representation;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.extern.log4j.Log4j2;
import lombok.Getter;
import lombok.Setter;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.*;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.ship.*;
import shipeditor.representation.weapon.ProjectileSpecFile;

import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import shipeditor.communication.events.files.FileEvents.HullmodDataSet;
import shipeditor.communication.events.files.FileEvents.WingDataSet;
import shipeditor.utility.text.StringConstants;
import java.nio.file.Paths;

@Log4j2
@SuppressWarnings({"ClassWithTooManyFields", "ClassWithTooManyMethods", "StaticMethodOnlyUsedInOneClass"})
@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class GameDataRepository {

    /**
     * All ship entries by their hull IDs.
     */
    @Setter
    private volatile Map<String, ShipCSVEntry> allShipEntries;

    /**
     * Base hull and skin entries by their ship hull IDs. Used when layer needs to be loaded from variant ID.
     */
    private final Map<String, ShipSpecFile> allSpecEntries;

    /**
     * All hullmod entries by their IDs.
     */
    private volatile Map<String, HullmodCSVEntry> allHullmodEntries;

    /**
     * All shipsystem entries by their IDs.
     */
    private volatile Map<String, ShipSystemCSVEntry> allShipsystemEntries;

    private volatile Map<String, WingCSVEntry> allWingEntries;

    @Setter
    private volatile Map<String, WeaponCSVEntry> allWeaponEntries;

    /**
     * Holds the same instances as id-entry collection, used for quick repopulating of entry tree with filtering.
     */
    private volatile Map<Path, List<ShipCSVEntry>> shipEntriesByPackage;

    private volatile Map<Path, List<WeaponCSVEntry>> weaponEntriesByPackage;

    private volatile Map<Path, List<ProjectileSpecFile>> projectileEntriesByPackage;

    private volatile Map<Path, List<HullmodCSVEntry>> hullmodEntriesByPackage;

    private volatile Map<Path, List<ShipSystemCSVEntry>> shipSystemEntriesByPackage;

    private volatile Map<Path, List<WingCSVEntry>> wingEntriesByPackage;

    public static class CachedCSVData {
        private final List<Map<String, String>> rawData;
        private final Object schema;

        public CachedCSVData(List<Map<String, String>> rawData, Object schema) {
            this.rawData = rawData;
            this.schema = schema;
        }

        public List<Map<String, String>> getRawData() {
            return rawData;
        }

        public Object getSchema() {
            return schema;
        }
    }

    @lombok.Getter(lombok.AccessLevel.NONE)
    private final Map<Path, SoftReference<CachedCSVData>> csvCacheByPath = new ConcurrentHashMap<>();

    public void putRawCSVDataForPath(Path path, List<Map<String, String>> rawData) {
        csvCacheByPath.compute(path, (k, ref) -> {
            CachedCSVData existing = ref != null ? ref.get() : null;
            Object schema = existing != null ? existing.getSchema() : null;
            return new SoftReference<>(new CachedCSVData(rawData, schema));
        });
    }

    public void putCsvSchemaForPath(Path path, Object schema) {
        csvCacheByPath.compute(path, (k, ref) -> {
            CachedCSVData existing = ref != null ? ref.get() : null;
            List<Map<String, String>> rawData = existing != null ? existing.getRawData() : null;
            return new SoftReference<>(new CachedCSVData(rawData, schema));
        });
    }

    public void putCachedCSVData(Path path, List<Map<String, String>> rawData, Object schema) {
        csvCacheByPath.put(path, new SoftReference<>(new CachedCSVData(rawData, schema)));
    }

    /**
     * Retrieves raw CSV data for the given path. If the SoftReference was cleared by GC,
     * transparently re-parses the CSV from disk.
     */
    public List<Map<String, String>> getRawCSVDataForPath(Path path) {
        SoftReference<CachedCSVData> ref = csvCacheByPath.get(path);
        if (ref != null) {
            CachedCSVData cached = ref.get();
            if (cached != null && cached.getRawData() != null) {
                return cached.getRawData();
            }
        }
        // Re-parse from disk on cache miss.
        List<Map<String, String>> reparsed = shipeditor.parsing.loading.FileLoading.reparseCSVForPath(path);
        if (reparsed != null) {
            SoftReference<CachedCSVData> refreshedRef = csvCacheByPath.get(path);
            CachedCSVData refreshed = refreshedRef != null ? refreshedRef.get() : null;
            if (refreshed != null && refreshed.getRawData() != null) {
                return refreshed.getRawData();
            }
        }
        return reparsed;
    }

    /**
     * Retrieves the CSV schema for the given path. If the SoftReference was cleared by GC,
     * transparently re-parses to recover the schema.
     */
    public Object getCsvSchemaForPath(Path path) {
        SoftReference<CachedCSVData> ref = csvCacheByPath.get(path);
        if (ref != null) {
            CachedCSVData cached = ref.get();
            if (cached != null && cached.getSchema() != null) {
                return cached.getSchema();
            }
        }
        // Re-parse from disk to recover the schema.
        shipeditor.parsing.loading.FileLoading.reparseCSVForPath(path);
        SoftReference<CachedCSVData> refreshedRef = csvCacheByPath.get(path);
        CachedCSVData refreshed = refreshedRef != null ? refreshedRef.get() : null;
        return refreshed != null ? refreshed.getSchema() : null;
    }

    /**
     * Hull styles by their IDs (field names in JSON).
     */
    @Setter
    private volatile Map<String, HullStyle> allHullStyles;

    /**
     * Engine styles by their IDs (field names in JSON).
     */
    @Setter
    private volatile Map<String, EngineStyle> allEngineStyles;

    /**
     * All variant files by variant IDs.
     */
    @Setter
    private volatile Map<String, VariantFile> allVariants;

    /**
     * Reverse index: hull ID → map of variant ID → VariantFile. Rebuilt when allVariants is set.
     */
    private volatile Map<String, Map<String, VariantFile>> variantsByHullID = new ConcurrentHashMap<>();

    /**
     * All projectile files by variant IDs.
     */
    @Setter
    private final Map<String, ProjectileSpecFile> allProjectiles = new ConcurrentHashMap<>();

    @Setter
    private volatile boolean shipDataLoaded;

    private volatile boolean hullmodDataLoaded;

    @Setter
    private volatile boolean shipsystemDataLoaded;

    private volatile boolean wingDataLoaded;

    @Setter
    private volatile boolean weaponsDataLoaded;

    public GameDataRepository() {
        this.allSpecEntries = new ConcurrentHashMap<>();
    }



    private static final com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>> LIST_MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {};

    public <T extends CSVEntry> Map<Path, List<T>> loadCsvEntriesByPackage(Path relativePath, CsvEntryFactory<T> factory) {
        return loadCsvEntriesByPackage(relativePath, factory, null);
    }

    public <T extends CSVEntry> Map<Path, List<T>> loadCsvEntriesByPackage(Path relativePath, CsvEntryFactory<T> factory,
                                                                          java.util.function.Predicate<Map<String, String>> validator) {
        Map<Path, List<T>> result = new LinkedHashMap<>();
        List<Path> searchFolders = new java.util.ArrayList<>();
        Path coreFolder = SettingsManager.getCoreFolderPath();
        if (coreFolder != null) {
            searchFolders.add(coreFolder);
        }
        searchFolders.addAll(SettingsManager.getAllModFolders());
        com.fasterxml.jackson.databind.ObjectMapper mapper = SettingsManager.getMapperForSettingsFile();
        for (Path modFolder : searchFolders) {
            Path modFolderName = modFolder.getFileName();
            if (modFolderName == null) {
                continue;
            }
            String modId = modFolderName.toString();
            if (!SettingsManager.isModActive(modId) && !SettingsManager.isCoreFolder(modFolder)) {
                continue;
            }
            if (shipeditor.parsing.loading.LibModFilter.isLibMod(modFolder)) {
                continue;
            }
            Path csvPath = modFolder.resolve(relativePath);
            if (java.nio.file.Files.exists(csvPath)) {
                List<Map<String, String>> rows = loadCsvRowsWithCache(csvPath, modId, mapper);
                if (rows != null) {
                    List<T> entries = new java.util.ArrayList<>();
                    for (Map<String, String> row : rows) {
                        if (validator != null && !validator.test(row)) {
                            continue;
                        }
                        entries.add(factory.create(row, modFolder, csvPath));
                    }
                    if (!entries.isEmpty()) {
                        result.put(modFolder, entries);
                    }
                }
            }
        }
        return result;
    }

    private List<Map<String, String>> loadCsvRowsWithCache(Path csvPath, String modId,
                                                            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        long diskModified = csvPath.toFile().lastModified();
        shipeditor.persistence.database.DatabaseQueryService.CsvCacheRow cached =
                shipeditor.persistence.database.DatabaseQueryService.getCsvCache(csvPath);

        if (cached != null && cached.lastModified() == diskModified) {
            // Cache hit — deserialize JSON rows from SQLite instead of re-parsing CSV from disk.
            try {
                return mapper.readValue(cached.rowsJson(), LIST_MAP_TYPE);
            } catch (Exception e) {
                log.warn("Failed to deserialize CSV cache for {}, falling back to disk", csvPath, e);
            }
        }

        // Cache miss or stale — parse CSV from disk and persist to cache.
        List<Map<String, String>> rows = getRawCSVDataForPath(csvPath);
        if (rows != null) {
            try {
                String rowsJson = mapper.writeValueAsString(rows);
                Path parent = csvPath.getParent();
                String parentStr = parent != null ? parent.toAbsolutePath().toString() : "";
                shipeditor.persistence.database.DatabaseQueryService.ensureModExists(modId, modId, parentStr);
                shipeditor.persistence.database.DatabaseQueryService.upsertCsvCache(csvPath, modId, diskModified, rowsJson);
            } catch (Exception e) {
                log.warn("Failed to write CSV cache for {}", csvPath, e);
            }
        }
        return rows;
    }

    private static <T extends CSVEntry> Map<String, T> flattenPackageEntries(Map<Path, List<T>> packageMap) {
        Map<String, T> local = new ConcurrentHashMap<>();
        if (packageMap != null) {
            for (List<T> list : packageMap.values()) {
                for (T entry : list) {
                    if (entry.getID() != null && !entry.getID().isEmpty()) {
                        local.put(entry.getID(), entry);
                    }
                }
            }
        }
        return local;
    }

    public Map<String, ShipCSVEntry> getAllShipEntries() {
        if (allShipEntries == null) {
            synchronized(this) {
                if (allShipEntries == null) {
                    allShipEntries = flattenPackageEntries(getShipEntriesByPackage());
                }
            }
        }
        return allShipEntries;
    }

    public Map<Path, List<ShipCSVEntry>> getShipEntriesByPackage() {
        if (shipEntriesByPackage == null) {
            synchronized(this) {
                if (shipEntriesByPackage == null) {
                    shipEntriesByPackage = loadCsvEntriesByPackage(Paths.get("data", "hulls", "ship_data.csv"),
                            (r, f, p) -> new ShipCSVEntry(r, null, f, "ship_data.csv", p),
                            shipeditor.parsing.loading.CsvLoader.getNormalValidationPredicate());
                }
            }
        }
        return shipEntriesByPackage;
    }

    public Map<String, WeaponCSVEntry> getAllWeaponEntries() {
        if (allWeaponEntries == null) {
            synchronized(this) {
                if (allWeaponEntries == null) {
                    allWeaponEntries = flattenPackageEntries(getWeaponEntriesByPackage());
                }
            }
        }
        return allWeaponEntries;
    }

    public Map<Path, List<WeaponCSVEntry>> getWeaponEntriesByPackage() {
        if (weaponEntriesByPackage == null) {
            synchronized(this) {
                if (weaponEntriesByPackage == null) {
                    weaponEntriesByPackage = loadCsvEntriesByPackage(Paths.get("data", "weapons", "weapon_data.csv"),
                            (r, f, p) -> new WeaponCSVEntry(r, f, p),
                            shipeditor.parsing.loading.CsvLoader.getNormalValidationPredicate());
                }
            }
        }
        return weaponEntriesByPackage;
    }

    public Map<String, HullmodCSVEntry> getAllHullmodEntries() {
        if (allHullmodEntries == null) {
            synchronized(this) {
                if (allHullmodEntries == null) {
                    allHullmodEntries = flattenPackageEntries(getHullmodEntriesByPackage());
                }
            }
        }
        return allHullmodEntries;
    }

    public Map<Path, List<HullmodCSVEntry>> getHullmodEntriesByPackage() {
        if (hullmodEntriesByPackage == null) {
            synchronized(this) {
                if (hullmodEntriesByPackage == null) {
                    hullmodEntriesByPackage = loadCsvEntriesByPackage(Paths.get("data", "hullmods", "hull_mods.csv"),
                            (r, f, p) -> new HullmodCSVEntry(r, f, p),
                            shipeditor.parsing.loading.CsvLoader.getNormalValidationPredicate());
                }
            }
        }
        return hullmodEntriesByPackage;
    }

    public Map<String, ShipSystemCSVEntry> getAllShipsystemEntries() {
        if (allShipsystemEntries == null) {
            synchronized(this) {
                if (allShipsystemEntries == null) {
                    allShipsystemEntries = flattenPackageEntries(getShipSystemEntriesByPackage());
                }
            }
        }
        return allShipsystemEntries;
    }

    public Map<Path, List<ShipSystemCSVEntry>> getShipSystemEntriesByPackage() {
        if (shipSystemEntriesByPackage == null) {
            synchronized(this) {
                if (shipSystemEntriesByPackage == null) {
                    shipSystemEntriesByPackage = loadCsvEntriesByPackage(Paths.get("data", "shipsystems", "ship_systems.csv"),
                            (r, f, p) -> new ShipSystemCSVEntry(r, f, p),
                            shipeditor.parsing.loading.CsvLoader.getNormalValidationPredicate());
                }
            }
        }
        return shipSystemEntriesByPackage;
    }

    public Map<String, WingCSVEntry> getAllWingEntries() {
        if (allWingEntries == null) {
            synchronized(this) {
                if (allWingEntries == null) {
                    allWingEntries = flattenPackageEntries(getWingEntriesByPackage());
                }
            }
        }
        return allWingEntries;
    }

    public Map<Path, List<WingCSVEntry>> getWingEntriesByPackage() {
        if (wingEntriesByPackage == null) {
            synchronized(this) {
                if (wingEntriesByPackage == null) {
                    wingEntriesByPackage = loadCsvEntriesByPackage(Paths.get("data", "hulls", "wing_data.csv"),
                            (r, f, p) -> new WingCSVEntry(r, f, p),
                            shipeditor.parsing.loading.CsvLoader.getWingValidationPredicate());
                }
            }
        }
        return wingEntriesByPackage;
    }

    public void reset() {
        if (allSpecEntries != null) allSpecEntries.clear();
        allShipEntries = null;
        allHullmodEntries = null;
        allShipsystemEntries = null;
        allWingEntries = null;
        allWeaponEntries = null;
        allVariants = null;
        allHullStyles = null;
        allEngineStyles = null;
        if (variantsByHullID != null) variantsByHullID.clear();
        if (allProjectiles != null) allProjectiles.clear();
        csvCacheByPath.clear();

        shipEntriesByPackage = null;
        weaponEntriesByPackage = null;
        projectileEntriesByPackage = null;
        hullmodEntriesByPackage = null;
        shipSystemEntriesByPackage = null;
        wingEntriesByPackage = null;

        shipDataLoaded = false;
        hullmodDataLoaded = false;
        shipsystemDataLoaded = false;
        wingDataLoaded = false;
        weaponsDataLoaded = false;
    }

    public void setShipEntriesByPackage(Map<Path, List<ShipCSVEntry>> shipEntries) {
        this.shipEntriesByPackage = shipEntries;
        Map<String, ShipCSVEntry> newAllShipEntries = new ConcurrentHashMap<>();
        if (shipEntries != null) {
            shipEntries.values().forEach(list -> list.forEach(entry -> {
                if (entry.getID() != null && !entry.getID().isEmpty()) {
                    newAllShipEntries.put(entry.getID(), entry);
                }
            }));
            SettingsManager.announcePackages(shipEntries);
        }
        this.allShipEntries = newAllShipEntries;
    }

    public void setWeaponEntriesByPackage(Map<Path, List<WeaponCSVEntry>> weaponEntries) {
        this.weaponEntriesByPackage = weaponEntries;
        Map<String, WeaponCSVEntry> newAllWeaponEntries = new ConcurrentHashMap<>();
        if (weaponEntries != null) {
            weaponEntries.values().forEach(list -> list.forEach(entry -> {
                if (entry.getID() != null && !entry.getID().isEmpty()) {
                    newAllWeaponEntries.put(entry.getID(), entry);
                }
            }));
            SettingsManager.announcePackages(weaponEntries);
        }
        this.allWeaponEntries = newAllWeaponEntries;
    }

    public void setProjectileEntriesByPackage(Map<Path, List<ProjectileSpecFile>> projectileEntries) {
        this.projectileEntriesByPackage = projectileEntries;
        if (projectileEntries != null) {
            SettingsManager.announcePackages(projectileEntries);
        }
    }

    public void setHullmodEntriesByPackage(Map<Path, List<HullmodCSVEntry>> hullmodEntries) {
        this.hullmodEntriesByPackage = hullmodEntries;
        Map<String, HullmodCSVEntry> newAllHullmodEntries = new ConcurrentHashMap<>();
        if (hullmodEntries != null) {
            hullmodEntries.values().forEach(list -> list.forEach(entry -> {
                if (entry.getID() != null && !entry.getID().isEmpty()) {
                    newAllHullmodEntries.put(entry.getID(), entry);
                }
            }));
            SettingsManager.announcePackages(hullmodEntries);
        }
        this.allHullmodEntries = newAllHullmodEntries;
    }

    public void setShipSystemEntriesByPackage(Map<Path, List<ShipSystemCSVEntry>> shipSystemEntries) {
        this.shipSystemEntriesByPackage = shipSystemEntries;
        Map<String, ShipSystemCSVEntry> newAllShipsystemEntries = new ConcurrentHashMap<>();
        if (shipSystemEntries != null) {
            shipSystemEntries.values().forEach(list -> list.forEach(entry -> {
                if (entry.getID() != null && !entry.getID().isEmpty()) {
                    newAllShipsystemEntries.put(entry.getID(), entry);
                }
            }));
            SettingsManager.announcePackages(shipSystemEntries);
        }
        this.allShipsystemEntries = newAllShipsystemEntries;
    }

    public void setWingEntriesByPackage(Map<Path, List<WingCSVEntry>> wingEntries) {
        this.wingEntriesByPackage = wingEntries;
        Map<String, WingCSVEntry> newAllWingEntries = new ConcurrentHashMap<>();
        if (wingEntries != null) {
            wingEntries.values().forEach(list -> list.forEach(entry -> {
                if (entry.getID() != null && !entry.getID().isEmpty()) {
                    newAllWingEntries.put(entry.getID(), entry);
                }
            }));
            SettingsManager.announcePackages(wingEntries);
        }
        this.allWingEntries = newAllWingEntries;
    }

    public ShipCSVEntry getOrCreateShipEntry(shipeditor.persistence.database.IndexedFile file) {
        if (file == null || file.getEntityId() == null) return null;
        Map<String, ShipCSVEntry> entries = getAllShipEntries();
        ShipCSVEntry existing = entries.get(file.getEntityId());
        if (existing != null) return existing;

        Path folder = SettingsManager.getFolderForModId(file.getModId());
        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, file.getEntityId());
        if (file.getEntityName() != null && !file.getEntityName().isBlank()) {
            row.put(StringConstants.NAME, file.getEntityName());
        }
        if (file.getDesignation() != null && !file.getDesignation().isBlank()) {
            row.put(StringConstants.DESIGNATION, file.getDesignation());
        }
        ShipCSVEntry synthetic = new ShipCSVEntry(row, null, folder, file.getFileName());
        entries.put(file.getEntityId(), synthetic);
        return synthetic;
    }

    public static ShipCSVEntry retrieveShipCSVEntryByID(String baseHullID) {
        if (baseHullID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var shipEntries = dataRepository.getAllShipEntries();
        if (shipEntries != null) {
            ShipCSVEntry found = shipEntries.get(baseHullID);
            if (found != null) return found;
        }
        shipeditor.persistence.database.IndexedFile file =
                shipeditor.persistence.database.DatabaseQueryService.getFileByEntityId(baseHullID, StringConstants.SHIP_TYPE);
        if (file != null) {
            return dataRepository.getOrCreateShipEntry(file);
        }
        return null;
    }

    public HullmodCSVEntry getOrCreateHullmodEntry(String hullmodID) {
        if (hullmodID == null || hullmodID.isBlank()) return null;
        Map<String, HullmodCSVEntry> entries = getAllHullmodEntries();
        if (entries == null) return null;
        HullmodCSVEntry existing = entries.get(hullmodID);
        if (existing != null) return existing;

        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, hullmodID);
        row.put(StringConstants.NAME, hullmodID);
        HullmodCSVEntry synthetic = new HullmodCSVEntry(row, null, null);
        entries.put(hullmodID, synthetic);
        return synthetic;
    }

    public static HullmodCSVEntry retrieveHullmodCSVEntryByID(String hullmodID) {
        if (hullmodID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var hullmodEntries = dataRepository.getAllHullmodEntries();
        if (hullmodEntries != null) {
            HullmodCSVEntry found = hullmodEntries.get(hullmodID);
            if (found != null) return found;
        }
        return dataRepository.getOrCreateHullmodEntry(hullmodID);
    }

    public WingCSVEntry getOrCreateWingEntry(String wingID) {
        if (wingID == null || wingID.isBlank()) return null;
        Map<String, WingCSVEntry> entries = getAllWingEntries();
        if (entries == null) return null;
        WingCSVEntry existing = entries.get(wingID);
        if (existing != null) return existing;

        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, wingID);
        row.put(StringConstants.NAME, wingID);
        WingCSVEntry synthetic = new WingCSVEntry(row, null, null);
        entries.put(wingID, synthetic);
        return synthetic;
    }

    public static WingCSVEntry retrieveWingCSVEntryByID(String wingID) {
        if (wingID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var wingEntries = dataRepository.getAllWingEntries();
        if (wingEntries != null) {
            WingCSVEntry found = wingEntries.get(wingID);
            if (found != null) return found;
        }
        return dataRepository.getOrCreateWingEntry(wingID);
    }

    public ShipSystemCSVEntry getOrCreateShipsystemEntry(String systemID) {
        if (systemID == null || systemID.isBlank()) return null;
        Map<String, ShipSystemCSVEntry> entries = getAllShipsystemEntries();
        if (entries == null) return null;
        ShipSystemCSVEntry existing = entries.get(systemID);
        if (existing != null) return existing;

        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, systemID);
        row.put(StringConstants.NAME, systemID);
        ShipSystemCSVEntry synthetic = new ShipSystemCSVEntry(row, null, null);
        entries.put(systemID, synthetic);
        return synthetic;
    }

    public static ShipSystemCSVEntry retrieveShipsystemCSVEntryByID(String systemID) {
        if (systemID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var systemEntries = dataRepository.getAllShipsystemEntries();
        if (systemEntries != null) {
            ShipSystemCSVEntry found = systemEntries.get(systemID);
            if (found != null) return found;
        }
        return dataRepository.getOrCreateShipsystemEntry(systemID);
    }

    public WeaponCSVEntry getOrCreateWeaponEntry(shipeditor.persistence.database.IndexedFile file) {
        if (file == null || file.getEntityId() == null) return null;
        Map<String, WeaponCSVEntry> entries = getAllWeaponEntries();
        WeaponCSVEntry existing = entries.get(file.getEntityId());
        if (existing != null) return existing;

        Path folder = SettingsManager.getFolderForModId(file.getModId());
        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, file.getEntityId());
        if (file.getEntityName() != null && !file.getEntityName().isBlank()) {
            row.put(StringConstants.NAME, file.getEntityName());
        }
        WeaponCSVEntry synthetic = new WeaponCSVEntry(row, folder, null);
        entries.put(file.getEntityId(), synthetic);
        return synthetic;
    }

    public static WeaponCSVEntry retrieveWeaponCSVEntryByID(String weaponID) {
        if (weaponID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var weaponEntries = dataRepository.getAllWeaponEntries();
        if (weaponEntries != null) {
            WeaponCSVEntry found = weaponEntries.get(weaponID);
            if (found != null) return found;
        }
        shipeditor.persistence.database.IndexedFile file =
                shipeditor.persistence.database.DatabaseQueryService.getFileByEntityId(weaponID, StringConstants.WEAPON_TYPE);
        if (file != null) {
            return dataRepository.getOrCreateWeaponEntry(file);
        }
        return null;
    }

    public static ShipSpecFile retrieveSpecByID(String hullID) {
        if (hullID == null) {
            return null;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        var allSpecs = dataRepository.getAllSpecEntries();
        if (allSpecs == null) return null;
        ShipSpecFile spec = allSpecs.get(hullID);
        if (spec == null) {
            Path filePath = shipeditor.persistence.database.DatabaseQueryService.getFilePathForEntity(hullID, "SHIP");
            if (filePath != null) {
                spec = shipeditor.parsing.loading.FileLoading.loadHullFile(filePath.toFile());
            } else {
                filePath = shipeditor.persistence.database.DatabaseQueryService.getFilePathForEntity(hullID, "SKIN");
                if (filePath != null) {
                    spec = shipeditor.parsing.loading.FileLoading.loadSkinFile(filePath.toFile());
                }
            }
            if (spec != null) {
                putSpec(spec);
            }
        }
        return spec;
    }

    /**
     * @param shipHullID ship ID, whether base or skin.
     * @return base hull ID.
     */
    public static String getBaseHullID(String shipHullID) {
        if (shipHullID == null) return null;
        ShipSpecFile specFile = GameDataRepository.retrieveSpecByID(shipHullID);
        if (specFile == null) return null;
        String baseHullId;
        if (specFile instanceof SkinSpecFile checkedSkin) {
            baseHullId = checkedSkin.getBaseHullId();
        } else {
            baseHullId = specFile.getHullId();
        }
        return baseHullId;
    }

    public static void putSpec(ShipSpecFile specFile) {
        if (specFile == null || specFile.getHullId() == null) {
            return;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository != null) {
            var allSpecs = dataRepository.getAllSpecEntries();
            if (allSpecs != null) {
                allSpecs.put(specFile.getHullId(), specFile);
            }
        }
    }

    public static void putVariant(VariantFile variantFile) {
        if (variantFile == null || variantFile.getVariantId() == null) {
            return;
        }
        GameDataRepository dataRepository = SettingsManager.getGameData();
        if (dataRepository != null && dataRepository.getAllVariants() != null) {
            dataRepository.getAllVariants().put(variantFile.getVariantId(), variantFile);
            // Update reverse index.
            String hullId = variantFile.getHullId();
            if (hullId != null) {
                dataRepository.variantsByHullID
                        .computeIfAbsent(hullId, k -> new ConcurrentHashMap<>())
                        .put(variantFile.getVariantId(), variantFile);
            }
        }
    }

    public void setHullmodDataLoaded(boolean hullmodsLoaded) {
        this.hullmodDataLoaded = hullmodsLoaded;
        EventBus.publish(new HullmodDataSet());
    }

    public void setWingDataLoaded(boolean wingsLoaded) {
        this.wingDataLoaded = wingsLoaded;
        EventBus.publish(new WingDataSet());
    }

    public Map<String, HullStyle> getAllHullStyles() {
        if (allHullStyles == null) {
            synchronized(this) {
                if (allHullStyles == null) {
                    shipeditor.parsing.loading.FileLoading.loadHullStyles().run();
                }
            }
        }
        return allHullStyles;
    }

    public Map<String, EngineStyle> getAllEngineStyles() {
        if (allEngineStyles == null) {
            synchronized(this) {
                if (allEngineStyles == null) {
                    shipeditor.parsing.loading.FileLoading.loadEngineStyles().run();
                }
            }
        }
        return allEngineStyles;
    }

    public static HullStyle fetchStyleByID(String styleID) {
        if (styleID == null) {
            return null;
        }
        var dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        Map<String, HullStyle> allStyles = dataRepository.getAllHullStyles();
        return allStyles != null ? allStyles.get(styleID) : null;
    }

    public Map<String, VariantFile> getAllVariants() {
        if (allVariants == null) {
            synchronized(this) {
                if (allVariants == null) {
                    Map<String, VariantFile> loadedVariants = new java.util.concurrent.ConcurrentHashMap<>();
                    java.util.List<shipeditor.persistence.database.IndexedFile> dbFiles = shipeditor.persistence.database.DatabaseQueryService.getFilesByType("VARIANT");
                    dbFiles.parallelStream().forEach(dbFile -> {
                        if (dbFile != null && dbFile.getFilePath() != null) {
                            java.io.File variantFile = dbFile.getFilePath().toFile();
                            VariantFile mapped = shipeditor.parsing.loading.FileLoading.loadVariantFile(variantFile);
                            if (mapped != null && mapped.getVariantId() != null) {
                                loadedVariants.put(mapped.getVariantId(), mapped);
                            }
                        }
                    });
                    this.allVariants = loadedVariants;
                    rebuildVariantsByHullIndex();
                }
            }
        }
        return allVariants;
    }

    public static VariantFile getVariantByID(String variantID) {
        if (variantID == null) {
            return null;
        }
        var dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        Map<String, VariantFile> variants = dataRepository.getAllVariants();
        return variants != null ? variants.get(variantID) : null;
    }

    public static ProjectileSpecFile getProjectileByID(String projectileID) {
        if (projectileID == null) {
            return null;
        }
        var dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) return null;
        ProjectileSpecFile spec = dataRepository.allProjectiles.get(projectileID);
        if (spec == null) {
            Path filePath = shipeditor.persistence.database.DatabaseQueryService.getFilePathForEntity(projectileID, shipeditor.utility.text.StringConstants.PROJECTILE_TYPE);
            if (filePath != null) {
                spec = shipeditor.parsing.loading.FileLoading.loadProjectileFile(filePath.toFile());
                if (spec != null) {
                    dataRepository.allProjectiles.put(projectileID, spec);
                }
            }
        }
        return spec;
    }

    public static WeaponCSVEntry getWeaponByID(String weaponID) {
        return retrieveWeaponCSVEntryByID(weaponID);
    }

    public static Map<String, VariantFile> getMatchingForHullID(String shipHullID) {
        if (shipHullID == null) {
            return new HashMap<>();
        }
        var dataRepository = SettingsManager.getGameData();
        if (dataRepository == null) {
            return new HashMap<>();
        }
        dataRepository.getAllVariants();
        if (dataRepository.variantsByHullID != null) {
            Map<String, VariantFile> indexed = dataRepository.variantsByHullID.get(shipHullID);
            if (indexed != null) {
                return new HashMap<>(indexed);
            }
        }
        return new HashMap<>();
    }

    /**
     * Rebuilds the reverse hull-ID → variants index from the full allVariants map.
     * Should be called after allVariants is fully populated.
     */
    public void rebuildVariantsByHullIndex() {
        Map<String, Map<String, VariantFile>> index = new ConcurrentHashMap<>();
        if (allVariants != null) {
            allVariants.forEach((variantId, variantFile) -> {
                if (variantId != null && variantFile != null) {
                    String hullId = variantFile.getHullId();
                    if (hullId != null) {
                        index.computeIfAbsent(hullId, k -> new ConcurrentHashMap<>())
                                .put(variantId, variantFile);
                    }
                }
            });
        }
        this.variantsByHullID = index;
    }

    // --- Re-indexing methods for CSV ID changes ---

    private <T> void reindexEntry(Map<String, T> map, String oldID, String newID, T entry) {
        if (map == null || newID == null || entry == null) {
            return;
        }
        if (oldID != null) {
            map.remove(oldID);
        }
        map.put(newID, entry);
    }

    public void reindexShipEntry(String oldID, String newID, ShipCSVEntry entry) {
        reindexEntry(allShipEntries, oldID, newID, entry);
    }

    public void reindexSpecEntry(String oldID, String newID) {
        if (oldID == null || newID == null) {
            return;
        }
        ShipSpecFile spec = allSpecEntries.remove(oldID);
        if (spec != null) {
            allSpecEntries.put(newID, spec);
        }
    }

    public void reindexWeaponEntry(String oldID, String newID, WeaponCSVEntry entry) {
        reindexEntry(allWeaponEntries, oldID, newID, entry);
    }

    public void reindexHullmodEntry(String oldID, String newID, HullmodCSVEntry entry) {
        reindexEntry(allHullmodEntries, oldID, newID, entry);
    }

    public void reindexWingEntry(String oldID, String newID, WingCSVEntry entry) {
        reindexEntry(allWingEntries, oldID, newID, entry);
    }

    public void reindexShipSystemEntry(String oldID, String newID, ShipSystemCSVEntry entry) {
        reindexEntry(allShipsystemEntries, oldID, newID, entry);
    }

}
