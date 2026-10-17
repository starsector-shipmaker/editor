package shipeditor.parsing.saving;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.Errors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.representation.GameDataRepository;
import shipeditor.utility.text.StringConstants;

@Log4j2
public class SaveCSVAction {

    public static void saveCSVEntry(CSVEntry entry) {
        if (entry == null || entry.getTableFilePath() == null) {
            return;
        }
        Path path = entry.getTableFilePath();
        List<Map<String, String>> rawData = SettingsManager.getGameData().getRawCSVDataForPath(path);
        CsvSchema schema = (CsvSchema) SettingsManager.getGameData().getCsvSchemaForPath(path);

        if (rawData == null || schema == null) {
            log.error("Cannot save CSV: raw data or schema is missing for path {}", path);
            return;
        }

        String rowId = entry.getID();
        String cleanRowId = rowId != null ? rowId.trim() : null;
        boolean found = false;
        for (Map<String, String> row : rawData) {
            String rowIdValue = findRowId(row);
            if (rowIdValue != null && cleanRowId != null && rowIdValue.trim().equalsIgnoreCase(cleanRowId)) {
                String rowKey = findRowIdKey(row);
                row.putAll(entry.getRowData());
                if (!"id".equals(rowKey) && rowId != null) {
                    row.put(rowKey, rowId);
                }
                found = true;
                break;
            }
        }
        
        if (!found) {
            log.info("Row with ID {} not found in raw data. Appending as new row.", rowId);
            rawData.add(entry.getRowData());
        }

        Path fileNamePath = path.getFileName();
        if (fileNamePath != null && fileNamePath.toString().equalsIgnoreCase(StringConstants.SHIP_DATA_CSV)) {
            String warningMsg = getValidationWarningMessage(rawData);

            if (warningMsg != null) {
                int response = JOptionPane.showConfirmDialog(null, warningMsg, "CSV Validation Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) {
                    log.info("User cancelled saving ship_data.csv due to validation errors.");
                    return;
                }
            }
        }
        try {
            CsvMapper mapper = new CsvMapper();

            // Rebuild schema to ensure header is written
            CsvSchema customFormat = schema.rebuild()
                    .setUseHeader(true)
                    .build();

            // Custom serialization hack: force CsvMapper to follow our custom format
            // by registering a module that modifies serialization behavior.
            SimpleModule module = new SimpleModule();

            @SuppressWarnings("unchecked")
            Class<Map<?, ?>> mapClass = (Class<Map<?, ?>>) (Class<?>) Map.class;

            module.addSerializer(mapClass, new JsonSerializer<Map<?, ?>>() {
                @Override
                public void serialize(Map<?, ?> value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    gen.writeStartObject();
                    // Iterate over the keys to write out the data.
                    // This custom serializer ensures we bypass any unwanted default map
                    // serializations
                    // and write exactly the entries expected by the CSV schema.
                    for (Map.Entry<?, ?> mapEntry : value.entrySet()) {
                        Object key = mapEntry.getKey();
                        Object val = mapEntry.getValue();
                        String fieldName = key.toString();
                        if (customFormat.column(fieldName) == null) {
                            if (customFormat.column("\uFEFF" + fieldName) != null) {
                                fieldName = "\uFEFF" + fieldName;
                            } else if (customFormat.column("\u00EF\u00BB\u00BF" + fieldName) != null) {
                                fieldName = "\u00EF\u00BB\u00BF" + fieldName;
                            }
                        }
                        if (val != null) {
                            gen.writeStringField(fieldName, val.toString());
                        } else {
                            gen.writeStringField(fieldName, "");
                        }
                    }
                    gen.writeEndObject();
                }
            });
            mapper.registerModule(module);

            File targetFile = path.toFile();
            Path tempPath = path.resolveSibling(targetFile.getName() + ".tmp");
            try {
                mapper.writer(customFormat).writeValue(tempPath.toFile(), rawData);
                try {
                    Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException atomicEx) {
                    log.debug("Atomic move failed for {}, falling back to standard replace: {}", path, atomicEx.getMessage());
                    Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
                }
                DatabaseQueryService.clearCsvCache();
                log.info("Saved CSV to {}", targetFile);
            } finally {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException cleanupEx) {
                    log.debug("Temporary file cleanup for {}: {}", tempPath, cleanupEx.getMessage());
                }
            }
        } catch (java.io.IOException e) {
            log.error("Failed to save CSV to {}", path, e);
            Errors.printToStream(e);
        }
    }

    public static String getValidationWarningMessage(List<Map<String, String>> rawData) {
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        Set<String> missingHullIds = new HashSet<>();

        if (rawData == null || rawData.isEmpty()) return null;

        for (Map<String, String> row : rawData) {
            String rowIdValue = findRowId(row);
            if (rowIdValue != null && !rowIdValue.trim().isEmpty()) {
                String cleanId = rowIdValue.trim();
                if (!seenIds.add(cleanId)) {
                    duplicateIds.add(cleanId);
                }
                if (GameDataRepository.retrieveSpecByID(cleanId) == null) {
                    missingHullIds.add(cleanId);
                }
            }
        }

        if (!duplicateIds.isEmpty() || !missingHullIds.isEmpty()) {
            StringBuilder warningMsg = new StringBuilder();
            warningMsg.append("WARNING: Saving this ship_data.csv may cause the game to crash on startup!\n\n");
            if (!missingHullIds.isEmpty()) {
                warningMsg.append("The following hull IDs do not have a corresponding .ship file:\n");
                int count = 0;
                for (String id : missingHullIds) {
                    if (count++ < 10) warningMsg.append("- ").append(id).append("\n");
                    else {
                        warningMsg.append("... and ").append(missingHullIds.size() - 10).append(" more.\n");
                        break;
                    }
                }
                warningMsg.append("\n");
            }
            if (!duplicateIds.isEmpty()) {
                warningMsg.append("The following hull IDs are duplicated in the CSV:\n");
                int count = 0;
                for (String id : duplicateIds) {
                    if (count++ < 10) warningMsg.append("- ").append(id).append("\n");
                    else {
                        warningMsg.append("... and ").append(duplicateIds.size() - 10).append(" more.\n");
                        break;
                    }
                }
                warningMsg.append("\n");
            }
            warningMsg.append("Do you want to save anyway?");
            return warningMsg.toString();
        }
        return null;
    }

    public static String findRowId(Map<String, String> row) {
        if (row == null) return null;
        for (Map.Entry<String, String> cell : row.entrySet()) {
            String cleanKey = cell.getKey().replace("\uFEFF", "").replace("\u00EF\u00BB\u00BF", "").trim().toLowerCase(java.util.Locale.ROOT);
            if ("id".equals(cleanKey)) {
                return cell.getValue();
            }
        }
        return null;
    }

    public static String findRowIdKey(Map<String, String> row) {
        if (row == null) return "id";
        for (String key : row.keySet()) {
            String cleanKey = key.replace("\uFEFF", "").replace("\u00EF\u00BB\u00BF", "").trim().toLowerCase(java.util.Locale.ROOT);
            if ("id".equals(cleanKey)) {
                return key;
            }
        }
        return "id";
    }
}
