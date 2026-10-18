package shipeditor.parsing;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.Assertions;

class JsonProcessorPropertiesTest {

    @Property
    void testDoesNotCrashOnRandomInput(@ForAll String input) {
        String result = JsonProcessor.straightenMalformedText(input);
        Assertions.assertNotNull(result);
    }

    @Property
    void testStripsComments(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String beforeComment,
                            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String commentContent) {
        String input = beforeComment + "#" + commentContent;
        String result = JsonProcessor.straightenMalformedText(input);
        
        String expectedBefore = JsonProcessor.straightenMalformedText(beforeComment);
        
        Assertions.assertFalse(result.contains("#"));
        Assertions.assertEquals(expectedBefore, result);
    }

    @Property
    void testTranslatesSemicolons(@ForAll @AlphaChars @StringLength(min = 1, max = 20) String input) {
        String withSemicolon = input + ";";
        String result = JsonProcessor.straightenMalformedText(withSemicolon);
        Assertions.assertFalse(result.contains(";"));
        Assertions.assertTrue(result.contains(","));
    }

    @Property
    void testStripsNumberSuffixes(@ForAll @IntRange(min = -1000, max = 1000) int number,
                                  @ForAll("suffixGenerator") char suffix) {
        String input = number + String.valueOf(suffix);
        String result = JsonProcessor.straightenMalformedText(input);
        // We expect the suffix to be stripped
        Assertions.assertEquals(String.valueOf(number), result.trim());
    }

    @Property
    void testStripsFloatSuffixes(@ForAll @DoubleRange(min = -1000.0, max = 1000.0) double number,
                                 @ForAll("suffixGenerator") char suffix) {
        String input = number + String.valueOf(suffix);
        String result = JsonProcessor.straightenMalformedText(input);
        Assertions.assertFalse(result.contains(String.valueOf(suffix)));
    }

    @Property
    void testPreservesQuotedStrings(@ForAll @StringLength(min = 1, max = 50) String innerContent) {
        // Exclude quotes and backslashes in the inner content for a simpler test case
        String safeContent = innerContent.replace("\"", "").replace("\\", "");
        String input = "\"" + safeContent + "\"";
        String result = JsonProcessor.straightenMalformedText(input);
        Assertions.assertEquals(input, result);
    }

    @Provide
    Arbitrary<Character> suffixGenerator() {
        return Arbitraries.of('f', 'F', 'd', 'D');
    }

    @Example
    void testCollapsesRedundantCommasAcrossComments() throws Exception {
        String malformedSkin = """
                {
                    "baseHullId":"vanguard",
                    "skinHullId":"vanguard_lp",
                    "removeBuiltInWeapons":[], \t\t# weapon slot ids
                    \t,
                    "builtInMods":["ill_advised","shared_flux_sink"],
                }
                """;
        String straightened = JsonProcessor.straightenMalformedText(malformedSkin);
        var tree = FileUtilities.getConfigured().readTree(straightened);
        Assertions.assertEquals("vanguard", tree.get("baseHullId").asText());
        Assertions.assertEquals("vanguard_lp", tree.get("skinHullId").asText());
    }
}
