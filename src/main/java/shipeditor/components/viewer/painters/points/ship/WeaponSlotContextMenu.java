package shipeditor.components.viewer.painters.points.ship;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerFactory;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringManager;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
