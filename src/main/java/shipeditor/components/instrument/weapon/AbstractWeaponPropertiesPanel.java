package shipeditor.components.instrument.weapon;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.components.ComponentEvents.LayerTabUpdated;
import shipeditor.components.instrument.AbstractDataPropertiesPanel;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.utility.overseers.StaticController;

@Log4j2
public abstract class AbstractWeaponPropertiesPanel extends AbstractDataPropertiesPanel<WeaponPainter> {

    protected WeaponLayer cachedLayer;

    protected AbstractWeaponPropertiesPanel() {
        super(WeaponPainter.class);
    }

    @Override
    protected void processChange() {
        if (cachedLayer != null) {
            log.debug("Weapon property changed for layer {}", cachedLayer);
            EventBus.publish(new LayerTabUpdated(cachedLayer));
            StaticController.getScheduler().queueViewerRepaint();
        }
    }

}
