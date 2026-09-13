package shipeditor.representation;

import net.jqwik.api.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shipeditor.parsing.loading.CsvLoader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

class GameDataRepositoryPropertiesTest {

    private GameDataRepository repository;

    @BeforeEach
    void setUp() {
        shipeditor.persistence.SettingsManager.setSettings(shipeditor.persistence.SettingsManager.createDefault());
        repository = new GameDataRepository();
    }

    @Property
    void testNormalValidationPredicate(@ForAll("mapWithAndWithoutId") Map<String, String> row) {
        Predicate<Map<String, String>> predicate = CsvLoader.getNormalValidationPredicate();
        boolean isValid = predicate.test(row);

        String id = row.get("id");
        String name = row.get("name");

        boolean expected = (id != null && !id.isEmpty()) && (name == null || !name.startsWith("#"));
        Assertions.assertEquals(expected, isValid);
    }

    @Property
    void testWingValidationPredicate(@ForAll("mapWithAndWithoutId") Map<String, String> row) {
        Predicate<Map<String, String>> predicate = CsvLoader.getWingValidationPredicate();
        boolean isValid = predicate.test(row);

        String id = row.get("id");

        boolean expected = (id != null && !id.isEmpty() && !id.startsWith("#"));
        Assertions.assertEquals(expected, isValid);
    }

    @Test
    void testCsvCacheFallbackTrigger(@org.junit.jupiter.api.io.TempDir Path tempDir) throws java.io.IOException {
        Path testPath = tempDir.resolve("test.csv");
        java.nio.file.Files.writeString(testPath, "id,name\ntest_id,Test Ship\n", java.nio.charset.StandardCharsets.UTF_8);

        java.util.List<Map<String, String>> data = new java.util.ArrayList<>();
        Map<String, String> row = new HashMap<>();
        row.put("id", "test_id");
        data.add(row);

        repository.putRawCSVDataForPath(testPath, data);

        // Retrieve and check
        java.util.List<Map<String, String>> retrieved = repository.getRawCSVDataForPath(testPath);
        Assertions.assertNotNull(retrieved);
        Assertions.assertEquals("test_id", retrieved.get(0).get("id"));
    }

    @Test
    void testResetClearsAndAllowsRepopulation() {
        Map<Path, java.util.List<shipeditor.components.datafiles.entities.HullmodCSVEntry>> mockMap = new HashMap<>();
        Map<String, String> row = new HashMap<>();
        row.put("id", "test_hullmod");
        row.put("name", "Test Hullmod");
        mockMap.put(Path.of("test/path"), List.of(new shipeditor.components.datafiles.entities.HullmodCSVEntry(row, Path.of("test"), Path.of("test/path"))));
        
        repository.setHullmodEntriesByPackage(mockMap);
        Assertions.assertEquals(1, repository.getAllHullmodEntries().size());
        Assertions.assertNotNull(repository.getAllHullmodEntries().get("test_hullmod"));

        repository.reset();

        // After reset, calling setHullmodEntriesByPackage again should properly populate getAllHullmodEntries()
        repository.setHullmodEntriesByPackage(mockMap);
        Assertions.assertEquals(1, repository.getAllHullmodEntries().size());
        Assertions.assertNotNull(repository.getAllHullmodEntries().get("test_hullmod"));
    }

    @Test
    void testSyntheticFallbacksForUnknownEntries() {
        shipeditor.components.datafiles.entities.HullmodCSVEntry syntheticHullmod = repository.getOrCreateHullmodEntry("unknown_mod");
        Assertions.assertNotNull(syntheticHullmod);
        Assertions.assertEquals("unknown_mod", syntheticHullmod.getID());
        Assertions.assertEquals("unknown_mod", syntheticHullmod.toString());

        shipeditor.components.datafiles.entities.WingCSVEntry syntheticWing = repository.getOrCreateWingEntry("unknown_wing");
        Assertions.assertNotNull(syntheticWing);
        Assertions.assertEquals("unknown_wing", syntheticWing.getID());

        shipeditor.components.datafiles.entities.ShipSystemCSVEntry syntheticSystem = repository.getOrCreateShipsystemEntry("unknown_system");
        Assertions.assertNotNull(syntheticSystem);
        Assertions.assertEquals("unknown_system", syntheticSystem.getID());
    }

    @Provide
    Arbitrary<Map<String, String>> mapWithAndWithoutId() {
        Arbitrary<String> idArb = Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(10).injectNull(0.2);
        Arbitrary<String> nameArb = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10).map(s -> "#" + s);
        Arbitrary<String> regularNameArb = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(10);
        
        Arbitrary<String> finalNameArb = Arbitraries.oneOf(nameArb, regularNameArb).injectNull(0.2);

        return Combinators.combine(idArb, finalNameArb).as((id, name) -> {
            Map<String, String> map = new HashMap<>();
            if (id != null) map.put("id", id);
            if (name != null) map.put("name", name);
            return map;
        });
    }
}
