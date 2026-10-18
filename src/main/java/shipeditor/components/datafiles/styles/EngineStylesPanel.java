package shipeditor.components.datafiles.styles;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.EngineStylesLoaded;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.Map;

public class EngineStylesPanel extends AbstractStylesPanel {

    @Override
    protected boolean isDataLoaded() {
        return shipeditor.persistence.SettingsManager.getGameData().getAllEngineStyles() != null;
    }

    @Override
    protected javax.swing.Action getLoadDataAction() {
        return new javax.swing.AbstractAction("Reload") { @Override public void actionPerformed(java.awt.event.ActionEvent e) { populatePanel(shipeditor.persistence.SettingsManager.getGameData().getAllEngineStyles()); } };
    }

    @Override
    protected void initListeners() {
        if (shipeditor.persistence.SettingsManager.getGameData().getAllEngineStyles() != null) {
            populatePanel(shipeditor.persistence.SettingsManager.getGameData().getAllEngineStyles());
        }
        EventBus.subscribe(this, event -> {
            if (event instanceof EngineStylesLoaded checked) {
                populatePanel(checked.engineStyles());
            }
        });
    }

    private void populatePanel(Map<String, EngineStyle> engineStyles) {
        populateStyles(engineStyles);
    }

    protected JPanel createStyleTitlePanel(Object style) {
        if (style instanceof EngineStyle checked) {
            String styleID = checked.getEngineStyleID();
            Path filePath = checked.getFilePath();
            Path containingPackage = checked.getContainingPackage();
            return ComponentUtilities.createFileTitlePanel(filePath, containingPackage, styleID);
        } else {
            throw new IllegalArgumentException(ILLEGAL_STYLE_ARGUMENT);
        }
    }

    protected JPanel createStyleContentPanel(Object style) {
        if (style instanceof EngineStyle checked) {
            JPanel contentContainer = createStyleContentContainer();

            contentContainer.add(createColorRow("ENGINE_COLOR", checked.getEngineColor(),
                    checked::setEngineColor,
                    () -> shipeditor.utility.overseers.StaticController.getScheduler().queueEnginesPanelRepaint()));

            contentContainer.add(createColorRow("CONTRAIL_COLOR", checked.getContrailColor(),
                    checked::setContrailColor,
                    () -> shipeditor.utility.overseers.StaticController.getScheduler().queueEnginesPanelRepaint()));

            return contentContainer;
        } else {
            throw new IllegalArgumentException(ILLEGAL_STYLE_ARGUMENT);
        }
    }

}
