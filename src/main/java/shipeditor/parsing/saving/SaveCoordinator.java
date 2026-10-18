package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullSaveQueued;
import shipeditor.communication.events.files.FileEvents.VariantSaveQueued;
import shipeditor.communication.events.files.FileEvents.WeaponSaveQueued;
import shipeditor.communication.events.files.FileEvents.ProjectileSaveQueued;

import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;
import shipeditor.utility.overseers.StaticController;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.parsing.FileUtilities;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.ShipSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.components.ToastManager;
import shipeditor.utility.components.dialog.DialogHelper;

import lombok.extern.log4j.Log4j2;

import javax.swing.JOptionPane;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
public final class SaveCoordinator {

    private SaveCoordinator() {
    }

    public static void init() {
        EventBus.subscribe(SaveCoordinator.class, event -> {
            switch (event.getClass().getSimpleName()) {
                case "VariantSaveQueued" -> SaveVariantAction.saveVariant(((VariantSaveQueued) event).variant());
                case "HullSaveQueued" -> SaveHullAction.saveHullFromLayer(((HullSaveQueued) event).shipLayer());
                case "WeaponSaveQueued" -> SaveWeaponAction.saveWeaponFromLayer(((WeaponSaveQueued) event).weaponLayer());
                case "ProjectileSaveQueued" -> SaveProjectileAction.saveProjectileFromLayer(((ProjectileSaveQueued) event).projectileLayer());
                case "CSVSaveQueued" -> SaveCSVAction.saveCSVEntry(((shipeditor.communication.events.files.FileEvents.CSVSaveQueued) event).entry());
                default -> {}
            }
        });
    }

    /**
     * Saves exactly one thing for the layer (Ctrl+S): in variant editor modes the active variant,
     * otherwise the hull; if the hull is clean but the variant is dirty (non-variant mode), the variant.
     * Never writes both. If the other category is still unsaved afterwards, an info toast says so.
     */
    public static void saveLayer(ViewerLayer layer) {
        if (layer instanceof ShipLayer shipLayer) {
            SavePlan plan = planSingleSave(shipLayer);
            log.debug("Single save plan: hull={}, variant={}", plan.writeHull(),
                    plan.variant() != null ? plan.variant().getVariantId() : null);

            List<SaveItem> items = collectSaveItems(plan);
            boolean newFileInOtherDir = false;
            for (SaveItem item : items) {
                if (item.newInDifferentDir()) {
                    newFileInOtherDir = true;
                }
            }
            if (newFileInOtherDir && !DialogHelper.confirmSaveSummary(formatSummary(items))) {
                log.debug("Save cancelled at summary dialog");
                return;
            }

            if (plan.writeHull()) {
                EventBus.publish(new HullSaveQueued(shipLayer));
            } else {
                EventBus.publish(new VariantSaveQueued(plan.variant()));
            }
            announceRemainingUnsaved(shipLayer, plan);
        } else if (layer instanceof WeaponLayer weaponLayer) {
            EventBus.publish(new WeaponSaveQueued(weaponLayer));
        } else if (layer instanceof ProjectileLayer projectileLayer) {
            EventBus.publish(new ProjectileSaveQueued(projectileLayer));
        }
    }

    private static boolean isVariantDirty(ShipLayer shipLayer, ShipVariant activeVariant) {
        LayerManager layerManager = StaticController.getLayerManager();
        return activeVariant != null && !activeVariant.isEmpty()
                && ((layerManager != null && layerManager.isVariantDirty(shipLayer))
                || !activeVariant.isLoadedFromFile());
    }

    private static ShipVariant activeVariantOf(ShipLayer shipLayer) {
        var painter = shipLayer.getPainter();
        ShipVariant active = painter != null ? painter.getActiveVariant() : null;
        return active != null && !active.isEmpty() ? active : null;
    }

    private static SavePlan planSingleSave(ShipLayer shipLayer) {
        EditorInstrument mode = StaticController.getEditorMode();
        boolean isVariantMode = mode == EditorInstrument.VARIANT_DATA
                || mode == EditorInstrument.VARIANT_WEAPONS
                || mode == EditorInstrument.VARIANT_MODULES;
        LayerManager layerManager = StaticController.getLayerManager();
        boolean hullDirty = layerManager != null && layerManager.isHullDirty(shipLayer);
        ShipVariant activeVariant = activeVariantOf(shipLayer);
        boolean variantDirty = isVariantDirty(shipLayer, activeVariant);
        log.debug("Planning single save: variantMode={}, hullDirty={}, variantDirty={}",
                isVariantMode, hullDirty, variantDirty);

        if (isVariantMode && activeVariant != null) {
            return new SavePlan(shipLayer, false, activeVariant);
        }
        if (!hullDirty && variantDirty) {
            return new SavePlan(shipLayer, false, activeVariant);
        }
        return new SavePlan(shipLayer, true, null);
    }

