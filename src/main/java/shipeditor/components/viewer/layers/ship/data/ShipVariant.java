package shipeditor.components.viewer.layers.ship.data;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.components.viewer.ViewerEnums.FireMode;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.representation.ship.SpecWeaponGroup;
import shipeditor.representation.ship.VariantFile;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.undo.EditDispatch;
import java.nio.file.Path;
import java.util.*;

/** * Heavy-footprint runtime variant class; stores full-fledged painters and point indexes for display in viewer.*/
@Log4j2
@SuppressWarnings({"ClassWithTooManyFields", "ClassWithTooManyMethods", "OverlyCoupledClass", "OverlyComplexClass"})
@Getter @Setter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShipVariant implements Variant {

    public static final String EMPTY_VARIANT = "Empty variant";

    /**
     * Signifies that the instance is a placeholder variant, not a variant without anything installed;
     * Variants that don't have weapons yet but can install them are normal variants.
     */
    private boolean empty;

    private Path variantFilePath;

    private Path containingPackage;

    /**
     * Can be either ID of base hull or skin hull ID.
     */
    private String shipHullId;

    private String variantId;

    private String displayName = "Custom";

    private List<HullmodCSVEntry> hullMods = new ArrayList<>();

    private List<HullmodCSVEntry> permaMods = new ArrayList<>();

    private List<HullmodCSVEntry> sMods = new ArrayList<>();

    private List<WingCSVEntry> wings = new ArrayList<>();

    private List<String> suppressedMods = new ArrayList<>();

    private ListOrderedMap<String, InstalledFeature> fittedModules;

    private List<FittedWeaponGroup> weaponGroups = new ArrayList<>();

    private boolean goalVariant;

    private double quality;

    private int fluxCapacitors;

    private int fluxVents;

    private boolean loadedFromFile;

    public ShipVariant() {
        this(true);
    }

    @SuppressWarnings("BooleanParameter")
    public ShipVariant(boolean isEmpty) {
        this.empty = isEmpty;
        this.fittedModules = new ListOrderedMap<>();
    }

    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    public static String createUniqueVariantID(ShipLayer shipLayer) {
        String result;
        String shipID = shipLayer.getShipID();

        result = shipID + "_custom";

        int counter = 1;
        while (true) {
            Map<String, ShipVariant> loadedVariants = shipLayer.getLoadedVariants();
            if (!loadedVariants.containsKey(result) && GameDataRepository.getVariantByID(result) == null) {
                break;
            }
            result = shipID + "_custom_" + String.format("%02d", counter);
            counter++;
        }

        return result;
    }

    /**
     * Creates an independent deep copy of this variant with a new variant ID and display name.
     */
    public ShipVariant createDeepCopy(String newVariantId, String newDisplayName) {
        log.debug("Creating deep copy of variant '{}' as '{}'", this.variantId, newVariantId);
        ShipVariant copy = new ShipVariant(false);
        copy.setVariantId(newVariantId);
        copy.setDisplayName(newDisplayName);
        copy.setShipHullId(this.shipHullId);
        copy.setContainingPackage(this.containingPackage);
        copy.setVariantFilePath(null);
        copy.setLoadedFromFile(false);
        copy.setFluxCapacitors(this.fluxCapacitors);
        copy.setFluxVents(this.fluxVents);
        copy.setGoalVariant(this.goalVariant);
        copy.setQuality(this.quality);

        if (this.hullMods != null) {
            copy.setHullMods(new ArrayList<>(this.hullMods));
        }
        if (this.permaMods != null) {
            copy.setPermaMods(new ArrayList<>(this.permaMods));
        }
        if (this.sMods != null) {
            copy.setSMods(new ArrayList<>(this.sMods));
        }
        if (this.suppressedMods != null) {
            copy.setSuppressedMods(new ArrayList<>(this.suppressedMods));
        }
        if (this.wings != null) {
            copy.setWings(new ArrayList<>(this.wings));
        }

        List<FittedWeaponGroup> copiedGroups = new ArrayList<>();
        if (this.weaponGroups != null) {
            for (FittedWeaponGroup group : this.weaponGroups) {
                if (group == null) {
                    continue;
                }
                FittedWeaponGroup copiedGroup = new FittedWeaponGroup(copy, group.isAutofire(), group.getMode());
                ListOrderedMap<String, InstalledFeature> groupWeapons = group.getWeapons();
                if (groupWeapons != null) {
                    for (Map.Entry<String, InstalledFeature> entry : groupWeapons.entrySet()) {
                        String slotID = entry.getKey();
                        InstalledFeature origFeature = entry.getValue();
                        if (origFeature == null) {
                            continue;
                        }
                        LayerPainter copiedPainter = origFeature.getFeaturePainter();
                        if (origFeature.getDataEntry() instanceof WeaponCSVEntry weaponEntry) {
                            WeaponSpecFile specFile = weaponEntry.getSpecFile();
                            if (specFile != null) {
                                copiedPainter = weaponEntry.createPainterFromEntry(null, specFile);
                            }
                        }
                        InstalledFeature copiedFeature;
                        if (origFeature.getDataEntry() != null) {
                            copiedFeature = InstalledFeature.of(
                                    slotID, origFeature.getFeatureID(), copiedPainter, origFeature.getDataEntry());
                        } else {
                            copiedFeature = InstalledFeature.createEmpty(slotID);
                        }
                        copiedFeature.setContainedInBuiltIns(origFeature.isContainedInBuiltIns());
                        copiedFeature.setParentGroup(copiedGroup);
                        copiedGroup.getWeapons().put(slotID, copiedFeature);
                    }
                }
                copiedGroups.add(copiedGroup);
            }
        }
        copy.setWeaponGroups(copiedGroups);

        ListOrderedMap<String, InstalledFeature> copiedModules = new ListOrderedMap<>();
        if (this.fittedModules != null) {
            for (Map.Entry<String, InstalledFeature> entry : this.fittedModules.entrySet()) {
                String slotID = entry.getKey();
                InstalledFeature origModule = entry.getValue();
                if (origModule == null) {
                    continue;
                }
                String moduleVariantID = origModule.getFeatureID();
                VariantFile moduleVariantFile = GameDataRepository.getVariantByID(moduleVariantID);
                InstalledFeature copiedModule = null;
                if (moduleVariantFile != null) {
                    copiedModule = shipeditor.components.viewer.layers.LayerFactory.createModuleFromVariant(
                            slotID, moduleVariantFile);
                }
                if (copiedModule == null) {
                    if (origModule.getDataEntry() != null) {
                        copiedModule = InstalledFeature.of(
                                slotID, moduleVariantID, origModule.getFeaturePainter(), origModule.getDataEntry());
                    } else {
                        copiedModule = origModule;
                    }
                }
                copiedModules.put(slotID, copiedModule);
            }
        }
        copy.setFittedModules(copiedModules);

        return copy;
    }

    public List<InstalledFeature> getFittedModulesList() {
        return fittedModules.valueList();
    }

    private void setFittedModules(Map<String, InstalledFeature> modules) {
        this.fittedModules = new ListOrderedMap<>();
        this.fittedModules.putAll(modules);
    }

    public void removeWeaponGroup(FittedWeaponGroup group) {
        EditDispatch.postWeaponGroupRemoved(this, group);
    }

    public void sortModules(Map<String, InstalledFeature> rearranged) {
        var old = this.getFittedModules();
        EditDispatch.postModulesSorted(this::setFittedModules, old, rearranged);
    }

    public String getFileName() {
        if (variantFilePath == null) return StringManager.getString("EMPTY");
        Path fileNamePath = variantFilePath.getFileName();
        return fileNamePath != null ? fileNamePath.toString() : StringManager.getString("EMPTY");
    }

    public void ensureBuiltInsSync(ShipPainter painter) {
        var builtIns = painter.getBuiltInsWithSkin(false, true);

        Map<String, InstalledFeature> fittedWeapons = this.getAllFittedWeapons();
        fittedWeapons.forEach((slotID, feature) -> {
            if (feature.isContainedInBuiltIns() && !builtIns.containsValue(feature)) {
                FittedWeaponGroup parentGroup = feature.getParentGroup();
                ListOrderedMap<String, InstalledFeature> installedWeapons = parentGroup.getWeapons();
                installedWeapons.remove(slotID);
            }
        });

        if (builtIns == null || builtIns.isEmpty()) {
            return;
        }

        builtIns.forEach((slotID, feature) -> {
            FittedWeaponGroup groupWithFit = this.getGroupWithExistingMapping(feature.getSlotID());
            if (groupWithFit != null) {
                var groupWeapons = groupWithFit.getWeapons();
                int index = groupWeapons.indexOf(slotID);
                feature.setParentGroup(groupWithFit);
                groupWeapons.setValue(index, feature);
            } else {
                if (weaponGroups.isEmpty() || weaponGroups.get(0) == null) {
                    FittedWeaponGroup newGroup = new FittedWeaponGroup(this,
                            false, FireMode.LINKED);
                    this.weaponGroups.add(newGroup);
                    feature.setParentGroup(newGroup);
                    var groupWeapons = newGroup.getWeapons();
                    groupWeapons.put(slotID, feature);
                } else {
                    FittedWeaponGroup firstGroup = weaponGroups.get(0);
                    var groupWeapons = firstGroup.getWeapons();
                    feature.setParentGroup(firstGroup);
                    groupWeapons.put(slotID, feature);
                }
            }
        });
    }

    public FittedWeaponGroup getGroupWithExistingMapping(String inputSlotID) {
        FittedWeaponGroup result = null;
        for (FittedWeaponGroup group : weaponGroups) {
            var groupWeapons = group.getWeapons();
            var slotIDs = groupWeapons.keyList();
            for (String checkedSlotID : slotIDs) {
                if (Objects.equals(checkedSlotID, inputSlotID)) {
                    result = group;
                    break;
                }
            }
        }
        return result;
    }

    public InstalledFeature getFittedWeaponBySlot(String inputSlotID) {
        FittedWeaponGroup group = getGroupWithExistingMapping(inputSlotID);
        if (group != null) {
            return group.getWeapons().get(inputSlotID);
        }
        return null;
    }

    public ShipCSVEntry getEntryFromShipID() {
        var baseHullID = GameDataRepository.getBaseHullID(this.shipHullId);
        return GameDataRepository.retrieveShipCSVEntryByID(baseHullID);
    }

    public int getFittedWingsCount() {
        return this.wings.size();
    }

    public int getTotalUsedOP(ShipLayer parentLayer) {
        int result = 0;

        result += getTotalOPInWings();
        result += getTotalOPInWeapons();
        result += getTotalOPInHullmods(parentLayer);

        result += getFluxVents();
        result += getFluxCapacitors();

        return result;
    }

    public int getTotalOPInWeapons() {
        var allWeapons = this.getAllFittedWeapons();
        int totalOP = 0;

        for (InstalledFeature feature : allWeapons.values()) {
            if (feature.isContainedInBuiltIns()) continue;
            totalOP += feature.getOPCost();
        }
        return totalOP;
    }

    /**
     * @param parentLayer needed to determine hull size, on which hullmod costs are dependent.
     */
    @SuppressWarnings("WeakerAccess")
    public int getTotalOPInHullmods(ShipLayer parentLayer) {
        if (parentLayer == null) return -1;
        ShipHull shipHull = parentLayer.getHull();
        if (shipHull == null) return -1;

        HullSize size = shipHull.getHullSize();
        int result = 0;
        for (HullmodCSVEntry entry : hullMods) {
            result += entry.getOrdnanceCost(size);
        }
        return result;
    }

    public int getTotalOPInWings() {
        int totalOPInWings = 0;
        var wingCSVEntries = this.getWings();
        for (WingCSVEntry wingCSVEntry : wingCSVEntries) {
            totalOPInWings += wingCSVEntry.getOrdnanceCost(null);
        }
        return totalOPInWings;
    }

    public Map<String, InstalledFeature> getAllFittedWeapons() {
        Map<String, InstalledFeature> result = new LinkedHashMap<>();
        for (FittedWeaponGroup weaponGroup : weaponGroups) {
            var weaponsInGroup = weaponGroup.getWeapons();
            result.putAll(weaponsInGroup);
        }
        return result;
    }

    public List<InstalledFeature> getAllFittedWeaponsList() {
        List<InstalledFeature> result = new ArrayList<>();
        for (FittedWeaponGroup weaponGroup : weaponGroups) {
            var weaponsInGroup = weaponGroup.getWeapons();
            result.addAll(weaponsInGroup.valueList());
        }
        return result;
    }

    public void setOpacityForAllFitted(float opacity) {
        List<InstalledFeature> fittedWeaponsList = this.getAllFittedWeaponsList();
        fittedWeaponsList.forEach(weapon -> {
            LayerPainter featurePainter = weapon.getFeaturePainter();
            featurePainter.setSpriteOpacity(opacity);
        });
    }

    public void initialize(VariantFile file) {
        this.setVariantId(file.getVariantId());
        this.setShipHullId(file.getHullId());
        this.setVariantFilePath(file.getVariantFilePath());
        this.setContainingPackage(file.getContainingPackage());
        this.setDisplayName(file.getDisplayName());

        this.setFluxVents(file.getFluxVents());
        this.setFluxCapacitors(file.getFluxCapacitors());

        this.setGoalVariant(file.isGoalVariant());
        this.setQuality(file.getQuality());

        loadedFromFile = true;

        weaponGroups = new ArrayList<>();

        List<SpecWeaponGroup> specWeaponGroups = file.getWeaponGroups();
        if (specWeaponGroups != null) {
            for (SpecWeaponGroup weaponGroup : specWeaponGroups) {
             String weaponGroupMode = weaponGroup.getMode();
             FireMode mode;
             if (weaponGroupMode == null) {
                 log.warn("Missing weapon group fire mode in variant file for {}, defaulting to LINKED", file.getVariantId());
                 mode = FireMode.LINKED;
             } else {
                 try {
                     mode = FireMode.valueOf(weaponGroupMode);
                 } catch (IllegalArgumentException e) {
                     log.warn("Invalid weapon group fire mode '{}' in variant file for {}, defaulting to LINKED", weaponGroupMode, file.getVariantId());
                     mode = FireMode.LINKED;
                 }
             }
             FittedWeaponGroup initialized = new FittedWeaponGroup(this, weaponGroup.isAutofire(), mode);
            var fitted = initialized.getWeapons();
            Map<String, String> specGroupWeapons = weaponGroup.getWeapons();
            for (Map.Entry<String, String> entry : specGroupWeapons.entrySet()) {
                String slotID = entry.getKey();
                String weaponID = entry.getValue();
                WeaponCSVEntry weaponEntry = GameDataRepository.getWeaponByID(weaponID);
                if (weaponEntry == null) {
                    log.error("Failed to locate weapon entry for variant, weapon ID: {}", weaponID);
                    continue;
                }
                WeaponSpecFile specFile = weaponEntry.getSpecFile();
                WeaponPainter weaponPainter = weaponEntry.createPainterFromEntry(null, specFile);
                InstalledFeature feature = InstalledFeature.of(slotID, weaponID, weaponPainter, weaponEntry);
                feature.setParentGroup(initialized);
                fitted.put(slotID, feature);
            }
            weaponGroups.add(initialized);
        }
        }

        var installedModules = file.getModules();
        if (installedModules != null) {
            fittedModules = new ListOrderedMap<>();
            installedModules.forEach((slotID, variantID) -> {
                VariantFile variant = GameDataRepository.getVariantByID(variantID);
                if (variant != null) {
                    InstalledFeature moduleFeature = shipeditor.components.viewer.layers.LayerFactory.createModuleFromVariant(slotID, variant);
                    if (moduleFeature != null) {
                        fittedModules.put(slotID, moduleFeature);
                    }
                }
            });
        }

        var normalMods = file.getHullMods();
        if (normalMods != null) {
            this.hullMods = ShipVariant.constructModsList(normalMods);
        }

        var filePermaMods = file.getPermaMods();
        if (filePermaMods != null) {
            this.permaMods = ShipVariant.constructModsList(filePermaMods);
        }

        var fileSMods = file.getSMods();
        if (fileSMods != null) {
            this.sMods = ShipVariant.constructModsList(fileSMods);
        }

        var fileSuppressedMods = file.getSuppressedMods();
        if (fileSuppressedMods != null) {
            this.suppressedMods = new ArrayList<>(fileSuppressedMods);
        }

        var fileWings = file.getWings();
        if (fileWings != null) {
            this.wings = new ArrayList<>();
            fileWings.forEach(wingID -> {
                WingCSVEntry entry = GameDataRepository.retrieveWingCSVEntryByID(wingID);
                if (entry != null) {
                    wings.add(entry);
                } else {
                    log.error("Wing entry not found, skipping in shown variant. ID: {}", wingID);
                }
            });
        }
    }

    private static List<HullmodCSVEntry> constructModsList(Iterable<String> rawList) {
        List<HullmodCSVEntry> result = new ArrayList<>();
        rawList.forEach(hullmodID -> {
            HullmodCSVEntry entry = GameDataRepository.retrieveHullmodCSVEntryByID(hullmodID);
            if (entry != null) {
                result.add(entry);
            } else {
                log.error("Hullmod entry not found, skipping in shown variant. ID: {}", hullmodID);
            }
        });
        return result;
    }

    @Override
    public String toString() {
        if (empty) {
            return EMPTY_VARIANT;
        }
        String suffix = (variantId != null && !variantId.isBlank()) ? " (" + variantId + ")" : "";
        var hullFile = GameDataRepository.retrieveSpecByID(this.getShipHullId());
        if (hullFile != null) {
            String hullName = hullFile.getHullName();
            return this.displayName + " " + hullName + suffix;
        }
        return displayName + suffix;
    }

}
