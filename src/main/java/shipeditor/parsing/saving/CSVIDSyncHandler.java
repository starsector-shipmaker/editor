package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.*;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.ShipSpecFile;

import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import shipeditor.communication.events.components.ComponentEvents.DataTreesReloadQueued;
import shipeditor.communication.events.components.ComponentEvents.CSVEntryIDChanged;
import shipeditor.communication.events.files.FileEvents.CSVSaveQueued;

@Log4j2
public final class CSVIDSyncHandler {

    private CSVIDSyncHandler() {}

    public static void init() {
        EventBus.subscribe(CSVIDSyncHandler.class, event -> {
            if (event instanceof CSVEntryIDChanged changed) {
                handleIDChange(changed);
            }
        });
    }

    private static void handleIDChange(CSVEntryIDChanged changed) {
        String oldID = changed.oldID();
        String newID = changed.newID();
        CSVEntry entry = changed.entry();
        if (entry == null) {
            return;
        }
        GameDataRepository repo = SettingsManager.getGameData();

        // 1. Patch the raw CSV row's "id" cell and update any BOM-prefixed key on the entry
        Map<String, String> entryRowData = entry.getRowData();
        if (entryRowData != null) {
            String entryKey = SaveCSVAction.findRowIdKey(entryRowData);
            entryRowData.put(entryKey, newID);
            entryRowData.put("id", newID);
        }
        updateRawCSVRow(entry.getTableFilePath(), oldID, newID);

        // 2. Update mutable ID field + re-index repository
        if (repo != null) {
            switch (entry.getClass().getSimpleName()) {
                case "ShipCSVEntry" -> {
                    ShipCSVEntry shipEntry = (ShipCSVEntry) entry;
                    shipEntry.setHullID(newID);
                    repo.reindexShipEntry(oldID, newID, shipEntry);
                    repo.reindexSpecEntry(oldID, newID);
                    // Also update the HullSpecFile's hullId so JSON save is consistent
                    ShipSpecFile spec = GameDataRepository.retrieveSpecByID(newID);
                    if (spec instanceof HullSpecFile hullSpec) {
                        hullSpec.setHullId(newID);
                    }
                }
                case "WeaponCSVEntry" -> {
                    WeaponCSVEntry weaponEntry = (WeaponCSVEntry) entry;
                    weaponEntry.setWeaponID(newID);
                    repo.reindexWeaponEntry(oldID, newID, weaponEntry);
                }
                case "HullmodCSVEntry" -> {
                    HullmodCSVEntry hullmodEntry = (HullmodCSVEntry) entry;
                    hullmodEntry.setHullmodID(newID);
                    repo.reindexHullmodEntry(oldID, newID, hullmodEntry);
                }
                case "WingCSVEntry" -> {
                    WingCSVEntry wingEntry = (WingCSVEntry) entry;
                    wingEntry.setWingID(newID);
                    repo.reindexWingEntry(oldID, newID, wingEntry);
                }
                case "ShipSystemCSVEntry" -> {
                    ShipSystemCSVEntry systemEntry = (ShipSystemCSVEntry) entry;
                    systemEntry.setShipSystemID(newID);
                    repo.reindexShipSystemEntry(oldID, newID, systemEntry);
                }
                default -> {}
            }
        }

        log.info("CSV entry ID re-indexed: '{}' -> '{}'", oldID, newID);

        // 3. Refresh data trees first so the UI reflects the change
        EventBus.publish(new DataTreesReloadQueued());

        // 4. Prompt user to save to disk
        int response = JOptionPane.showConfirmDialog(null,
                StringManager.getString("ID_CHANGED_FROM_MSG") + oldID + "' to '" + newID + "'.\n"
                        + "Save the updated CSV to disk now?",
                "CSV ID Updated", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
            EventBus.publish(new CSVSaveQueued(entry));
        }
    }

    private static void updateRawCSVRow(Path tablePath, String oldID, String newID) {
        GameDataRepository repo = SettingsManager.getGameData();
        if (tablePath == null || repo == null) {
            return;
        }
        List<Map<String, String>> rawData = repo.getRawCSVDataForPath(tablePath);
        if (rawData == null) return;

        for (Map<String, String> row : rawData) {
            String rowId = SaveCSVAction.findRowId(row);
            if (rowId != null && oldID.equalsIgnoreCase(rowId.trim())) {
                String key = SaveCSVAction.findRowIdKey(row);
                row.put(key, newID);
                row.put("id", newID);
                return;
            }
        }
        log.warn("Raw CSV row with ID '{}' not found for path: {}", oldID, tablePath);
    }

}
