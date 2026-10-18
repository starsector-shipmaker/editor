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
import shipeditor.utility.components.ToastManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
public final class SaveVariantAction {

    private SaveVariantAction() {
    }

    public static void saveVariant(ShipVariant variant) {
        if (variant == null) {
            return;
        }
        java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof javax.swing.JTextField tf) {
            tf.postActionEvent();
        }

        // --- Cascading Save Check ---
        LayerManager manager = StaticController.getLayerManager();
        Set<ShipVariant> dirtyModules = new LinkedHashSet<>();
        if (manager != null && variant.getFittedModules() != null) {
            for (InstalledFeature module : variant.getFittedModules().values()) {
                if (module != null && module.getFeaturePainter() instanceof ShipPainter shipPainter) {
                    ShipVariant modVariant = shipPainter.getActiveVariant();
                    if (modVariant != null && modVariant != variant) {
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
                    StringManager.getString("UNSAVED_MODULES_PROMPT_MSG", dirtyModules.size()),
                    StringManager.getString("UNSAVED_MODULES_DETECTED"),
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                for (ShipVariant dirtyMod : dirtyModules) {
                    // For simplicity, just invoke the normal save flow which prompts the user for each.
                    // A silent save is risky if they wanted to fork them.
                    saveVariant(dirtyMod);
                }
            }
        }
        // -----------------------------

        ShipLayer owningLayer = null;
        if (manager != null) {
            if (manager.getActiveLayer() instanceof ShipLayer activeShip
                    && (activeShip.getActiveVariant() == variant || activeShip.getLoadedVariants().containsValue(variant))) {
                owningLayer = activeShip;
            } else {
                for (ViewerLayer layer : manager.getLayers()) {
                    if (layer instanceof ShipLayer sl
                            && (sl.getActiveVariant() == variant || sl.getLoadedVariants().containsValue(variant))) {
                        owningLayer = sl;
                        break;
                    }
                }
            }
            if (owningLayer == null && manager.getActiveLayer() instanceof ShipLayer activeShip) {
                owningLayer = activeShip;
            }
        }

        JFileChooser fileChooser = FileUtilities.getVariantFileChooser(variant, owningLayer);

        String varId = variant.getVariantId() != null ? variant.getVariantId() : "unnamed_variant";
        File currentDirectory = fileChooser.getCurrentDirectory();
        File initial = new File(currentDirectory, varId);
        fileChooser.setSelectedFile(initial);

        if (variant.getVariantFilePath() != null && variant.getVariantFilePath().toFile().isFile()) {
            fileChooser.setSelectedFile(variant.getVariantFilePath().toFile());
        } else {
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
        }

        int returnVal = fileChooser.showSaveDialog(shipeditor.PrimaryWindow.getInstance());

        File lastVariantDirectory = fileChooser.getCurrentDirectory();
        FileUtilities.setLastVariantDirectory(lastVariantDirectory);
        FileUtilities.setLastGeneralDirectory(lastVariantDirectory);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String extension = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
            File result = FileUtilities.ensureFileExtension(fileChooser, extension);

            String fileName = result.getName();
            String fileStem = fileName.endsWith("." + extension)
                    ? fileName.substring(0, fileName.length() - extension.length() - 1)
                    : fileName;
            LayerManager layerManager = StaticController.getLayerManager();
            if (!fileStem.isBlank() && !fileStem.equals(variant.getVariantId())) {
                String oldVariantId = variant.getVariantId();
                variant.setVariantId(fileStem);
                if (layerManager != null) {
                    for (ViewerLayer layer : layerManager.getLayers()) {
                        if (layer instanceof ShipLayer shipLayer) {
                            Map<String, ShipVariant> loaded = shipLayer.getLoadedVariants();
                            if (oldVariantId != null && loaded.get(oldVariantId) == variant) {
                                loaded.remove(oldVariantId);
                                loaded.put(fileStem, variant);
                            } else if (shipLayer.getActiveVariant() == variant) {
                                loaded.put(fileStem, variant);
                            }
                        }
                    }
                }
            }

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
                variant.setVariantFilePath(result.toPath());
                variant.setLoadedFromFile(true);
                GameDataRepository.putVariant(toSerialize);
                if (layerManager != null) {
                    for (ViewerLayer layer : layerManager.getLayers()) {
                        if (layer instanceof ShipLayer shipLayer) {
                            if (shipLayer.getLoadedVariants().containsValue(variant) || shipLayer.getActiveVariant() == variant) {
                                shipLayer.getLoadedVariants().put(variant.getVariantId(), variant);
                                layerManager.markSaved(shipLayer, "variant");
                            }
                        }
                    }
                    if (layerManager.getActiveLayer() != null) {
                        shipeditor.communication.EventBus.publish(
                                new shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated(
                                        layerManager.getActiveLayer()));
                    }
                }
                shipeditor.communication.EventBus.publish(
                        new shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued(
                                shipeditor.components.ComponentEnums.EditorInstrument.VARIANT_DATA));
                ToastManager.show("Saved " + result.getName() + " to " + result,
                        ToastManager.ToastLevel.SUCCESS);
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

    /**
     * Writes a copy of the given variant under a new file chosen by the user.
     * The copy's variant ID becomes the chosen file stem and it is registered in the loaded variants
     * of the owning layer(s). The original variant (and its file) is left untouched.
     *
     * @param variant variant to copy
     */
    public static void saveVariantAsCopy(ShipVariant variant) {
        if (variant == null || variant.isEmpty()) {
            return;
        }
        LayerManager manager = StaticController.getLayerManager();
        ShipLayer owningLayer = null;
        if (manager != null) {
            if (manager.getActiveLayer() instanceof ShipLayer activeShip
                    && (activeShip.getActiveVariant() == variant
                    || activeShip.getLoadedVariants().containsValue(variant))) {
                owningLayer = activeShip;
            } else {
                for (ViewerLayer layer : manager.getLayers()) {
                    if (layer instanceof ShipLayer sl
                            && (sl.getActiveVariant() == variant || sl.getLoadedVariants().containsValue(variant))) {
                        owningLayer = sl;
                        break;
                    }
                }
            }
            if (owningLayer == null && manager.getActiveLayer() instanceof ShipLayer activeShip) {
                owningLayer = activeShip;
            }
        }

        JFileChooser fileChooser = FileUtilities.getVariantFileChooser(variant, owningLayer);
        fileChooser.setDialogTitle("Save Variant As Copy");
        String baseId = variant.getVariantId() != null ? variant.getVariantId() : "unnamed_variant";
        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), baseId + "_copy"));

