package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.components.ComponentEnums.CoordsDisplayMode;
import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.entities.ShieldCenterPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.entities.bays.LaunchBay;
import shipeditor.components.viewer.entities.bays.LaunchPortPoint;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;

import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.painters.points.ship.*;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.parsing.FileUtilities;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.*;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponSlot;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.ColorUtilities;
import shipeditor.utility.components.ToastManager;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringConstants;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("OverlyCoupledClass")
@Log4j2
final class SaveHullAction {

    private SaveHullAction() {
    }

    @SuppressWarnings("CallToPrintStackTrace")
    static void saveHullFromLayer(ShipLayer shipLayer) {
        JFileChooser fileChooser =  FileUtilities.getHullFileChooser();

        File currentDirectory = fileChooser.getCurrentDirectory();
        ShipHull shipHull = shipLayer.getHull();
        File initial = new File(currentDirectory, shipHull.getHullID());
        fileChooser.setSelectedFile(initial);

        ShipSpecFile existing = GameDataRepository.retrieveSpecByID(shipHull.getHullID());
        if (existing instanceof HullSpecFile hullSpecFile) {
            Path specFilePath = hullSpecFile.getFilePath();
            if (specFilePath != null) {
                File originalPath = specFilePath.toFile();
                if (originalPath.isFile()) {
                    fileChooser.setSelectedFile(originalPath);
                }
            }
        }

        int returnVal = fileChooser.showSaveDialog(shipeditor.PrimaryWindow.getInstance());
        File lastShipDirectory = fileChooser.getCurrentDirectory();
        FileUtilities.setLastShipDirectory(lastShipDirectory);
        FileUtilities.setLastGeneralDirectory(lastShipDirectory);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String extension = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
            File result = FileUtilities.ensureFileExtension(fileChooser, extension);

            log.info("Commencing hull saving: {}", result);

            ObjectMapper objectMapper = FileUtilities.getConfigured();
            HullSpecFile toSerialize = null;
            try {
                toSerialize = SaveHullAction.rebuildHullFile(shipLayer);
            } catch (Exception e) {
                log.error("Failed to rebuild HullSpecFile before saving: {}", result.getName(), e);
                return;
            }
            
            String errorMessage = "Hull file saving failed: {}";
            if (toSerialize == null) {
                log.error(errorMessage, result.getName());
                return;
            }
            try {
                SaveActionUtilities.mergeUnrecognizedProperties(result, toSerialize.getUnrecognizedProperties(), HullSpecFile.class);
                toSerialize.setFilePath(result.toPath());
                objectMapper.writeValue(result, toSerialize);
                GameDataRepository.putSpec(toSerialize);
                ShipCSVEntry csvEntry = GameDataRepository.retrieveShipCSVEntryByID(toSerialize.getHullId());
                if (csvEntry != null) {
                    csvEntry.setHullSpecFile(toSerialize);
                }
                var layerManager = StaticController.getViewer().getLayerManager();
                layerManager.markSaved(shipLayer, "hull");
                if (shipLayer.getActiveVariant() == null) {
                    layerManager.markSaved(shipLayer, "variant");
                }
                ToastManager.show("Saved " + result.getName() + " to " + result, ToastManager.ToastLevel.SUCCESS);
            } catch (IOException e) {
                log.error(errorMessage, result.getName(), e);
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("HULL_FILE_SAVING_FAILED_EXCEPTION_THROWN_MSG") + result,
                        StringManager.getString("FILE_SAVING_ERROR"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @SuppressWarnings("OverlyCoupledMethod")
    private static HullSpecFile rebuildHullFile(ShipLayer shipLayer) {
        log.trace("Rebuilding HullSpecFile for ship ID: {}", shipLayer.getShipID());
        HullSpecFile result = new HullSpecFile();

        var shipPainter = shipLayer.getPainter();

        log.trace("Rebuilding bounds...");
        Point2D.Double[] serializableBounds = SaveHullAction.rebuildBounds(shipPainter);
        result.setBounds(serializableBounds);

        log.trace("Rebuilding engines...");
        EngineSlot[] serializableEngines = SaveHullAction.rebuildEngineSlots(shipPainter);
        if (serializableEngines == null) {
            String shipID = shipLayer.getShipID();
            log.error("Engine misconfiguration at hull serialization. Ship ID: {}",
                    shipID);
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("ENGINE_MISCONFIGURATION_AT_HULL_SERIALIZ_MSG") +
                            "Ship ID: " + shipID,
                    StringManager.getString("FILE_SAVING_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        result.setEngineSlots(serializableEngines);

        WeaponSlot[] serializableWeaponSlots = SaveHullAction.rebuildWeaponSlots(shipPainter);
        result.setWeaponSlots(serializableWeaponSlots);

        ShipHull shipHull = shipLayer.getHull();
        String[] serializableBuiltInWings = SaveHullAction.rebuildBuiltInWings(shipHull);
        if (serializableBuiltInWings != null) {
            result.setBuiltInWings(serializableBuiltInWings);
        }

        @SuppressWarnings("LocalVariableNamingConvention")
        Map<String, String> serializableBuiltInWeapons = SaveHullAction.rebuildBuiltInWeapons(shipLayer);
        if (serializableBuiltInWeapons != null) {
            result.setBuiltInWeapons(serializableBuiltInWeapons);
        }

        @SuppressWarnings("LocalVariableNamingConvention")
        Map<String, String> serializableBuiltInModules = SaveHullAction.rebuildBuiltInModules(shipLayer);
        if (serializableBuiltInModules != null) {
            result.setBuiltInModules(serializableBuiltInModules);
        }

        String[] serializableBuiltInMods = SaveHullAction.rebuildBuiltInMods(shipHull);
        if (serializableBuiltInMods != null) {
            result.setBuiltInMods(serializableBuiltInMods);
        }

        var runtimeCoversColor = shipHull.getCoversColor();
        if (runtimeCoversColor != null) {
            String serializableCoversColor = ColorUtilities.convertColorToString(runtimeCoversColor);
            result.setCoversColor(serializableCoversColor);
        } else {
            result.setCoversColor("");
        }

        result.setViewOffset(shipHull.getViewOffset());

        ShieldPointPainter shieldPointPainter = shipPainter.getShieldPointPainter();
        ShieldCenterPoint shieldCenterPoint = shieldPointPainter.getShieldCenterPoint();
        float shieldRadius = shieldCenterPoint.getShieldRadius();

        result.setShieldRadius(shieldRadius);

        Point2D shieldPosition = Utility.getPointCoordinatesForDisplay(shieldCenterPoint.getPosition(),
                shipPainter, CoordsDisplayMode.SHIP_CENTER);
        result.setShieldCenter((Point2D.Double) shieldPosition);

        CenterPointPainter centerPointPainter = shipPainter.getCenterPointPainter();
        ShipCenterPoint shipCenterPoint = centerPointPainter.getCenterPoint();
        float collisionRadius = shipCenterPoint.getCollisionRadius();
        result.setCollisionRadius(collisionRadius);

        Point2D centerPosition = Utility.getPointCoordinatesForDisplay(shipCenterPoint.getPosition(),
                shipPainter, CoordsDisplayMode.SHIPCENTER_ANCHOR);
        result.setCenter((Point2D.Double) centerPosition);

        Point2D moduleAnchor = centerPointPainter.getModuleAnchorOffset();
        if (moduleAnchor != null) {
            result.setModuleAnchor((Point2D.Double) moduleAnchor);
        }

        result.setHeight(shipPainter.getSpriteHeight());
        result.setWidth(shipPainter.getSpriteWidth());

        HullStyle hullStyle = shipHull.getHullStyle();
        String styleID = hullStyle != null ? hullStyle.getHullStyleID() : shipHull.getStyleID();
        if (styleID == null || styleID.isEmpty()) {
            styleID = StringConstants.LOW_TECH;
        }
        result.setStyle(styleID);
        HullSize hullSize = shipHull.getHullSize();
        result.setHullSize(hullSize != null ? hullSize.toString() : HullSize.DEFAULT.toString());

        result.setSpriteName(shipLayer.getRelativeSpritePath());

        result.setHullId(shipHull.getHullID());
        result.setHullName(shipHull.getHullName());

        if (shipHull.getUnrecognizedProperties() != null) {
            result.getUnrecognizedProperties().putAll(shipHull.getUnrecognizedProperties());
        }

        return result;
    }

    private static Point2D.Double[] rebuildBounds(ShipPainter shipPainter) {
        BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
        var boundPoints = boundsPainter.getPointsIndex();

        return boundPoints.stream()
                .map(boundPoint -> (Point2D.Double) Utility.getPointCoordinatesForDisplay(
                        boundPoint.getPosition(), shipPainter, CoordsDisplayMode.SHIP_CENTER))
                .toArray(Point2D.Double[]::new);
    }

    private static EngineSlot[] rebuildEngineSlots(ShipPainter shipPainter) {
        EngineSlotPainter enginePainter = shipPainter.getEnginePainter();
        var enginePoints = enginePainter.getPointsIndex();

        return enginePoints.stream().map(enginePoint -> {
            EngineSlot serializableSlot = new EngineSlot();

            Point2D locationRelativeToCenter = Utility.getPointCoordinatesForDisplay(enginePoint.getPosition(),
                    shipPainter, CoordsDisplayMode.SHIP_CENTER);
            serializableSlot.setLocation((Point2D.Double) locationRelativeToCenter);

            serializableSlot.setAngle(enginePoint.getAngle());
            serializableSlot.setWidth(enginePoint.getWidth());
            serializableSlot.setLength(enginePoint.getLength());
            serializableSlot.setContrailSize(enginePoint.getContrailSize());

            String engineStyleID = enginePoint.getStyleID();
            EngineStyle customStyleSpec = enginePoint.getCustomStyleSpec();
            boolean isCustom = enginePoint.isStyleIsCustom();

            if (isCustom && engineStyleID != null && !engineStyleID.isEmpty()) {
                serializableSlot.setStyle(StringConstants.CUSTOM);
                serializableSlot.setStyleId(engineStyleID);
            } else if (customStyleSpec != null) {
                serializableSlot.setStyle(StringConstants.CUSTOM);
                serializableSlot.setStyleSpec(customStyleSpec);
            } else if (engineStyleID != null && !engineStyleID.isEmpty()) {
                serializableSlot.setStyle(engineStyleID);
            } else {
                var engineStyle = enginePoint.getStyle();
                if (engineStyle != null && engineStyle.getEngineStyleID() != null) {
                    serializableSlot.setStyle(engineStyle.getEngineStyleID());
                } else {
                    serializableSlot.setStyle(StringConstants.LOW_TECH);
                }
            }

            return serializableSlot;
        }).toArray(EngineSlot[]::new);
    }

    private static WeaponSlot[] rebuildWeaponSlots(ShipPainter shipPainter) {
        WeaponSlot[] slotsFromWeapons = SaveHullAction.transformSlotsFromWeapons(shipPainter);
        WeaponSlot[] slotsFromBays = SaveHullAction.transformSlotsFromBays(shipPainter);

        Stream<WeaponSlot> weaponSlotStream = Stream.of(slotsFromWeapons,
                slotsFromBays).flatMap(Stream::of);

        return weaponSlotStream.toArray(WeaponSlot[]::new);
    }

    private static WeaponSlot[] transformSlotsFromWeapons(ShipPainter shipPainter) {
        WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
        var slotPoints = slotPainter.getPointsIndex();

        return slotPoints.stream().map(slotPoint -> {
            WeaponSlot serializableSlot = SaveHullAction.createSerializable(slotPoint);

            Point2D locationRelativeToCenter = Utility.getPointCoordinatesForDisplay(slotPoint.getPosition(),
                    shipPainter, CoordsDisplayMode.SHIP_CENTER);
            Point2D.Double[] location = {(Point2D.Double) locationRelativeToCenter};
            serializableSlot.setLocations(location);

            int renderOrderMod = slotPoint.getRenderOrderMod();
            if (renderOrderMod != 0) {
                serializableSlot.setRenderOrderMod(renderOrderMod);
            }

            return serializableSlot;
        }).toArray(WeaponSlot[]::new);
    }

    private static WeaponSlot createSerializable(SlotData slotData) {
        WeaponSlot serializableSlot = new WeaponSlot();

        serializableSlot.setAngle(slotData.getAngle());
        serializableSlot.setArc(slotData.getArc());

        serializableSlot.setId(slotData.getId());

        WeaponSize weaponSize;
        WeaponType weaponType;
        WeaponMount weaponMount;

        // This is to ensure we serialize hull with the base values and not with skin overrides.
        if (slotData instanceof WeaponSlotPoint slotPoint) {
            weaponSize = slotPoint.getBaseSize();
            weaponType = slotPoint.getBaseType();
            weaponMount = slotPoint.getBaseMount();
        } else {
            weaponSize = slotData.getWeaponSize();
            weaponType = slotData.getWeaponType();
            weaponMount = slotData.getWeaponMount();
        }

        serializableSlot.setSize(weaponSize.getId());
        serializableSlot.setType(weaponType.getId());
        serializableSlot.setMount(weaponMount.getId());

        return serializableSlot;
    }

    private static WeaponSlot[] transformSlotsFromBays(ShipPainter shipPainter) {
        LaunchBayPainter bayPainter = shipPainter.getBayPainter();
        var bays = bayPainter.getBaysList();

        return bays.stream().map(launchBay -> {
            WeaponSlot serializableSlot = SaveHullAction.createSerializable(launchBay);

            List<Point2D.Double> portPositions = new ArrayList<>();
            List<LaunchPortPoint> portPoints = launchBay.getPortPoints();
            portPoints.forEach(portPoint -> {
                Point2D.Double locationRelativeToCenter = (Point2D.Double)
                        Utility.getPointCoordinatesForDisplay(portPoint.getPosition(),
                        shipPainter, CoordsDisplayMode.SHIP_CENTER);
                portPositions.add(locationRelativeToCenter);
            });

            Point2D.Double[] locations = portPositions.toArray(new Point2D.Double[0]);
            if (locations.length == 1) {
                locations = new Point2D.Double[]{locations[0], locations[0]};
            }
            serializableSlot.setLocations(locations);

            int renderOrderMod = launchBay.getRenderOrderMod();
            if (renderOrderMod != 0) {
                serializableSlot.setRenderOrderMod(renderOrderMod);
            }

            return serializableSlot;
        }).toArray(WeaponSlot[]::new);
    }

    private static String[] rebuildBuiltInWings(ShipHull shipHull) {
        var runtimeWings = shipHull.getBuiltInWings();
        if (runtimeWings == null || runtimeWings.isEmpty()) {
            return null;
        } else {
            return runtimeWings.stream()
                    .map(wingEntry -> wingEntry.getWingID())
                    .toArray(String[]::new);
        }
    }

    private static Map<String, String> rebuildBuiltInWeapons(ShipLayer shipLayer) {
        ShipPainter shipPainter = shipLayer.getPainter();

        var runtimeWeapons = shipPainter.getBuiltInWeapons();

        if (runtimeWeapons == null || runtimeWeapons.isEmpty()) {
            return null;
        } else {
            Map<String, String> serializableWeapons = new ListOrderedMap<>();
            
            List<Map.Entry<String, InstalledFeature>> entries = new ArrayList<>(runtimeWeapons.entrySet());
            entries.sort(InstalledFeature.SERIALIZATION_ORDER);
            
            for (Map.Entry<String, InstalledFeature> entry : entries) {
                serializableWeapons.put(entry.getKey(), entry.getValue().getID());
            }
            
            return serializableWeapons;
        }
    }

    private static Map<String, String> rebuildBuiltInModules(ShipLayer shipLayer) {
        ShipPainter shipPainter = shipLayer.getPainter();

        var builtInModules = shipPainter.getBuiltInModules();

        if (builtInModules == null || builtInModules.isEmpty()) {
            return null;
        } else {
            Map<String, String> serializableModules = new ListOrderedMap<>();
            builtInModules.forEach((slotID, feature) -> serializableModules.put(slotID, feature.getID()));
            return serializableModules;
        }
    }

    private static String[] rebuildBuiltInMods(ShipHull shipHull) {
        var runtimeMods = shipHull.getBuiltInMods();
        if (runtimeMods == null || runtimeMods.isEmpty()) {
            return null;
        } else {
            return runtimeMods.stream()
                    .map(modEntry -> modEntry.getHullmodID())
                    .toArray(String[]::new);
        }
    }

}
