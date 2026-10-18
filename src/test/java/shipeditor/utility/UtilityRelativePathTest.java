package shipeditor.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that sprite paths are made relative to the owning package (mod or core), not to the
 * game root that contains the "mods" folder.
 */
class UtilityRelativePathTest {

    private Settings originalSettings;

    @BeforeEach
    void setUp() {
        originalSettings = SettingsManager.getSettings();
        // Must be null while Settings is deserialized, so that its setters do not write the real settings file.
        SettingsManager.setSettings(null);
    }

    @AfterEach
    void tearDown() {
        SettingsManager.setSettings(originalSettings);
    }

    private static Settings createSettings(Path gameRoot, Path modsFolder) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("coreFolderPath", gameRoot.toString());
        node.put("modFolderPath", modsFolder.toString());
        return mapper.treeToValue(node, Settings.class);
    }

    private static Path createGameRoot(Path tempDir) throws IOException {
        Path gameRoot = tempDir.resolve("starsector");
        Files.createDirectories(gameRoot.resolve("mods"));
        Files.createDirectories(gameRoot.resolve("starsector-core"));
        return gameRoot;
    }

    private static void createMod(Path modFolder) throws IOException {
        Files.createDirectories(modFolder);
        Files.writeString(modFolder.resolve("mod_info.json"), "{}");
    }

    @Test
    void modFileIsRelativeToModFolderWhenCoreIsParentOfMods(@TempDir Path tempDir) throws IOException {
        Path gameRoot = createGameRoot(tempDir);
        Path modFolder = gameRoot.resolve("mods").resolve("DME");
        createMod(modFolder);
        SettingsManager.setSettings(createSettings(gameRoot, gameRoot.resolve("mods")));

        Path sprite = modFolder.resolve("graphics").resolve("ships").resolve("a.png");

        assertEquals("graphics/ships/a.png", Utility.computeRelativePathFromPackage(sprite));
    }

    @Test
    void modFileInsideWrapperFolderIsRelativeToInnerModFolder(@TempDir Path tempDir) throws IOException {
        Path gameRoot = createGameRoot(tempDir);
        Path wrapper = gameRoot.resolve("mods").resolve("DME-release");
        Path innerMod = wrapper.resolve("DME");
        createMod(innerMod);
        SettingsManager.setSettings(createSettings(gameRoot, gameRoot.resolve("mods")));

        Path sprite = innerMod.resolve("graphics").resolve("ships").resolve("a.png");

        assertEquals("graphics/ships/a.png", Utility.computeRelativePathFromPackage(sprite));
    }

    @Test
    void coreSubfolderFileIsRelativeToStarsectorCore(@TempDir Path tempDir) throws IOException {
        Path gameRoot = createGameRoot(tempDir);
        createMod(gameRoot.resolve("mods").resolve("DME"));
        SettingsManager.setSettings(createSettings(gameRoot, gameRoot.resolve("mods")));

        Path sprite = gameRoot.resolve("starsector-core").resolve("graphics").resolve("ships").resolve("core.png");

        assertEquals("graphics/ships/core.png", Utility.computeRelativePathFromPackage(sprite));
    }

    @Test
    void fileDirectlyUnderGameRootIsRelativeToCoreFolder(@TempDir Path tempDir) throws IOException {
        Path gameRoot = createGameRoot(tempDir);
        SettingsManager.setSettings(createSettings(gameRoot, gameRoot.resolve("mods")));

        Path sprite = gameRoot.resolve("graphics").resolve("ships").resolve("root.png");

        assertEquals("graphics/ships/root.png", Utility.computeRelativePathFromPackage(sprite));
    }

    @Test
    void pathOutsideAnyPackageIsReturnedUnchanged(@TempDir Path tempDir) throws IOException {
        Path gameRoot = createGameRoot(tempDir);
        SettingsManager.setSettings(createSettings(gameRoot, gameRoot.resolve("mods")));

        Path outside = tempDir.resolve("elsewhere").resolve("b.png");

        assertEquals(outside.toString(), Utility.computeRelativePathFromPackage(outside));
    }

    @Test
    void nullPathProducesEmptyString() {
        assertEquals("", Utility.computeRelativePathFromPackage(null));
    }
}
