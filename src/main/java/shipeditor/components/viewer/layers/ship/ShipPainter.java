package shipeditor.components.viewer.layers.ship;

import shipeditor.utility.text.StringManager;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.Events;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerShipDataInitialized;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.entities.bays.LaunchBay;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.data.ActiveShipSpec;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.layers.ship.data.Variant;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;

import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.ship.*;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeaturePainter;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.RepresentationEnums.ShipTypeHints;
import shipeditor.representation.ship.VariantFile;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.graphics.Sprite;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

/** * Distinct from parent ship layer instance: present class has to do with direct visual representation.
 * Painter instance is not concerned with loading and file interactions, and leaves that to other classes.*/
@Getter
@SuppressWarnings({"OverlyCoupledClass", "ClassWithTooManyFields", "ClassWithTooManyMethods", "OverlyComplexClass"})
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShipPainter extends LayerPainter {

    private static final char SPACE = ' ';

    private CenterPointPainter centerPointPainter;

    private ShieldPointPainter shieldPointPainter;

    private BoundPointsPainter boundsPainter;

    private WeaponSlotPainter weaponSlotPainter;

    private LaunchBayPainter bayPainter;

    private EngineSlotPainter enginePainter;

    @Setter
    private Map<String, InstalledFeature> builtInWeapons;

    
    @Setter
    private Map<String, InstalledFeature> builtInModules;

    private InstalledFeaturePainter installablesPainter;

    /**
     * Backup for when sprite is switched to skin version.
     */
    @Setter
    private Sprite baseHullSprite;

    @Setter
    private ShipSkin activeSkin;

    @Setter
    private ShipVariant activeVariant;

    @Setter
    private String baseHullId;

    public ShipPainter(ShipLayer layer) {
        super(layer);
        this.clearVariant();
        this.activateEmptySkin();
    }

    private void clearVariant() {
        this.selectVariant(VariantFile.empty());
    }

    public void selectVariant(Variant variant) {
        boolean wasPaused = shipeditor.undo.UndoOverseer.isPaused();
        shipeditor.undo.UndoOverseer.setPaused(true);
        try {
            if (variant instanceof VariantFile checked) {
                this.installVariant(checked);
            } else if (variant instanceof ShipVariant checked) {
                activeVariant = checked;
            } else if (variant == null) {
                activeVariant = null;
            }
            this.notifyLayerUpdate();
        } finally {
            shipeditor.undo.UndoOverseer.setPaused(wasPaused);
        }
    }

    private void installVariant(VariantFile file) {
        boolean empty = file.isEmpty();

        activeVariant = new ShipVariant(empty);
        if (!empty) {
            String variantId = file.getVariantId();

            activeVariant.initialize(file);

            // Validate that the variant's fitted weapons match the hull's slot restrictions without corrupting or stripping data
            var slotPainter = this.getWeaponSlotPainter();
            var fittedWeapons = activeVariant.getAllFittedWeapons();
            var builtIns = this.getBuiltInsWithSkin(true, true);
            fittedWeapons.forEach((slotID, feature) -> {
                // Built-in weapons defined on the hull or skin are always valid for their assigned slot
                if (feature.isContainedInBuiltIns() || (builtIns != null && builtIns.containsKey(slotID))) {
                    return;
                }
                var slotPoint = slotPainter.getSlotByID(slotID);
                if (slotPoint == null) {
                    // Check if this slot belongs to a fitted module
                    var modules = activeVariant.getFittedModules();
                    if (modules != null) {
                        for (InstalledFeature module : modules.values()) {
                            LayerPainter modulePainter = module.getFeaturePainter();
                            if (modulePainter instanceof ShipPainter shipModulePainter
                                    && shipModulePainter.getWeaponSlotPainter().getSlotByID(slotID) != null) {
                                return;
                            }
                        }
                    }
                    log.debug("Variant {} specifies weapon {} for non-hull slot {}.", variantId, feature.getDataEntry().getID(), slotID);
                    return;
                }
                if (!shipeditor.representation.weapon.WeaponEnums.WeaponType.isValidForSlot(slotPoint, feature.getDataEntry())) {
                    log.debug("Variant {} weapon {} does not match slot {} type/size restrictions.", variantId, feature.getDataEntry().getID(), slotID);
                }
            });

            var parentLayer = getParentLayer();
            if (parentLayer != null) {
                var loadedVariants = parentLayer.getLoadedVariants();
                loadedVariants.put(variantId, activeVariant);
            }

        }
    }

    @Override
    public void cleanupForRemoval() {
        super.cleanupForRemoval();

        var allInstallables = this.getAllLoadedInstallables();
        allInstallables.forEach((s, installedFeature) -> {
            var painter = installedFeature.getFeaturePainter();
            painter.cleanupForRemoval();
        });

        var parentLayer = getParentLayer();
        if (parentLayer != null) {
            var featuresOverseer = parentLayer.getFeaturesOverseer();
            featuresOverseer.cleanupListeners();
        }
    }

    void activateEmptySkin() {
        this.setActiveSkin(ShipSkin.EMPTY);
    }

    /**
     * @param skin only evaluated if spec type is SKIN.
     */
    public void setActiveSpec(ActiveShipSpec type, ShipSkin skin) {
        ShipLayer parentLayer = this.getParentLayer();
        if (type == ActiveShipSpec.HULL) {
            this.setSprite(baseHullSprite);

            if (parentLayer != null) {
                parentLayer.setActiveSkinFileName(StringManager.getString("NOT_LOADED"));
            }

            this.weaponSlotPainter.resetSkinSlotOverride();
            this.enginePainter.resetSkinSlotOverride();

            this.activateEmptySkin();
        } else {
            if (skin == null) {
                throw new IllegalArgumentException("Attempted to activate invalid skin!");
            }
            Sprite loadedSkinSprite = skin.getLoadedSkinSprite();
            if (loadedSkinSprite != null) {
                this.setSprite(loadedSkinSprite);
            } else {
                this.setSprite(baseHullSprite);
            }

            if (skin.getWeaponSlotChanges() != null) {
                this.weaponSlotPainter.toggleSkinSlotOverride(skin);
            } else {
                this.weaponSlotPainter.resetSkinSlotOverride();
            }

            if (skin.getEngineSlotChanges() != null) {
                this.enginePainter.toggleSkinSlotOverride(skin);
            } else {
                this.enginePainter.resetSkinSlotOverride();
            }

            if (parentLayer != null) {
                java.nio.file.Path skinFilePath = skin.getSkinFilePath();
                java.nio.file.Path fileNamePath = skinFilePath != null ? skinFilePath.getFileName() : null;
                String skinFileName = fileNamePath != null ? fileNamePath.toString() : "";
                parentLayer.setActiveSkinFileName(skinFileName);
            }

            this.activeSkin = skin;
        }
        this.selectVariant(VariantFile.empty());
        this.notifyLayerUpdate();
    }

    private void notifyLayerUpdate() {
        ShipLayer parentLayer = this.getParentLayer();
        if (parentLayer != null) {
            EventBus.publish(new ActiveLayerUpdated(parentLayer));
        }
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.SKIN_DATA));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.SKIN_SLOTS));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.SKIN_ENGINES));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.SKIN_REMOVALS));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.VARIANT_DATA));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.VARIANT_WEAPONS));
        EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.VARIANT_MODULES));
        Events.repaintShipView();
    }

    @Override
    public ShipLayer getParentLayer() {
        ViewerLayer parentLayer = super.getParentLayer();
        if (parentLayer instanceof ShipLayer checked) {
            return checked;
        } else if (parentLayer != null) {
            throw new IllegalStateException("Found illegal parent layer of ShipPainter!");
        }
        return null;
    }

    private void createPointPainters() {
        if (!isUninitialized()) {
            cleanupPointPainters();
        }
        this.centerPointPainter = new CenterPointPainter(this);
        this.shieldPointPainter = new ShieldPointPainter(this);
        this.boundsPainter = new BoundPointsPainter(this);
        this.weaponSlotPainter = new WeaponSlotPainter(this);
        this.bayPainter = new LaunchBayPainter(this);
        this.enginePainter = new EngineSlotPainter(this);

        this.installablesPainter = new InstalledFeaturePainter();
        this.builtInWeapons = new ListOrderedMap<>();

        List<AbstractPointPainter> allPainters = getAllPainters();
        allPainters.add(centerPointPainter);
        allPainters.add(shieldPointPainter);
        allPainters.add(boundsPainter);
        allPainters.add(weaponSlotPainter);
        allPainters.add(bayPainter);
        allPainters.add(enginePainter);
    }

    void finishInitialization() {
        this.setUninitialized(false);

        log.trace("{} initialized!", this);
        EventBus.publish(new LayerShipDataInitialized(this));
        EventBus.publish(new ViewerRepaintQueued());
    }

    public void initFromHullSpec(HullSpecFile hullSpecFile) {
        this.createPointPainters();
        ShipPainterInitialization.loadHullData(this, hullSpecFile);
    }

    private List<BaseWorldPoint> getAllShipPoints() {
        List<BaseWorldPoint> result = new ArrayList<>();

        ShieldPointPainter shieldPainter = this.getShieldPointPainter();
        result.add(shieldPainter.getShieldCenterPoint());

        BoundPointsPainter boundPointsPainter = this.getBoundsPainter();
        result.addAll(boundPointsPainter.getPointsIndex());

        WeaponSlotPainter slotPainter = this.getWeaponSlotPainter();
        result.addAll(slotPainter.getPointsIndex());

        LaunchBayPainter launchBayPainter = this.getBayPainter();
        result.addAll(launchBayPainter.getPointsIndex());

        EngineSlotPainter engineSlotPainter = this.getEnginePainter();
        result.addAll(engineSlotPainter.getPointsIndex());

        return result;
    }

    public void flipShipPointsHorizontally() {
        EditDispatch.postShipPointsFlipped(getAllShipPoints(), this.getShipCenter());
    }

    public ShipCenterPoint getShipCenter() {
        return this.centerPointPainter.getCenterPoint();
    }

    @Override
    public Point2D getEntityCenter() {
        ShipCenterPoint shipCenter = this.getShipCenter();
        return shipCenter.getPosition();
    }

    @Override
    public Point2D getRotationAnchor() {
        CenterPointPainter pointPainter = this.getCenterPointPainter();
        if (pointPainter == null) {
            return this.getSpriteCenter();
        }
        Point2D moduleAnchorOffset = pointPainter.getModuleAnchorOffset();
        if (moduleAnchorOffset == null) {
            return getEntityCenter();
        } else {
            Point2D entityCenter = getEntityCenter();
            double unrotatedOx = -moduleAnchorOffset.getY();
            double unrotatedOy = -moduleAnchorOffset.getX();
            Point2D unrotatedOffset = new Point2D.Double(unrotatedOx, unrotatedOy);
            
            java.awt.geom.AffineTransform rotationTransform = java.awt.geom.AffineTransform.getRotateInstance(this.getRotationRadians());
            Point2D rotatedOffset = rotationTransform.transform(unrotatedOffset, null);
            
            double x = entityCenter.getX() + rotatedOffset.getX();
            double y = entityCenter.getY() + rotatedOffset.getY();
            return new Point2D.Double(x, y);
        }
    }

    public Point2D getCenterAnchor() {
        Point2D anchor = getAnchor();
        BufferedImage sprite = getSpriteImage();
        return new Point2D.Double( anchor.getX(), anchor.getY() + sprite.getHeight());
    }

    public Set<String> getAllSlotIDs() {
        WeaponSlotPainter slotPainter = this.getWeaponSlotPainter();
        List<WeaponSlotPoint> slotPoints = slotPainter.getSlotPoints();

        Set<String> slotIDs = slotPoints.stream()
                .map(a -> a.getId())
                .collect(Collectors.toSet());

        LaunchBayPainter launchBayPainter = this.getBayPainter();
        List<LaunchBay> layerBays = launchBayPainter.getBaysList();

        Set<String> bayIDs = layerBays.stream()
                .map(a -> a.getId())
                .collect(Collectors.toSet());

        slotIDs.addAll(bayIDs);

        return slotIDs;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isGeneratedIDUnassigned(String newId) {
        Set<String> existingIDs = this.getAllSlotIDs();
        return !existingIDs.contains(newId);
    }

    public String generateUniqueSlotID(String baseID) {
        return generateUniqueSlotID(baseID, this.getAllSlotIDs());
    }

    public String generateUniqueSlotID(String baseID, Set<String> existingIDs) {
        int suffix = 0;

        while (true) {
            String newID = baseID + " " + String.format("%03d", suffix);
            if (!existingIDs.contains(newID)) {
                existingIDs.add(newID);
                return newID;
            }
            suffix++;
        }
    }

    public String incrementUniqueSlotID(String id) {
        return incrementUniqueSlotID(id, this.getAllSlotIDs());
    }

    public String incrementUniqueSlotID(String id, Set<String> existingIDs) {
        String baseID = id.substring(0, id.lastIndexOf(SPACE) + 1);

        int suffix;
        try {
            suffix = Integer.parseInt(id.substring(id.lastIndexOf(SPACE) + 1));
        } catch (NumberFormatException e) {
            return id + " 001";
        }

        while (true) {
            suffix++;
            String newID = baseID + String.format("%03d", suffix);
            if (!existingIDs.contains(newID)) {
                existingIDs.add(newID);
                return newID;
            }
        }
    }

    public List<ShipTypeHints> getHintsModified() {
        ShipCSVEntry dataEntry = GameDataRepository.retrieveShipCSVEntryByID(this.getBaseHullId());

        var skin  = this.getActiveSkin();
        if (skin != null && !skin.isBase()) {
            return skin.getHintsModifiedBySkin();
        } else if (dataEntry != null) {
            return dataEntry.getBaseHullHints();
        }
        return null;
    }

    @SuppressWarnings("BooleanParameter")
    public Map<String, InstalledFeature> getBuiltInsWithSkin(boolean includeDecorative,
                                                             boolean includeNonDecorative) {
        Map<String, InstalledFeature> builtIns = this.getBuiltInWeapons();

        Map<String, InstalledFeature> result = new LinkedHashMap<>();
        var slotPainter = this.getWeaponSlotPainter();
        if (builtIns != null) {
            builtIns.forEach((slotID, feature) -> {
                boolean isSlotDecorative = slotPainter.isSlotDecorative(slotID);
                if (isSlotDecorative && includeDecorative) {
                    result.put(slotID, feature);
                } else if (slotPainter.getSlotByID(slotID) != null && !isSlotDecorative && includeNonDecorative) {
                    result.put(slotID, feature);
                }
            });
        }

        if (activeSkin != null && !activeSkin.isBase()) {
            addSkinEntriesToBuiltInList(includeDecorative, includeNonDecorative, result, slotPainter);
        }

        return result;
    }

    private void addSkinEntriesToBuiltInList(boolean includeDecorative, boolean includeNonDecorative,
                                             Map<String, InstalledFeature> result, WeaponSlotPainter slotPainter) {
        var removedBuiltIns = activeSkin.getRemoveBuiltInWeapons();
        if (removedBuiltIns != null) {
            removedBuiltIns.forEach(result::remove);
        }

        var addedBuiltIns = activeSkin.getInitializedBuiltIns();
        if (!addedBuiltIns.isEmpty()) {
            addedBuiltIns.forEach((slotID, feature) -> {
                boolean isSlotDecorative = slotPainter.isSlotDecorative(slotID);
                if (isSlotDecorative && includeDecorative) {
                    result.put(slotID, feature);
                } else if (slotPainter.getSlotByID(slotID) != null && !isSlotDecorative && includeNonDecorative) {
                    result.put(slotID, feature);
                }
            });
        }
    }

    private Map<String, InstalledFeature> getAllLoadedInstallables() {
        var builtIns = this.getBuiltInsWithSkin(true, true);
        Map<String, InstalledFeature> allFeatures = new LinkedHashMap<>(builtIns);

        ShipVariant shipVariant = this.getActiveVariant();
        Collection<ShipVariant> allLoaded = new HashSet<>();
        allLoaded.add(shipVariant);

        var parentLayer = this.getParentLayer();
        if (parentLayer != null) {
            var loadedToLayer = parentLayer.getLoadedVariants();
            allLoaded.addAll(loadedToLayer.values());
        }

        allLoaded.forEach(variant -> {
            if (variant != null && !variant.isEmpty()) {
                var modules = variant.getFittedModules();
                if (modules != null) {
                    allFeatures.putAll(modules);
                }
                var allWeapons = variant.getAllFittedWeapons();
                if (allWeapons != null) {
                    allFeatures.putAll(allWeapons);
                }
            }
        });

        return allFeatures;
    }

    @Override
    public java.awt.geom.Rectangle2D getVisualBounds() {
        java.awt.geom.Rectangle2D bounds = super.getVisualBounds();
        var installedFeaturePainter = this.getInstallablesPainter();
        if (installedFeaturePainter != null) {
            installedFeaturePainter.updateRenderQueue(this);
            java.awt.geom.Rectangle2D featureBounds = installedFeaturePainter.getVisualBounds();
            if (featureBounds != null) {
                java.awt.geom.Rectangle2D.union(bounds, featureBounds, bounds);
            }
        }
        return bounds;
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (!isShouldDrawPainter()) return;
        if (this.isUninitialized()) {
            super.paint(spriteRenderer, shapeRenderer, projection, view);
        } else {
            var installedFeaturePainter = this.getInstallablesPainter();
            installedFeaturePainter.updateRenderQueue(this);
            installedFeaturePainter.paintUnderParent(spriteRenderer, shapeRenderer, projection, view);
            super.paint(spriteRenderer, shapeRenderer, projection, view);
            installedFeaturePainter.paintNormal(spriteRenderer, shapeRenderer, projection, view);
        }
    }

    public void cycleWeaponFrames() {
        if (activeVariant != null && !activeVariant.isEmpty()) {
            var allWeapons = activeVariant.getAllFittedWeapons();
            if (allWeapons != null) {
                allWeapons.values().forEach(feature -> {
                    if (feature.getFeaturePainter() instanceof WeaponPainter weaponPainter) {
                        weaponPainter.cycleNextFrame();
                    }
                });
            }
        }
        var builtIns = getBuiltInsWithSkin(true, true);
        if (builtIns != null) {
            builtIns.values().forEach(feature -> {
                if (feature.getFeaturePainter() instanceof WeaponPainter weaponPainter) {
                    weaponPainter.cycleNextFrame();
                }
            });
        }
    }

}
