package shipeditor.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.representation.weapon.WeaponEnums;
import shipeditor.representation.weapon.WeaponSpecFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that parse <strong>every</strong> real Starsector game file from a local
 * game installation. This validates the complete pipeline:
 * {@link JsonProcessor#straightenMalformedText(String)} → Jackson {@link ObjectMapper} deserialization.
 * <p>
 * These tests are conditioned on the existence of the game data directory via {@code @EnabledIf}.
 * If the game is not installed at the expected path, they are silently skipped.
 * <p>
 * Purpose: catch field mapping regressions, unknown enum values, and preprocessing edge
 * cases that synthetic tests cannot cover.
 */
class GameDataParsingIntegrationTest {

    private static final Path GAME_DATA_DIR;
    static {
        Path resolvedPath = null;
        try {
            Path workingDirectory = Path.of("").toAbsolutePath();
            Path settingsPath = workingDirectory.resolve("ship_editor_settings.json");
            if (Files.exists(settingsPath)) {
                ObjectMapper tempMapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = tempMapper.readValue(settingsPath.toFile(), java.util.Map.class);
                String gameFolderPath = (String) map.get("gameFolderPath");
                if (gameFolderPath != null) {
                    Path candidate = Path.of(gameFolderPath).resolve("data");
                    if (Files.isDirectory(candidate)) {
                        resolvedPath = candidate;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore, fallback will be used
        }
        if (resolvedPath == null) {
            resolvedPath = Path.of("starsector/data");
        }
        GAME_DATA_DIR = resolvedPath;
    }
    private static final Path HULLS_DIR = GAME_DATA_DIR.resolve("hulls");
    private static final Path SKINS_DIR = HULLS_DIR.resolve("skins");
    private static final Path VARIANTS_DIR = GAME_DATA_DIR.resolve("variants");
    private static final Path WEAPONS_DIR = GAME_DATA_DIR.resolve("weapons");
    private static final Path PROJ_DIR = WEAPONS_DIR.resolve("proj");

    private static ObjectMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = FileUtilities.getConfigured();
    }

    static boolean gameDataExists() {
        return Files.isDirectory(GAME_DATA_DIR);
    }

    // ============================================================
    // Helper: collect all files with a given extension from a directory (recursive)
    // ============================================================

    private static List<Path> collectFiles(Path directory, String extension) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .toList();
        }
    }

    // ============================================================
    // Helper: preprocess + deserialize a file
    // ============================================================

    private <T> T parseFile(Path file, Class<T> type) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        String preprocessed = JsonProcessor.straightenMalformedText(raw);
        return mapper.readValue(preprocessed, type);
    }

    // ============================================================
    // Section 1: Parse ALL .ship files
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Parse all .ship files from game data")
    void testParseAllShipFiles() throws IOException {
        List<Path> shipFiles = collectFiles(HULLS_DIR, ".ship");
        assertFalse(shipFiles.isEmpty(), "No .ship files found in " + HULLS_DIR);

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (Path shipFile : shipFiles) {
            try {
                HullSpecFile hull = parseFile(shipFile, HullSpecFile.class);
                assertNotNull(hull.getHullId(),
                        "hullId should not be null for " + shipFile.getFileName());
                assertNotNull(hull.getHullName(),
                        "hullName should not be null for " + shipFile.getFileName());
                successCount++;
            } catch (Exception e) {
                failures.add(shipFile.getFileName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(String.format("Failed to parse %d/%d .ship files:\n%s",
                    failures.size(), shipFiles.size(), String.join("\n", failures)));
        }

        assertTrue(successCount > 0, "Should have parsed at least one ship file");
        System.out.printf("Successfully parsed %d .ship files%n", successCount);
    }

    // ============================================================
    // Section 2: Parse ALL .skin files
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Parse all .skin files from game data")
    void testParseAllSkinFiles() throws IOException {
        List<Path> skinFiles = collectFiles(SKINS_DIR, ".skin");
        assertFalse(skinFiles.isEmpty(), "No .skin files found in " + SKINS_DIR);

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (Path skinFile : skinFiles) {
            try {
                SkinSpecFile skin = parseFile(skinFile, SkinSpecFile.class);
                assertNotNull(skin.getBaseHullId(),
                        "baseHullId should not be null for " + skinFile.getFileName());
                assertNotNull(skin.getSkinHullId(),
                        "skinHullId should not be null for " + skinFile.getFileName());
                successCount++;
            } catch (Exception e) {
                failures.add(skinFile.getFileName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(String.format("Failed to parse %d/%d .skin files:\n%s",
                    failures.size(), skinFiles.size(), String.join("\n", failures)));
        }

        assertTrue(successCount > 0, "Should have parsed at least one skin file");
        System.out.printf("Successfully parsed %d .skin files%n", successCount);
    }

    // ============================================================
    // Section 3: Parse ALL .variant files
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Parse all .variant files from game data")
    void testParseAllVariantFiles() throws IOException {
        List<Path> variantFiles = collectFiles(VARIANTS_DIR, ".variant");
        assertFalse(variantFiles.isEmpty(), "No .variant files found in " + VARIANTS_DIR);

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (Path variantFile : variantFiles) {
            try {
                VariantFile variant = parseFile(variantFile, VariantFile.class);
                assertNotNull(variant.getVariantId(),
                        "variantId should not be null for " + variantFile.getFileName());
                assertNotNull(variant.getShipHullId(),
                        "hullId should not be null for " + variantFile.getFileName());
                successCount++;
            } catch (Exception e) {
                failures.add(variantFile.getFileName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(String.format("Failed to parse %d/%d .variant files:\n%s",
                    failures.size(), variantFiles.size(), String.join("\n", failures)));
        }

        assertTrue(successCount > 0, "Should have parsed at least one variant file");
        System.out.printf("Successfully parsed %d .variant files%n", successCount);
    }

    // ============================================================
    // Section 4: Parse ALL .wpn files
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Parse all .wpn files from game data")
    void testParseAllWeaponFiles() throws IOException {
        List<Path> wpnFiles = collectFiles(WEAPONS_DIR, ".wpn");
        assertFalse(wpnFiles.isEmpty(), "No .wpn files found in " + WEAPONS_DIR);

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (Path wpnFile : wpnFiles) {
            try {
                WeaponSpecFile weapon = parseFile(wpnFile, WeaponSpecFile.class);
                assertNotNull(weapon.getId(),
                        "id should not be null for " + wpnFile.getFileName());
                successCount++;
            } catch (Exception e) {
                failures.add(wpnFile.getFileName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(String.format("Failed to parse %d/%d .wpn files:\n%s",
                    failures.size(), wpnFiles.size(), String.join("\n", failures)));
        }

        assertTrue(successCount > 0, "Should have parsed at least one weapon file");
        System.out.printf("Successfully parsed %d .wpn files%n", successCount);
    }

    // ============================================================
    // Section 5: Parse ALL .proj files
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Parse all .proj files from game data")
    void testParseAllProjectileFiles() throws IOException {
        List<Path> projFiles = collectFiles(PROJ_DIR, ".proj");
        assertFalse(projFiles.isEmpty(), "No .proj files found in " + PROJ_DIR);

        List<String> failures = new ArrayList<>();
        int successCount = 0;

        for (Path projFile : projFiles) {
            try {
                ProjectileSpecFile proj = parseFile(projFile, ProjectileSpecFile.class);
                assertNotNull(proj.getId(),
                        "id should not be null for " + projFile.getFileName());
                successCount++;
            } catch (Exception e) {
                failures.add(projFile.getFileName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(String.format("Failed to parse %d/%d .proj files:\n%s",
                    failures.size(), projFiles.size(), String.join("\n", failures)));
        }

        assertTrue(successCount > 0, "Should have parsed at least one projectile file");
        System.out.printf("Successfully parsed %d .proj files%n", successCount);
    }

    // ============================================================
    // Section 6: Spot-check specific well-known files for deep validation
    // ============================================================

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate onslaught.ship — complex capital ship with builtins")
    void testDeepValidateOnslaughtShip() throws IOException {
        Path file = HULLS_DIR.resolve("onslaught.ship");
        Assumptions.assumeTrue(Files.exists(file), "onslaught.ship not found");

        HullSpecFile hull = parseFile(file, HullSpecFile.class);

        assertEquals("onslaught", hull.getHullId());
        assertEquals("Onslaught", hull.getHullName());
        assertEquals("CAPITAL_SHIP", hull.getHullSize());
        assertEquals("LOW_TECH", hull.getStyle());
        assertEquals(384, hull.getHeight());
        assertEquals(288, hull.getWidth());
        assertEquals(275.0, hull.getCollisionRadius(), 0.001);

        // center: [144, 140]
        assertNotNull(hull.getCenter());
        assertEquals(144.0, hull.getCenter().getX(), 0.001);
        assertEquals(140.0, hull.getCenter().getY(), 0.001);

        // Has builtInMods: ["hbi"]
        assertNotNull(hull.getBuiltInMods());
        assertTrue(hull.getBuiltInMods().length > 0);
        assertEquals("hbi", hull.getBuiltInMods()[0]);

        // Has builtInWeapons: {"WS 016":"tpc", "WS 017":"tpc"}
        assertNotNull(hull.getBuiltInWeapons());
        assertEquals(2, hull.getBuiltInWeapons().size());
        assertEquals("tpc", hull.getBuiltInWeapons().get("WS 016"));

        // Has 25 weapon slots
        assertNotNull(hull.getWeaponSlots());
        assertEquals(25, hull.getWeaponSlots().length);

        // Has 6 engine slots
        assertNotNull(hull.getEngineSlots());
        assertEquals(6, hull.getEngineSlots().length);

        // Has 50 bound points (100 values / 2)
        assertNotNull(hull.getBounds());
        assertEquals(50, hull.getBounds().length);
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate onslaught_xiv.skin — skin with inline comments and baseValueMult")
    void testDeepValidateOnslaughtXivSkin() throws IOException {
        Path file = SKINS_DIR.resolve("onslaught_xiv.skin");
        Assumptions.assumeTrue(Files.exists(file), "onslaught_xiv.skin not found");

        SkinSpecFile skin = parseFile(file, SkinSpecFile.class);

        assertEquals("onslaught", skin.getBaseHullId());
        assertEquals("onslaught_xiv", skin.getSkinHullId());
        assertEquals("Onslaught (XIV)", skin.getHullName());
        assertFalse(skin.isBase());

        // baseValueMult: 1.75
        assertNotNull(skin.getBaseValueMult());
        assertEquals(1.75, skin.getBaseValueMult(), 0.001);

        // fleetPoints: 35
        assertEquals(Integer.valueOf(35), skin.getFleetPoints());

        // ordnancePoints (varies across Starsector patches, e.g. 370 in 0.97a, 350 in 0.98a)
        assertNotNull(skin.getOrdnancePoints());
        assertTrue(skin.getOrdnancePoints() > 0);

        // builtInMods contains "fourteenth"
        assertNotNull(skin.getBuiltInMods());
        assertTrue(skin.getBuiltInMods().contains("fourteenth"));

        // tags: ["XIV_bp", "hist3t"]
        assertNotNull(skin.getTags());
        assertEquals(2, skin.getTags().size());
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate onslaught_Standard.variant — full variant with 7 weapon groups")
    void testDeepValidateOnslaughtStandardVariant() throws IOException {
        Path file = VARIANTS_DIR.resolve("onslaught/onslaught_Standard.variant");
        Assumptions.assumeTrue(Files.exists(file), "onslaught_Standard.variant not found");

        VariantFile variant = parseFile(file, VariantFile.class);

        assertEquals("onslaught_Standard", variant.getVariantId());
        assertEquals("onslaught", variant.getShipHullId());
        assertEquals("Standard", variant.getDisplayName());
        assertTrue(variant.getFluxCapacitors() > 0);
        assertEquals(50, variant.getFluxVents());
        assertTrue(variant.isGoalVariant());

        // 5 hullmods
        assertNotNull(variant.getHullMods());
        assertEquals(5, variant.getHullMods().size());
        assertTrue(variant.getHullMods().contains("insulatedengine"));
        assertTrue(variant.getHullMods().contains("armoredweapons"));

        // 7 weapon groups
        assertNotNull(variant.getWeaponGroups());
        assertEquals(7, variant.getWeaponGroups().size());

        // First group is manual fire
        assertFalse(variant.getWeaponGroups().get(0).isAutofire());
        assertEquals("LINKED", variant.getWeaponGroups().get(0).getMode());
        assertEquals("tpc", variant.getWeaponGroups().get(0).getWeapons().get("WS 016"));
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate tachyonlance.wpn — beam weapon with unquoted enums and pierceSet")
    void testDeepValidateTachyonLanceWeapon() throws IOException {
        Path file = WEAPONS_DIR.resolve("tachyonlance.wpn");
        Assumptions.assumeTrue(Files.exists(file), "tachyonlance.wpn not found");

        WeaponSpecFile weapon = parseFile(file, WeaponSpecFile.class);

        assertEquals("tachyonlance", weapon.getId());
        assertEquals("beam", weapon.getSpecClass());
        assertEquals(WeaponEnums.WeaponType.ENERGY, weapon.getType());
        assertEquals(WeaponEnums.WeaponSize.LARGE, weapon.getSize());

        // turretOffsets: [-3, 0] → 1 point
        assertNotNull(weapon.getTurretOffsets());
        assertEquals(1, weapon.getTurretOffsets().length);
        assertEquals(-3.0, weapon.getTurretOffsets()[0].getX(), 0.001);

        // width: 25.0
        assertEquals(25.0, weapon.getWidth(), 0.001);

        // textureType: ROUGH (unquoted enum value, processed by JsonProcessor)
        assertNotNull(weapon.getTextureType());
        assertEquals(1, weapon.getTextureType().size());
        assertEquals("ROUGH", weapon.getTextureType().get(0));

        // pierceSet: [PROJECTILE_FF,...] — unquoted enum array
        assertNotNull(weapon.getPierceSet());
        assertTrue(weapon.getPierceSet().size() >= 5);

        // Colors
        assertNotNull(weapon.getFringeColor());
        assertEquals(85, weapon.getFringeColor().getRed());
        assertEquals(25, weapon.getFringeColor().getGreen());
        assertEquals(215, weapon.getFringeColor().getBlue());
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate autopulse.wpn — weapon with muzzleFlashSpec and inline comments")
    void testDeepValidateAutopulseWeapon() throws IOException {
        Path file = WEAPONS_DIR.resolve("autopulse.wpn");
        Assumptions.assumeTrue(Files.exists(file), "autopulse.wpn not found");

        WeaponSpecFile weapon = parseFile(file, WeaponSpecFile.class);

        assertEquals("autopulse", weapon.getId());
        assertEquals("projectile", weapon.getSpecClass());
        assertEquals("autopulse_shot", weapon.getProjectileSpecId());
        assertTrue(weapon.isAutocharge());

        // muzzleFlashSpec nested object
        assertNotNull(weapon.getMuzzleFlashSpec());
        assertEquals(35.0, weapon.getMuzzleFlashSpec().getLength(), 0.001);
        assertEquals(12, weapon.getMuzzleFlashSpec().getParticleCount());
        assertNotNull(weapon.getMuzzleFlashSpec().getParticleColor());
        assertEquals(100, weapon.getMuzzleFlashSpec().getParticleColor().getRed());

        // animationType: GLOW_AND_FLASH (not the commented-out GLOW)
        assertNotNull(weapon.getAnimationType());
        assertEquals(WeaponEnums.AnimationType.GLOW_AND_FLASH, weapon.getAnimationType());

        // barrelMode: ALTERNATING
        assertEquals(WeaponEnums.BarrelMode.ALTERNATING, weapon.getBarrelMode());
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate amsrm.wpn — weapon with smokeSpec and RENDER_LOADED_MISSILES")
    void testDeepValidateAmsrmWeapon() throws IOException {
        Path file = WEAPONS_DIR.resolve("amsrm.wpn");
        Assumptions.assumeTrue(Files.exists(file), "amsrm.wpn not found");

        WeaponSpecFile weapon = parseFile(file, WeaponSpecFile.class);

        assertEquals("amsrm", weapon.getId());
        assertEquals("MISSILE", weapon.getType().name());

        // smokeSpec nested object
        assertNotNull(weapon.getSmokeSpec());
        assertEquals(10.0, weapon.getSmokeSpec().getParticleSizeMin(), 0.001);
        assertEquals(16.0, weapon.getSmokeSpec().getParticleSizeRange(), 0.001);
        assertEquals(3, weapon.getSmokeSpec().getCloudParticleCount());
        assertNotNull(weapon.getSmokeSpec().getParticleColor());

        // renderHints: [RENDER_LOADED_MISSILES] — unquoted
        assertNotNull(weapon.getRenderHints());
        assertTrue(weapon.getRenderHints().contains("RENDER_LOADED_MISSILES"));
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate autopulse_shot.proj — projectile with comments and spawnType")
    void testDeepValidateAutopulseShotProj() throws IOException {
        Path file = PROJ_DIR.resolve("autopulse_shot.proj");
        Assumptions.assumeTrue(Files.exists(file), "autopulse_shot.proj not found");

        ProjectileSpecFile proj = parseFile(file, ProjectileSpecFile.class);

        assertEquals("autopulse_shot", proj.getId());
        assertEquals("projectile", proj.getSpecClass());
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Deep validate buffalo_d.skin — skin with leading decimal (.67) and restoreToBaseHull")
    void testDeepValidateBuffaloDSkin() throws IOException {
        Path file = SKINS_DIR.resolve("buffalo_d.skin");
        Assumptions.assumeTrue(Files.exists(file), "buffalo_d.skin not found");

        SkinSpecFile skin = parseFile(file, SkinSpecFile.class);

        assertEquals("buffalo", skin.getBaseHullId());
        assertEquals("buffalo_d", skin.getSkinHullId());
        assertEquals("Buffalo (D)", skin.getHullName());

        // restoreToBaseHull: true
        assertTrue(skin.getRestoreToBaseHull());

        // baseValueMult: .67 (leading decimal, no zero)
        assertNotNull(skin.getBaseValueMult());
        assertEquals(0.67, skin.getBaseValueMult(), 0.001);

        // builtInMods: ["degraded_engines"]
        assertNotNull(skin.getBuiltInMods());
        assertEquals(1, skin.getBuiltInMods().size());
        assertEquals("degraded_engines", skin.getBuiltInMods().get(0));
    }

    @Test
    @EnabledIf("gameDataExists")
    @DisplayName("Validate hullmod loading via GameDataRepository")
    void testValidateHullmodLoading() {
        shipeditor.persistence.Initializations.initializeSettingsFile();
        var repo = shipeditor.persistence.SettingsManager.getGameData();
        var allHullmods = repo.getAllHullmodEntries();
        assertNotNull(allHullmods);
        assertTrue(allHullmods.size() > 0);

        String[] testIds = {"armoredweapons", "missleracks", "advancedshieldemitter", "SKR_plagueBearer", "automated", "missile_reload", "reduced_explosion", "always_detaches", "safetyoverrides", "hardened_subsystems"};
        for (String id : testIds) {
            var entry = repo.retrieveHullmodCSVEntryByID(id);
            assertNotNull(entry, "Hullmod ID should resolve: " + id);
            assertEquals(id, entry.getID());
        }

        // Test reset and reload cycle
        repo.reset();
        var reloadedHullmods = repo.getAllHullmodEntries();
        assertNotNull(reloadedHullmods);
        assertEquals(allHullmods.size(), reloadedHullmods.size());
        for (String id : testIds) {
            var entry = repo.retrieveHullmodCSVEntryByID(id);
            assertNotNull(entry, "Hullmod ID should resolve after reset: " + id);
            assertEquals(id, entry.getID());
        }
    }
}
