package shipeditor.components.instrument.ship.variant;

import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;

/**
 * Info banner shared by all variant panels. It is visible only when the active variant is empty,
 * explaining that the first edit auto-creates a '&lt;hull&gt;_custom' variant. Controls stay enabled.
 */
@Log4j2
public class EmptyVariantBanner extends JPanel {

    private final JLabel messageLabel;

    public EmptyVariantBanner() {
        super(new BorderLayout(6, 0));
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Themes.getBorderColor()),
                new EmptyBorder(4, 6, 4, 6)));

        JLabel iconLabel = new JLabel(FontIcon.of(BoxiconsRegular.INFO_CIRCLE, 16, Themes.getIconColor()));
        this.add(iconLabel, BorderLayout.LINE_START);

        messageLabel = new JLabel();
        this.add(messageLabel, BorderLayout.CENTER);

        this.setVisible(false);
    }

    /**
     * Shows or hides the banner for the given layer.
     *
     * @param selected layer the owning panel is displaying; anything but an initialized ship layer hides the banner.
     */
    public void refresh(ViewerLayer selected) {
        if (!(selected instanceof ShipLayer shipLayer) || shipLayer.getHull() == null) {
            this.setVisible(false);
            return;
        }
        ShipPainter painter = shipLayer.getPainter();
        if (painter == null || painter.isUninitialized()) {
            this.setVisible(false);
            return;
        }

        ShipVariant active = painter.getActiveVariant();
        boolean emptyVariant = active == null || active.isEmpty();
        log.debug("Empty variant banner for layer '{}': visible={}", shipLayer.getShipID(), emptyVariant);
        if (emptyVariant) {
            String hullId = StaticController.resolveEditableHullId(painter);
            String nextId = StaticController.nextCustomVariantId(hullId, shipLayer.getLoadedVariants());
            messageLabel.setText("No variant selected: editing creates " + nextId);
        }
        this.setVisible(emptyVariant);
    }
}
