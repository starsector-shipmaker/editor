package shipeditor.utility.components.rendering;

import shipeditor.utility.text.StringManager;

import com.formdev.flatlaf.ui.FlatLineBorder;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.text.StringConstants;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

public class EngineCellRenderer extends PointCellRenderer {

    private final JLabel styleIcon;

    public EngineCellRenderer() {
        styleIcon = new JLabel();
        styleIcon.setOpaque(true);
        styleIcon.setBorder(new FlatLineBorder(new Insets(2, 2, 2, 2), Color.GRAY));
        styleIcon.setBackground(Color.LIGHT_GRAY);

        JPanel leftContainer = getLeftContainer();
        leftContainer.removeAll();
        leftContainer.add(styleIcon);
        JLabel textLabel = getTextLabel();
        textLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
        leftContainer.add(textLabel);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends BaseWorldPoint> list,
                                                  BaseWorldPoint value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        this.setToolTipText(null);

        if (value instanceof EnginePoint checked) {
            EngineStyle engineStyle = checked.getStyle();
            String styleOrID = checked.getStyleID();
            styleIcon.setIcon(null);
            styleIcon.setVisible(false);
            if (engineStyle != null) {
                styleOrID = engineStyle.getEngineStyleID();

                if (engineStyle.getEngineColor() != null) {
                    Icon color = ComponentUtilities.createIconFromColor(engineStyle.getEngineColor(), 10, 10);
                    styleIcon.setIcon(color);
                    styleIcon.setVisible(true);
                }
            }
            else if (checked.getCustomStyleSpec() != null) {
                styleOrID = StringConstants.CUSTOM;
                this.setToolTipText(StringManager.getString("CUSTOM_STYLE_SPEC_DETECTED_INLINE_STYLE_EDITING_NOT_SUPPORTED"));
            }
            String displayText = styleOrID + " #" + index + ":";

            JLabel textLabel = getTextLabel();
            textLabel.setText(displayText);
        } else {
            styleIcon.setVisible(false);
        }

        return this;
    }

}
