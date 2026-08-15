package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.ViewerEnums.FireMode;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.parsing.FileUtilities;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.SpecWeaponGroup;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Errors;
import shipeditor.utility.text.StringConstants;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
final class SaveVariantAction {

    private SaveVariantAction() {
    }

    static void saveVariant(ShipVariant variant) {
        // --- Cascading Save Check ---
        LayerManager manager = StaticController.getViewer().getLayerManager();
        List<ShipVariant> dirtyModules = new ArrayList<>();
        if (variant.getFittedModules() != null) {
            for (InstalledFeature module : variant.getFittedModules().values()) {
                if (module.getFeaturePainter() instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter) {
                    ShipVariant modVariant = shipPainter.getActiveVariant();
                    if (modVariant != null) {
                        for (ViewerLayer layer : manager.getLayers()) {
                            if (layer instanceof ShipLayer sl && sl.getActiveVariant() == modVariant) {
                                if (manager.isVariantDirty(layer)) {
                                    dirtyModules.add(modVariant);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (!dirtyModules.isEmpty()) {
            int result = JOptionPane.showConfirmDialog(shipeditor.PrimaryWindow.getInstance(),
                    "There are " + dirtyModules.size() + " unsaved module variants associated with this ship.\nDo you want to save them as well?",
                    "Unsaved Modules Detected", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                for (ShipVariant dirtyMod : dirtyModules) {
                    // For simplicity, just invoke the normal save flow which prompts the user for each.
                    // A silent save is risky if they wanted to fork them.
                    saveVariant(dirtyMod);
                }
            }
        }
        // -----------------------------

        JFileChooser fileChooser = SaveVariantAction.getSaveVariantFileChooser();

        File currentDirectory = fileChooser.getCurrentDirectory();
        File initial = new File(currentDirectory, variant.getVariantId());
        fileChooser.setSelectedFile(initial);

        VariantFile existing = GameDataRepository.getVariantByID(variant.getVariantId());
        if (existing != null) {
            Path specFilePath = existing.getVariantFilePath();
            if (specFilePath != null) {
                File originalPath = specFilePath.toFile();
                if (originalPath.isFile()) {
                    fileChooser.setSelectedFile(originalPath);
                }
            }
        }

        int returnVal = fileChooser.showSaveDialog(shipeditor.PrimaryWindow.getInstance());

        File lastVariantDirectory = fileChooser.getCurrentDirectory();
        FileUtilities.setLastVariantDirectory(lastVariantDirectory);
        FileUtilities.setLastGeneralDirectory(lastVariantDirectory);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String extension = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
            File result = FileUtilities.ensureFileExtension(fileChooser, extension);

            log.info("Commencing variant saving: {}", result);

            ObjectMapper objectMapper = FileUtilities.getConfigured();
            VariantFile toSerialize = null;
            try {
                toSerialize = SaveVariantAction.rebuildVariantFile(variant);
            } catch (Exception e) {
                log.error("Failed to rebuild VariantFile before saving: {}", result.getName(), e);
                return;
            }
            try {
                SaveActionUtilities.mergeUnrecognizedProperties(result, toSerialize.getUnrecognizedProperties(), VariantFile.class);
                toSerialize.setVariantFilePath(result.toPath());
                objectMapper.writeValue(result, toSerialize);
                GameDataRepository.putVariant(toSerialize);
                LayerManager layerManager = StaticController.getViewer().getLayerManager();
                for (ViewerLayer layer : layerManager.getLayers()) {
                    if (layer instanceof ShipLayer shipLayer) {
                        if (shipLayer.getLoadedVariants().containsValue(variant) || shipLayer.getActiveVariant() == variant) {
                            layerManager.markSaved(shipLayer, "variant");
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Variant file saving failed: {}", result.getName(), e);
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("VARIANT_FILE_SAVING_FAILED_EXCEPTION_THR_MSG") + result,
                        StringManager.getString("FILE_SAVING_ERROR"),
                        JOptionPane.ERROR_MESSAGE);
                Errors.printToStream(e);
            }
        }
    }

    private static VariantFile rebuildVariantFile(ShipVariant shipVariant) {
        log.trace("Rebuilding VariantFile for variant ID: {}", shipVariant.getVariantId());
        VariantFile result = new VariantFile();

        result.setDisplayName(shipVariant.getDisplayName());
        result.setVariantId(shipVariant.getVariantId());
        result.setGoalVariant(shipVariant.isGoalVariant());
        result.setFluxCapacitors(shipVariant.getFluxCapacitors());
        result.setFluxVents(shipVariant.getFluxVents());
        result.setHullId(shipVariant.getShipHullId());

        result.setQuality(shipVariant.getQuality());

        List<HullmodCSVEntry> hullMods = shipVariant.getHullMods();
        result.setHullMods(hullMods.stream().map(a -> a.getHullmodID()).collect(Collectors.toList()));
        List<HullmodCSVEntry> permaMods = shipVariant.getPermaMods();
        result.setPermaMods(permaMods.stream().map(a -> a.getHullmodID()).collect(Collectors.toList()));
        List<HullmodCSVEntry> sMods = shipVariant.getSMods();
        result.setSMods(sMods.stream().map(a -> a.getHullmodID()).collect(Collectors.toList()));

        List<String> suppressedMods = shipVariant.getSuppressedMods();
        if (suppressedMods != null && !suppressedMods.isEmpty()) {
            result.setSuppressedMods(new ArrayList<>(suppressedMods));
        }

        List<SpecWeaponGroup> specWeaponGroups = SaveVariantAction.recreateSpecWeaponGroups(shipVariant);
        result.setWeaponGroups(specWeaponGroups);

        List<WingCSVEntry> wings = shipVariant.getWings();
        result.setWings(wings.stream().map(a -> a.getWingID()).collect(Collectors.toList()));

        Map<String, String> modules = new LinkedHashMap<>();
        Map<String, InstalledFeature> fittedModules = shipVariant.getFittedModules();
        if (fittedModules != null) {
            fittedModules.forEach((slotID, moduleFeature) -> {
                String moduleHullID = moduleFeature.getFeatureID();
                modules.put(slotID, moduleHullID);
            });
        }
        result.setModules(modules);

        VariantFile existing = GameDataRepository.getVariantByID(shipVariant.getVariantId());
        if (existing != null && existing.getUnrecognizedProperties() != null) {
            result.getUnrecognizedProperties().putAll(existing.getUnrecognizedProperties());
        }

        return result;
    }

    private static List<SpecWeaponGroup> recreateSpecWeaponGroups(ShipVariant shipVariant) {
        List<FittedWeaponGroup> weaponGroups = shipVariant.getWeaponGroups();

        List<SpecWeaponGroup> specWeaponGroups = new ArrayList<>();
        weaponGroups.forEach(fittedWeaponGroup -> {
            SpecWeaponGroup specWeaponGroup = new SpecWeaponGroup();
            FireMode mode = fittedWeaponGroup.getMode();
            specWeaponGroup.setMode(mode.toString());
            specWeaponGroup.setAutofire(fittedWeaponGroup.isAutofire());

            Map<String, String> specGroupWeapons = new LinkedHashMap<>();
            ListOrderedMap<String, InstalledFeature> groupWeapons = fittedWeaponGroup.getWeapons();
            groupWeapons.forEach((slotID, feature) -> {
                String weaponID = feature.getFeatureID();
                specGroupWeapons.put(slotID, weaponID);
            });

            specWeaponGroup.setWeapons(specGroupWeapons);
            specWeaponGroups.add(specWeaponGroup);
        });
        return specWeaponGroups;
    }

    private static JFileChooser getSaveVariantFileChooser() {
        FileNameExtensionFilter variantFileFilter = new FileNameExtensionFilter(
                "JSON variant files", StringConstants.VARIANT);

        JFileChooser fileChooser = FileUtilities.getFileChooser(variantFileFilter);

        File directory = FileUtilities.getLastVariantDirectory();
        if (directory != null) {
            fileChooser.setCurrentDirectory(directory);
        }

        return fileChooser;
    }

}
