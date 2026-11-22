package shipeditor.parsing.saving;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shipeditor.components.viewer.entities.engine.EngineDataOverride;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.entities.weapon.WeaponSlotOverride;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.representation.weapon.WeaponSlot;
import shipeditor.undo.edits.points.engines.EngineEdits;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SaveHullEngineSerializationTest {

    private Settings originalSettings;
    private Map<String, EngineStyle> originalEngineStyles;

    @BeforeEach
    void setUp() throws Exception {
        originalSettings = SettingsManager.getSettings();
        GameDataRepository repo = SettingsManager.getGameData();
        originalEngineStyles = repo.getAllEngineStyles();
        Settings settings = SettingsManager.getMapperForSettingsFile()
                .readValue("{\"coreFolderPath\":\"/core\"}", Settings.class);
        SettingsManager.setSettings(settings);

        EngineStyle lowTech = new EngineStyle();
        lowTech.setEngineStyleID("LOW_TECH");
        lowTech.setEngineColor(new Color(255, 125, 25));
        lowTech.setContainingPackage(Path.of("/core"));

        EngineStyle customModStyle = new EngineStyle();
        customModStyle.setEngineStyleID("LOOTERS_ENGINES");
        customModStyle.setEngineColor(new Color(120, 200, 255));
        customModStyle.setContainingPackage(Path.of("/mods/Artefact"));

        EngineStyle colorlessStyle = new EngineStyle();
        colorlessStyle.setEngineStyleID("COLORLESS_STYLE");
        colorlessStyle.setEngineColor(null);
        colorlessStyle.setContainingPackage(Path.of("/mods/TestMod"));

        Map<String, EngineStyle> styles = new HashMap<>();
        styles.put("LOW_TECH", lowTech);
        styles.put("LOOTERS_ENGINES", customModStyle);
        styles.put("COLORLESS_STYLE", colorlessStyle);
        repo.setAllEngineStyles(styles);
    }

    @AfterEach
    void tearDown() {
        SettingsManager.setSettings(originalSettings);
        SettingsManager.getGameData().setAllEngineStyles(originalEngineStyles);
    }

    @Test
    void testCustomEngineStyleFlagSurvivesLazyResolveAndSkinOverrideToggle() {
        EnginePoint engine = new EnginePoint(new Point2D.Double(10, 20), null);
        engine.setAngle(180);
        engine.setWidth(12);
        engine.setLength(40);
        engine.setStyleID("LOOTERS_ENGINES");
        engine.setStyleIsCustom(true);

        // Lazy resolution via getStyle() must NOT wipe styleIsCustom
        EngineStyle resolved = engine.getStyle();
        assertNotNull(resolved);
        assertEquals("LOOTERS_ENGINES", resolved.getEngineStyleID());
        assertTrue(engine.isStyleIsCustom(), "Lazy style lookup must preserve styleIsCustom");

        // Applying and clearing a skin override must NOT wipe styleIsCustom or mutate base fields
        EngineDataOverride override = EngineDataOverride.builder()
                .index(0)
                .angle(90.0)
                .width(20.0)
                .length(60.0)
                .styleID("LOW_TECH")
                .build();
        engine.setSkinOverride(override);
        assertEquals(90.0, engine.getAngle(), 0.001);
        assertEquals(180.0, engine.getBaseAngle(), 0.001);
        assertEquals(20.0, engine.getWidth(), 0.001);
        assertEquals(12.0, engine.getBaseWidth(), 0.001);
        assertEquals(60.0, engine.getLength(), 0.001);
        assertEquals(40.0, engine.getBaseLength(), 0.001);

        engine.setSkinOverride(null);
        assertTrue(engine.isStyleIsCustom(), "Clearing skin override must preserve styleIsCustom");
        assertEquals("LOOTERS_ENGINES", engine.getStyleID());
    }

    @Test
    void testEnginePointCopyPreservesCustomStyleAndBaseValues() {
        EnginePoint source = new EnginePoint(new Point2D.Double(0, 0), null);
        source.setAngle(180);
        source.setWidth(10);
        source.setLength(30);
        source.setContrailSize(16);
        source.setStyleID("LOOTERS_ENGINES");
        source.setStyleIsCustom(true);
        source.setSkinOverride(EngineDataOverride.builder()
                .angle(45.0)
                .width(99.0)
                .length(99.0)
                .build());

        EnginePoint copy = new EnginePoint(new Point2D.Double(5, 5), null, source);
        assertEquals(180.0, copy.getBaseAngle(), 0.001);
        assertEquals(10.0, copy.getBaseWidth(), 0.001);
        assertEquals(30.0, copy.getBaseLength(), 0.001);
        assertEquals(16.0, copy.getContrailSize(), 0.001);
        assertEquals("LOOTERS_ENGINES", copy.getStyleID());
        assertTrue(copy.isStyleIsCustom());
    }

    @Test
    void testColorlessEngineStyleDoesNotThrow() {
        EnginePoint engine = new EnginePoint(new Point2D.Double(0, 0), null);
        EngineStyle colorless = SettingsManager.getGameData().getAllEngineStyles().get("COLORLESS_STYLE");
        assertDoesNotThrow(() -> engine.setStyle(colorless));
        assertTrue(engine.isStyleIsCustom(), "Mod engine style should be marked custom");
    }

    @Test
    void testEngineStyleUndoRestoresCustomAndInlineSpecState() {
        EnginePoint engine = new EnginePoint(new Point2D.Double(0, 0), null);
        EngineStyle inlineSpec = new EngineStyle();
        inlineSpec.setType("SMOKE");
        engine.setCustomStyleSpec(inlineSpec);

        EngineStyle lowTech = SettingsManager.getGameData().getAllEngineStyles().get("LOW_TECH");
        EngineEdits.EngineStyleSet edit = new EngineEdits.EngineStyleSet(engine, engine.getBaseStyle(), lowTech);
        engine.setStyle(lowTech);

        assertNull(engine.getCustomStyleSpec(), "Changing style should clear inline customStyleSpec");
        assertEquals("LOW_TECH", engine.getStyleID());
        assertFalse(engine.isStyleIsCustom());

        edit.undo();
        assertSame(inlineSpec, engine.getCustomStyleSpec(), "Undo must restore inline customStyleSpec");
        assertNull(engine.getStyleID());

        edit.redo();
        assertNull(engine.getCustomStyleSpec());
        assertEquals("LOW_TECH", engine.getStyleID());
    }

    @Test
    void testCreateSerializableUsesBaseWeaponSlotAngleAndArc() {
        WeaponSlotPoint slot = new WeaponSlotPoint(new Point2D.Double(0, 0), null);
        slot.setId("WS 001");
        slot.setWeaponSize(WeaponSize.MEDIUM);
        slot.setWeaponType(WeaponType.BALLISTIC);
        slot.setWeaponMount(WeaponMount.TURRET);
        slot.setAngle(15.0);
        slot.setArc(120.0);

        WeaponSlotOverride override = WeaponSlotOverride.builder()
                .slotID("WS 001")
                .weaponSize(WeaponSize.LARGE)
                .weaponType(WeaponType.ENERGY)
                .weaponMount(WeaponMount.HARDPOINT)
                .angle(90.0)
                .arc(10.0)
                .build();
        slot.setSkinOverride(override);

        WeaponSlot serialized = SaveHullAction.createSerializable(slot);
        assertEquals("WS 001", serialized.getId());
        assertEquals("MEDIUM", serialized.getSize());
        assertEquals("BALLISTIC", serialized.getType());
        assertEquals("TURRET", serialized.getMount());
        assertEquals(15.0, serialized.getAngle(), 0.001, "Base angle must be serialized, not skin override");
        assertEquals(120.0, serialized.getArc(), 0.001, "Base arc must be serialized, not skin override");
    }
}
