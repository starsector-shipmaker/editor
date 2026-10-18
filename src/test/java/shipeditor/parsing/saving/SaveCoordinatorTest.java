package shipeditor.parsing.saving;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.BusEvent;
import shipeditor.communication.events.files.FileEvents.HullSaveQueued;
import shipeditor.communication.events.files.FileEvents.VariantSaveQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.ShipInstrumentsPane;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.utility.overseers.StaticController;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the hull/variant decision in {@link SaveCoordinator#saveLayer}. No window or GL context is created:
 * the layer manager is injected into a {@link PrimaryViewer} instance that is allocated without running its
 * (AWT canvas) constructor, and events are captured from the {@link EventBus}.
 */
class SaveCoordinatorTest {

    /**
     * Painter that is already marked initialized, so that the layer behaves like a loaded ship.
     */
    private static final class InitializedShipPainter extends ShipPainter {
        InitializedShipPainter(ShipLayer layer) {
            super(layer);
            setUninitialized(false);
        }
    }

    private final List<BusEvent> captured = Collections.synchronizedList(new ArrayList<>());

    private PrimaryViewer originalViewer;
    private EditorInstrument originalMode;
    private LayerManager layerManager;

    @BeforeEach
    void setUp() {
        EventBus.reset();
        originalViewer = StaticController.getViewer();
        originalMode = ShipInstrumentsPane.getCurrentMode();
        layerManager = new LayerManager();
        EventBus.subscribe(this, event -> {
            if (event instanceof HullSaveQueued || event instanceof VariantSaveQueued) {
                captured.add(event);
            }
        });
    }

    @AfterEach
    void tearDown() {
        StaticController.setViewer(originalViewer);
        ShipInstrumentsPane.setCurrentMode(originalMode);
        EventBus.unsubscribeByParent(this);
        EventBus.reset();
    }

    private void installViewerWithLayerManager() {
        PrimaryViewer viewer;
        try {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            viewer = (PrimaryViewer) unsafe.getClass().getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, PrimaryViewer.class);
            Field managerField = PrimaryViewer.class.getDeclaredField("layerManager");
            managerField.setAccessible(true);
            managerField.set(viewer, layerManager);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            assumeTrue(false, "Cannot create a headless PrimaryViewer stand-in: " + e);
            return;
        }
        StaticController.setViewer(viewer);
        assertSame(layerManager, StaticController.getLayerManager());
    }

    private ShipLayer createShipLayer(ShipVariant variant) {
        ShipLayer layer = new ShipLayer();
        ShipPainter painter = new InitializedShipPainter(layer);
        layer.setPainter(painter);
        if (variant != null) {
            painter.selectVariant(variant);
        }
        return layer;
    }

    private static ShipVariant createVariant(boolean loadedFromFile) {
        ShipVariant variant = new ShipVariant(false);
        variant.setVariantId("test_variant");
        variant.setShipHullId("test_hull");
        variant.setLoadedFromFile(loadedFromFile);
        return variant;
    }

    /**
     * Runs on the EDT so that {@link EventBus#publish} dispatches synchronously.
     */
    private List<BusEvent> save(ShipLayer layer) throws InterruptedException, InvocationTargetException {
        captured.clear();
        SwingUtilities.invokeAndWait(() -> SaveCoordinator.saveLayer(layer));
        return new ArrayList<>(captured);
    }

    private static void assertOnlyHullSave(List<BusEvent> events, ShipLayer layer) {
        assertEquals(1, events.size(), "Exactly one save event expected but got " + events);
        assertTrue(events.get(0) instanceof HullSaveQueued, "Expected HullSaveQueued but got " + events.get(0));
        assertSame(layer, ((HullSaveQueued) events.get(0)).shipLayer());
    }

    private static void assertOnlyVariantSave(List<BusEvent> events, ShipVariant variant) {
        assertEquals(1, events.size(), "Exactly one save event expected but got " + events);
        assertTrue(events.get(0) instanceof VariantSaveQueued, "Expected VariantSaveQueued but got " + events.get(0));
        assertSame(variant, ((VariantSaveQueued) events.get(0)).variant());
    }

    @Test
    void hullOnlyDirtyQueuesOnlyHullSave() throws Exception {
        installViewerWithLayerManager();
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.BOUNDS);
        layerManager.markUnsaved(layer, "hull");

        assertOnlyHullSave(save(layer), layer);
    }

    @Test
    void variantOnlyDirtyQueuesOnlyVariantSave() throws Exception {
        installViewerWithLayerManager();
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.BOUNDS);
        layerManager.markUnsaved(layer, "variant");

        assertOnlyVariantSave(save(layer), variant);
    }

    @Test
    void bothDirtyOutsideVariantModeQueuesOnlyHullSave() throws Exception {
        installViewerWithLayerManager();
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.BOUNDS);
        layerManager.markUnsaved(layer, "hull");
        layerManager.markUnsaved(layer, "variant");

        assertOnlyHullSave(save(layer), layer);
    }

    @Test
    void variantEditorModeQueuesVariantEvenWhenHullIsDirty() throws Exception {
        installViewerWithLayerManager();
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.VARIANT_DATA);
        layerManager.markUnsaved(layer, "hull");

        assertOnlyVariantSave(save(layer), variant);
    }

    @Test
    void allVariantEditorModesQueueVariantSave() throws Exception {
        installViewerWithLayerManager();
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        for (EditorInstrument mode : List.of(EditorInstrument.VARIANT_DATA, EditorInstrument.VARIANT_WEAPONS,
                EditorInstrument.VARIANT_MODULES)) {
            ShipInstrumentsPane.setCurrentMode(mode);
            assertOnlyVariantSave(save(layer), variant);
        }
    }

    @Test
    void variantModeWithoutRealVariantFallsBackToHullSave() throws Exception {
        installViewerWithLayerManager();
        ShipLayer layer = createShipLayer(null);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.VARIANT_DATA);

        assertOnlyHullSave(save(layer), layer);
    }

    @Test
    void newUnsavedVariantQueuesVariantSaveWithoutLayerManager() throws Exception {
        StaticController.setViewer(null);
        ShipVariant variant = createVariant(false);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.BOUNDS);

        assertOnlyVariantSave(save(layer), variant);
    }

    @Test
    void cleanLayerWithoutLayerManagerQueuesHullSave() throws Exception {
        StaticController.setViewer(null);
        ShipVariant variant = createVariant(true);
        ShipLayer layer = createShipLayer(variant);
        ShipInstrumentsPane.setCurrentMode(EditorInstrument.BOUNDS);

        assertOnlyHullSave(save(layer), layer);
    }
}
