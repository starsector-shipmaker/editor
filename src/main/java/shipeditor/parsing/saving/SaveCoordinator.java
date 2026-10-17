package shipeditor.parsing.saving;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullSaveQueued;
import shipeditor.communication.events.files.FileEvents.VariantSaveQueued;
import shipeditor.communication.events.files.FileEvents.WeaponSaveQueued;
import shipeditor.communication.events.files.FileEvents.ProjectileSaveQueued;

import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JOptionPane;

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

    public static void saveLayer(ViewerLayer layer) {
        if (layer instanceof ShipLayer shipLayer) {
            EventBus.publish(new HullSaveQueued(shipLayer));
            if (shipLayer.getPainter() != null && shipLayer.getPainter().getActiveVariant() != null && !shipLayer.getPainter().getActiveVariant().isEmpty()) {
                EventBus.publish(new VariantSaveQueued(shipLayer.getPainter().getActiveVariant()));
            }
        } else if (layer instanceof WeaponLayer weaponLayer) {
            EventBus.publish(new WeaponSaveQueued(weaponLayer));
        } else if (layer instanceof ProjectileLayer projectileLayer) {
            EventBus.publish(new ProjectileSaveQueued(projectileLayer));
        }
    }

    public static void saveActiveLayer() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null && layerManager.getActiveLayer() != null) {
            saveLayer(layerManager.getActiveLayer());
        } else {
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(), StringManager.getString("NO_ACTIVE_LAYER_TO_SAVE_MSG"), "Warning", JOptionPane.WARNING_MESSAGE);
        }
    }

    public static void saveAllLayers() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null) {
            for (ViewerLayer layer : layerManager.getLayers()) {
                saveLayer(layer);
            }
        }
    }

}
