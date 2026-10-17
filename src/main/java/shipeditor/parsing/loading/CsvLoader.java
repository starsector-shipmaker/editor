package shipeditor.parsing.loading;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.log4j.Log4j2;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.Errors;
import shipeditor.utility.text.StringConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Log4j2
public final class CsvLoader {

    private CsvLoader() {
    }

    public static List<Map<String, String>> parseCSVTable(Path path) {
        return parseCSVTable(path, getNormalValidationPredicate());
    }

    public static List<Map<String, String>> parseCSVTable(Path path, Predicate<Map<String, String>> validationPredicate) {
        if (path == null) {
            return null;
        }
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.configure(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE, true);

        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();
        File csvFile = path.toFile();

        if (!csvFile.isFile()) {
            return null;
        }

        if (csvFile.length() == 0) {
            log.warn(StringManager.getString("CSV_FILE_EMPTY"), csvFile.getName());
            return null;
        }

        Predicate<Map<String, String>> effectivePredicate = validationPredicate != null ? validationPredicate : (row -> true);
        List<Map<String, String>> csvData = new ArrayList<>();
        try {
            csvData = readCSVWithCharset(csvFile, csvMapper, csvSchema, effectivePredicate, StandardCharsets.ISO_8859_1);
        } catch (Throwable exception) {
            log.warn(StringManager.getString("CSV_ISO_LOAD_FAILED"), csvFile.getAbsolutePath(), exception);
            try {
                csvData = readCSVWithCharset(csvFile, csvMapper, csvSchema, effectivePredicate, StandardCharsets.UTF_8);
            } catch (Throwable fallbackException) {
                log.error(StringManager.getString("CSV_FALLBACK_LOAD_FAILED"), csvFile.getAbsolutePath(), fallbackException);
                if (SettingsManager.isDeveloperModeEnabled()) {
                    log.trace("Exception trace:", fallbackException);
                }
                if (SettingsManager.areFileErrorPopupsEnabled()) {
                    Errors.showFileError(StringManager.getString("CSV_PARSE_FAILED") + csvFile, fallbackException);
                }
                return csvData;
            }
        }
        return csvData;
    }

    private static List<Map<String, String>> readCSVWithCharset(File csvFile, CsvMapper csvMapper, CsvSchema csvSchema,
            Predicate<Map<String, String>> validationPredicate,
            java.nio.charset.Charset charset) throws IOException {
        List<Map<String, String>> csvData = new ArrayList<>();
        List<Map<String, String>> rawData = new ArrayList<>();
        try (java.io.Reader reader = Files.newBufferedReader(csvFile.toPath(), charset);
                MappingIterator<Map<String, String>> iterator = csvMapper.readerFor(Map.class)
                        .with(csvSchema)
                        .readValues(reader)) {

            CsvSchema parsedSchema = (CsvSchema) iterator.getParser().getSchema();

            while (iterator.hasNext()) {
                Map<String, String> row = iterator.next();
                if (row == null) {
                    continue;
                }
                Map<String, String> optimizedRow = new java.util.LinkedHashMap<>(row.size());
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    String cleanKey = entry.getKey() != null
                            ? entry.getKey().replace("\uFEFF", "").replace("\u00EF\u00BB\u00BF", "").intern()
                            : "";
                    String value = entry.getValue();
                    if (value != null) {
                        value = value.intern();
                    }
                    optimizedRow.put(cleanKey, value);
                }
                rawData.add(optimizedRow);
                if (validationPredicate != null && validationPredicate.test(optimizedRow)) {
                    csvData.add(optimizedRow);
                }
            }
            shipeditor.representation.GameDataRepository repo = SettingsManager.getGameData();
            if (repo != null) {
                repo.putCachedCSVData(csvFile.toPath(), rawData, parsedSchema);
            }
        }
        return csvData;
    }

    public static Predicate<Map<String, String>> getNormalValidationPredicate() {
        return row -> {
            if (row == null) {
                return false;
            }
            String id = row.get(StringConstants.ID);
            String name = row.get("name");
            boolean validID = id != null && !id.isEmpty();
            return validID && (name == null || !name.startsWith("#"));
        };
    }

    public static Predicate<Map<String, String>> getWingValidationPredicate() {
        return row -> {
            if (row == null) {
                return false;
            }
            String id = row.get(StringConstants.ID);
            return id != null && !id.isEmpty() && !id.startsWith("#");
        };
    }

    public static List<Map<String, String>> reparseCSVForPath(Path path) {
        if (path == null) {
            return null;
        }
        if (SettingsManager.isDeveloperModeEnabled()) {
            log.trace(StringManager.getString("REPARSING_CSV_DISK"), path);
        }
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.configure(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE, true);
        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();
        File csvFile = path.toFile();
        if (!csvFile.isFile()) {
            return null;
        }
        Predicate<Map<String, String>> acceptAll = row -> true;
        try {
            return readCSVWithCharset(csvFile, csvMapper, csvSchema, acceptAll, StandardCharsets.ISO_8859_1);
        } catch (Throwable e) {
            try {
                return readCSVWithCharset(csvFile, csvMapper, csvSchema, acceptAll, StandardCharsets.UTF_8);
            } catch (Throwable fallback) {
                log.error(StringManager.getString("CSV_REPARSE_FALLBACK_FAILED"), path, fallback);
                return null;
            }
        }
    }
}
