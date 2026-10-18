package shipeditor.components.viewer.painters.points.ship;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerFactory;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringManager;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public final class WeaponSlotContextMenu {

    private WeaponSlotContextMenu() {
    }

    public static JPopupMenu createModuleContextMenu(WeaponSlotPoint slotPoint, ShipVariant activeVariant) {
        JPopupMenu menu = new JPopupMenu();

        Map<String, InstalledFeature> fittedModules = activeVariant != null ? activeVariant.getFittedModules() : null;

        InstalledFeature installed = null;
        if (fittedModules != null) {
            installed = fittedModules.get(slotPoint.getId());
        }

        if (installed != null) {
            JMenuItem infoItem = new JMenuItem(StringManager.getString("INSTALLED") + installed.getName());
            infoItem.setEnabled(false);
            menu.add(infoItem);

            JMenuItem editItem = new JMenuItem("Edit Module in New Tab");
            final InstalledFeature moduleToEdit = installed;
            editItem.addActionListener(e -> {
                if (moduleToEdit.getFeaturePainter() instanceof ShipPainter shipPainter) {
                    ShipVariant variant = shipPainter.getActiveVariant();
                    if (variant != null) {
                        ShipLayer newLayer = LayerFactory.createLayerFromVariant(variant);
                        if (newLayer != null) {
                            if (variant.getVariantId() != null) {
                                newLayer.getLoadedVariants().put(variant.getVariantId(), variant);
                            }
                            StaticController.getViewer().getLayerManager().setActiveLayer(newLayer);
                        }
                    }
                }
            });
            menu.add(editItem);

            JMenuItem forkItem = new JMenuItem("Fork Module Variant");
            forkItem.addActionListener(e -> {
                if (moduleToEdit.getFeaturePainter() instanceof ShipPainter modulePainter) {
                    ShipVariant original = modulePainter.getActiveVariant();
                    if (original != null) {
                        forkModuleVariant(modulePainter, original);
                    }
                }
            });
            menu.add(forkItem);

            JMenuItem clearItem = new JMenuItem(StringManager.getString("CLEAR_MODULE"));
            InstalledFeature toRemove = installed;
            clearItem.addActionListener(e -> EditDispatch.postFeatureUninstalled(fittedModules, slotPoint.getId(), toRemove, null));
            menu.add(clearItem);
        } else {
            JMenuItem emptyItem = new JMenuItem(StringManager.getString("SLOT") + slotPoint.getId() + " (Empty)");
            emptyItem.setEnabled(false);
            menu.add(emptyItem);
        }

        menu.addSeparator();

        JMenuItem selectItem = new JMenuItem(StringManager.getString("SELECT_SLOT_FOR_INSTALL"));
        selectItem.addActionListener(e -> EventBus.publish(new PointSelectQueued(slotPoint)));
        menu.add(selectItem);

        return menu;
    }

    public static JPopupMenu createWeaponContextMenu(WeaponSlotPoint slotPoint, ShipLayer shipLayer) {
        JPopupMenu menu = new JPopupMenu();
        ShipPainter shipPainter = shipLayer != null ? shipLayer.getPainter() : null;
        ShipVariant activeVariant = shipPainter != null ? shipPainter.getActiveVariant() : null;

        InstalledFeature fittedWeapon = null;
        if (activeVariant != null && !activeVariant.isEmpty()) {
            FittedWeaponGroup group = activeVariant.getGroupWithExistingMapping(slotPoint.getId());
            if (group != null && group.getWeapons() != null) {
                fittedWeapon = group.getWeapons().get(slotPoint.getId());
            }
        }

        if (fittedWeapon != null) {
            JMenuItem infoItem = new JMenuItem(StringManager.getString("INSTALLED") + fittedWeapon.getName() + " (" + slotPoint.getId() + ")");
            infoItem.setEnabled(false);
            menu.add(infoItem);
        } else {
            JMenuItem emptyItem = new JMenuItem(StringManager.getString("SLOT") + slotPoint.getId() + " (" + slotPoint.getWeaponType().getDisplayedName() + " / " + slotPoint.getWeaponSize().getDisplayedName() + ")");
            emptyItem.setEnabled(false);
            menu.add(emptyItem);
        }

        menu.addSeparator();

        if (slotPoint.isFittable() && shipLayer != null) {
            JMenuItem pickWeaponItem = new JMenuItem(fittedWeapon != null ? "Change Weapon..." : "Install Weapon...");
            pickWeaponItem.addActionListener(e -> {
                EventBus.publish(new PointSelectQueued(slotPoint));
                shipeditor.components.datafiles.entities.WeaponCSVEntry picked =
                        shipeditor.utility.components.dialog.DialogUtilities.showWeaponPickerDialog(slotPoint);
                if (picked != null) {
                    shipLayer.getFeaturesOverseer().installWeapon(slotPoint, picked);
                }
            });
            menu.add(pickWeaponItem);
        }

        if (fittedWeapon != null && shipLayer != null) {
            JMenuItem clearWeaponItem = new JMenuItem("Clear Weapon");
            clearWeaponItem.addActionListener(e -> shipLayer.getFeaturesOverseer().uninstallWeapon(slotPoint));
            menu.add(clearWeaponItem);
        }

        if (fittedWeapon != null && shipLayer != null
                && fittedWeapon.getDataEntry() instanceof WeaponCSVEntry fittedEntry) {
            addBulkFitItems(menu, slotPoint, shipLayer, shipPainter, fittedEntry);
        }

        JMenuItem selectItem = new JMenuItem(StringManager.getString("SELECT_SLOT_FOR_INSTALL"));
        selectItem.addActionListener(e -> EventBus.publish(new PointSelectQueued(slotPoint)));
        menu.add(selectItem);

        return menu;
    }

    private static void addBulkFitItems(JPopupMenu menu, WeaponSlotPoint slotPoint, ShipLayer shipLayer,
                                        ShipPainter shipPainter, WeaponCSVEntry weapon) {
        var slotPainter = shipPainter != null ? shipPainter.getWeaponSlotPainter() : null;
        if (slotPainter == null) return;

        log.debug("Building bulk fit menu items for slot {} with weapon {}", slotPoint.getId(), weapon.getWeaponID());
        menu.addSeparator();

        BaseWorldPoint counterpart = slotPainter.getMirroredCounterpart(slotPoint);
        JMenuItem mirrorItem = new JMenuItem("Copy weapon to mirrored slot");
        if (counterpart instanceof WeaponSlotPoint mirroredSlot && mirroredSlot.isFittable()
                && WeaponType.isWeaponFitting(mirroredSlot, weapon)) {
            mirrorItem.setToolTipText("Install " + weapon + " on slot " + mirroredSlot.getId());
            mirrorItem.addActionListener(e -> {
                try {
                    int installed = shipLayer.getFeaturesOverseer().installWeaponOnSlots(List.of(mirroredSlot), weapon);
                    log.debug("Copied weapon {} to mirrored slot {} (installed={})",
                            weapon.getWeaponID(), mirroredSlot.getId(), installed);
                } catch (RuntimeException ex) {
                    log.error("Failed to copy weapon to mirrored slot {}", mirroredSlot.getId(), ex);
                }
            });
        } else {
            mirrorItem.setEnabled(false);
            mirrorItem.setToolTipText("No compatible mirrored slot found");
        }
        menu.add(mirrorItem);

        List<WeaponSlotPoint> matchingEmpty = new ArrayList<>();
        ShipVariant variant = shipPainter.getActiveVariant();
        for (WeaponSlotPoint other : slotPainter.getSlotPoints()) {
            if (other == slotPoint || !other.isFittable()) continue;
            if (other.getWeaponType() != slotPoint.getWeaponType()
                    || other.getWeaponSize() != slotPoint.getWeaponSize()) continue;
            if (!WeaponType.isWeaponFitting(other, weapon)) continue;
            if (isSlotEmpty(other, variant)) {
                matchingEmpty.add(other);
            }
        }
        JMenuItem fillItem = new JMenuItem("Fill all matching slots with this weapon");
        if (matchingEmpty.isEmpty()) {
            fillItem.setEnabled(false);
            fillItem.setToolTipText("No other empty slots of type " + slotPoint.getWeaponType().getDisplayedName()
                    + " / " + slotPoint.getWeaponSize().getDisplayedName());
        } else {
            fillItem.setToolTipText("Install " + weapon + " on " + matchingEmpty.size() + " empty matching slot(s)");
            fillItem.addActionListener(e -> {
                try {
                    int installed = shipLayer.getFeaturesOverseer().installWeaponOnSlots(matchingEmpty, weapon);
                    log.debug("Filled {} matching slots with weapon {}", installed, weapon.getWeaponID());
                } catch (RuntimeException ex) {
                    log.error("Failed to fill matching slots with weapon {}", weapon.getWeaponID(), ex);
                }
            });
        }
        menu.add(fillItem);
    }

    private static boolean isSlotEmpty(WeaponSlotPoint slot, ShipVariant variant) {
        if (variant == null || variant.isEmpty()) return true;
        FittedWeaponGroup group = variant.getGroupWithExistingMapping(slot.getId());
        return group == null || group.getWeapons() == null || group.getWeapons().get(slot.getId()) == null;
    }

    private static void forkModuleVariant(ShipPainter modulePainter, ShipVariant original) {
        String newVariantId = original.getVariantId() + "_custom";
        ShipVariant forked = new ShipVariant(false);
        forked.setVariantId(newVariantId);
        forked.setDisplayName(original.getDisplayName() + " (Custom)");
        forked.setShipHullId(original.getShipHullId());
        forked.setFluxCapacitors(original.getFluxCapacitors());
        forked.setFluxVents(original.getFluxVents());
        forked.setGoalVariant(original.isGoalVariant());
        forked.setQuality(original.getQuality());

        if (original.getHullMods() != null) {
            forked.setHullMods(new ArrayList<>(original.getHullMods()));
        }
        if (original.getPermaMods() != null) {
            forked.setPermaMods(new ArrayList<>(original.getPermaMods()));
        }
        if (original.getSMods() != null) {
            forked.setSMods(new ArrayList<>(original.getSMods()));
        }
        if (original.getSuppressedMods() != null) {
            forked.setSuppressedMods(new ArrayList<>(original.getSuppressedMods()));
        }
        if (original.getWings() != null) {
            forked.setWings(new ArrayList<>(original.getWings()));
        }
        if (original.getWeaponGroups() != null) {
            List<FittedWeaponGroup> copiedGroups = new ArrayList<>();
            for (FittedWeaponGroup group : original.getWeaponGroups()) {
                FittedWeaponGroup copiedGroup = new FittedWeaponGroup(forked, group.isAutofire(), group.getMode());
                if (group.getWeapons() != null) {
                    for (Map.Entry<String, InstalledFeature> entry : group.getWeapons().entrySet()) {
                        copiedGroup.addFitting(entry.getKey(), entry.getValue());
                    }
                }
                copiedGroups.add(copiedGroup);
            }
            forked.setWeaponGroups(copiedGroups);
        }
        if (original.getFittedModules() != null) {
            forked.getFittedModules().putAll(original.getFittedModules());
        }

        modulePainter.selectVariant(forked);
        ShipLayer newLayer = LayerFactory.createLayerFromVariant(forked);
        if (newLayer != null) {
            newLayer.getLoadedVariants().put(newVariantId, forked);
            StaticController.getViewer().getLayerManager().setActiveLayer(newLayer);
        }
    }
}
