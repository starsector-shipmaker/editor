package shipeditor.components.datafiles.styles;

import com.formdev.flatlaf.ui.FlatLineBorder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.text.StringManager;
import shipeditor.utility.themes.Themes;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@Log4j2
abstract class AbstractStylesPanel extends JPanel {

    protected static final int CONTENT_SIDE_PAD = 2;

    static final String ILLEGAL_STYLE_ARGUMENT = "Illegal style argument!";

    private final JPanel scrollerContent;

    AbstractStylesPanel() {
        this.setLayout(new BorderLayout());

        JPanel topContainer = this.createTopPanel();
        if (topContainer != null) {
            topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Themes.getBorderColor()));
            this.add(topContainer, BorderLayout.PAGE_START);
        }

        this.scrollerContent = new JPanel();
        scrollerContent.setLayout(new BoxLayout(scrollerContent, BoxLayout.PAGE_AXIS));
        JScrollPane scroller = new JScrollPane(scrollerContent);
        JScrollBar verticalScrollBar = scroller.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(12);
        this.add(scroller, BorderLayout.CENTER);
        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (this.isShowing()) {
                    if (!this.isDataLoaded() && !shipeditor.parsing.loading.FileLoading.isLoadingInProgress()) {
                        javax.swing.Action loadAction = this.getLoadDataAction();
                        if (loadAction != null) {
                            loadAction.actionPerformed(null);
                        }
                    }
                }
            }
        });

        this.initListeners();
    }

    protected abstract boolean isDataLoaded();

    protected abstract javax.swing.Action getLoadDataAction();

    protected JPanel createTopPanel() {
        return new JPanel();
    }

    protected abstract void initListeners();

    JPanel createStylePanel(Object style) {
        JPanel stylePanel = new JPanel();
        stylePanel.setLayout(new BoxLayout(stylePanel, BoxLayout.PAGE_AXIS));

        Insets outsideInsets = new Insets(2, 2, 2, 2);
        Insets insideInsets = new Insets(6, 6, 6, 6);
        Border lineBorder = new FlatLineBorder(insideInsets, Themes.getBorderColor());
        Border outsideBorder = new EmptyBorder(outsideInsets);

        stylePanel.setBorder(BorderFactory.createCompoundBorder(outsideBorder, lineBorder));

        JPanel titleContainer = this.createStyleTitlePanel(style);
        stylePanel.add(titleContainer);

        JPanel contentContainer = this.createStyleContentPanel(style);
        stylePanel.add(contentContainer);

        return stylePanel;
    }

    /**
     * Rebuilds the scroller content with one style panel per given style.
     */
    protected void populateStyles(Map<String, ?> styles) {
        if (styles == null) return;
        log.debug("Populating {} with {} styles", this.getClass().getSimpleName(), styles.size());
        scrollerContent.removeAll();
        for (Object style : styles.values()) {
            scrollerContent.add(this.createStylePanel(style));
        }
        scrollerContent.revalidate();
        scrollerContent.repaint();
    }

    protected JPanel createStyleContentContainer() {
        JPanel contentContainer = new JPanel();
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.PAGE_AXIS));
        return contentContainer;
    }

    /**
     * Creates a labelled color picker row; {@code onChanged} runs after the new color has been set.
     */
    protected JPanel createColorRow(String labelKey, Color color, Consumer<Color> setter, Runnable onChanged) {
        JLabel label = new JLabel(StringManager.getString(labelKey));
        return ComponentUtilities.createColorPropertyPanel(label, color, CONTENT_SIDE_PAD, picked -> {
            setter.accept(picked);
            onChanged.run();
        });
    }

    protected abstract JPanel createStyleTitlePanel(Object style);

    protected abstract JPanel createStyleContentPanel(Object style);

}
