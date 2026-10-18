package shipeditor.utility.components.rendering;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.BoxLayout;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;

@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class BoxPanelCellRenderer<E> extends PanelListCellRenderer<E> {

    private final JPanel leftContainer;

    private final JPanel rightContainer;

    @SuppressWarnings("ThisEscapedInObjectConstruction")
    BoxPanelCellRenderer() {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

        leftContainer = ComponentUtilities.createTransparentBoxPanel();
        rightContainer = ComponentUtilities.createTransparentBoxPanel();

        ComponentUtilities.layoutAsOpposites(this, leftContainer, rightContainer, 4);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends E> list,
                                                  E value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        Color background = super.getBackground();
        leftContainer.setBackground(background);
        rightContainer.setBackground(background);
        return this;
    }

}
