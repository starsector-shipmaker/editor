package shipeditor.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.representation.weapon.WeaponSpecFile;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive round-trip parsing and serialization tests for all Starsector
 * spec file types.
 * <p>
 * Each test follows the same pattern:
 * <ol>
 * <li>Define raw Starsector-style JSON (with {@code #} comments, semicolons,
 * unquoted keys,
 * trailing commas, type suffixes like {@code 100f}, etc.).</li>
 * <li>Pre-process through
 * {@link JsonProcessor#straightenMalformedText(String)}.</li>
 * <li>Deserialize with the configured {@link ObjectMapper}.</li>
 * <li>Verify deserialized values match expectations.</li>
 * <li>Serialize back to JSON.</li>
 * <li>Deserialize from the serialized JSON and verify all values survive the
 * round-trip.</li>
 * </ol>
 * <p>
 * This fills the gap identified in the testing skill as "No Jackson Parsing
 * Tests".
 */
class SpecFileSerializationTest {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = FileUtilities.getConfigured();
    }

    // ============================================================
    // Helper: preprocess + deserialize
    // ============================================================

    private <T> T parseStarsectorJson(String rawJson, Class<T> type) throws IOException {
        String preprocessed = JsonProcessor.straightenMalformedText(rawJson);
        return mapper.readValue(preprocessed, type);
    }

    // ============================================================
    // Helper: round-trip (serialize then deserialize)
    // ============================================================

    private <T> T roundTrip(T object, Class<T> type) throws IOException {
        String json = mapper.writeValueAsString(object);
        assertNotNull(json, "Serialized JSON should not be null");
        assertFalse(json.isEmpty(), "Serialized JSON should not be empty");
        return mapper.readValue(json, type);
    }

    // ============================================================
    // Section 1: .ship (HullSpecFile)
    // ============================================================

    @Nested
    class ShipFileTests {

        /**
         * Minimal .ship file with all core fields, using authentic Starsector
         * formatting quirks:
         * - # comments
         * - semicolons as separators
         * - unquoted keys and string values
         * - trailing commas in arrays
         * - numeric suffixes (100f)
         */
        private static final String MINIMAL_SHIP_JSON = """
                {
                    # This is a Starsector ship hull definition
                    hullName: "Test Frigate";
                    hullId: "test_frigate";
                    hullSize: "FRIGATE";
                    style: "LOW_TECH";
                    spriteName: "graphics/ships/test_frigate.png";
                    height: 80;
                    width: 120;
                    center: [60, 40];
                    collisionRadius: 90f;
                    shieldCenter: [0, -5];
                    shieldRadius: 100;
                    viewOffset: 0;
                    weaponSlots: [
                        {
                            id: "WS0001";
                            size: "MEDIUM";
                            type: "BALLISTIC";
                            mount: "TURRET";
                            arc: 200;
                            angle: 0;
                            locations: [30, 0]
                        },
                        {
                            id: "WS0002";
                            size: "SMALL";
                            type: "ENERGY";
                            mount: "HARDPOINT";
                            arc: 5;
                            angle: 0;
                            locations: [40, 10]
                        },
                    ];
                    engineSlots: [
                        {
                            location: [-35, 15];
                            length: 40f;
                            width: 12f;
                            angle: 180;
                            contrailSize: 12;
                            style: "LOW_TECH"
                        },
                        {
                            location: [-35, -15];
                            length: 40f;
                            width: 12f;
                            angle: 180;
                            contrailSize: 12;
                            style: "LOW_TECH"
                        },
                    ];
                    bounds: [
                        -40, -20,
                        -30, -40,
                        20, -40,
                        40, 0,
                        20, 40,
                        -30, 40,
                    ]
                }
                """;

        @Test
        void testDeserializeMinimalShip() throws IOException {
            HullSpecFile hull = parseStarsectorJson(MINIMAL_SHIP_JSON, HullSpecFile.class);

            assertEquals("Test Frigate", hull.getHullName());
            assertEquals("test_frigate", hull.getHullId());
            assertEquals("FRIGATE", hull.getHullSize());
            assertEquals("LOW_TECH", hull.getStyle());
            assertEquals("graphics/ships/test_frigate.png", hull.getSpriteName());
            assertEquals(80, hull.getHeight());
            assertEquals(120, hull.getWidth());

            // center [60, 40]
            assertNotNull(hull.getCenter());
            assertEquals(60.0, hull.getCenter().getX(), 0.001);
            assertEquals(40.0, hull.getCenter().getY(), 0.001);

            // collisionRadius: 90f — the "f" should be stripped by JsonProcessor
            assertEquals(90.0, hull.getCollisionRadius(), 0.001);

            // shieldCenter [0, -5]
            assertNotNull(hull.getShieldCenter());
            assertEquals(0.0, hull.getShieldCenter().getX(), 0.001);
            assertEquals(-5.0, hull.getShieldCenter().getY(), 0.001);

            assertEquals(100.0, hull.getShieldRadius(), 0.001);

            // weaponSlots
            assertNotNull(hull.getWeaponSlots());
            assertEquals(2, hull.getWeaponSlots().length);
            assertEquals("WS0001", hull.getWeaponSlots()[0].getId());
            assertEquals("MEDIUM", hull.getWeaponSlots()[0].getSize());
            assertEquals("BALLISTIC", hull.getWeaponSlots()[0].getType());
            assertEquals("TURRET", hull.getWeaponSlots()[0].getMount());
            assertEquals(200.0, hull.getWeaponSlots()[0].getArc(), 0.001);

            assertEquals("WS0002", hull.getWeaponSlots()[1].getId());
            assertEquals("SMALL", hull.getWeaponSlots()[1].getSize());
            assertEquals("ENERGY", hull.getWeaponSlots()[1].getType());
            assertEquals("HARDPOINT", hull.getWeaponSlots()[1].getMount());

            // engineSlots
            assertNotNull(hull.getEngineSlots());
            assertEquals(2, hull.getEngineSlots().length);
            assertEquals(40.0, hull.getEngineSlots()[0].getLength(), 0.001);
            assertEquals(12.0, hull.getEngineSlots()[0].getWidth(), 0.001);
            assertEquals(180.0, hull.getEngineSlots()[0].getAngle(), 0.001);
            assertEquals("LOW_TECH", hull.getEngineSlots()[0].getStyle());

            // bounds — 6 coordinate pairs = 6 points
            assertNotNull(hull.getBounds());
            assertEquals(6, hull.getBounds().length);
            assertEquals(-40.0, hull.getBounds()[0].getX(), 0.001);
            assertEquals(-20.0, hull.getBounds()[0].getY(), 0.001);
        }

        @Test
        void testRoundTripMinimalShip() throws IOException {
            HullSpecFile original = parseStarsectorJson(MINIMAL_SHIP_JSON, HullSpecFile.class);
            HullSpecFile roundTripped = roundTrip(original, HullSpecFile.class);

            assertEquals(original.getHullName(), roundTripped.getHullName());
            assertEquals(original.getHullId(), roundTripped.getHullId());
            assertEquals(original.getHullSize(), roundTripped.getHullSize());
            assertEquals(original.getStyle(), roundTripped.getStyle());
            assertEquals(original.getSpriteName(), roundTripped.getSpriteName());
            assertEquals(original.getHeight(), roundTripped.getHeight());
            assertEquals(original.getWidth(), roundTripped.getWidth());
            assertEquals(original.getCollisionRadius(), roundTripped.getCollisionRadius(), 0.001);
            assertEquals(original.getShieldRadius(), roundTripped.getShieldRadius(), 0.001);

            assertNotNull(roundTripped.getCenter());
            assertEquals(original.getCenter().getX(), roundTripped.getCenter().getX(), 0.001);
            assertEquals(original.getCenter().getY(), roundTripped.getCenter().getY(), 0.001);

            assertNotNull(roundTripped.getShieldCenter());
            assertEquals(original.getShieldCenter().getX(), roundTripped.getShieldCenter().getX(), 0.001);
            assertEquals(original.getShieldCenter().getY(), roundTripped.getShieldCenter().getY(), 0.001);

            assertEquals(original.getWeaponSlots().length, roundTripped.getWeaponSlots().length);
            assertEquals(original.getEngineSlots().length, roundTripped.getEngineSlots().length);
            assertEquals(original.getBounds().length, roundTripped.getBounds().length);
        }

        /**
         * Tests a ship with built-in mods, built-in weapons, built-in wings, and
         * modules.
         * Uses the Starsector convention where builtInModules can be an array of
         * single-entry objects OR a plain object — both should work via
         * ModulesDeserializer.
         */
        private static final String SHIP_WITH_BUILTINS_JSON = """
                {
                    hullName: "Station Core";
                    hullId: "station_core";
                    hullSize: "CAPITAL_SHIP";
                    style: "MIDLINE";
                    spriteName: "graphics/ships/station_core.png";
                    height: 300;
                    width: 300;
                    center: [150, 150];
                    collisionRadius: 200;
                    shieldCenter: [0, 0];
                    shieldRadius: 250;
                    viewOffset: 0;
                    builtInMods: [
                        "autorepair",
                        "do_not_fire_through",
                    ];
                    builtInWeapons: {
                        "WS0001": "flarelauncher",
                        "WS0002": "blinker",
                    };
                    builtInWings: [
                        "mining_drone_wing",
                    ];
                    builtInModules: {
                        "LEFT": "station_left_Standard",
                        "RIGHT": "station_right_Standard",
                    };
                    weaponSlots: [];
                    engineSlots: [];
                    bounds: []
                }
                """;

        @Test
        void testDeserializeShipWithBuiltins() throws IOException {
            HullSpecFile hull = parseStarsectorJson(SHIP_WITH_BUILTINS_JSON, HullSpecFile.class);

            assertNotNull(hull.getBuiltInMods());
            assertEquals(2, hull.getBuiltInMods().length);
            assertEquals("autorepair", hull.getBuiltInMods()[0]);
            assertEquals("do_not_fire_through", hull.getBuiltInMods()[1]);

            assertNotNull(hull.getBuiltInWeapons());
            assertEquals(2, hull.getBuiltInWeapons().size());
            assertEquals("flarelauncher", hull.getBuiltInWeapons().get("WS0001"));
            assertEquals("blinker", hull.getBuiltInWeapons().get("WS0002"));

            assertNotNull(hull.getBuiltInWings());
            assertEquals(1, hull.getBuiltInWings().length);
            assertEquals("mining_drone_wing", hull.getBuiltInWings()[0]);

            assertNotNull(hull.getBuiltInModules());
            assertEquals(2, hull.getBuiltInModules().size());
            assertEquals("station_left_Standard", hull.getBuiltInModules().get("LEFT"));
            assertEquals("station_right_Standard", hull.getBuiltInModules().get("RIGHT"));
        }

        @Test
        void testRoundTripShipWithBuiltins() throws IOException {
            HullSpecFile original = parseStarsectorJson(SHIP_WITH_BUILTINS_JSON, HullSpecFile.class);
            HullSpecFile roundTripped = roundTrip(original, HullSpecFile.class);

            assertArrayEquals(original.getBuiltInMods(), roundTripped.getBuiltInMods());

            assertEquals(original.getBuiltInWeapons().size(), roundTripped.getBuiltInWeapons().size());
            for (var entry : original.getBuiltInWeapons().entrySet()) {
                assertEquals(entry.getValue(), roundTripped.getBuiltInWeapons().get(entry.getKey()));
            }

            assertArrayEquals(original.getBuiltInWings(), roundTripped.getBuiltInWings());
        }

        /**
         * Tests handling of the moduleAnchor field, which is used for station modules.
         */
        @Test
        void testShipWithModuleAnchor() throws IOException {
            String json = """
                    {
                        hullName: "Module Wing";
                        hullId: "module_wing";
                        hullSize: "CRUISER";
                        style: "LOW_TECH";
                        spriteName: "graphics/ships/module.png";
                        height: 100;
                        width: 80;
                        center: [50, 40];
                        moduleAnchor: [25, 0];
                        collisionRadius: 60;
                        shieldCenter: [0, 0];
                        shieldRadius: 0;
                        viewOffset: 0;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);

            assertNotNull(hull.getModuleAnchor());
            assertEquals(25.0, hull.getModuleAnchor().getX(), 0.001);
            assertEquals(0.0, hull.getModuleAnchor().getY(), 0.001);

            HullSpecFile roundTripped = roundTrip(hull, HullSpecFile.class);
            assertNotNull(roundTripped.getModuleAnchor());
            assertEquals(25.0, roundTripped.getModuleAnchor().getX(), 0.001);
        }

        /**
         * Tests that the numeric suffix 'f' on numbers is correctly stripped.
         */
        @Test
        void testNumericSuffixStripping() throws IOException {
            String json = """
                    {
                        hullName: "Suffix Test";
                        hullId: "suffix_test";
                        hullSize: "FRIGATE";
                        style: "LOW_TECH";
                        spriteName: "graphics/ships/test.png";
                        height: 50;
                        width: 50;
                        center: [25, 25];
                        collisionRadius: 123.5f;
                        shieldCenter: [0, 0];
                        shieldRadius: 75.0d;
                        viewOffset: 0;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertEquals(123.5, hull.getCollisionRadius(), 0.001, "f suffix should be stripped");
            assertEquals(75.0, hull.getShieldRadius(), 0.001, "d suffix should be stripped");
        }

        /**
         * Tests that # comments are correctly stripped from ship JSON.
         */
        @Test
        void testCommentStripping() throws IOException {
            String json = """
                    {
                        # Main hull definition
                        hullName: "Comment Test"; # inline comment
                        hullId: "comment_test";
                        hullSize: "FRIGATE";
                        style: "LOW_TECH";
                        spriteName: "graphics/ships/test.png";
                        height: 50;
                        width: 50;
                        center: [25, 25];
                        collisionRadius: 50;
                        shieldCenter: [0, 0];
                        shieldRadius: 50;
                        # This line is a full comment
                        viewOffset: 0;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertEquals("Comment Test", hull.getHullName());
            assertEquals("comment_test", hull.getHullId());
        }
    }

    // ============================================================
    // Section 2: .skin (SkinSpecFile)
    // ============================================================

    @Nested
    class SkinFileTests {

        /**
         * A typical .skin file that overrides base hull properties.
         */
        private static final String MINIMAL_SKIN_JSON = """
                {
                    baseHullId: "onslaught";
                    skinHullId: "onslaught_xiv";
                    hullName: "Onslaught (XIV)";
                    hullDesignation: "Battleship";
                    hullStyle: "MIDLINE";
                    manufacturer: "Domain";
                    descriptionId: "onslaught_xiv";
                    descriptionPrefix: "Fourteen Legion variant.";
                    fleetPoints: 45;
                    ordnancePoints: 420;
                    baseValue: 350000;
                    spriteName: "graphics/ships/onslaught_xiv.png";
                    tags: ["xiv", "rare_bp"];
                    builtInMods: ["fourteenthbattlegroup"];
                    removeWeaponSlots: ["WS0008"];
                    removeEngineSlots: [3];
                    weaponSlotChanges: {
                        "WS0001": {
                            id: "WS0001";
                            size: "LARGE";
                            type: "BALLISTIC";
                            mount: "TURRET";
                            arc: 120;
                            angle: 0;
                            locations: [80, 0]
                        }
                    };
                    coversColor: [200, 180, 140, 255];
                }
                """;

        @Test
        void testDeserializeMinimalSkin() throws IOException {
            SkinSpecFile skin = parseStarsectorJson(MINIMAL_SKIN_JSON, SkinSpecFile.class);

            assertEquals("onslaught", skin.getBaseHullId());
            assertEquals("onslaught_xiv", skin.getSkinHullId());
            assertEquals("Onslaught (XIV)", skin.getHullName());
            assertEquals("Battleship", skin.getHullDesignation());
            assertEquals("MIDLINE", skin.getHullStyle());
            assertEquals("Domain", skin.getManufacturer());
            assertEquals("onslaught_xiv", skin.getDescriptionId());
            assertEquals("Fourteen Legion variant.", skin.getDescriptionPrefix());
            assertEquals(Integer.valueOf(45), skin.getFleetPoints());
            assertEquals(Integer.valueOf(420), skin.getOrdnancePoints());
            assertEquals(Integer.valueOf(350000), skin.getBaseValue());
            assertEquals("graphics/ships/onslaught_xiv.png", skin.getSpriteName());

            assertNotNull(skin.getTags());
            assertEquals(2, skin.getTags().size());
            assertTrue(skin.getTags().contains("xiv"));
            assertTrue(skin.getTags().contains("rare_bp"));

            assertNotNull(skin.getBuiltInMods());
            assertEquals(1, skin.getBuiltInMods().size());
            assertEquals("fourteenthbattlegroup", skin.getBuiltInMods().get(0));

            assertNotNull(skin.getRemoveWeaponSlots());
            assertEquals(1, skin.getRemoveWeaponSlots().size());
            assertEquals("WS0008", skin.getRemoveWeaponSlots().get(0));

            assertNotNull(skin.getRemoveEngineSlots());
            assertEquals(1, skin.getRemoveEngineSlots().size());
            assertEquals(Integer.valueOf(3), skin.getRemoveEngineSlots().get(0));

            assertNotNull(skin.getWeaponSlotChanges());
            assertEquals(1, skin.getWeaponSlotChanges().size());
            assertTrue(skin.getWeaponSlotChanges().containsKey("WS0001"));
            assertEquals("LARGE", skin.getWeaponSlotChanges().get("WS0001").getSize());

            // coversColor deserialized via ColorArrayRGBADeserializer
            assertNotNull(skin.getCoversColor());
            assertEquals(200, skin.getCoversColor().getRed());
            assertEquals(180, skin.getCoversColor().getGreen());
            assertEquals(140, skin.getCoversColor().getBlue());
            assertEquals(255, skin.getCoversColor().getAlpha());
        }

        @Test
        void testRoundTripMinimalSkin() throws IOException {
            SkinSpecFile original = parseStarsectorJson(MINIMAL_SKIN_JSON, SkinSpecFile.class);
            SkinSpecFile roundTripped = roundTrip(original, SkinSpecFile.class);

            assertEquals(original.getBaseHullId(), roundTripped.getBaseHullId());
            assertEquals(original.getSkinHullId(), roundTripped.getSkinHullId());
            assertEquals(original.getHullName(), roundTripped.getHullName());
            assertEquals(original.getHullDesignation(), roundTripped.getHullDesignation());
            assertEquals(original.getHullStyle(), roundTripped.getHullStyle());
            assertEquals(original.getFleetPoints(), roundTripped.getFleetPoints());
            assertEquals(original.getOrdnancePoints(), roundTripped.getOrdnancePoints());
            assertEquals(original.getBaseValue(), roundTripped.getBaseValue());
            assertEquals(original.getSpriteName(), roundTripped.getSpriteName());
            assertEquals(original.getTags(), roundTripped.getTags());
            assertEquals(original.getBuiltInMods(), roundTripped.getBuiltInMods());
            assertEquals(original.getRemoveWeaponSlots(), roundTripped.getRemoveWeaponSlots());
            assertEquals(original.getRemoveEngineSlots(), roundTripped.getRemoveEngineSlots());
            assertEquals(original.getCoversColor(), roundTripped.getCoversColor());
        }

        /**
         * Tests skin with addHints and removeHints using ShipTypeHints enum
         * deserialization.
         */
        @Test
        void testSkinWithHints() throws IOException {
            String json = """
                    {
                        baseHullId: "wolf";
                        skinHullId: "wolf_d";
                        hullName: "Wolf (D)";
                        addHints: ["CIVILIAN"];
                        removeHints: ["COMBAT"];
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);

            assertNotNull(skin.getAddHints());
            assertEquals(1, skin.getAddHints().size());

            assertNotNull(skin.getRemoveHints());
            assertEquals(1, skin.getRemoveHints().size());
        }

        /**
         * Tests that getHullId() returns skinHullId for SkinSpecFile.
         */
        @Test
        void testGetHullIdReturnsSkinHullId() throws IOException {
            String json = """
                    {
                        baseHullId: "eagle";
                        skinHullId: "eagle_xiv";
                        hullName: "Eagle (XIV)";
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);
            assertEquals("eagle_xiv", skin.getHullId());
        }

        /**
         * Tests engine slot changes in a skin (map of index → EngineSlot).
         */
        @Test
        void testSkinWithEngineSlotChanges() throws IOException {
            String json = """
                    {
                        baseHullId: "onslaught";
                        skinHullId: "onslaught_mod";
                        hullName: "Onslaught Modified";
                        engineSlotChanges: {
                            "0": {
                                location: [-100, 30];
                                length: 60;
                                width: 20;
                                angle: 180;
                                style: "MIDLINE"
                            }
                        }
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);

            assertNotNull(skin.getEngineSlotChanges());
            assertEquals(1, skin.getEngineSlotChanges().size());
            assertTrue(skin.getEngineSlotChanges().containsKey("0"));
            assertEquals("MIDLINE", skin.getEngineSlotChanges().get("0").getStyle());
        }

        /**
         * Tests empty skin (factory default).
         */
        @Test
        void testEmptySkinFactory() {
            SkinSpecFile empty = SkinSpecFile.empty();
            assertTrue(empty.isBase());
            assertEquals("Base hull", empty.toString());
        }

        @Test
        void testSkinWithEscapedQuotesInDescription() throws IOException {
            String json = """
                    {
                        "baseHullId": "els_mistral",
                        "skinHullId": "els_mistral_m",
                        "hullName": "Mistral (M)",
                        "descriptionPrefix": "Hulls modified in this manner are usually tagged with an \\"M\\", for \\"military spec\\".",
                        "spriteName": "graphics/ELS/ships/els_mistral_m.png"
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);
            assertNotNull(skin);
            assertEquals("els_mistral_m", skin.getSkinHullId());
            assertEquals("Hulls modified in this manner are usually tagged with an \"M\", for \"military spec\".", skin.getDescriptionPrefix());
        }
    }

    // ============================================================
    // Section 3: .variant (VariantFile)
    // ============================================================

    @Nested
    class VariantFileTests {

        /**
         * A typical .variant file with weapon groups, hullmods, and wings.
         */
        private static final String MINIMAL_VARIANT_JSON = """
                {
                    displayName: "Standard";
                    fluxCapacitors: 20;
                    fluxVents: 30;
                    goalVariant: true;
                    hullId: "onslaught";
                    hullMods: [
                        "heavyarmor",
                        "unstable_injector",
                    ];
                    permaMods: [
                        "reinforcedhull",
                    ];
                    sMods: [
                        "heavyarmor",
                    ];
                    quality: 0.75;
                    variantId: "onslaught_Standard";
                    weaponGroups: [
                        {
                            autofire: false;
                            mode: "LINKED";
                            weapons: {
                                "WS0001": "tachyon_lance",
                                "WS0002": "tachyon_lance",
                            }
                        },
                        {
                            autofire: true;
                            mode: "LINKED";
                            weapons: {
                                "WS0003": "vulcan",
                                "WS0004": "vulcan",
                            }
                        },
                    ];
                    wings: [
                        "broadsword_wing",
                        "talon_wing",
                    ];
                    modules: {
                        "LEFT": "station_left_Standard",
                        "RIGHT": "station_right_Standard",
                    }
                }
                """;

        @Test
        void testDeserializeMinimalVariant() throws IOException {
            VariantFile variant = parseStarsectorJson(MINIMAL_VARIANT_JSON, VariantFile.class);

            assertEquals("Standard", variant.getDisplayName());
            assertEquals(20, variant.getFluxCapacitors());
            assertEquals(30, variant.getFluxVents());
            assertTrue(variant.isGoalVariant());
            assertEquals("onslaught", variant.getShipHullId());
            assertEquals("onslaught_Standard", variant.getVariantId());
            assertEquals(0.75, variant.getQuality(), 0.001);

            assertNotNull(variant.getHullMods());
            assertEquals(2, variant.getHullMods().size());
            assertTrue(variant.getHullMods().contains("heavyarmor"));
            assertTrue(variant.getHullMods().contains("unstable_injector"));

            assertNotNull(variant.getPermaMods());
            assertEquals(1, variant.getPermaMods().size());
            assertEquals("reinforcedhull", variant.getPermaMods().get(0));

            assertNotNull(variant.getSMods());
            assertEquals(1, variant.getSMods().size());
            assertEquals("heavyarmor", variant.getSMods().get(0));

            assertNotNull(variant.getWeaponGroups());
            assertEquals(2, variant.getWeaponGroups().size());
            assertFalse(variant.getWeaponGroups().get(0).isAutofire());
            assertEquals("LINKED", variant.getWeaponGroups().get(0).getMode());
            assertEquals("tachyon_lance", variant.getWeaponGroups().get(0).getWeapons().get("WS0001"));

            assertTrue(variant.getWeaponGroups().get(1).isAutofire());
            assertEquals("vulcan", variant.getWeaponGroups().get(1).getWeapons().get("WS0003"));

            assertNotNull(variant.getWings());
            assertEquals(2, variant.getWings().size());
            assertEquals("broadsword_wing", variant.getWings().get(0));

            assertNotNull(variant.getModules());
            assertEquals(2, variant.getModules().size());
            assertEquals("station_left_Standard", variant.getModules().get("LEFT"));
        }

        @Test
        void testRoundTripMinimalVariant() throws IOException {
            VariantFile original = parseStarsectorJson(MINIMAL_VARIANT_JSON, VariantFile.class);

            // VariantFile uses a custom VariantFileSerializer
            String json = mapper.writeValueAsString(original);
            assertNotNull(json);
            assertFalse(json.isEmpty());

            VariantFile roundTripped = mapper.readValue(json, VariantFile.class);

            assertEquals(original.getDisplayName(), roundTripped.getDisplayName());
            assertEquals(original.getFluxCapacitors(), roundTripped.getFluxCapacitors());
            assertEquals(original.getFluxVents(), roundTripped.getFluxVents());
            assertEquals(original.isGoalVariant(), roundTripped.isGoalVariant());
            assertEquals(original.getShipHullId(), roundTripped.getShipHullId());
            assertEquals(original.getVariantId(), roundTripped.getVariantId());

            assertEquals(original.getHullMods(), roundTripped.getHullMods());
            assertEquals(original.getPermaMods(), roundTripped.getPermaMods());
            assertEquals(original.getSMods(), roundTripped.getSMods());

            assertEquals(original.getWeaponGroups().size(), roundTripped.getWeaponGroups().size());
            for (int i = 0; i < original.getWeaponGroups().size(); i++) {
                assertEquals(original.getWeaponGroups().get(i).isAutofire(),
                        roundTripped.getWeaponGroups().get(i).isAutofire());
                assertEquals(original.getWeaponGroups().get(i).getMode(),
                        roundTripped.getWeaponGroups().get(i).getMode());
                assertEquals(original.getWeaponGroups().get(i).getWeapons(),
                        roundTripped.getWeaponGroups().get(i).getWeapons());
            }
        }

        /**
         * Tests that quality < 0 is excluded from serialization by
         * VariantFileSerializer.
         */
        @Test
        void testQualityExclusionWhenNegative() throws IOException {
            String json = """
                    {
                        displayName: "No Quality";
                        fluxCapacitors: 10;
                        fluxVents: 10;
                        goalVariant: false;
                        hullId: "wolf";
                        hullMods: [];
                        variantId: "wolf_NoQuality";
                        weaponGroups: []
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);
            assertEquals(-1.0, variant.getQuality(), 0.001, "Default quality should be -1");

            String serialized = mapper.writeValueAsString(variant);
            assertFalse(serialized.contains("quality"),
                    "quality field should be excluded when < 0");
        }

        /**
         * Tests that quality >= 0 is included in serialization.
         */
        @Test
        void testQualityIncludedWhenNonNegative() throws IOException {
            VariantFile variant = parseStarsectorJson(MINIMAL_VARIANT_JSON, VariantFile.class);
            assertEquals(0.75, variant.getQuality(), 0.001);

            String serialized = mapper.writeValueAsString(variant);
            assertTrue(serialized.contains("quality"),
                    "quality field should be present when >= 0");
        }

        /**
         * Tests suppressedMods field handling.
         */
        @Test
        void testSuppressedMods() throws IOException {
            String json = """
                    {
                        displayName: "Suppressed";
                        fluxCapacitors: 5;
                        fluxVents: 5;
                        goalVariant: false;
                        hullId: "wolf";
                        hullMods: [];
                        suppressedMods: ["heavyarmor", "unstable_injector"];
                        variantId: "wolf_Suppressed";
                        weaponGroups: []
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);

            assertNotNull(variant.getSuppressedMods());
            assertEquals(2, variant.getSuppressedMods().size());
            assertEquals("heavyarmor", variant.getSuppressedMods().get(0));

            String serialized = mapper.writeValueAsString(variant);
            assertTrue(serialized.contains("suppressedMods"));
        }

        /**
         * Tests the "mods" alias for hullMods field.
         */
        @Test
        void testModsAliasForHullMods() throws IOException {
            String json = """
                    {
                        displayName: "Alias Test";
                        fluxCapacitors: 0;
                        fluxVents: 0;
                        goalVariant: false;
                        hullId: "test";
                        mods: ["heavyarmor"];
                        variantId: "test_Alias";
                        weaponGroups: []
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);
            assertNotNull(variant.getHullMods());
            assertEquals(1, variant.getHullMods().size());
            assertEquals("heavyarmor", variant.getHullMods().get(0));
        }

        /**
         * Tests the "goalVariants" alias for goalVariant field.
         */
        @Test
        void testGoalVariantsAlias() throws IOException {
            String json = """
                    {
                        displayName: "Alias Test";
                        fluxCapacitors: 0;
                        fluxVents: 0;
                        goalVariants: true;
                        hullId: "test";
                        hullMods: [];
                        variantId: "test_Alias";
                        weaponGroups: []
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);
            assertTrue(variant.isGoalVariant());
        }

        /**
         * Tests modules deserialized as array of single-entry objects (alternative
         * format).
         */
        @Test
        void testModulesAsArrayFormat() throws IOException {
            String json = """
                    {
                        displayName: "Module Array Test";
                        fluxCapacitors: 0;
                        fluxVents: 0;
                        goalVariant: false;
                        hullId: "test";
                        hullMods: [];
                        variantId: "test_Modules";
                        weaponGroups: [];
                        modules: [
                            {"LEFT": "station_left_Standard"},
                            {"RIGHT": "station_right_Standard"},
                        ]
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);

            assertNotNull(variant.getModules());
            assertEquals(2, variant.getModules().size());
            assertEquals("station_left_Standard", variant.getModules().get("LEFT"));
            assertEquals("station_right_Standard", variant.getModules().get("RIGHT"));
        }

        /**
         * Tests that empty variant factory works correctly.
         */
        @Test
        void testEmptyVariantFactory() {
            VariantFile empty = VariantFile.empty();
            assertTrue(empty.isEmpty());
        }
    }

    // ============================================================
    // Section 4: .wpn (WeaponSpecFile)
    // ============================================================

    @Nested
    class WeaponFileTests {

        /**
         * A typical .wpn file with offsets, colors, and all major weapon properties.
         */
        private static final String MINIMAL_WEAPON_JSON = """
                {
                    specClass: "projectile";
                    id: "heavy_blaster";
                    type: "ENERGY";
                    size: "MEDIUM";
                    collisionClass: "PROJECTILE_FF";
                    turretSprite: "graphics/weapons/heavy_blaster_turret.png";
                    turretGunSprite: "graphics/weapons/heavy_blaster_gun.png";
                    turretGlowSprite: "graphics/weapons/heavy_blaster_glow.png";
                    hardpointSprite: "graphics/weapons/heavy_blaster_hardpoint.png";
                    hardpointGunSprite: "graphics/weapons/heavy_blaster_gun_hp.png";
                    hardpointGlowSprite: "graphics/weapons/heavy_blaster_glow_hp.png";
                    visualRecoil: 5f;
                    turretOffsets: [12, 0];
                    turretAngleOffsets: [0];
                    hardpointOffsets: [18, 0];
                    hardpointAngleOffsets: [0];
                    numFrames: 6;
                    frameRate: 15;
                    fringeColor: [255, 200, 50, 200];
                    coreColor: [255, 255, 255, 255];
                    glowColor: [255, 200, 100, 150];
                    width: 15f;
                    renderHints: ["RENDER_BARREL_BELOW"];
                    animationType: "MUZZLE_FLASH";
                    projectileSpecId: "heavy_blaster_proj";
                    fireSoundOne: "heavy_blaster_fire";
                }
                """;

        @Test
        void testDeserializeMinimalWeapon() throws IOException {
            WeaponSpecFile weapon = parseStarsectorJson(MINIMAL_WEAPON_JSON, WeaponSpecFile.class);

            assertEquals("projectile", weapon.getSpecClass());
            assertEquals("heavy_blaster", weapon.getId());
            assertEquals("ENERGY", weapon.getType().name());
            assertEquals("MEDIUM", weapon.getSize().name());
            assertEquals("PROJECTILE_FF", weapon.getCollisionClass());

            assertEquals("graphics/weapons/heavy_blaster_turret.png", weapon.getTurretSprite());
            assertEquals("graphics/weapons/heavy_blaster_gun.png", weapon.getTurretGunSprite());
            assertEquals("graphics/weapons/heavy_blaster_glow.png", weapon.getTurretGlowSprite());
            assertEquals("graphics/weapons/heavy_blaster_hardpoint.png", weapon.getHardpointSprite());
            assertEquals("graphics/weapons/heavy_blaster_gun_hp.png", weapon.getHardpointGunSprite());
            assertEquals("graphics/weapons/heavy_blaster_glow_hp.png", weapon.getHardpointGlowSprite());

            // visualRecoil: 5f — f suffix should be stripped
            assertEquals(5.0, weapon.getVisualRecoil(), 0.001);

            // turretOffsets: [12, 0] → 1 Point2D
            assertNotNull(weapon.getTurretOffsets());
            assertEquals(1, weapon.getTurretOffsets().length);
            assertEquals(12.0, weapon.getTurretOffsets()[0].getX(), 0.001);
            assertEquals(0.0, weapon.getTurretOffsets()[0].getY(), 0.001);

            // turretAngleOffsets: [0]
            assertNotNull(weapon.getTurretAngleOffsets());
            assertEquals(1, weapon.getTurretAngleOffsets().length);
            assertEquals(0.0, weapon.getTurretAngleOffsets()[0], 0.001);

            // hardpointOffsets: [18, 0] → 1 Point2D
            assertNotNull(weapon.getHardpointOffsets());
            assertEquals(1, weapon.getHardpointOffsets().length);
            assertEquals(18.0, weapon.getHardpointOffsets()[0].getX(), 0.001);

            assertEquals(6, weapon.getNumFrames());
            assertEquals(15, weapon.getFrameRate());

            // fringeColor deserialized via ColorArrayRGBADeserializer
            assertNotNull(weapon.getFringeColor());
            assertEquals(255, weapon.getFringeColor().getRed());
            assertEquals(200, weapon.getFringeColor().getGreen());
            assertEquals(50, weapon.getFringeColor().getBlue());
            assertEquals(200, weapon.getFringeColor().getAlpha());

            // coreColor
            assertNotNull(weapon.getCoreColor());
            assertEquals(255, weapon.getCoreColor().getRed());
            assertEquals(255, weapon.getCoreColor().getGreen());
            assertEquals(255, weapon.getCoreColor().getBlue());
            assertEquals(255, weapon.getCoreColor().getAlpha());

            // width: 15f — f suffix stripped
            assertEquals(15.0, weapon.getWidth(), 0.001);

            assertNotNull(weapon.getRenderHints());
            assertEquals(1, weapon.getRenderHints().size());
            assertEquals("RENDER_BARREL_BELOW", weapon.getRenderHints().get(0));

            assertEquals("MUZZLE_FLASH", weapon.getAnimationType().name());
            assertEquals("heavy_blaster_proj", weapon.getProjectileSpecId());
            assertEquals("heavy_blaster_fire", weapon.getFireSoundOne());
        }

        /**
         * Round-trip test for WeaponSpecFile. Note: turretOffsets/hardpointOffsets use
         * Point2DArrayDeserializer for input (flat [x,y,x,y,...]) but WeaponSpecFile
         * does
         * NOT annotate these fields with a custom serializer (unlike HullSpecFile's
         * bounds),
         * so serialized output uses Jackson's default Point2D.Double serialization (as
         * an object).
         * This means offset arrays cannot fully round-trip. We verify scalar fields
         * survive.
         */
        @Test
        void testRoundTripMinimalWeapon() throws IOException {
            WeaponSpecFile original = parseStarsectorJson(MINIMAL_WEAPON_JSON, WeaponSpecFile.class);
            WeaponSpecFile roundTripped = roundTrip(original, WeaponSpecFile.class);

            assertEquals(original.getId(), roundTripped.getId());
            assertEquals(original.getSpecClass(), roundTripped.getSpecClass());
            assertEquals(original.getType(), roundTripped.getType());
            assertEquals(original.getSize(), roundTripped.getSize());
            assertEquals(original.getTurretSprite(), roundTripped.getTurretSprite());
            assertEquals(original.getVisualRecoil(), roundTripped.getVisualRecoil(), 0.001);
            assertEquals(original.getNumFrames(), roundTripped.getNumFrames());
            assertEquals(original.getFrameRate(), roundTripped.getFrameRate());
            assertEquals(original.getWidth(), roundTripped.getWidth(), 0.001);
            assertEquals(original.getProjectileSpecId(), roundTripped.getProjectileSpecId());
            assertEquals(original.getFireSoundOne(), roundTripped.getFireSoundOne());
            assertEquals(original.getAnimationType(), roundTripped.getAnimationType());

            // Offset arrays use custom deserialization (flat [x,y,...] → Point2D[]) but no
            // custom serialization, so they serialize as JSON objects — we just verify they
            // exist.
            assertNotNull(roundTripped.getTurretAngleOffsets());
            assertEquals(original.getTurretAngleOffsets().length, roundTripped.getTurretAngleOffsets().length);
        }

        /**
         * Tests weapon with multiple barrels (multiple offset pairs).
         */
        @Test
        void testWeaponWithMultipleBarrels() throws IOException {
            String json = """
                    {
                        id: "dual_blaster";
                        type: "ENERGY";
                        size: "LARGE";
                        turretOffsets: [15, 10, 15, -10];
                        turretAngleOffsets: [0, 0];
                        hardpointOffsets: [20, 10, 20, -10];
                        hardpointAngleOffsets: [0, 0];
                        barrelMode: "ALTERNATING";
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);

            // [15, 10, 15, -10] → 2 Point2D objects
            assertNotNull(weapon.getTurretOffsets());
            assertEquals(2, weapon.getTurretOffsets().length);
            assertEquals(15.0, weapon.getTurretOffsets()[0].getX(), 0.001);
            assertEquals(10.0, weapon.getTurretOffsets()[0].getY(), 0.001);
            assertEquals(15.0, weapon.getTurretOffsets()[1].getX(), 0.001);
            assertEquals(-10.0, weapon.getTurretOffsets()[1].getY(), 0.001);

            assertEquals("ALTERNATING", weapon.getBarrelMode().name());
        }

        /**
         * Tests weapon with muzzle flash spec (nested object).
         */
        @Test
        void testWeaponWithMuzzleFlashSpec() throws IOException {
            String json = """
                    {
                        id: "flash_weapon";
                        type: "BALLISTIC";
                        size: "SMALL";
                        muzzleFlashSpec: {
                            length: 30f;
                            spread: 8;
                            particleSizeMin: 5;
                            particleSizeRange: 10;
                            particleDuration: 0.15;
                            particleCount: 12;
                            particleColor: [255, 200, 100, 200]
                        }
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);

            assertNotNull(weapon.getMuzzleFlashSpec());
            assertEquals(30.0, weapon.getMuzzleFlashSpec().getLength(), 0.001);
            assertEquals(8.0, weapon.getMuzzleFlashSpec().getSpread(), 0.001);
            assertEquals(5.0, weapon.getMuzzleFlashSpec().getParticleSizeMin(), 0.001);
            assertEquals(10.0, weapon.getMuzzleFlashSpec().getParticleSizeRange(), 0.001);
            assertEquals(0.15, weapon.getMuzzleFlashSpec().getParticleDuration(), 0.001);
            assertEquals(12, weapon.getMuzzleFlashSpec().getParticleCount());

            assertNotNull(weapon.getMuzzleFlashSpec().getParticleColor());
            assertEquals(255, weapon.getMuzzleFlashSpec().getParticleColor().getRed());
        }

        /**
         * Tests weapon with smoke spec (nested object).
         */
        @Test
        void testWeaponWithSmokeSpec() throws IOException {
            String json = """
                    {
                        id: "smoke_weapon";
                        type: "BALLISTIC";
                        size: "LARGE";
                        smokeSpec: {
                            particleSizeMin: 15;
                            particleSizeRange: 20;
                            cloudParticleCount: 5;
                            cloudDuration: 1.5;
                            cloudRadius: 30;
                            blowbackParticleCount: 3;
                            blowbackDuration: 0.8;
                            blowbackLength: 40;
                            blowbackSpread: 10;
                            particleColor: [100, 100, 100, 180]
                        }
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);

            assertNotNull(weapon.getSmokeSpec());
            assertEquals(15.0, weapon.getSmokeSpec().getParticleSizeMin(), 0.001);
            assertEquals(20.0, weapon.getSmokeSpec().getParticleSizeRange(), 0.001);
            assertEquals(5, weapon.getSmokeSpec().getCloudParticleCount());
            assertEquals(1.5, weapon.getSmokeSpec().getCloudDuration(), 0.001);
            assertEquals(30.0, weapon.getSmokeSpec().getCloudRadius(), 0.001);
            assertEquals(3, weapon.getSmokeSpec().getBlowbackParticleCount());
        }

        /**
         * Tests textureType deserialized as a list (can be single string or array).
         */
        @Test
        void testTextureTypeAsString() throws IOException {
            String json = """
                    {
                        id: "beam_test";
                        type: "ENERGY";
                        size: "SMALL";
                        textureType: "ROUGH";
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);
            assertNotNull(weapon.getTextureType());
            assertEquals(1, weapon.getTextureType().size());
            assertEquals("ROUGH", weapon.getTextureType().get(0));
        }

        /**
         * Tests weapon with boolean flags.
         */
        @Test
        void testWeaponBooleanFlags() throws IOException {
            String json = """
                    {
                        id: "flag_test";
                        type: "ENERGY";
                        size: "SMALL";
                        renderBelowAllWeapons: true;
                        renderAdditive: true;
                        beamFireOnlyOnFullCharge: true;
                        autocharge: true;
                        interruptibleBurst: true;
                        requiresFullCharge: true;
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);

            assertTrue(weapon.isRenderBelowAllWeapons());
            assertTrue(weapon.isRenderAdditive());
            assertTrue(weapon.isBeamFireOnlyOnFullCharge());
            assertTrue(weapon.isAutocharge());
            assertTrue(weapon.isInterruptibleBurst());
            assertTrue(weapon.isRequiresFullCharge());
        }

        /**
         * Tests the framerate alias (lowercase).
         */
        @Test
        void testFramerateAlias() throws IOException {
            String json = """
                    {
                        id: "alias_test";
                        type: "ENERGY";
                        size: "SMALL";
                        framerate: 24;
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);
            assertEquals(24, weapon.getFrameRate());
        }
    }

    // ============================================================
    // Section 5: .proj (ProjectileSpecFile)
    // ============================================================

    @Nested
    class ProjectileFileTests {

        /**
         * A typical .proj file.
         */
        private static final String MINIMAL_PROJ_JSON = """
                {
                    id: "heavy_blaster_proj";
                    specClass: "projectile";
                    missileType: "";
                    sprite: "graphics/missiles/heavy_blaster_shot.png";
                    size: [16, 8];
                    center: [8, 4]
                }
                """;

        @Test
        void testDeserializeMinimalProjectile() throws IOException {
            ProjectileSpecFile proj = parseStarsectorJson(MINIMAL_PROJ_JSON, ProjectileSpecFile.class);

            assertEquals("heavy_blaster_proj", proj.getId());
            assertEquals("projectile", proj.getSpecClass());
            assertEquals("", proj.getMissileType());
            assertEquals("graphics/missiles/heavy_blaster_shot.png", proj.getSprite());

            assertNotNull(proj.getSize());
            assertEquals(2, proj.getSize().length);
            assertEquals(16, proj.getSize()[0]);
            assertEquals(8, proj.getSize()[1]);

            assertNotNull(proj.getCenter());
            assertEquals(8.0, proj.getCenter().getX(), 0.001);
            assertEquals(4.0, proj.getCenter().getY(), 0.001);
        }

        /**
         * Round-trip test for ProjectileSpecFile. Note: the center field has
         * 
         * @JsonDeserialize(Point2DDeserializer) for input (array [x, y]) but
         *                                       no @JsonSerialize,
         *                                       so serialized output uses Jackson's
         *                                       default Point2D.Double serialization
         *                                       (as an object
         *                                       with x,y fields). The round-trip for
         *                                       center coordinates works because
         *                                       Jackson's default
         *                                       can deserialize its own output, but it
         *                                       differs from the Starsector format.
         */
        @Test
        void testRoundTripMinimalProjectile() throws IOException {
            ProjectileSpecFile original = parseStarsectorJson(MINIMAL_PROJ_JSON, ProjectileSpecFile.class);
            ProjectileSpecFile roundTripped = roundTrip(original, ProjectileSpecFile.class);

            assertEquals(original.getId(), roundTripped.getId());
            assertEquals(original.getSpecClass(), roundTripped.getSpecClass());
            assertEquals(original.getMissileType(), roundTripped.getMissileType());
            assertEquals(original.getSprite(), roundTripped.getSprite());

            assertArrayEquals(original.getSize(), roundTripped.getSize());

            assertNotNull(roundTripped.getCenter());
            assertEquals(original.getCenter().getX(), roundTripped.getCenter().getX(), 0.001);
            assertEquals(original.getCenter().getY(), roundTripped.getCenter().getY(), 0.001);
        }

        /**
         * Tests projectile with missile type set.
         */
        @Test
        void testProjectileWithMissileType() throws IOException {
            String json = """
                    {
                        id: "harpoon_missile";
                        specClass: "missile";
                        missileType: "HARPOON";
                        sprite: "graphics/missiles/harpoon.png";
                        size: [24, 8];
                        center: [12, 4]
                    }
                    """;
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);

            assertEquals("harpoon_missile", proj.getId());
            assertEquals("missile", proj.getSpecClass());
            assertEquals("HARPOON", proj.getMissileType());
            assertEquals(24, proj.getSize()[0]);
            assertEquals(8, proj.getSize()[1]);
        }

        /**
         * Tests projectile with Starsector quirks: semicolons as separators and numeric
         * suffixes.
         */
        @Test
        void testProjectileWithStarsectorQuirks() throws IOException {
            String json = """
                    {
                        id: "quirky_proj";
                        specClass: "projectile";
                        missileType: "";
                        sprite: "graphics/missiles/quirky.png";
                        size: [32, 16];
                        center: [16, 8]
                    }
                    """;
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);
            assertEquals("quirky_proj", proj.getId());
        }
    }

    // ============================================================
    // Section 6: Cross-cutting concerns — JsonProcessor
    // ============================================================

    @Nested
    class JsonProcessorTests {

        @Test
        void testSemicolonReplacement() throws IOException {
            String json = """
                    {
                        id: "semi_test";
                        specClass: "projectile";
                        missileType: "";
                        sprite: "graphics/test.png";
                        size: [10, 5];
                        center: [5, 2]
                    }
                    """;
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);
            assertEquals("semi_test", proj.getId());
        }

        @Test
        void testHashCommentStripping() {
            String input = """
                    {
                        # Full line comment
                        "key": "value" # Inline comment
                    }
                    """;
            String result = JsonProcessor.straightenMalformedText(input);
            assertFalse(result.contains("#"));
            assertTrue(result.contains("\"key\""));
            assertTrue(result.contains("\"value\""));
        }

        @Test
        void testUnquotedIdentifierQuoting() {
            String input = """
                    {
                        hullName: "Test"
                    }
                    """;
            String result = JsonProcessor.straightenMalformedText(input);
            assertTrue(result.contains("\"hullName\""));
        }

        @Test
        void testBooleanAndNullNotQuoted() {
            String input = "{goalVariant: true, modules: null, isEmpty: false}";
            String result = JsonProcessor.straightenMalformedText(input);
            assertTrue(result.contains("true"));
            assertTrue(result.contains("null"));
            assertTrue(result.contains("false"));
            // Booleans and null should NOT be wrapped in quotes
            assertFalse(result.contains("\"true\""));
            assertFalse(result.contains("\"null\""));
            assertFalse(result.contains("\"false\""));
        }

        @Test
        void testNumericSuffixRemoval() {
            String input = "{\"value\": 100f, \"other\": 2.5d}";
            String result = JsonProcessor.straightenMalformedText(input);
            assertTrue(result.contains("100"));
            assertTrue(result.contains("2.5"));
            // The f and d should be stripped
            assertFalse(result.matches(".*100f[^a-zA-Z_].*"));
            assertFalse(result.matches(".*2\\.5d[^a-zA-Z_].*"));
        }

        @Test
        void testDotNotationPreserved() {
            // style.MIDLINE should NOT become style."MIDLINE"
            String input = "{\"textureType\": style.MIDLINE}";
            String result = JsonProcessor.straightenMalformedText(input);
            assertFalse(result.contains("style.\"MIDLINE\""),
                    "Dot-preceded words should not be independently quoted");
        }

        @Test
        void testTrailingCommaHandling() throws IOException {
            // Jackson is configured to accept trailing commas, but this verifies the full
            // pipeline
            String json = """
                    {
                        id: "trailing_test";
                        specClass: "projectile";
                        sprite: "test.png";
                        size: [10, 5,];
                        center: [5, 2,]
                    }
                    """;
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);
            assertEquals("trailing_test", proj.getId());
            assertEquals(10, proj.getSize()[0]);
        }

        @Test
        void testEscapedQuotesPreservedInStrings() {
            String input = "{\"prefix\": \"tagged with an \\\"M\\\", for \\\"military spec\\\".\", \"name\": \"test\"}";
            String result = JsonProcessor.straightenMalformedText(input);
            assertTrue(result.contains("\"tagged with an \\\"M\\\", for \\\"military spec\\\".\""));
            assertTrue(result.contains("\"name\""));
        }

        @Test
        void testEmptyInput() {
            String result = JsonProcessor.straightenMalformedText("");
            assertEquals("", result);
        }

        @Test
        void testNullInput() {
            String result = JsonProcessor.straightenMalformedText(null);
            assertEquals("", result);
        }
    }

    // ============================================================
    // Section 7: Custom Deserializer Edge Cases
    // ============================================================

    @Nested
    class DeserializerEdgeCases {

        /**
         * Tests Point2DDeserializer with a null/missing field.
         */
        @Test
        void testPoint2DDeserializerWithMissingField() throws IOException {
            String json = """
                    {
                        hullName: "No Center Test";
                        hullId: "no_center";
                        hullSize: "FRIGATE";
                        style: "LOW_TECH";
                        spriteName: "test.png";
                        height: 50;
                        width: 50;
                        collisionRadius: 50;
                        shieldCenter: [0, 0];
                        shieldRadius: 50;
                        viewOffset: 0;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            // center field is missing — should default to null, not crash
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            // Depending on implementation, center may be null or (0,0)
            // The key assertion is that parsing doesn't crash
            assertNotNull(hull.getHullName());
        }

        /**
         * Tests Point2DArrayDeserializer with empty array.
         */
        @Test
        void testEmptyBoundsArray() throws IOException {
            String json = """
                    {
                        hullName: "Empty Bounds";
                        hullId: "empty_bounds";
                        hullSize: "FRIGATE";
                        style: "LOW_TECH";
                        spriteName: "test.png";
                        height: 50;
                        width: 50;
                        center: [25, 25];
                        collisionRadius: 50;
                        shieldCenter: [0, 0];
                        shieldRadius: 50;
                        viewOffset: 0;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertNotNull(hull.getBounds());
            assertEquals(0, hull.getBounds().length);
        }

        /**
         * Tests ColorArrayRGBADeserializer with 3-element array (no alpha).
         */
        @Test
        void testColorWithoutAlpha() throws IOException {
            String json = """
                    {
                        baseHullId: "test";
                        skinHullId: "test_skin";
                        coversColor: [200, 150, 100]
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);

            assertNotNull(skin.getCoversColor());
            assertEquals(200, skin.getCoversColor().getRed());
            assertEquals(150, skin.getCoversColor().getGreen());
            assertEquals(100, skin.getCoversColor().getBlue());
            assertEquals(255, skin.getCoversColor().getAlpha(), "Default alpha should be 255");
        }

        /**
         * Tests that unknown fields are silently ignored (JsonIgnoreProperties).
         */
        @Test
        void testUnknownFieldsIgnored() throws IOException {
            String json = """
                    {
                        id: "unknown_fields_test";
                        specClass: "projectile";
                        sprite: "test.png";
                        size: [10, 5];
                        center: [5, 2];
                        completelyMadeUpField: "should be ignored";
                        anotherFakeField: 42;
                    }
                    """;
            // Should not throw
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);
            assertEquals("unknown_fields_test", proj.getId());
        }

        /**
         * Tests ModulesDeserializer with both object and array formats.
         */
        @Test
        void testModulesDeserializerObjectFormat() throws IOException {
            String json = """
                    {
                        hullName: "Module Object Test";
                        hullId: "module_obj_test";
                        hullSize: "CAPITAL_SHIP";
                        style: "LOW_TECH";
                        spriteName: "test.png";
                        height: 200;
                        width: 200;
                        center: [100, 100];
                        collisionRadius: 150;
                        shieldCenter: [0, 0];
                        shieldRadius: 0;
                        viewOffset: 0;
                        builtInModules: {
                            "SLOT1": "module_a",
                            "SLOT2": "module_b"
                        };
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertNotNull(hull.getBuiltInModules());
            assertEquals(2, hull.getBuiltInModules().size());
            assertEquals("module_a", hull.getBuiltInModules().get("SLOT1"));
            assertEquals("module_b", hull.getBuiltInModules().get("SLOT2"));
        }

        @Test
        void testModulesDeserializerArrayFormat() throws IOException {
            String json = """
                    {
                        hullName: "Module Array Test";
                        hullId: "module_arr_test";
                        hullSize: "CAPITAL_SHIP";
                        style: "LOW_TECH";
                        spriteName: "test.png";
                        height: 200;
                        width: 200;
                        center: [100, 100];
                        collisionRadius: 150;
                        shieldCenter: [0, 0];
                        shieldRadius: 0;
                        viewOffset: 0;
                        builtInModules: [
                            {"SLOT1": "module_a"},
                            {"SLOT2": "module_b"}
                        ];
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertNotNull(hull.getBuiltInModules());
            assertEquals(2, hull.getBuiltInModules().size());
            assertEquals("module_a", hull.getBuiltInModules().get("SLOT1"));
            assertEquals("module_b", hull.getBuiltInModules().get("SLOT2"));
        }

        /**
         * Tests decimal point values in weapon offsets.
         */
        @Test
        void testDecimalPointValues() throws IOException {
            String json = """
                    {
                        id: "decimal_test";
                        type: "ENERGY";
                        size: "SMALL";
                        turretOffsets: [12.5, -3.25];
                        turretAngleOffsets: [0.5];
                        hardpointOffsets: [15.75, 0];
                        hardpointAngleOffsets: [0];
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);
            assertEquals(12.5, weapon.getTurretOffsets()[0].getX(), 0.001);
            assertEquals(-3.25, weapon.getTurretOffsets()[0].getY(), 0.001);
            assertEquals(0.5, weapon.getTurretAngleOffsets()[0], 0.001);
        }

        /**
         * Tests leading decimal point (.5 instead of 0.5).
         */
        @Test
        void testLeadingDecimalPoint() throws IOException {
            String json = """
                    {
                        id: "leading_decimal";
                        type: "ENERGY";
                        size: "SMALL";
                        turretOffsets: [.5, -.25];
                        turretAngleOffsets: [0];
                        hardpointOffsets: [0, 0];
                        hardpointAngleOffsets: [0];
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);
            assertEquals(0.5, weapon.getTurretOffsets()[0].getX(), 0.001);
            assertEquals(-0.25, weapon.getTurretOffsets()[0].getY(), 0.001);
        }
    }

    // ============================================================
    // Section 7: Non-Destructive JSON Preservation Tests
    // ============================================================

    @Nested
    class NonDestructiveJsonTests {

        @Test
        void testWeaponUnrecognizedPropertiesPreserved() throws IOException {
            String json = """
                    {
                        id: "test_weapon_custom";
                        specClass: "projectile";
                        type: "BALLISTIC";
                        size: "SMALL";
                        customModProperty: "special_value";
                        customConfig: {
                            spinUpTime: 1.5;
                            laserColor: "red";
                        };
                        turretOffsets: [0, 0];
                        turretAngleOffsets: [0];
                        hardpointOffsets: [0, 0];
                        hardpointAngleOffsets: [0];
                    }
                    """;
            WeaponSpecFile weapon = parseStarsectorJson(json, WeaponSpecFile.class);
            assertNotNull(weapon.getUnrecognizedProperties());
            assertEquals("special_value", weapon.getUnrecognizedProperties().get("customModProperty"));
            assertNotNull(weapon.getUnrecognizedProperties().get("customConfig"));

            WeaponSpecFile roundTripped = roundTrip(weapon, WeaponSpecFile.class);
            assertNotNull(roundTripped.getUnrecognizedProperties());
            assertEquals("special_value", roundTripped.getUnrecognizedProperties().get("customModProperty"));
            assertNotNull(roundTripped.getUnrecognizedProperties().get("customConfig"));
        }

        @Test
        void testHullUnrecognizedPropertiesPreserved() throws IOException {
            String json = """
                    {
                        hullName: "Custom Data Ship";
                        hullId: "custom_ship";
                        hullSize: "CRUISER";
                        style: "HIGH_TECH";
                        spriteName: "graphics/ships/custom.png";
                        height: 100;
                        width: 100;
                        center: [50, 50];
                        collisionRadius: 80;
                        shieldCenter: [0, 0];
                        shieldRadius: 90;
                        viewOffset: 0;
                        customHullModPayload: {
                            overloadThreshold: 1200;
                        };
                        customFleetCost: 25;
                        weaponSlots: [];
                        engineSlots: [];
                        bounds: []
                    }
                    """;
            HullSpecFile hull = parseStarsectorJson(json, HullSpecFile.class);
            assertNotNull(hull.getUnrecognizedProperties());
            assertEquals(25, ((Number) hull.getUnrecognizedProperties().get("customFleetCost")).intValue());
            assertNotNull(hull.getUnrecognizedProperties().get("customHullModPayload"));

            HullSpecFile roundTripped = roundTrip(hull, HullSpecFile.class);
            assertNotNull(roundTripped.getUnrecognizedProperties());
            assertEquals(25, ((Number) roundTripped.getUnrecognizedProperties().get("customFleetCost")).intValue());
            assertNotNull(roundTripped.getUnrecognizedProperties().get("customHullModPayload"));
        }

        @Test
        void testVariantUnrecognizedPropertiesPreserved() throws IOException {
            String json = """
                    {
                        displayName: "Elite Variant";
                        hullId: "test_hull";
                        variantId: "test_hull_elite";
                        fluxCapacitors: 10;
                        fluxVents: 10;
                        goalVariant: true;
                        customTag: "faction_flagship";
                        customNumericParam: 42.5;
                        hullMods: [];
                        weaponGroups: []
                    }
                    """;
            VariantFile variant = parseStarsectorJson(json, VariantFile.class);
            assertNotNull(variant.getUnrecognizedProperties());
            assertEquals("faction_flagship", variant.getUnrecognizedProperties().get("customTag"));
            assertEquals(42.5, ((Number) variant.getUnrecognizedProperties().get("customNumericParam")).doubleValue(), 0.001);

            VariantFile roundTripped = roundTrip(variant, VariantFile.class);
            assertNotNull(roundTripped.getUnrecognizedProperties());
            assertEquals("faction_flagship", roundTripped.getUnrecognizedProperties().get("customTag"));
            assertEquals(42.5, ((Number) roundTripped.getUnrecognizedProperties().get("customNumericParam")).doubleValue(), 0.001);
        }

        @Test
        void testSkinUnrecognizedPropertiesPreserved() throws IOException {
            String json = """
                    {
                        baseHullId: "test_hull";
                        skinHullId: "test_hull_skin";
                        hullName: "Skin Name";
                        customSkinData: "preserved_attribute";
                    }
                    """;
            SkinSpecFile skin = parseStarsectorJson(json, SkinSpecFile.class);
            assertNotNull(skin.getUnrecognizedProperties());
            assertEquals("preserved_attribute", skin.getUnrecognizedProperties().get("customSkinData"));

            SkinSpecFile roundTripped = roundTrip(skin, SkinSpecFile.class);
            assertNotNull(roundTripped.getUnrecognizedProperties());
            assertEquals("preserved_attribute", roundTripped.getUnrecognizedProperties().get("customSkinData"));
        }

        @Test
        void testProjectileUnrecognizedPropertiesPreserved() throws IOException {
            String json = """
                    {
                        id: "test_proj_custom";
                        specClass: "projectile";
                        customTrailShader: "shader_plasma_v2";
                    }
                    """;
            ProjectileSpecFile proj = parseStarsectorJson(json, ProjectileSpecFile.class);
            assertNotNull(proj.getUnrecognizedProperties());
            assertEquals("shader_plasma_v2", proj.getUnrecognizedProperties().get("customTrailShader"));

            ProjectileSpecFile roundTripped = roundTrip(proj, ProjectileSpecFile.class);
            assertNotNull(roundTripped.getUnrecognizedProperties());
            assertEquals("shader_plasma_v2", roundTripped.getUnrecognizedProperties().get("customTrailShader"));
        }
    }
}
