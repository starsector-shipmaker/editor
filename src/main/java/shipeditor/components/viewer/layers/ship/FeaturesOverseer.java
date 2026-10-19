package shipeditor.components.viewer.layers.ship;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.FeatureInstallQueued;
import shipeditor.components.datafiles.entities.InstallableEntry;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.layers.ship.data.Variant;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.components.viewer.ViewerEnums.FeatureOverrideState;
import shipeditor.components.viewer.ViewerEnums.FireMode;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.representation.ship.VariantFile;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.undo.AbstractEdit;
import shipeditor.undo.Edit;
import shipeditor.undo.EditDispatch;
import shipeditor.undo.UndoOverseer;
import shipeditor.utility.UtilityEnums.EditCategory;
import shipeditor.utility.overseers.StaticController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import shipeditor.communication.events.components.ComponentEvents.DeleteButtonPressed;

/** * Responsible for all non-rendering interactions with installed features of layer,
 * be it base hull or skin built-in, or variant fits.*/
@Log4j2
@SuppressWarnings({"WeakerAccess", "OverlyCoupledClass", "OverlyComplexClass", "ClassWithTooManyMethods"})
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class FeaturesOverseer {

    @Getter
    private WeaponCSVEntry weaponForInstall;

    @Getter
    private VariantFile moduleVariantForInstall;
    private final ShipLayer parent;

    FeaturesOverseer(ShipLayer layer) {
        this.parent = layer;
        this.initInstallListeners();
    }

    public Map<String, InstalledFeature> getBuiltInsFromBaseHull() {
        var painter = parent.getPainter();
        Supplier<Map<String, InstalledFeature>> getter = painter::getBuiltInWeapons;
        Map<String, InstalledFeature> result = getFilteredInstallables(getter, (slotPainter, featureEntry) -> {
            InstalledFeature installedFeature = featureEntry.getValue();
            return installedFeature.isNormalWeapon();
        });
        stampOverrideStates(result);
        return result;
    }

    public Map<String, InstalledFeature> getDecorativesFromBaseHull() {
        var painter = parent.getPainter();
        Supplier<Map<String, InstalledFeature>> getter = painter::getBuiltInWeapons;
        Map<String, InstalledFeature> result = getFilteredInstallables(getter, (slotPainter, featureEntry) -> {
            InstalledFeature installedFeature = featureEntry.getValue();
            return installedFeature.isDecoWeapon();
        });
        stampOverrideStates(result);
        return result;
    }

    /**
     * Eagerly computes and sets the override state on each feature so that
     * the cell renderer can read it directly without reaching into domain objects.
     */
    private void stampOverrideStates(Map<String, InstalledFeature> features) {
        if (features == null) return;
        var painter = parent.getPainter();
        if (painter == null) return;
        var slotPainter = painter.getWeaponSlotPainter();
        if (slotPainter == null) return;

        features.forEach((slotID, feature) -> {
            var slotPoint = slotPainter.getSlotByID(slotID);
            if (slotPoint != null) {
                feature.setOverrideState(getOverrideState(slotPoint));
            } else {
                feature.setOverrideState(FeatureOverrideState.NORMAL);
            }
        });
    }

    public List<String> getBuiltInsRemovedBySkin() {
        ShipPainter painter = parent.getPainter();
        if (painter == null) return null;
        ShipSkin activeSkin = painter.getActiveSkin();

        if (activeSkin != null && !activeSkin.isBase()) {
            return activeSkin.getRemoveBuiltInWeapons();

        }
        return null;
    }

    public Map<String, InstalledFeature> getBuiltInsFromSkin() {
        var painter = parent.getPainter();
        ShipSkin activeSkin = painter.getActiveSkin();

        if (activeSkin != null && !activeSkin.isBase()) {
            Supplier<Map<String, InstalledFeature>> getter = activeSkin::getInitializedBuiltIns;
            return getFilteredInstallables(getter, (slotPainter, featureEntry) -> {
                InstalledFeature installedFeature = featureEntry.getValue();
                return installedFeature.isNormalWeapon();
            });
        }
        return null;
    }

    public Map<String, InstalledFeature> getDecorativesFromSkin() {
        var painter = parent.getPainter();
        ShipSkin activeSkin = painter.getActiveSkin();

        if (activeSkin != null && !activeSkin.isBase()) {
            Supplier<Map<String, InstalledFeature>> getter = activeSkin::getInitializedBuiltIns;
            return getFilteredInstallables(getter, (slotPainter, featureEntry) -> {
                InstalledFeature installedFeature = featureEntry.getValue();
                return installedFeature.isDecoWeapon();
            });
        }
        return null;
    }

    public FeatureOverrideState getOverrideState(SlotData slotPoint) {
        var shipPainter = parent.getPainter();
        var skin = shipPainter.getActiveSkin();
        if (skin == null || skin.isBase()) return FeatureOverrideState.NORMAL;
        
        var removals = this.getBuiltInsRemovedBySkin();
        if (removals != null && removals.contains(slotPoint.getId())) {
            return FeatureOverrideState.REMOVED;
        }
        
        var skinBuiltIns = skin.getInitializedBuiltIns();
        if (skinBuiltIns != null && skinBuiltIns.containsKey(slotPoint.getId())) {
            return FeatureOverrideState.OVERRIDDEN;
        }
        
        return FeatureOverrideState.NORMAL;
    }

    Map<String, InstalledFeature> getFilteredInstallables(
            Supplier<Map<String, InstalledFeature>> getter,
            BiFunction<WeaponSlotPainter, Map.Entry<String, InstalledFeature>, Boolean> filter) {
        var painter = parent.getPainter();
        if (painter == null) return null;
        var installedFeatureMap = getter.get();
        if (installedFeatureMap == null) return null;
        var slotPainter = painter.getWeaponSlotPainter();
        if (slotPainter == null) return null;

        Map<String, InstalledFeature> result = new ListOrderedMap<>();
        Set<Map.Entry<String, InstalledFeature>> entries = installedFeatureMap.entrySet();
        Stream<Map.Entry<String, InstalledFeature>> stream = entries.stream();
        stream.forEach(featureEntry -> {
            if (filter.apply(slotPainter, featureEntry)) {
                result.put(featureEntry.getKey(), featureEntry.getValue());
            }
        });
        return result;
    }

    
    

    private Map<String, InstalledFeature> combineBuiltIns(Map<String, InstalledFeature> first, Map<String, InstalledFeature> second) {
        Map<String, InstalledFeature> combined = new ListOrderedMap<>();
        if (first != null) combined.putAll(first);
        if (second != null) combined.putAll(second);
        return combined;
    }

    private void updateBaseBuiltIns(Map<String, InstalledFeature> first, Map<String, InstalledFeature> second) {
        Map<String, InstalledFeature> combined = combineBuiltIns(first, second);
        ShipPainter shipPainter = parent.getPainter();

        combined.forEach((slotID, feature) -> feature.setContainedInBuiltIns(true));

        var oldCollection = shipPainter.getBuiltInWeapons();
        EditDispatch.postBuiltInFeaturesSorted(shipPainter::setBuiltInWeapons, oldCollection, combined);
    }

    private void updateSkinBuiltIns(Map<String, InstalledFeature> first, Map<String, InstalledFeature> second) {
        Map<String, InstalledFeature> combined = combineBuiltIns(first, second);
        ShipPainter shipPainter = parent.getPainter();

        ShipSkin activeSkin = shipPainter.getActiveSkin();

        var reconstructed = ShipSkin.reconstructAsEntries(combined);

        var oldCollection = activeSkin.getBuiltInWeapons();
        EditDispatch.postBuiltInFeaturesSorted(activeSkin::setBuiltInWeapons, oldCollection, reconstructed);
    }

    public void setBaseBuiltInsWithNewNormal(Map<String, InstalledFeature> rearrangedNormal) {
        updateBaseBuiltIns(rearrangedNormal, this.getDecorativesFromBaseHull());
    }

    public void setSkinBuiltInsWithNewNormal(Map<String, InstalledFeature> rearrangedNormal) {
        updateSkinBuiltIns(rearrangedNormal, this.getDecorativesFromSkin());
    }

    public void setBaseBuiltInsWithNewDecos(Map<String, InstalledFeature> rearrangedDecos) {
        updateBaseBuiltIns(this.getBuiltInsFromBaseHull(), rearrangedDecos);
    }

    public void setSkinBuiltInsWithNewDecos(Map<String, InstalledFeature> rearrangedDecos) {
        updateSkinBuiltIns(this.getBuiltInsFromSkin(), rearrangedDecos);
    }

    public void cleanupListeners() {
        EventBus.unsubscribeByParent(this);
    }

    private void initInstallListeners() {
        BusEventListener selectionListener = event -> {
            if (event instanceof shipeditor.communication.events.components.ComponentEvents.ShipEntryPicked checked) {
                if (StaticController.getActiveLayer() == parent) {
                    this.moduleVariantForInstall = checked.variant();
                    this.weaponForInstall = null;
                }
            } else if (event instanceof shipeditor.communication.events.components.ComponentEvents.WeaponEntryPicked checked) {
                if (StaticController.getActiveLayer() == parent) {
                    this.weaponForInstall = checked.weapon();
                    this.moduleVariantForInstall = null;
                }
            }
        };
        EventBus.subscribe(this, selectionListener);

        BusEventListener installListener = event -> {
            if (event instanceof FeatureInstallQueued) {
                if (StaticController.getActiveLayer() != parent) return;
                if (moduleVariantForInstall != null) {
                    addModuleToSelectedSlot(moduleVariantForInstall);
                }
                if (weaponForInstall != null) {
                    addWeaponToSelectedSlot(weaponForInstall);
                }
            }
        };
        EventBus.subscribe(this, installListener);

        BusEventListener uninstallListener = event -> {
            if (event instanceof DeleteButtonPressed) {
                if (StaticController.getActiveLayer() != parent) return;
                var shipPainter = parent.getPainter();
                if (shipPainter == null || shipPainter.isUninitialized()) return;
                var slotPainter = shipPainter.getWeaponSlotPainter();

                var mode = StaticController.getEditorMode();
                WeaponSlotPoint selected = slotPainter.getSelected();
                var eligibleSlots = slotPainter.getEligibleForSelection();

                if (selected == null || !eligibleSlots.contains(selected)) return;

                FeaturesOverseer.handleFeatureRemoval(selected, mode, shipPainter);
            }
        };
        EventBus.subscribe(this, uninstallListener);
    }

    public void addModuleToSelectedSlot(VariantFile moduleVariant) {
        chooseWeaponPointAndInstall((editorInstrument, slotPoint) -> {
            boolean choseVariant = moduleVariant != null && !moduleVariant.isEmpty();
            if (editorInstrument == EditorInstrument.VARIANT_MODULES && choseVariant) {
                if (slotPoint.isModule()) {
                    installModule(slotPoint, moduleVariant);
                }
            }
        });
    }

    public void addWeaponToSelectedSlot(WeaponCSVEntry weaponEntry) {
        chooseWeaponPointAndInstall((editorInstrument, slotPoint) -> {
            if (!WeaponType.isWeaponFitting(slotPoint, weaponEntry)) return;
            switch (editorInstrument) {
                case VARIANT_WEAPONS -> {
                    if (slotPoint.isFittable()) {
                        installWeapon(slotPoint, weaponEntry);
                    }
                }
                case WEAPON_SLOTS -> {
                    if (slotPoint.isBuiltIn()) {
                        installBuiltIn(slotPoint, weaponEntry);
                    }
                }
                default -> {}
            }
        });
    }

    private void chooseWeaponPointAndInstall(BiConsumer<EditorInstrument,
            WeaponSlotPoint> installAction) {
        var shipPainter = parent.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) return;
        var slotPainter = shipPainter.getWeaponSlotPainter();

        WeaponSlotPoint selected = slotPainter.getSelected();
        var eligibleSlots = slotPainter.getEligibleForSelection();

        if (selected == null || !eligibleSlots.contains(selected)) return;
        var mode = StaticController.getEditorMode();

        installAction.accept(mode, selected);
    }

    private static WeaponSlotPoint getMirroredSlot(SlotData slot, ShipPainter shipPainter) {
        if (!ControlPredicates.isMirrorModeEnabled() || shipPainter == null
                || !(slot instanceof WeaponSlotPoint slotPoint)) {
            return null;
        }
        WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
        if (slotPainter == null) {
            return null;
        }
        var counterpart = slotPainter.getMirroredCounterpart(slotPoint);
        if (counterpart instanceof WeaponSlotPoint mirroredSlot && mirroredSlot != slotPoint) {
            return mirroredSlot;
        }
        return null;
    }

    @SuppressWarnings({"MethodWithMultipleReturnPoints", "OverlyComplexMethod"})
    private static void handleFeatureRemoval(SlotData selected, EditorInstrument mode,
                                             ShipPainter shipPainter) {
        removeFeatureFromSingleSlot(selected, mode, shipPainter);
        WeaponSlotPoint mirrored = getMirroredSlot(selected, shipPainter);
        if (mirrored != null) {
            removeFeatureFromSingleSlot(mirrored, mode, shipPainter);
        }
    }

    @SuppressWarnings({"MethodWithMultipleReturnPoints", "OverlyComplexMethod"})
    private static void removeFeatureFromSingleSlot(SlotData selected, EditorInstrument mode,
                                                    ShipPainter shipPainter) {
        String slotID = selected.getId();
        switch (mode) {
            case VARIANT_WEAPONS -> {
                var variant = shipPainter.getActiveVariant();
                if (variant == null || variant.isEmpty()) return;
                FittedWeaponGroup targetGroup = variant.getGroupWithExistingMapping(slotID);

                if (targetGroup != null) {
                    targetGroup.removeBySlotID(slotID);
                }
            }
            case VARIANT_MODULES -> {
                var variant = shipPainter.getActiveVariant();
                if (variant == null || variant.isEmpty()) return;
                var modules = variant.getFittedModules();
                InstalledFeature toRemove = modules.get(slotID);
                if (toRemove == null) return;
                EditDispatch.postFeatureUninstalled(modules, slotID,
                        toRemove, null);
            }
            default -> {}
        }
    }

    public void uninstallWeapon(SlotData selected) {
        var shipPainter = parent.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) return;
        FeaturesOverseer.handleFeatureRemoval(selected, EditorInstrument.VARIANT_WEAPONS, shipPainter);
    }

    /**
     * Delegates to {@link StaticController#getOrCreateEditableVariant(ShipLayer)}, the single source of truth
     * for auto-creating a custom variant on first install.
     */
    private ShipVariant ensureEditableVariant(ShipPainter shipPainter) {
        if (shipPainter == null || shipPainter.isUninitialized()) {
            return null;
        }
        return StaticController.getOrCreateEditableVariant(parent);
    }

    /**
     * Installs the weapon on every given slot that is fittable and compatible with it,
     * replacing whatever is currently fitted there. Incompatible or non-fittable slots are skipped.
     * Where the undo system allows, all resulting edits are collapsed into one undo step.
     *
     * @return the number of slots the weapon was installed on
     */
    public int installWeaponOnSlots(Collection<? extends SlotData> slots, WeaponCSVEntry weapon) {
        if (slots == null || slots.isEmpty() || weapon == null) return 0;
        try {
            var shipPainter = parent.getPainter();
            ShipVariant variant = ensureEditableVariant(shipPainter);
            if (variant == null || variant.isEmpty()) return 0;
            if (weapon.getSpecFile() == null) {
                log.warn("Cannot install weapon {} on slots: no weapon spec file loaded", weapon.getWeaponID());
                return 0;
            }

            Set<SlotData> expandedSlots = new LinkedHashSet<>();
            for (SlotData slot : slots) {
                if (slot != null) {
                    expandedSlots.add(slot);
                    WeaponSlotPoint mirrored = getMirroredSlot(slot, shipPainter);
                    if (mirrored != null) {
                        expandedSlots.add(mirrored);
                    }
                }
            }

            List<SlotData> eligible = new ArrayList<>();
            for (SlotData slot : expandedSlots) {
                if (slot instanceof WeaponSlotPoint fittableSlot && fittableSlot.isFittable()
                        && WeaponType.isWeaponFitting(slot, weapon)) {
                    eligible.add(slot);
                } else {
                    log.debug("Skipping slot {} for weapon {}: not fittable or incompatible",
                            slot.getId(), weapon.getWeaponID());
                }
            }
            if (eligible.isEmpty()) return 0;

            log.debug("Installing weapon {} on {} slot(s)", weapon.getWeaponID(), eligible.size());
            String editName = "Install " + weapon + " on " + eligible.size() + " slot(s)";
            return runAsSingleUndoStep(editName, variant, () -> {
                int installed = 0;
                for (SlotData slot : eligible) {
                    installWeaponSingle(slot, weapon, variant);
                    installed++;
                }
                return installed;
            });
        } catch (RuntimeException e) {
            log.error("Failed to install weapon {} on slots", weapon.getWeaponID(), e);
            return 0;
        }
    }

    /**
     * Installs the weapon on every currently empty, fittable slot that is compatible with it.
     *
     * @return the number of slots the weapon was installed on
     */
    public int installWeaponOnMatchingSlots(WeaponCSVEntry weapon) {
        if (weapon == null) return 0;
        var shipPainter = parent.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) return 0;
        var slotPainter = shipPainter.getWeaponSlotPainter();
        if (slotPainter == null) return 0;

        ShipVariant variant = shipPainter.getActiveVariant();
        List<SlotData> matching = new ArrayList<>();
        for (WeaponSlotPoint slot : slotPainter.getSlotPoints()) {
            if (slot.isFittable() && WeaponType.isWeaponFitting(slot, weapon) && isSlotEmpty(slot, variant)) {
                matching.add(slot);
            }
        }
        log.debug("Found {} empty slot(s) matching weapon {}", matching.size(), weapon.getWeaponID());
        return installWeaponOnSlots(matching, weapon);
    }

    private static boolean isSlotEmpty(SlotData slot, ShipVariant variant) {
        if (variant == null || variant.isEmpty()) return true;
        FittedWeaponGroup group = variant.getGroupWithExistingMapping(slot.getId());
        return group == null || group.getWeapons() == null || group.getWeapons().get(slot.getId()) == null;
    }

    /**
     * Runs the action and merges all edits it posted to the undo stack into a single compound edit,
     * so that one undo reverts the whole batch.
     */
    private int runAsSingleUndoStep(String editName, ShipVariant variant, IntSupplier action) {
        Deque<Edit> undoStack = UndoOverseer.getUndoStack();
        Edit marker = undoStack.peekFirst();

        int result = action.getAsInt();

        if (marker != null && !undoStack.contains(marker)) {
            log.debug("Undo stack was trimmed during batch install, leaving edits ungrouped");
            return result;
        }
        List<Edit> posted = new ArrayList<>();
        while (!undoStack.isEmpty() && undoStack.peekFirst() != marker) {
            posted.add(undoStack.pollFirst());
        }
        if (posted.size() < 2) {
            // Nothing to group; restore as they were (oldest first so the newest ends up on top).
            for (int i = posted.size() - 1; i >= 0; i--) {
                undoStack.addFirst(posted.get(i));
            }
            return result;
        }
        log.debug("Grouping {} edits into compound edit '{}'", posted.size(), editName);
        CompoundInstallEdit compound = new CompoundInstallEdit(editName, parent.getPainter());
        compound.setTargetEntity(variant);
        for (int i = posted.size() - 1; i >= 0; i--) {
            compound.add(posted.get(i));
        }
        UndoOverseer.post(compound);
        return result;
    }

    /**
     * Collapses the edits of a batch install into one undo/redo step. Sub-edits are undone newest first
     * and redone oldest first, as maintained by {@link AbstractEdit}.
     */
    private static final class CompoundInstallEdit extends AbstractEdit {
        private final String name;
        private final ShipPainter painter;

        private CompoundInstallEdit(String name, ShipPainter painter) {
            this.name = name;
            this.painter = painter;
        }

        @Override
        public void undo() {
            undoSubEdits();
        }

        @Override
        public void redo() {
            redoSubEdits();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public EditCategory getCategory() {
            return EditCategory.VARIANT;
        }

        @Override
        public LayerPainter getLayerPainter() {
            return painter;
        }
    }

    public void installWeapon(SlotData selected, WeaponCSVEntry forInstall) {
        if (selected == null || forInstall == null) return;
        if (selected instanceof WeaponSlotPoint sp && !sp.isFittable()) return;
        if (!WeaponType.isWeaponFitting(selected, forInstall)) return;
        var shipPainter = parent.getPainter();
        var activeVariant = ensureEditableVariant(shipPainter);
        if (activeVariant == null || activeVariant.isEmpty()) return;
        if (forInstall.getSpecFile() == null) return;

        WeaponSlotPoint mirrored = getMirroredSlot(selected, shipPainter);
        if (mirrored != null && mirrored.isFittable() && WeaponType.isWeaponFitting(mirrored, forInstall)) {
            String editName = "Install " + forInstall + " on mirrored slots";
            runAsSingleUndoStep(editName, activeVariant, () -> {
                installWeaponSingle(selected, forInstall, activeVariant);
                installWeaponSingle(mirrored, forInstall, activeVariant);
                return 2;
            });
        } else {
            installWeaponSingle(selected, forInstall, activeVariant);
        }
    }

    private static void installWeaponSingle(SlotData selected, WeaponCSVEntry forInstall, ShipVariant activeVariant) {
        WeaponSpecFile specFile = forInstall.getSpecFile();
        if (specFile == null) {
            return;
        }

        String slotID = selected.getId();
        List<FittedWeaponGroup> weaponGroups = activeVariant.getWeaponGroups();

        WeaponPainter weaponPainter = forInstall.createPainterFromEntry(null, specFile);
        InstalledFeature feature = InstalledFeature.of(slotID, forInstall.getWeaponID(),
                weaponPainter, forInstall);

        FittedWeaponGroup targetGroup = activeVariant.getGroupWithExistingMapping(slotID);
        Map<String, InstalledFeature> groupWeapons;
        if (targetGroup != null) {
            groupWeapons = targetGroup.getWeapons();
            InstalledFeature existing = groupWeapons.get(slotID);
            EditDispatch.postFeatureUninstalled(groupWeapons, slotID, existing, null);
        } else {
            if (weaponGroups.isEmpty()) {
                FittedWeaponGroup newGroup = new FittedWeaponGroup(activeVariant,
                        false, FireMode.ALTERNATING);
                weaponGroups.add(newGroup);
                targetGroup = newGroup;
            } else {
                targetGroup = weaponGroups.get(0);
            }
        }

        if (targetGroup == null) {
            targetGroup = new FittedWeaponGroup(activeVariant,
                    false, FireMode.LINKED);
            weaponGroups.add(targetGroup);
        }
        groupWeapons = targetGroup.getWeapons();
        feature.setParentGroup(targetGroup);
        FeaturesOverseer.commenceInstall(slotID, feature, groupWeapons, null);
    }

    private void installModule(SlotData selected, Variant variantFile) {
        var shipPainter = parent.getPainter();
        var activeVariant = ensureEditableVariant(shipPainter);
        if (activeVariant == null || activeVariant.isEmpty()) return;

        installModuleSingle(selected, variantFile, activeVariant);
        WeaponSlotPoint mirrored = getMirroredSlot(selected, shipPainter);
        if (mirrored != null && mirrored.isModule()) {
            installModuleSingle(mirrored, variantFile, activeVariant);
        }
    }

    private static void installModuleSingle(SlotData selected, Variant variantFile, ShipVariant activeVariant) {
        String slotID = selected.getId();
        var modules = activeVariant.getFittedModules();

        InstalledFeature moduleFeature = shipeditor.components.viewer.layers.LayerFactory.createModuleFromVariant(slotID, variantFile);

        InstalledFeature existing = modules.get(slotID);
        if (existing != null) {
            EditDispatch.postFeatureUninstalled(modules, slotID, existing, null);
        }

        FeaturesOverseer.commenceInstall(slotID, moduleFeature, modules, null);
    }

    public void uninstallBuiltIn(SlotData selected) {
        var shipPainter = parent.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) return;
        uninstallBuiltInSingle(selected, shipPainter);
        WeaponSlotPoint mirrored = getMirroredSlot(selected, shipPainter);
        if (mirrored != null) {
            uninstallBuiltInSingle(mirrored, shipPainter);
        }
    }

    private static void uninstallBuiltInSingle(SlotData selected, ShipPainter shipPainter) {
        String slotID = selected.getId();
        ShipSkin activeSkin = shipPainter.getActiveSkin();
        if (activeSkin != null && !activeSkin.isBase()) {
            var addedBySkin = activeSkin.getBuiltInWeapons();
            WeaponCSVEntry toRemove = addedBySkin.get(slotID);
            if (toRemove != null) {
                EditDispatch.postFeatureUninstalled(addedBySkin, slotID,
                        toRemove, activeSkin::invalidateBuiltIns);
            }
        } else {
            var weapons = shipPainter.getBuiltInWeapons();
            InstalledFeature toRemove = weapons.get(slotID);
            if (toRemove != null) {
                EditDispatch.postFeatureUninstalled(weapons, slotID,
                        toRemove, null);
            }
        }
    }

    public void installBuiltIn(SlotData selected, WeaponCSVEntry forInstall) {
        if (selected == null || forInstall == null) return;
        var shipPainter = parent.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized()) return;

        installBuiltInSingle(selected, forInstall, shipPainter);
        WeaponSlotPoint mirrored = getMirroredSlot(selected, shipPainter);
        if (mirrored != null && mirrored.isBuiltIn() && WeaponType.isWeaponFitting(mirrored, forInstall)) {
            installBuiltInSingle(mirrored, forInstall, shipPainter);
        }
    }

    private static void installBuiltInSingle(SlotData selected, WeaponCSVEntry forInstall, ShipPainter shipPainter) {
        var activeSkin = shipPainter.getActiveSkin();
        String slotID = selected.getId();

        ShipVariant activeVariant = shipPainter.getActiveVariant();

        if (activeSkin != null && !activeSkin.isBase()) {
            var skinBuiltIns = activeSkin.getBuiltInWeapons();

            Runnable invalidator = activeSkin::invalidateBuiltIns;
            FeaturesOverseer.removeExistingBeforeInstall(skinBuiltIns, activeVariant, slotID, invalidator);

            FeaturesOverseer.commenceInstall(slotID, forInstall, skinBuiltIns,
                    invalidator);
        } else {
            var baseBuiltIns = shipPainter.getBuiltInWeapons();

            FeaturesOverseer.removeExistingBeforeInstall(baseBuiltIns, activeVariant, slotID, null);

            WeaponSpecFile specFile = forInstall.getSpecFile();
            WeaponPainter weaponPainter = forInstall.createPainterFromEntry(null, specFile);
            InstalledFeature feature = InstalledFeature.of(slotID, forInstall.getWeaponID(),
                    weaponPainter, forInstall);
            feature.setContainedInBuiltIns(true);

            FeaturesOverseer.commenceInstall(slotID, feature, baseBuiltIns, null);
        }
    }

    private static <T extends InstallableEntry> void removeExistingBeforeInstall(Map<String, T> collection,
                                                                                 ShipVariant activeVariant,
                                                                                 String slotID, Runnable builtInInvalidator) {
        T existingBuiltIn = collection.get(slotID);
        if (existingBuiltIn != null) {
            EditDispatch.postFeatureUninstalled(collection, slotID, existingBuiltIn, builtInInvalidator);
        } else if (activeVariant != null) {
            FittedWeaponGroup targetGroup = activeVariant.getGroupWithExistingMapping(slotID);
            Map<String, InstalledFeature> groupWeapons;
            if (targetGroup != null) {
                groupWeapons = targetGroup.getWeapons();
                InstalledFeature existing = groupWeapons.get(slotID);
                EditDispatch.postFeatureUninstalled(groupWeapons, slotID, existing, null);
            }
        }
    }

    private static <T extends InstallableEntry> void commenceInstall(String slotID, T entry,
                                                                     Map<String, T> collection,
                                                                     Runnable invalidator) {
        EditDispatch.postFeatureInstalled(collection, slotID, entry, invalidator);
    }

}
