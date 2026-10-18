package shipeditor.components.instrument.ship.skins;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Collection;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

public class SkinDataPanel extends JPanel {

    private final JPanel chooserContainer;

    private final SkinInfoPanel infoPanel;

    public SkinDataPanel() {
        this.setLayout(new BorderLayout());
        chooserContainer = new JPanel();
        chooserContainer.setLayout(new BoxLayout(chooserContainer, BoxLayout.PAGE_AXIS));
        chooserContainer.setBorder(new EmptyBorder(4, 4, 4, 4));
        this.add(chooserContainer, BorderLayout.PAGE_START);

        infoPanel = new SkinInfoPanel();
        this.add(infoPanel, BorderLayout.CENTER);

        this.initLayerListeners();
        this.recreateSkinChooser(null);
    }

    private void recreateSkinChooser(ViewerLayer selected) {
        chooserContainer.removeAll();

        if (!(selected instanceof ShipLayer checkedLayer)) {
            chooserContainer.add(SkinDataPanel.createDisabledChooser());
            chooserContainer.revalidate();
            chooserContainer.repaint();
            return;
        }

        ShipHull shipHull = checkedLayer.getHull();
        if (shipHull == null)  {
            chooserContainer.add(SkinDataPanel.createDisabledChooser());
            chooserContainer.revalidate();
            chooserContainer.repaint();
            return;
        }
        JComboBox<ShipSkin> skinChooser = SkinDataPanel.getShipSkinComboBox(checkedLayer);

        chooserContainer.add(skinChooser);
        chooserContainer.add(Box.createVerticalGlue());

        chooserContainer.revalidate();
        chooserContainer.repaint();
    }

    private static JComboBox<ShipSkin> getShipSkinComboBox(ShipLayer checkedLayer) {
        Collection<ShipSkin> skins = checkedLayer.getSkins();
        ShipPainter painter = checkedLayer.getPainter();

        ShipSkin[] model = skins.toArray(new ShipSkin[0]);

        JComboBox<ShipSkin> skinChooser = new JComboBox<>(model);
        skinChooser.setSelectedItem(painter.getActiveSkin());
        skinChooser.addActionListener(e -> {
            AbstractSkinOverridesPanel.applyChosenSkin(painter, (ShipSkin) skinChooser.getSelectedItem());
        });
        skinChooser.setAlignmentX(Component.CENTER_ALIGNMENT);
        return skinChooser;
    }

    private void refreshPanel(ViewerLayer layer) {
        this.recreateSkinChooser(layer);
        if (layer != null) {
            infoPanel.refresh(layer.getPainter());
        } else {
            infoPanel.refresh(null);
        }

    }

    private static JComboBox<ShipSkin> createDisabledChooser() {
        ShipSkin[] skinSpecFileArray = {new ShipSkin()};
        JComboBox<ShipSkin> skinChooser = new JComboBox<>(skinSpecFileArray);
        skinChooser.setSelectedItem(skinSpecFileArray[0]);
        skinChooser.setEnabled(false);
        return skinChooser;
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                this.refreshPanel(checked.selected());
            } else if (event instanceof ActiveLayerUpdated checked) {
                this.refreshPanel(checked.updated());
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.SKIN_DATA) {
                    this.repaint();
                }
            }
        });
    }

}