        int returnVal = fileChooser.showSaveDialog(shipeditor.PrimaryWindow.getInstance());
        File lastVariantDirectory = fileChooser.getCurrentDirectory();
        FileUtilities.setLastVariantDirectory(lastVariantDirectory);
        FileUtilities.setLastGeneralDirectory(lastVariantDirectory);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String extension = ((FileNameExtensionFilter) fileChooser.getFileFilter()).getExtensions()[0];
        File result = FileUtilities.ensureFileExtension(fileChooser, extension);
        String fileName = result.getName();
        String fileStem = fileName.endsWith("." + extension)
                ? fileName.substring(0, fileName.length() - extension.length() - 1)
                : fileName;
        if (fileStem.isBlank() || fileStem.equals(variant.getVariantId())) {
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    "Choose a file name different from the original variant ID ('" + baseId + "').",
                    "Save Variant As Copy",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (result.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(shipeditor.PrimaryWindow.getInstance(),
                    "File already exists:\n" + result + "\nOverwrite it?",
                    "Save Variant As Copy",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        log.info("Commencing variant copy saving: {} -> {}", variant.getVariantId(), result);
        String baseDisplay = variant.getDisplayName() != null && !variant.getDisplayName().isBlank()
                ? variant.getDisplayName()
                : fileStem;
        ShipVariant copy = variant.createDeepCopy(fileStem, baseDisplay);

        VariantFile toSerialize;
        try {
            toSerialize = SaveVariantAction.rebuildVariantFile(copy);
            VariantFile original = GameDataRepository.getVariantByID(variant.getVariantId());
            if (original != null && original.getUnrecognizedProperties() != null) {
                toSerialize.getUnrecognizedProperties().putAll(original.getUnrecognizedProperties());
            }
        } catch (Exception e) {
            log.error("Failed to rebuild VariantFile for copy: {}", result.getName(), e);
            return;
        }
        try {
            ObjectMapper objectMapper = FileUtilities.getConfigured();
            SaveActionUtilities.mergeUnrecognizedProperties(result, toSerialize.getUnrecognizedProperties(), VariantFile.class);
            toSerialize.setVariantFilePath(result.toPath());
            objectMapper.writeValue(result, toSerialize);
            copy.setVariantFilePath(result.toPath());
            copy.setLoadedFromFile(true);
            GameDataRepository.putVariant(toSerialize);
            if (manager != null) {
                for (ViewerLayer layer : manager.getLayers()) {
                    if (layer instanceof ShipLayer shipLayer
                            && (shipLayer.getLoadedVariants().containsValue(variant)
                            || shipLayer.getActiveVariant() == variant)) {
                        shipLayer.getLoadedVariants().put(fileStem, copy);
                    }
                }
                if (manager.getActiveLayer() != null) {
                    shipeditor.communication.EventBus.publish(
                            new shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated(
                                    manager.getActiveLayer()));
                }
            }
            ToastManager.show("Saved " + result.getName() + " to " + result, ToastManager.ToastLevel.SUCCESS);
        } catch (IOException e) {
            log.error("Variant copy saving failed: {}", result.getName(), e);
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("VARIANT_FILE_SAVING_FAILED_EXCEPTION_THR_MSG") + result,
                    StringManager.getString("FILE_SAVING_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static boolean deleteVariantFile(ShipVariant variant, ShipLayer shipLayer) {
        if (variant == null || variant.isEmpty()) {
            return false;
        }
        String variantId = variant.getVariantId();
        Path filePath = variant.getVariantFilePath();
        if (filePath == null && variantId != null) {
            VariantFile existing = GameDataRepository.getVariantByID(variantId);
            if (existing != null) {
                filePath = existing.getVariantFilePath();
            }
        }

        if (filePath != null && java.nio.file.Files.isRegularFile(filePath)) {
            int confirm = JOptionPane.showConfirmDialog(
                    shipeditor.PrimaryWindow.getInstance(),
                    "Delete variant file '" + filePath.getFileName() + "' from disk?",
                    "Delete Variant File",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return false;
            }
            try {
                java.nio.file.Files.deleteIfExists(filePath);
                log.info("Deleted variant file from disk: {}", filePath);
            } catch (IOException ex) {
                log.error("Failed to delete variant file: {}", filePath, ex);
                JOptionPane.showMessageDialog(
                        shipeditor.PrimaryWindow.getInstance(),
                        "Failed to delete variant file: " + filePath,
                        StringManager.getString("FILE_SAVING_ERROR"),
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        if (shipLayer != null && variantId != null) {
            shipLayer.getLoadedVariants().remove(variantId);
        }

        var gameData = shipeditor.persistence.SettingsManager.getGameData();
        if (gameData != null && variantId != null && gameData.getAllVariants() != null) {
            gameData.getAllVariants().remove(variantId);
            gameData.rebuildVariantsByHullIndex();
        }

        if (shipLayer != null && shipLayer.getPainter() != null) {
            shipLayer.getPainter().selectVariant(VariantFile.empty());
        }
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && shipLayer != null) {
            layerManager.markSaved(shipLayer, "variant");
        }
        return true;
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
        result.setHullMods(hullMods != null
                ? hullMods.stream().map(HullmodCSVEntry::getHullmodID).collect(Collectors.toList())
                : new ArrayList<>());
        List<HullmodCSVEntry> permaMods = shipVariant.getPermaMods();
        result.setPermaMods(permaMods != null
                ? permaMods.stream().map(HullmodCSVEntry::getHullmodID).collect(Collectors.toList())
                : new ArrayList<>());
        List<HullmodCSVEntry> sMods = shipVariant.getSMods();
        result.setSMods(sMods != null
                ? sMods.stream().map(HullmodCSVEntry::getHullmodID).collect(Collectors.toList())
                : new ArrayList<>());

        List<String> suppressedMods = shipVariant.getSuppressedMods();
        if (suppressedMods != null && !suppressedMods.isEmpty()) {
            result.setSuppressedMods(new ArrayList<>(suppressedMods));
        }

        List<SpecWeaponGroup> specWeaponGroups = SaveVariantAction.recreateSpecWeaponGroups(shipVariant);
        result.setWeaponGroups(specWeaponGroups);

        List<WingCSVEntry> wings = shipVariant.getWings();
        result.setWings(wings != null
                ? wings.stream().map(WingCSVEntry::getWingID).collect(Collectors.toList())
                : new ArrayList<>());

        Map<String, String> modules = new LinkedHashMap<>();
        Map<String, InstalledFeature> fittedModules = shipVariant.getFittedModules();
        if (fittedModules != null) {
            fittedModules.forEach((slotID, moduleFeature) -> {
                if (moduleFeature == null) {
                    return;
                }
                String moduleVariantID = moduleFeature.getFeatureID();
                if (moduleFeature.getFeaturePainter() instanceof ShipPainter sp
                        && sp.getActiveVariant() != null
                        && sp.getActiveVariant().getVariantId() != null
                        && !sp.getActiveVariant().isEmpty()) {
                    moduleVariantID = sp.getActiveVariant().getVariantId();
                }
                modules.put(slotID, moduleVariantID);
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
        if (weaponGroups == null) {
            return specWeaponGroups;
        }
        weaponGroups.forEach(fittedWeaponGroup -> {
            if (fittedWeaponGroup == null) {
                return;
            }
            SpecWeaponGroup specWeaponGroup = new SpecWeaponGroup();
            FireMode mode = fittedWeaponGroup.getMode();
            specWeaponGroup.setMode(mode != null ? mode.toString() : FireMode.LINKED.toString());
            specWeaponGroup.setAutofire(fittedWeaponGroup.isAutofire());

            Map<String, String> specGroupWeapons = new LinkedHashMap<>();
            ListOrderedMap<String, InstalledFeature> groupWeapons = fittedWeaponGroup.getWeapons();
            if (groupWeapons != null) {
                groupWeapons.forEach((slotID, feature) -> {
                    if (feature != null) {
                        String weaponID = feature.getFeatureID();
                        specGroupWeapons.put(slotID, weaponID);
                    }
                });
            }

            specWeaponGroup.setWeapons(specGroupWeapons);
            specWeaponGroups.add(specWeaponGroup);
        });
        return specWeaponGroups;
    }

}
