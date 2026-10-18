package shipeditor.utility.overseers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shipeditor.communication.EventBus;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.representation.ship.VariantFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link StaticController#getOrCreateEditableVariant(ShipLayer)} and its ID helpers.
 */
class StaticControllerEditableVariantTest {

    private static final String HULL_ID = "test_hull";

    private static final class InitializedShipPainter extends ShipPainter {
        InitializedShipPainter(ShipLayer layer) {
            super(layer);
            setUninitialized(false);
        }
    }

    private PrimaryViewer originalViewer;

    @BeforeEach
    void setUp() {
        EventBus.reset();
        originalViewer = StaticController.getViewer();
        // No layer manager: the helper must not depend on one.
        StaticController.setViewer(null);
    }

    @AfterEach
    void tearDown() {
        StaticController.setViewer(originalViewer);
        EventBus.reset();
    }

    private static ShipLayer createLayer(String baseHullId) {
        ShipLayer layer = new ShipLayer();
        ShipPainter painter = new InitializedShipPainter(layer);
        painter.setBaseHullId(baseHullId);
        layer.setPainter(painter);
        return layer;
    }

    @Test
    void nextCustomVariantIdStartsWithPlainCustomSuffix() {
        assertEquals("wolf_custom", StaticController.nextCustomVariantId("wolf", new HashMap<>()));
        assertEquals("wolf_custom", StaticController.nextCustomVariantId("wolf", null));
    }

    @Test
    void nextCustomVariantIdSkipsTakenIds() {
        Map<String, ShipVariant> taken = new HashMap<>();
        taken.put("wolf_custom", new ShipVariant(false));
        assertEquals("wolf_custom_01", StaticController.nextCustomVariantId("wolf", taken));

        taken.put("wolf_custom_01", new ShipVariant(false));
        taken.put("wolf_custom_02", new ShipVariant(false));
        assertEquals("wolf_custom_03", StaticController.nextCustomVariantId("wolf", taken));
    }

    @Test
    void nextCustomVariantIdIgnoresOtherHulls() {
        Map<String, ShipVariant> taken = new HashMap<>();
        taken.put("hound_custom", new ShipVariant(false));
        assertEquals("wolf_custom", StaticController.nextCustomVariantId("wolf", taken));
    }

    @Test
    void resolveEditableHullIdFallsBackToHull() {
        assertEquals("hull", StaticController.resolveEditableHullId(null));
        assertEquals("hull", StaticController.resolveEditableHullId(createLayer(null).getPainter()));
        assertEquals("hull", StaticController.resolveEditableHullId(createLayer("  ").getPainter()));
        assertEquals(HULL_ID, StaticController.resolveEditableHullId(createLayer(HULL_ID).getPainter()));
    }

    @Test
    void returnsNullForMissingOrUninitializedLayer() {
        assertNull(StaticController.getOrCreateEditableVariant(null));

        ShipLayer layer = new ShipLayer();
        assertNull(StaticController.getOrCreateEditableVariant(layer), "Layer without painter");

        layer.setPainter(new ShipPainter(layer));
        assertNull(StaticController.getOrCreateEditableVariant(layer), "Painter is still uninitialized");
    }

    @Test
    void createsRegistersAndSelectsCustomVariant() {
        ShipLayer layer = createLayer(HULL_ID);
        ShipPainter painter = layer.getPainter();

        ShipVariant created = StaticController.getOrCreateEditableVariant(layer);

        assertEquals(HULL_ID + "_custom", created.getVariantId());
        assertEquals(HULL_ID, created.getShipHullId());
        assertFalse(created.isEmpty());
        assertFalse(created.isLoadedFromFile());
        assertSame(created, painter.getActiveVariant());
        assertSame(created, layer.getLoadedVariants().get(HULL_ID + "_custom"));
    }

    @Test
    void returnsExistingNonEmptyActiveVariantWithoutCreatingAnother() {
        ShipLayer layer = createLayer(HULL_ID);

        ShipVariant first = StaticController.getOrCreateEditableVariant(layer);
        ShipVariant second = StaticController.getOrCreateEditableVariant(layer);

        assertSame(first, second);
        assertEquals(1, layer.getLoadedVariants().size());
    }

    @Test
    void generatedIdsAreUniqueWhenVariantIsRepeatedlyReplacedByEmpty() {
        ShipLayer layer = createLayer(HULL_ID);
        ShipPainter painter = layer.getPainter();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            ShipVariant created = StaticController.getOrCreateEditableVariant(layer);
            assertTrue(ids.add(created.getVariantId()), "Duplicate ID generated: " + created.getVariantId());
            painter.selectVariant(VariantFile.empty());
        }

        assertEquals(Set.of(HULL_ID + "_custom", HULL_ID + "_custom_01", HULL_ID + "_custom_02",
                HULL_ID + "_custom_03", HULL_ID + "_custom_04"), ids);
        assertEquals(5, layer.getLoadedVariants().size());
    }

    @Test
    void doesNotReuseIdOfAlreadyLoadedVariant() {
        ShipLayer layer = createLayer(HULL_ID);
        ShipVariant preloaded = new ShipVariant(false);
        preloaded.setVariantId(HULL_ID + "_custom");
        layer.getLoadedVariants().put(HULL_ID + "_custom", preloaded);

        ShipVariant created = StaticController.getOrCreateEditableVariant(layer);

        assertNotSame(preloaded, created);
        assertEquals(HULL_ID + "_custom_01", created.getVariantId());
        assertSame(preloaded, layer.getLoadedVariants().get(HULL_ID + "_custom"));
    }
}
