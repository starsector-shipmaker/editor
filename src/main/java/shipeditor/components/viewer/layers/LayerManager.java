package shipeditor.components.viewer.layers;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullFileOpened;
import shipeditor.communication.events.files.FileEvents.SkinFileOpened;
import shipeditor.communication.events.viewer.layers.LayerEvents.ViewerLayerRemovalConfirmed;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerSpriteLoadQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerRemovalQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerShipDataInitialized;
import shipeditor.communication.events.viewer.layers.LayerEvents.ShipLayerCreated;
import shipeditor.communication.events.viewer.layers.LayerEvents.WeaponLayerCreated;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ActiveShipSpec;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import shipeditor.communication.events.components.ComponentEvents.LayerTabUpdated;
import shipeditor.communication.events.files.FileEvents.SpriteOpened;
import shipeditor.communication.events.files.FileEvents.HullmodDataSet;
import shipeditor.communication.events.files.FileEvents.WingDataSet;
import shipeditor.communication.events.viewer.layers.LayerEvents.ShipLayerCreationQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.WeaponLayerCreationQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.LastLayerSelectQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerOpacityChangeQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerRemovalQueued;

@Getter
@SuppressWarnings("OverlyCoupledClass")
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class LayerManager {

    @Setter
    private List<ViewerLayer> layers = new ArrayList<>();

    private ViewerLayer activeLayer;

    private final Map<ViewerLayer, List<String>> unsavedChangesRegistry = new HashMap<>();

    public List<String> getUnsavedLayerNames() {
        List<String> names = new ArrayList<>();
        for (ViewerLayer layer : unsavedChangesRegistry.keySet()) {
            String layerName = "";
            if (layer instanceof ShipLayer shipLayer) {
                ShipHull shipHull = shipLayer.getHull();
                if (shipHull != null && shipHull.getHullName() != null && !shipHull.getHullName().isEmpty()) {
                    layerName = shipHull.getHullName();
                } else if (shipLayer.getHullFileName() != null && !shipLayer.getHullFileName().isEmpty()) {
                    layerName = shipLayer.getHullFileName();
                }
                
                ShipPainter painter = shipLayer.getPainter();
                if (painter != null) {
                    ShipSkin activeSkin = painter.getActiveSkin();
                    if (activeSkin != null && !activeSkin.isBase() && activeSkin.getHullName() != null) {
                        layerName = activeSkin.getHullName();
                    }
                }
            }
            if (layerName.isEmpty()) {
                LayerPainter painter = layer.getPainter();
                if (painter != null && painter.getSprite() != null && painter.getSprite().getFilename() != null && !painter.getSprite().getFilename().isEmpty()) {
                    layerName = painter.getSprite().getFilename();
                }
            }
            if (layerName.isEmpty()) {
                int index = this.layers.indexOf(layer) + 1;
                layerName = "Layer " + index;
            }
            names.add(layerName);
        }
        return names;
    }

    public void markUnsaved(ViewerLayer layer, String changeType) {
        List<String> types = unsavedChangesRegistry.computeIfAbsent(layer, k -> new ArrayList<>());
        if (!types.contains(changeType)) {
            types.add(changeType);
            EventBus.publish(new LayerTabUpdated(layer));
        }
    }

    public void markSaved(ViewerLayer layer, String changeType) {
        List<String> types = unsavedChangesRegistry.get(layer);
        if (types != null) {
            types.remove(changeType);
            if (types.isEmpty()) {
                unsavedChangesRegistry.remove(layer);
            }
            EventBus.publish(new LayerTabUpdated(layer));
        }
    }

    public boolean isLayerDirty(ViewerLayer layer) {
        List<String> types = unsavedChangesRegistry.get(layer);
        return types != null && !types.isEmpty();
    }

    public boolean isHullDirty(ViewerLayer layer) {
        List<String> types = unsavedChangesRegistry.get(layer);
        return types != null && types.contains("hull");
    }

    public boolean isVariantDirty(ViewerLayer layer) {
        List<String> types = unsavedChangesRegistry.get(layer);
        return types != null && types.contains("variant");
    }

    public void initListeners() {
        this.initLayerListening();
        this.initOpenSpriteListener();
        this.initOpenHullListener();
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public void activateLastLayer() {
        ViewerLayer next = layers.get(layers.size() - 1);
        this.setActiveLayer(next);
    }

    public void setActiveLayer(ViewerLayer newlySelected) {
        ViewerLayer old = this.activeLayer;
        this.activeLayer = newlySelected;
        StaticController.setActiveLayer(activeLayer);
        EventBus.publish(new LayerWasSelected(old, newlySelected));
    }

    public ShipLayer createShipLayer() {
        ShipLayer newLayer = new ShipLayer();
        layers.add(newLayer);
        EventBus.publish(new ShipLayerCreated(newLayer));
        return newLayer;
    }

    public WeaponLayer createWeaponLayer() {
        WeaponLayer newLayer = new WeaponLayer();
        layers.add(newLayer);
        EventBus.publish(new WeaponLayerCreated(newLayer));
        return newLayer;
    }

    public void addLayer(ViewerLayer newLayer) {
        layers.add(newLayer);
        if (newLayer instanceof ShipLayer sl) {
            EventBus.publish(new ShipLayerCreated(sl));
        } else if (newLayer instanceof WeaponLayer wl) {
            EventBus.publish(new WeaponLayerCreated(wl));
        }
    }

    @SuppressWarnings({"OverlyCoupledMethod"})
    private void initLayerListening() {
        // Creation & Removal Events
        EventBus.subscribe(this, event -> {
            if (event instanceof ShipLayerCreationQueued) this.createShipLayer();
            else if (event instanceof WeaponLayerCreationQueued) this.createWeaponLayer();
            else if (event instanceof LastLayerSelectQueued) activateLastLayer();
            else if (event instanceof ActiveLayerRemovalQueued) publishLayerRemoval(this.activeLayer);
            else if (event instanceof LayerRemovalQueued checked) publishLayerRemoval(checked.layer());
        });

        // Properties Events
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerOpacityChangeQueued checked) {
                if (activeLayer == null) return;
                LayerPainter painter = activeLayer.getPainter();
                if (painter == null) return;
                painter.setSpriteOpacity(checked.changedValue());
            }
        });

        // Layer Data Flow Events
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerShipDataInitialized checked) {
                ShipPainter source = checked.source();
                ShipLayer parentLayer = source.getParentLayer();
                if (parentLayer != null) {
                    this.setActiveLayer(parentLayer);
                    EventBus.publish(new ActiveLayerUpdated(this.getActiveLayer()));
                }
            } else if (event instanceof ActiveLayerUpdated checked) {
                setActiveLayer(checked.updated());
            }
        });

        // Game Data Loading Events
        EventBus.subscribe(this, event -> {
            if (event instanceof HullmodDataSet) actOnAllLayerHulls((a, b) -> a.loadBuiltInMods(b));
            else if (event instanceof WingDataSet) actOnAllLayerHulls((a, b) -> a.loadBuiltInWings(b));
        });
    }

    private void actOnAllLayerHulls(BiConsumer<ShipHull, HullSpecFile> action) {
        layers.forEach(layer -> {
            if (layer instanceof ShipLayer checkedLayer) {
                ShipHull hull = checkedLayer.getHull();
                if (hull != null) {
                    ShipCSVEntry shipEntry = GameDataRepository.retrieveShipCSVEntryByID(hull.getHullID());
                    if (shipEntry != null) {
                        HullSpecFile hullSpec = shipEntry.getHullSpecFile();
                        if (hullSpec != null) {
                            action.accept(hull, hullSpec);
                        }
                    }
                }
            }
        });
        setActiveLayer(this.getActiveLayer());
    }

    private void publishLayerRemoval(ViewerLayer layer) {
        if (!shipeditor.utility.components.dialog.DialogHelper.confirmLayerRemoval(this, layer)) {
            return;
        }
        if (layers.size() >= 2) {
            int index = layers.indexOf(layer);
            // Select the previous layer, or the next one if removing the first.
            ViewerLayer adjacent = (index > 0) ? layers.get(index - 1) : layers.get(index + 1);
            this.setActiveLayer(adjacent);
        } else {
            this.setActiveLayer(null);
        }
        layers.remove(layer);
        unsavedChangesRegistry.remove(layer);
        EventBus.publish(new ViewerLayerRemovalConfirmed(layer));
        EventBus.publish(new shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformsReset());
    }

    private void initOpenSpriteListener() {
        EventBus.subscribe(this, event -> {
            if (event instanceof SpriteOpened checked) {
                Sprite sprite = checked.sprite();
                if (activeLayer == null) return;
                EventBus.publish(new LayerSpriteLoadQueued(activeLayer, sprite));
            }
        });
    }

    private void initOpenHullListener() {
        EventBus.subscribe(this, event -> {
            if (event instanceof HullFileOpened checked) {
                HullSpecFile hullSpecFile = checked.hullSpecFile();
                if (activeLayer != null && activeLayer instanceof ShipLayer checkedLayer) {
                    boolean wasPaused = shipeditor.undo.UndoOverseer.isPaused();
                    shipeditor.undo.UndoOverseer.setPaused(true);
                    try {
                        checkedLayer.initializeHullData(hullSpecFile);
                    } finally {
                        shipeditor.undo.UndoOverseer.setPaused(wasPaused);
                        markSaved(checkedLayer, "hull");
                        markSaved(checkedLayer, "variant");
                    }
                } else {
                    throw new IllegalStateException("Hull file loaded onto invalid layer!");
                }
            } else if (event instanceof SkinFileOpened checked) {
                SkinSpecFile skinSpecFile = checked.skinSpecFile();
                if (activeLayer != null && activeLayer instanceof ShipLayer checkedLayer) {
                    ShipHull data = checkedLayer.getHull();
                    if (data != null) {
                        boolean wasPaused = shipeditor.undo.UndoOverseer.isPaused();
                        shipeditor.undo.UndoOverseer.setPaused(true);
                        try {
                            LayerManager.openSkinFile(checkedLayer, data, skinSpecFile, checked.setAsActive());
                        } finally {
                            shipeditor.undo.UndoOverseer.setPaused(wasPaused);
                            markSaved(checkedLayer, "hull");
                            markSaved(checkedLayer, "variant");
                        }
                    } else {
                        throw new IllegalStateException("Skin file loaded onto a null ship data!");
                    }
                    EventBus.publish(new ActiveLayerUpdated(checkedLayer));
                } else {
                    throw new IllegalStateException("Skin file loaded onto invalid layer!");
                }
            }
        });
    }

    @SuppressWarnings("BooleanParameter")
    public static void openSkinFile(ShipLayer checkedLayer, ShipHull data,
                                    SkinSpecFile skinSpecFile, boolean setAsActive) {
        String hullID = data.getHullID();
        if (!hullID.equals(skinSpecFile.getBaseHullId())) {
            Path skinFilePath = skinSpecFile.getFilePath();
            String pathString = skinFilePath != null ? skinFilePath.toString() : skinSpecFile.toString();
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("HULL_ID_OF_ACTIVE_LAYER_DOES_NOT_EQUAL_B_MSG") + pathString,
                    "Ship ID mismatch!",
                    JOptionPane.ERROR_MESSAGE);
            throw new IllegalStateException("Illegal skin file opening operation!");
        }
        ShipSkin created = checkedLayer.addSkin(skinSpecFile);
        if (setAsActive) {
            ShipPainter shipPainter = checkedLayer.getPainter();
            shipPainter.setActiveSpec(ActiveShipSpec.SKIN, created);
        }
    }

}
