package shipeditor.parsing.loading;

import net.jqwik.api.*;
import shipeditor.utility.text.StringConstants;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvLoaderPropertiesTest {

    @Property
    void testNormalValidationPredicate(
            @ForAll String id,
            @ForAll String name) {
        
        Map<String, String> row = new HashMap<>();
        if (id != null) row.put(StringConstants.ID, id);
        if (name != null) row.put("name", name);
        
        boolean expected = (id != null && !id.isBlank() && !id.trim().startsWith("#"))
                && (name == null || !name.startsWith("#"));
        
        assertEquals(expected, CsvLoader.getNormalValidationPredicate().test(row));
    }

    @Property
    void testWingValidationPredicate(
            @ForAll String id) {
        
        Map<String, String> row = new HashMap<>();
        if (id != null) row.put(StringConstants.ID, id);
        
        boolean expected = (id != null && !id.isBlank() && !id.trim().startsWith("#"));
        
        assertEquals(expected, CsvLoader.getWingValidationPredicate().test(row));
    }

    @Provide
    Arbitrary<String> blankIds() {
        return Arbitraries.strings().withChars(' ', '\t', '\n').ofMaxLength(8);
    }

    @Provide
    Arbitrary<String> hashPrefixedIds() {
        Arbitrary<String> padding = Arbitraries.strings().withChars(' ', '\t').ofMaxLength(4);
        Arbitrary<String> rest = Arbitraries.strings().ofMaxLength(12);
        return Combinators.combine(padding, rest).as((pad, tail) -> pad + "#" + tail);
    }

    @Provide
    Arbitrary<String> plainIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(16);
    }

    private static Map<String, String> rowWith(String id, String name) {
        Map<String, String> row = new HashMap<>();
        row.put(StringConstants.ID, id);
        if (name != null) {
            row.put("name", name);
        }
        return row;
    }

    @Property
    void normalPredicateRejectsBlankIds(@ForAll("blankIds") String id, @ForAll String name) {
        assertFalse(CsvLoader.getNormalValidationPredicate().test(rowWith(id, name)));
    }

    @Property
    void normalPredicateRejectsHashPrefixedIds(@ForAll("hashPrefixedIds") String id, @ForAll String name) {
        assertFalse(CsvLoader.getNormalValidationPredicate().test(rowWith(id, name)));
    }

    @Property
    void wingPredicateRejectsBlankIds(@ForAll("blankIds") String id) {
        assertFalse(CsvLoader.getWingValidationPredicate().test(rowWith(id, null)));
    }

    @Property
    void wingPredicateRejectsHashPrefixedIds(@ForAll("hashPrefixedIds") String id) {
        assertFalse(CsvLoader.getWingValidationPredicate().test(rowWith(id, null)));
    }

    @Property
    void predicatesAcceptPlainIds(@ForAll("plainIds") String id) {
        assertTrue(CsvLoader.getNormalValidationPredicate().test(rowWith(id, "Some Name")));
        assertTrue(CsvLoader.getWingValidationPredicate().test(rowWith(id, null)));
    }

    @Example
    void predicatesRejectMissingIdAndNullRow() {
        Map<String, String> noId = new HashMap<>();
        noId.put("name", "Some Name");
        assertFalse(CsvLoader.getNormalValidationPredicate().test(noId));
        assertFalse(CsvLoader.getWingValidationPredicate().test(noId));
        assertFalse(CsvLoader.getNormalValidationPredicate().test(null));
        assertFalse(CsvLoader.getWingValidationPredicate().test(null));
    }
}
