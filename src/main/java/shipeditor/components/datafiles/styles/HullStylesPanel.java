package shipeditor.components.datafiles.styles;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullStylesLoaded;
import shipeditor.representation.ship.HullStyle;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.Map;

public class HullStylesPanel extends AbstractStylesPanel {

    @Override
    protected boolean isDataLoaded() {
        return shipeditor.persistence.SettingsManager.getGameData().getAllHullStyles() != null;
    }

    @Override
    protected javax.swing.Action getLoadDataAction() {
        return new javax.swing.AbstractAction("Reload") { @Override public void actionPerformed(java.awt.event.ActionEvent e) { populatePanel(shipeditor.persistence.SettingsManager.getGameData().getAllHullStyles()); } };
    }

    @Override
    protected void initListeners() {
        if (shipeditor.persistence.SettingsManager.getGameData().getAllHullStyles() != null) {
            populatePanel(shipeditor.persistence.SettingsManager.getGameData().getAllHullStyles());
        }
        EventBus.subscribe(this, event -> {
            if (event instanceof HullStylesLoaded checked) {
                populatePanel(checked.hullStyles());
            }
        });
    }

    private void populatePanel(Map<String, HullStyle> hullStyles) {
        populateStyles(hullStyles);
    }

    @Override
    protected  JPanel createStyleTitlePanel(Object style) {
        if (style instanceof HullStyle checked) {
            String hullStyleID = checked.getHullStyleID();
            Path filePath = checked.getFilePath();
            Path containingPackage = checked.getContainingPackage();
            return ComponentUtilities.createFileTitlePanel(filePath, containingPackage, hullStyleID);
        } else {
            throw new IllegalArgumentException(ILLEGAL_STYLE_ARGUMENT);
        }

    }

    @Override
    protected JPanel createStyleContentPanel(Object style) {
        if (style instanceof HullStyle checked) {
            JPanel contentContainer = createStyleContentContainer();

            contentContainer.add(createColorRow("SHIELD_RING_COLOR", checked.getShieldRingColor(),
                    checked::setShieldRingColor,
                    () -> shipeditor.utility.overseers.StaticController.getScheduler().queueBuiltInsRepaint()));

            contentContainer.add(createColorRow("SHIELD_INNER_COLOR", checked.getShieldInnerColor(),
                    checked::setShieldInnerColor,
                    () -> shipeditor.utility.overseers.StaticController.getScheduler().queueBuiltInsRepaint()));

            return contentContainer;
        } else {
            throw new IllegalArgumentException(ILLEGAL_STYLE_ARGUMENT);
        }
    }

}
