package shipeditor.components.logging;

import shipeditor.utility.Utility;
import shipeditor.utility.themes.Themes;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Rectangle;

public class LogsPanel extends JPanel {

    private static final JTextArea LOGGER;

    private JScrollPane scrollPane;

    static {
        LOGGER = new JTextArea();
        LOGGER.setEditable(false);
    }

    public LogsPanel() {
        this.setLayout(new BorderLayout());
        LOGGER.setBorder(new EmptyBorder(2, 2, 2, 2));
        LOGGER.setForeground(Themes.getTextColor());
        LOGGER.setBackground(Themes.getListBackgroundColor());
        LOGGER.setFont(Utility.getDefaultFont());
        scrollPane = new JScrollPane(LOGGER);
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        verticalScrollBar.setUnitIncrement(20);
        this.add(scrollPane, BorderLayout.CENTER);
    }

    public static void append(String formattedMessage) {
        if (LOGGER != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                LOGGER.append(formattedMessage);
                LogsPanel.scrollToBottom();
            } else {
                SwingUtilities.invokeLater(() -> {
                    LOGGER.append(formattedMessage);
                    LogsPanel.scrollToBottom();
                });
            }
        }
    }

    public static void scrollToBottom() {
        Rectangle visible = LOGGER.getVisibleRect();
        Rectangle bounds = LOGGER.getBounds();

        visible.y = bounds.height - visible.height;
        visible.x = 0;
        LOGGER.scrollRectToVisible(visible);
    }

}
