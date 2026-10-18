package shipeditor.utility.overseers;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerCursorMoved;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.communication.events.viewer.control.ControlEvents.CoordsModeChanged;
import shipeditor.components.ComponentEnums.CoordsDisplayMode;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.ShipInstrumentsPane;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.parsing.FileUtilities;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ToastManager;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

/** * Convenience class for static access to active layer and whatever other global features need to be accessed.*/
@Log4j2
@SuppressWarnings({"OverlyCoupledClass", "ClassWithTooManyMethods"})
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class StaticController {

    @Getter @Setter
    private static PrimaryViewer viewer;

    @Getter @Setter
    private static ViewerLayer activeLayer;

    @Getter @Setter
    private static double rotationRadians;

    @Getter @Setter
    private static double rotationDegrees;

    @Getter @Setter
    private static double zoomLevel = 1;

    @Getter
    private static Point2D rawCursor = new Point2D.Double();

    @Getter
    private static Point2D adjustedCursor = new Point2D.Double();

    @Getter
    private static Point2D correctedCursor = new Point2D.Double();

    private static final EventScheduler SCHEDULER = new EventScheduler();

    public static EventScheduler getScheduler() {
        return SCHEDULER;
    }

    @Getter
    private static CoordsDisplayMode coordsMode = CoordsDisplayMode.SHIP_CENTER;

    private StaticController() {
    }

    public static LayerManager getLayerManager() {
        if (viewer != null) {
            return viewer.getLayerManager();
        }
        return null;
    }

    /**
     * Is used as a shortcut to refresh UI for respective ship editing panels. It's not an optimal practice!
     */
    public static void reselectCurrentLayer() {
        if (viewer == null) return;
        LayerManager manager = viewer.getLayerManager();
        var current = manager.getActiveLayer();
        manager.setActiveLayer(current);
    }

    public static Point2D getCorrectedWithoutRotate() {
        Point2D currentCursor = StaticController.getAdjustedCursor();
        AffineTransform screenToWorld = viewer.getScreenToWorld();
        return Utility.correctAdjustedCursor(currentCursor, screenToWorld);
    }

    public static AffineTransform getScreenToWorld() {
        if (activeLayer != null) {
            LayerPainter painter = activeLayer.getPainter();
            if (painter != null) {
                AffineTransform worldToScreen = viewer.getWorldToScreen();
                return painter.getWithRotationInverse(worldToScreen);
            }
        }
        return viewer.getScreenToWorld();
    }

    public static EditorInstrument getEditorMode() {
        return ShipInstrumentsPane.getCurrentMode();
    }

    public static Point2D getFinalWorldCursor() {
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D finalWorldCursor = screenToWorld.transform(StaticController.getRawCursor(), null);
        if (ControlPredicates.isCursorSnappingEnabled()) {
            Point2D cursor = StaticController.getAdjustedCursor();
            finalWorldCursor = Utility.correctAdjustedCursor(cursor, screenToWorld);
        }
        return finalWorldCursor;
    }

    public static AffineTransform getWorldToScreen() {
        return viewer.getWorldToScreen();
    }

    public static void updateViewerRotation(double radiansChange, double degrees) {
        rotationRadians += radiansChange;
        rotationDegrees = degrees;
    }

    public static boolean checkIsHovered(Shape shape) {
        return shape.contains(rawCursor);
    }

    public static void init() {
        // Intentionally permanent subscription: StaticController acts as a global singleton
        // that manages application-wide state for the entire lifecycle of the program.
        EventBus.subscribe(StaticController.class, event -> {
            if (event instanceof LayerWasSelected) {
                FileUtilities.updateActionStates(activeLayer);
            } else if (event instanceof ViewerCursorMoved checked) {
                rawCursor = checked.rawCursor();
                adjustedCursor = checked.adjusted();
                correctedCursor = checked.adjustedAndCorrected();
            } else if (event instanceof CoordsModeChanged checked) {
                coordsMode = checked.newMode();
                EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.BOUNDS));
                EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.COLLISION));
                EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.SHIELD));
            }
        });
    }

    public static java.util.Optional<ShipPainter> getActiveInitializedShipPainter() {
        if (activeLayer instanceof ShipLayer shipLayer) {
            ShipPainter shipPainter = shipLayer.getPainter();
            if (shipPainter != null && !shipPainter.isUninitialized()) {
                return java.util.Optional.of(shipPainter);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Canonical helper that returns the currently active variant when it is editable, or auto-creates,
     * registers and selects a new custom variant ('&lt;hullId&gt;_custom', '&lt;hullId&gt;_custom_NN') otherwise.
     * The hull ID is the skin hull ID when a non-base skin is active, else the base hull ID.
     *
     * @return the editable variant, or null when the layer is missing or not initialized.
     */
    public static ShipVariant getOrCreateEditableVariant(ShipLayer shipLayer) {
        if (shipLayer == null) {
            return null;
        }
        ShipPainter shipPainter = shipLayer.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) {
            return null;
        }
        ShipVariant activeVariant = shipPainter.getActiveVariant();
        if (activeVariant != null && !activeVariant.isEmpty()) {
            return activeVariant;
        }

        String hullId = resolveEditableHullId(shipPainter);
        Map<String, ShipVariant> loadedVariants = shipLayer.getLoadedVariants();
        String newVariantId = nextCustomVariantId(hullId, loadedVariants);

        log.debug("Auto-creating editable custom variant '{}' for hull '{}'", newVariantId, hullId);
        ShipVariant created = new ShipVariant(false);
        created.setShipHullId(hullId);
        created.setVariantId(newVariantId);
        created.setDisplayName("Custom");
        created.setLoadedFromFile(false);

        if (loadedVariants != null) {
            loadedVariants.put(newVariantId, created);
        }
        shipPainter.selectVariant(created);

        LayerManager layerManager = getLayerManager();
        if (layerManager != null) {
            layerManager.markUnsaved(shipLayer, "variant");
        }
        ToastManager.show("Created variant " + newVariantId);
        return created;
    }

    /**
     * @return the hull ID a newly auto-created variant would reference: the skin hull ID when a
     * non-base skin is active, else the base hull ID, falling back to "hull".
     */
    public static String resolveEditableHullId(ShipPainter shipPainter) {
        String hullId = null;
        if (shipPainter != null) {
            ShipSkin activeSkin = shipPainter.getActiveSkin();
            hullId = (activeSkin != null && !activeSkin.isBase())
                    ? activeSkin.getSkinHullId()
                    : shipPainter.getBaseHullId();
        }
        if (hullId == null || hullId.isBlank()) {
            hullId = "hull";
        }
        return hullId;
    }

    /**
     * @return the first unused ID of '&lt;hullId&gt;_custom', '&lt;hullId&gt;_custom_01', ... among the given variants.
     */
    public static String nextCustomVariantId(String hullId, Map<String, ShipVariant> loadedVariants) {
        String newVariantId = hullId + "_custom";
        if (loadedVariants != null) {
            int counter = 1;
            while (loadedVariants.containsKey(newVariantId)) {
                newVariantId = hullId + "_custom_" + String.format("%02d", counter);
                counter++;
            }
        }
        return newVariantId;
    }

    public static void actOnCurrentVariant(BiConsumer<ShipLayer, ShipVariant> action) {
        getActiveInitializedShipPainter().ifPresent(p -> {
            ShipLayer shipLayer = p.getParentLayer();
            var variant = p.getActiveVariant();
            if (variant == null || variant.isEmpty()) {
                variant = getOrCreateEditableVariant(shipLayer);
            }
            if (variant != null && !variant.isEmpty()) {
                action.accept(shipLayer, variant);
            }
        });
    }

    public static void actOnCurrentSkin(BiConsumer<ShipLayer, ShipSkin> action) {
        getActiveInitializedShipPainter().ifPresent(p -> {
            var skin = p.getActiveSkin();
            if (skin != null && !skin.isBase()) {
                action.accept((ShipLayer) p.getParentLayer(), skin);
            }
        });
    }

    public static void actOnCurrentShip(Consumer<ShipLayer> action) {
        getActiveInitializedShipPainter().ifPresent(p -> action.accept((ShipLayer) p.getParentLayer()));
    }

    public static HullSize getSizeOfActiveLayer() {
        HullSize size = null;

        var layer = StaticController.getActiveLayer();
        if (layer instanceof ShipLayer shipLayer) {
            ShipHull shipHull = shipLayer.getHull();
            if (shipHull != null) {
                size = shipHull.getHullSize();
            }
        }

        return size;
    }

    public static WeaponSlotPainter getSelectedSlotPainter() {
        return getActiveInitializedShipPainter().map(a -> a.getWeaponSlotPainter()).orElse(null);
    }

    public static boolean isShipLayerActive() {
        return getActiveInitializedShipPainter().isPresent();
    }

    public static boolean isShipVariantActive() {
        return getActiveInitializedShipPainter()
                .map(a -> a.getActiveVariant())
                .map(v -> !v.isEmpty())
                .orElse(false);
    }

    /**
     * @return selected slot from a currently active layer, with instrument mode eligibility checks.
     */
    public static WeaponSlotPoint getSelectedAndEligibleSlot() {
        return getActiveInitializedShipPainter()
                .map(Utility::getSelectedFromLayer)
                .orElse(null);
    }

}
