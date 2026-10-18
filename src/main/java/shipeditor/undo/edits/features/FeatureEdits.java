package shipeditor.undo.edits.features;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.components.datafiles.entities.InstallableEntry;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.AbstractEdit;
import shipeditor.utility.overseers.StaticController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class FeatureEdits {

    private FeatureEdits() {
    }

    @Log4j2
    @AllArgsConstructor
    public static class FeatureInstallEdit<T extends InstallableEntry> extends AbstractEdit {
        private final Map<String, T> collection;
        private final String slotID;
        private final T feature;
        /**
         * Can be null, needed for skin built-ins reloading.
         */
        private final Runnable afterAction;

        @Override
        public void undo() {
            collection.remove(slotID, feature);
            if (afterAction != null) {
                afterAction.run();
            }
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            if (feature instanceof InstalledFeature installed && installed.getDataEntry() instanceof ShipCSVEntry) {
                repainter.queueModulesRepaint();
            } else {
                repainter.queueBuiltInsRepaint();
                repainter.queueVariantsRepaint();
            }
        }

        @Override
        public void redo() {
            collection.put(slotID, feature);
            if (afterAction != null) {
                afterAction.run();
            }
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            if (feature instanceof InstalledFeature installed && installed.getDataEntry() instanceof ShipCSVEntry) {
                repainter.queueModulesRepaint();
            } else {
                repainter.queueBuiltInsRepaint();
                repainter.queueVariantsRepaint();
            }
        }

        @Override
        public String getName() {
            return "Install Feature";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            if (afterAction != null) {
                return shipeditor.utility.UtilityEnums.EditCategory.SKIN;
            }
            if (feature instanceof InstalledFeature installed && installed.isContainedInBuiltIns()) {
                return shipeditor.utility.UtilityEnums.EditCategory.HULL;
            }
            return resolveFeatureCategoryFromMode();
        }
    }

    public static class FeatureSortEdit<T extends InstallableEntry> extends AbstractEdit {
        /**
         * Is expected to also provide invalidation in case collection belongs to skin.
         */
        private final Consumer<Map<String, T>> setter;
        private final Map<String, T> oldCollection;
        private final Map<String, T> newCollection;
        private final shipeditor.utility.UtilityEnums.EditCategory category;

        public FeatureSortEdit(Consumer<Map<String, T>> consumer, Map<String, T> oldMap, Map<String, T> newMap) {
            this.setter = consumer;
            this.oldCollection = oldMap;
            this.newCollection = newMap;
            this.category = resolveSortCategory(oldMap, newMap);
        }

        private static <T extends InstallableEntry> shipeditor.utility.UtilityEnums.EditCategory resolveSortCategory(
                Map<String, T> oldMap, Map<String, T> newMap) {
            Map<String, T> sampleMap = (newMap != null && !newMap.isEmpty()) ? newMap : oldMap;
            if (sampleMap != null && !sampleMap.isEmpty()) {
                T first = sampleMap.values().iterator().next();
                if (first instanceof InstalledFeature installed && installed.isContainedInBuiltIns()) {
                    return shipeditor.utility.UtilityEnums.EditCategory.HULL;
                }
                if (!(first instanceof InstalledFeature)) {
                    return shipeditor.utility.UtilityEnums.EditCategory.SKIN;
                }
            }
            EditorInstrument mode = StaticController.getEditorMode();
            if (mode == EditorInstrument.SKIN_SLOTS || mode == EditorInstrument.SKIN_DATA
                    || mode == EditorInstrument.SKIN_ENGINES || mode == EditorInstrument.SKIN_REMOVALS) {
                return shipeditor.utility.UtilityEnums.EditCategory.SKIN;
            }
            return shipeditor.utility.UtilityEnums.EditCategory.HULL;
        }

        @Override
        public void undo() {
            setter.accept(oldCollection);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueBuiltInsRepaint();
        }

        @Override
        public void redo() {
            setter.accept(newCollection);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueBuiltInsRepaint();
        }

        @Override
        public String getName() {
            return "Sort Features";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return category;
        }
    }

    @Log4j2
    @AllArgsConstructor
    public static class FeatureUninstallEdit<T extends InstallableEntry> extends AbstractEdit {
        private final Map<String, T> collectionBefore;
        private final Map<String, T> collectionAfter;
        private final Map<String, T> collection;
        /**
         * Can be null, needed for skin built-ins reloading.
         */
        private final Runnable invalidator;
        private final boolean isModule;

        @Override
        public void undo() {
            collection.clear();
            collection.putAll(collectionBefore);
            if (invalidator != null) {
                invalidator.run();
            }
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            if (isModule) {
                repainter.queueModulesRepaint();
            } else {
                repainter.queueBuiltInsRepaint();
                repainter.queueVariantsRepaint();
            }
        }

        @Override
        public void redo() {
            collection.clear();
            collection.putAll(collectionAfter);
            if (invalidator != null) {
                invalidator.run();
            }
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            if (isModule) {
                repainter.queueModulesRepaint();
            } else {
                repainter.queueBuiltInsRepaint();
                repainter.queueVariantsRepaint();
            }
        }

        @Override
        public String getName() {
            return "Uninstall Feature";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            if (invalidator != null) {
                return shipeditor.utility.UtilityEnums.EditCategory.SKIN;
            }
            if (collectionBefore != null) {
                for (T val : collectionBefore.values()) {
                    if (val instanceof InstalledFeature installed && installed.isContainedInBuiltIns()) {
                        return shipeditor.utility.UtilityEnums.EditCategory.HULL;
                    }
                }
            }
            return resolveFeatureCategoryFromMode();
        }
    }

    private static shipeditor.utility.UtilityEnums.EditCategory resolveFeatureCategoryFromMode() {
        EditorInstrument mode = StaticController.getEditorMode();
        if (mode == null) {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
        return switch (mode) {
            case WEAPON_SLOTS, BUILT_IN_MODS, BUILT_IN_WINGS, LAYER, COLLISION, SHIELD, BOUNDS, LAUNCH_BAYS, ENGINES ->
                    shipeditor.utility.UtilityEnums.EditCategory.HULL;
            case SKIN_DATA, SKIN_SLOTS, SKIN_ENGINES, SKIN_REMOVALS ->
                    shipeditor.utility.UtilityEnums.EditCategory.SKIN;
            default -> shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        };
    }

    public static class ModulesSortEdit extends AbstractEdit {
        private final Consumer<Map<String, InstalledFeature>> setter;
        private final Map<String, InstalledFeature> oldCollection;
        private final Map<String, InstalledFeature> newCollection;

        public ModulesSortEdit(Consumer<Map<String, InstalledFeature>> consumer,
                               Map<String, InstalledFeature> oldMap,
                               Map<String, InstalledFeature> newMap) {
            this.setter = consumer;
            this.oldCollection = oldMap;
            this.newCollection = newMap;
        }

        @Override
        public void undo() {
            setter.accept(oldCollection);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueModulesRepaint();
        }

        @Override
        public void redo() {
            setter.accept(newCollection);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueModulesRepaint();
        }

        @Override
        public String getName() {
            return "Sort Modules";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
    }

    public static class SuppressedModsEdit extends AbstractEdit {
        private final ShipVariant variant;
        private final ViewerLayer layer;
        private final List<String> oldMods;
        private final List<String> newMods;

        public SuppressedModsEdit(ShipVariant variant, ViewerLayer layer, List<String> oldMods, List<String> newMods) {
            this.variant = variant;
            this.layer = layer;
            this.oldMods = oldMods;
            this.newMods = newMods;
        }

        @Override
        public void undo() {
            undoSubEdits();
            variant.setSuppressedMods(oldMods);
            EventBus.publish(new ActiveLayerUpdated(layer));
        }

        @Override
        public void redo() {
            variant.setSuppressedMods(newMods);
            EventBus.publish(new ActiveLayerUpdated(layer));
            redoSubEdits();
        }

        @Override
        public String getName() {
            return "Suppressed Mods Change";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
    }

    public static class WeaponGroupRemovalEdit extends AbstractEdit {
        private final List<FittedWeaponGroup> weaponGroups;
        private final int groupIndex;
        private final FittedWeaponGroup toRemove;

        @SuppressWarnings("ParameterHidesMemberVariable")
        public WeaponGroupRemovalEdit(List<FittedWeaponGroup> weaponGroups, int groupIndex, FittedWeaponGroup toRemove) {
            this.weaponGroups = weaponGroups;
            this.groupIndex = groupIndex;
            this.toRemove = toRemove;
        }

        @Override
        public void undo() {
            weaponGroups.add(groupIndex, toRemove);
            if (StaticController.getEditorMode() == EditorInstrument.VARIANT_WEAPONS) {
                var repainter = StaticController.getScheduler();
                repainter.queueViewerRepaint();
                repainter.queueVariantWeaponsRepaint();
            }
        }

        @Override
        public void redo() {
            weaponGroups.remove(toRemove);
            if (StaticController.getEditorMode() == EditorInstrument.VARIANT_WEAPONS) {
                var repainter = StaticController.getScheduler();
                repainter.queueViewerRepaint();
                repainter.queueVariantWeaponsRepaint();
            }
        }

        @Override
        public String getName() {
            return "Remove Weapon Group";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
    }

    @RequiredArgsConstructor
    public static class WeaponGroupsSortEdit extends AbstractEdit {
        private final InstalledFeature feature;
        private final FittedWeaponGroup targetGroup;
        private final FittedWeaponGroup oldParentGroup;
        private final int targetIndex;
        private final int oldIndex;
        private int cachedOldGroupIndex;
        private int cachedNewGroupIndex;
        private final boolean removeEmptyGroups;

        @Override
        public void undo() {
            this.transferFeature(targetGroup, oldParentGroup, oldIndex,
                    integer -> cachedNewGroupIndex = integer,
                    () -> cachedOldGroupIndex);
        }

        @Override
        public void redo() {
            this.transferFeature(oldParentGroup, targetGroup, targetIndex,
                    integer -> cachedOldGroupIndex = integer,
                    () -> cachedNewGroupIndex);
        }

        private void transferFeature(FittedWeaponGroup supplier, FittedWeaponGroup recipient, int index,
                                     Consumer<Integer> cachedIndexSetter, Supplier<Integer> cachedGroupIndex) {
            var oldParentWeapons = supplier.getWeapons();
            oldParentWeapons.remove(feature.getSlotID());

            if (oldParentWeapons.isEmpty() && removeEmptyGroups) {
                var variant = supplier.getParent();
                List<FittedWeaponGroup> groupList = variant.getWeaponGroups();
                cachedIndexSetter.accept(groupList.indexOf(supplier));
                groupList.remove(supplier);
            }

            feature.setParentGroup(recipient);
            var weapons = recipient.getWeapons();
            if (index != -1) {
                weapons.put(index, feature.getSlotID(), feature);

                var variant = recipient.getParent();
                List<FittedWeaponGroup> groupList = variant.getWeaponGroups();
                if (!groupList.contains(recipient)) {
                    groupList.add(cachedGroupIndex.get(), recipient);
                }
            }

            if (StaticController.getEditorMode() == EditorInstrument.VARIANT_WEAPONS) {
                var repainter = StaticController.getScheduler();
                repainter.queueViewerRepaint();
                repainter.queueVariantWeaponsRepaint();
            }
        }

        @Override
        public String getName() {
            return "Sort Weapon Groups";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
    }

    public static class WeaponGroupsUpdateEdit extends AbstractEdit {
        private final ShipVariant variant;
        private final List<FittedWeaponGroup> oldGroups;
        private final List<FittedWeaponGroup> newGroups;

        public WeaponGroupsUpdateEdit(ShipVariant variant, List<FittedWeaponGroup> oldGroups, List<FittedWeaponGroup> newGroups) {
            this.variant = variant;
            this.oldGroups = new ArrayList<>(oldGroups);
            this.newGroups = new ArrayList<>(newGroups);
        }

        @Override
        public void undo() {
            applyGroups(oldGroups);
            refreshUI();
        }

        @Override
        public void redo() {
            applyGroups(newGroups);
            refreshUI();
        }

        private void applyGroups(List<FittedWeaponGroup> groups) {
            variant.setWeaponGroups(groups);
            for (FittedWeaponGroup group : groups) {
                for (var feature : group.getWeapons().values()) {
                    feature.setParentGroup(group);
                }
            }
        }

        private void refreshUI() {
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            if (StaticController.getEditorMode() == EditorInstrument.VARIANT_WEAPONS) {
                repainter.queueVariantWeaponsRepaint();
            }
        }

        @Override
        public String getName() {
            return "Configure Weapon Groups";
        }

        @Override
        public shipeditor.utility.UtilityEnums.EditCategory getCategory() {
            return shipeditor.utility.UtilityEnums.EditCategory.VARIANT;
        }
    }
}
