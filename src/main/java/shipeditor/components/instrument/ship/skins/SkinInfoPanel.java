package shipeditor.components.instrument.ship.skins;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.components.instrument.LayerPropertiesPanel;
import shipeditor.utility.objects.Pair;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

public class SkinInfoPanel extends LayerPropertiesPanel {

    public void refreshContent(LayerPainter layerPainter) {
        fireClearingListeners(layerPainter);

        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) return;
        ShipLayer shipLayer = shipPainter.getParentLayer();
        ShipSkin activeSkin = shipLayer.getActiveSkin();
        if (activeSkin == null || activeSkin.isBase()) return;

        fireRefresherListeners(layerPainter);
    }

    protected void populateContent() {
        this.setLayout(new BorderLayout());
        Map<JLabel, JComponent> widgets = new LinkedHashMap<>();

        var hullNameWidget = createHullNameEditor();
        widgets.put(hullNameWidget.getFirst(), hullNameWidget.getSecond());

        JPanel widgetsPanel = createWidgetsPanel(widgets);
        this.add(widgetsPanel, BorderLayout.PAGE_START);
    }

    @Override
    protected JPanel createWidgetsPanel(Map<JLabel, JComponent> widgets) {
        JPanel widgetsPanel = super.createWidgetsPanel(widgets);
        ComponentUtilities.outfitPanelWithTitle(widgetsPanel, "Skin data");
        return widgetsPanel;
    }

    private Pair<JLabel, JTextField> createHullNameEditor() {
        JTextField hullNameEditor = new JTextField();
        hullNameEditor.setToolTipText(StringManager.getString("ENTER_TO_SAVE_CHANGES"));
        hullNameEditor.putClientProperty("JTextField.placeholderText", "Skin hull name...");
        hullNameEditor.setColumns(10);
        hullNameEditor.addActionListener(e -> {
            if (isWidgetsReadyForInput()) {
                String currentText = hullNameEditor.getText();
                LayerPainter cachedLayer = getCachedLayerPainter();
                ViewerLayer viewerLayer = cachedLayer.getParentLayer();
                if (viewerLayer instanceof ShipLayer shipLayer) {
                    var activeSkin = shipLayer.getActiveSkin();
                    if (activeSkin != null) {
                        activeSkin.setHullName(currentText);
                        processChange();
                    }
                }
            }
        });

        registerWidgetListeners(hullNameEditor, layer -> {
            hullNameEditor.setText(StringManager.getString("NOT_INITIALIZED"));
            hullNameEditor.setEnabled(false);
        }, layerPainter -> {
            ShipLayer shipLayer = (ShipLayer) layerPainter.getParentLayer();
            var skin = shipLayer.getActiveSkin();
            hullNameEditor.setEnabled(true);
            hullNameEditor.setText(skin.getHullName());
        });

        return new Pair<>(new JLabel(StringManager.getString("HULL_NAME")), hullNameEditor);
    }

}