    /**
     * Plan for "Save All Layers": every dirty category of the layer is written.
     * A layer with nothing marked dirty keeps the old fallback of saving its hull.
     */
    private static SavePlan planFullSave(ShipLayer shipLayer) {
        LayerManager layerManager = StaticController.getLayerManager();
        boolean hullDirty = layerManager != null && layerManager.isHullDirty(shipLayer);
        ShipVariant activeVariant = activeVariantOf(shipLayer);
        boolean variantDirty = isVariantDirty(shipLayer, activeVariant);
        return new SavePlan(shipLayer, hullDirty || !variantDirty, variantDirty ? activeVariant : null);
    }

    /**
     * After a single save, tells the user if the other category is still unsaved
     * (its dirty badge on the layer tab is kept).
     */
    private static void announceRemainingUnsaved(ShipLayer shipLayer, SavePlan plan) {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager == null) {
            return;
        }
        ShipVariant activeVariant = activeVariantOf(shipLayer);
        boolean hullDirtyNow = layerManager.isHullDirty(shipLayer);
        boolean variantDirtyNow = isVariantDirty(shipLayer, activeVariant);
        if (plan.writeHull() && !hullDirtyNow && variantDirtyNow) {
            ToastManager.show("Hull saved; variant " + activeVariant.getVariantId() + " still unsaved",
                    ToastManager.ToastLevel.INFO);
        } else if (!plan.writeHull() && !variantDirtyNow && hullDirtyNow) {
            ToastManager.show("Variant " + plan.variant().getVariantId() + " saved; hull still unsaved",
                    ToastManager.ToastLevel.INFO);
        }
    }

    /**
     * Describes the files a plan will write, for the pre-save summary dialog.
     */
    private static List<SaveItem> collectSaveItems(SavePlan plan) {
        List<SaveItem> items = new ArrayList<>();
        ShipLayer shipLayer = plan.layer();
        if (plan.writeHull() && shipLayer.getHull() != null) {
            String hullId = shipLayer.getHull().getHullID();
            Path existing = findExistingHullPath(hullId);
            Path existingDir = existing != null ? existing.getParent() : null;
            String name = existing != null ? existing.getFileName().toString() : hullId + ".ship (new file)";
            items.add(new SaveItem("hull " + name, existingDir, false));
        }
        ShipVariant variantToWrite = plan.variant();
        if (variantToWrite != null) {
            Path existing = findExistingVariantPath(variantToWrite);
            Path existingDir = existing != null ? existing.getParent() : null;
            File defaultDir = FileUtilities.resolveDefaultVariantDirectory(variantToWrite, shipLayer);
            Path targetDir = existingDir != null ? existingDir : (defaultDir != null ? defaultDir.toPath() : null);
            File lastDir = FileUtilities.getLastVariantDirectory();
            boolean newInOtherDir = existing == null && targetDir != null
                    && (lastDir == null || !targetDir.equals(lastDir.toPath()));
            String name = existing != null
                    ? existing.getFileName().toString()
                    : variantToWrite.getVariantId() + ".variant (new file)";
            items.add(new SaveItem("variant " + name, targetDir, newInOtherDir));
        }
        return items;
    }

    /**
     * Formats save items as e.g. "Saving hull x.ship, variant y.variant to data/variants/".
     */
    private static String formatSummary(List<SaveItem> items) {
        boolean sameTarget = !items.isEmpty() && items.get(0).targetDir() != null;
        for (SaveItem item : items) {
            if (!Objects.equals(item.targetDir(), items.get(0).targetDir())) {
                sameTarget = false;
            }
        }

        StringBuilder builder = new StringBuilder();
        if (sameTarget) {
            builder.append("Saving ");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(items.get(i).label());
            }
            builder.append(" to ").append(displayDirectory(items.get(0).targetDir()));
        } else {
            for (SaveItem item : items) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("Saving ").append(item.label());
                if (item.targetDir() != null) {
                    builder.append(" to ").append(displayDirectory(item.targetDir()));
                } else {
                    builder.append(" (location chosen in the next dialog)");
                }
            }
        }
        return builder.toString();
    }

    private static Path findExistingHullPath(String hullId) {
        if (hullId == null) {
            return null;
        }
        ShipSpecFile spec = GameDataRepository.retrieveSpecByID(hullId);
        if (spec instanceof HullSpecFile hullSpec && hullSpec.getFilePath() != null
                && Files.isRegularFile(hullSpec.getFilePath())) {
            return hullSpec.getFilePath();
        }
        return null;
    }

    private static Path findExistingVariantPath(ShipVariant variant) {
        Path own = variant.getVariantFilePath();
        if (own != null && Files.isRegularFile(own)) {
            return own;
        }
        if (variant.getVariantId() != null) {
            VariantFile repoVariant = GameDataRepository.getVariantByID(variant.getVariantId());
            if (repoVariant != null && repoVariant.getVariantFilePath() != null
                    && Files.isRegularFile(repoVariant.getVariantFilePath())) {
                return repoVariant.getVariantFilePath();
            }
        }
        return null;
    }

    /**
     * Formats a directory relative to its "data" folder (e.g. "data/variants/") when possible.
     */
    private static String displayDirectory(Path directory) {
        int count = directory.getNameCount();
        for (int i = count - 1; i >= 0; i--) {
            if ("data".equalsIgnoreCase(directory.getName(i).toString())) {
                return directory.subpath(i, count).toString().replace('\\', '/') + "/";
            }
        }
        return directory + "/";
    }

    private record SaveItem(String label, Path targetDir, boolean newInDifferentDir) {
    }

    private record SavePlan(ShipLayer layer, boolean writeHull, ShipVariant variant) {
    }

    public static void saveActiveHull() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && layerManager.getActiveLayer() instanceof ShipLayer shipLayer) {
            EventBus.publish(new HullSaveQueued(shipLayer));
        } else {
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"),
                    "Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    public static void saveActiveVariant() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && layerManager.getActiveLayer() instanceof ShipLayer shipLayer) {
            var activeVariant = shipLayer.getPainter() != null ? shipLayer.getPainter().getActiveVariant() : null;
            if (activeVariant != null && !activeVariant.isEmpty()) {
                EventBus.publish(new VariantSaveQueued(activeVariant));
            } else {
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"),
                        "Warning",
                        JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"),
                    "Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Writes a copy of the given variant under a new file; the original variant is left untouched.
     */
    public static void saveVariantAsCopy(ShipVariant variant) {
        SaveVariantAction.saveVariantAsCopy(variant);
    }

    public static void saveActiveVariantAsCopy() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && layerManager.getActiveLayer() instanceof ShipLayer shipLayer) {
            var activeVariant = shipLayer.getPainter() != null ? shipLayer.getPainter().getActiveVariant() : null;
            if (activeVariant != null && !activeVariant.isEmpty()) {
                saveVariantAsCopy(activeVariant);
                return;
            }
        }
        JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"),
                "Warning",
                JOptionPane.WARNING_MESSAGE);
    }

    public static void saveActiveLayer() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && layerManager.getActiveLayer() != null) {
            saveLayer(layerManager.getActiveLayer());
        } else {
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(), StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"), "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Saves every dirty category of every layer. Unlike {@link #saveLayer}, this may write both hull and
     * variant of a ship layer, since the user explicitly asked for everything. One summary is shown first.
     */
    public static void saveAllLayers() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager == null) {
            return;
        }
        List<ViewerLayer> layers = new ArrayList<>(layerManager.getLayers());
        List<SavePlan> plans = new ArrayList<>();
        List<SaveItem> items = new ArrayList<>();
        for (ViewerLayer layer : layers) {
            if (layer instanceof ShipLayer shipLayer) {
                SavePlan plan = planFullSave(shipLayer);
                plans.add(plan);
                items.addAll(collectSaveItems(plan));
            }
        }
        log.debug("Save all: {} ship plans, {} files described", plans.size(), items.size());
        if (!items.isEmpty() && !DialogHelper.confirmSaveSummary(formatSummary(items))) {
            log.debug("Save all cancelled at summary dialog");
            return;
        }

        for (ViewerLayer layer : layers) {
            if (layer instanceof ShipLayer shipLayer) {
                for (SavePlan plan : plans) {
                    if (plan.layer() != shipLayer) {
                        continue;
                    }
                    if (plan.writeHull()) {
                        EventBus.publish(new HullSaveQueued(shipLayer));
                    }
                    if (plan.variant() != null) {
                        EventBus.publish(new VariantSaveQueued(plan.variant()));
                    }
                }
            } else if (layer instanceof WeaponLayer weaponLayer) {
                EventBus.publish(new WeaponSaveQueued(weaponLayer));
            } else if (layer instanceof ProjectileLayer projectileLayer) {
                EventBus.publish(new ProjectileSaveQueued(projectileLayer));
            }
        }
    }

}
