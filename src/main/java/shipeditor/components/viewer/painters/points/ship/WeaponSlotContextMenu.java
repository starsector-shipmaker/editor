package shipeditor.components.viewer.painters.points.ship;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.EditDispatch;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
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
                if (moduleToEdit.getFeaturePainter() instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter) {
                    shipeditor.components.viewer.layers.ship.data.ShipVariant variant = shipPainter.getActiveVariant();
                    if (variant != null) {
                        shipeditor.components.viewer.layers.ship.ShipLayer newLayer = 
                                shipeditor.components.viewer.layers.LayerFactory.createLayerFromVariant(variant);
                        shipeditor.utility.overseers.StaticController.getViewer().getLayerManager().addLayer(newLayer);
                    }
                }
            });
            menu.add(editItem);

            JMenuItem forkItem = new JMenuItem("Fork Module Variant");
            forkItem.addActionListener(e -> {
                if (moduleToEdit.getFeaturePainter() instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter) {
                    shipeditor.components.viewer.layers.ship.data.ShipVariant original = shipPainter.getActiveVariant();
                    if (original != null) {
                        shipeditor.components.viewer.layers.ship.data.ShipVariant forked = new shipeditor.components.viewer.layers.ship.data.ShipVariant();
                        forked.setVariantId(original.getVariantId() + "_custom");
                        forked.setDisplayName(original.getDisplayName() + " (Custom)");
                        forked.setShipHullId(original.getShipHullId());
                        // Just open it in a new tab for them to edit and save.
                        shipeditor.components.viewer.layers.ship.ShipLayer newLayer = 
                                shipeditor.components.viewer.layers.LayerFactory.createLayerFromVariant(forked);
                        shipeditor.utility.overseers.StaticController.getViewer().getLayerManager().addLayer(newLayer);
                    }
                }
            });
            menu.add(forkItem);

            JMenuItem clearItem = new JMenuItem(StringManager.getString("CLEAR_MODULE"));
            InstalledFeature toRemove = installed;
            clearItem.addActionListener(e -> shipeditor.undo.EditDispatch.postFeatureUninstalled(fittedModules, slotPoint.getId(), toRemove, null));
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
}
