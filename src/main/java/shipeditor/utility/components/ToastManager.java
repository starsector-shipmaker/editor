package shipeditor.utility.components;

import lombok.extern.log4j.Log4j2;
import shipeditor.PrimaryWindow;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking, auto-dismissing toast notifications stacked at the bottom-right of the {@link PrimaryWindow}.
 * <p>
 * Every toast is a small undecorated, non-focusable {@link JWindow} owned by the primary window. Heavyweight
 * windows are used on purpose: the OpenGL viewer is an {@code AWTGLCanvas} (heavyweight), which would paint over
 * any lightweight layered-pane component.
 * <p>
 * All public methods are thread-safe and may be called from any thread, before the window exists or in
 * headless mode (in which case the message is only logged).
 */
@Log4j2
public final class ToastManager {

    /**
     * Severity of a toast, controlling its colour and display duration.
     */
    public enum ToastLevel {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    private static final int NORMAL_DURATION_MS = 3500;
    private static final int ERROR_DURATION_MS = 6000;
    private static final int MARGIN = 16;
    private static final int GAP = 8;
    private static final int MAX_ACTIVE_TOASTS = 5;
    private static final int MAX_MESSAGE_LENGTH = 400;
    private static final int TOAST_WIDTH = 380;

    /** Only accessed from the EDT. */
    private static final List<Toast> ACTIVE = new ArrayList<>();

    /** Only accessed from the EDT. */
    private static Window attachedWindow;

    private ToastManager() {
    }

    public static void show(String message) {
        show(message, ToastLevel.INFO);
    }

    public static void show(String message, ToastLevel level) {
        if (message == null || message.isBlank()) {
            return;
        }
        ToastLevel effectiveLevel = level != null ? level : ToastLevel.INFO;
        if (GraphicsEnvironment.isHeadless()) {
            log.info("[toast:{}] {}", effectiveLevel, message);
            return;
        }
        SwingUtilities.invokeLater(() -> showOnEdt(message, effectiveLevel));
    }

    /**
     * Hooks the toast host into the given window so visible toasts follow it when it is moved, resized or
     * minimized. Safe to call repeatedly. Must be called on the EDT.
     */
    public static void attach(Window window) {
        if (window == null || GraphicsEnvironment.isHeadless() || window == attachedWindow) {
            return;
        }
        attachedWindow = window;
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                layoutToasts();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                layoutToasts();
            }
        });
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                for (Toast toast : ACTIVE) {
                    toast.window.setVisible(false);
                }
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                for (Toast toast : ACTIVE) {
                    toast.window.setVisible(true);
                }
                layoutToasts();
            }
        });
    }

    private static void showOnEdt(String message, ToastLevel level) {
        PrimaryWindow owner = PrimaryWindow.getInstance();
        if (owner == null || !owner.isShowing()) {
            log.info("[toast:{}] {}", level, message);
            return;
        }
        try {
            attach(owner);
            String text = message.length() > MAX_MESSAGE_LENGTH
                    ? message.substring(0, MAX_MESSAGE_LENGTH) + "..."
                    : message;

            for (Toast existing : ACTIVE) {
                if (existing.level == level && existing.message.equals(text)) {
                    existing.restartTimer();
                    return;
                }
            }
            while (ACTIVE.size() >= MAX_ACTIVE_TOASTS) {
                ACTIVE.get(0).dismiss();
            }
            Toast toast = new Toast(owner, text, level);
            ACTIVE.add(toast);
            layoutToasts();
            toast.window.setVisible(true);
            toast.restartTimer();
        } catch (RuntimeException e) {
            log.warn("Failed to display toast '{}': {}", message, e.getMessage());
        }
    }

    private static void layoutToasts() {
        Window owner = attachedWindow;
        if (owner == null || !owner.isShowing() || ACTIVE.isEmpty()) {
            return;
        }
        Point origin = owner.getLocationOnScreen();
        Rectangle bounds = new Rectangle(origin, owner.getSize());
        int right = bounds.x + bounds.width - MARGIN;
        int y = bounds.y + bounds.height - MARGIN;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Toast toast = ACTIVE.get(i);
            Dimension size = toast.window.getSize();
            y -= size.height;
            toast.window.setLocation(right - size.width, y);
            y -= GAP;
        }
    }

    private static void remove(Toast toast) {
        if (ACTIVE.remove(toast)) {
            layoutToasts();
        }
    }

    private static final class Toast {

        private final JWindow window;
        private final String message;
        private final ToastLevel level;
        private final Timer timer;

        Toast(Window owner, String message, ToastLevel level) {
            this.message = message;
            this.level = level;
            this.window = new JWindow(owner);
            window.setFocusableWindowState(false);
            window.setAutoRequestFocus(false);
            window.setAlwaysOnTop(false);

            Color accent = accentFor(level);
            JPanel content = new JPanel(new BorderLayout(8, 0));
            content.setBackground(new Color(0x2B2B2B));
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                    BorderFactory.createEmptyBorder(8, 10, 8, 6)));

            String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
            JLabel label = new JLabel("<html><body style='width:" + (TOAST_WIDTH - 70) + "px'>" + escaped
                    + "</body></html>");
            label.setForeground(Color.WHITE);
            content.add(label, BorderLayout.CENTER);

            JButton close = new JButton("\u00D7");
            close.setFocusable(false);
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setForeground(new Color(0xBBBBBB));
            close.setFont(close.getFont().deriveFont(Font.BOLD, 16f));
            close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            close.setVerticalAlignment(SwingConstants.TOP);
            close.addActionListener(e -> dismiss());
            content.add(close, BorderLayout.EAST);

            content.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dismiss();
                }
            });
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dismiss();
                }
            });

            window.setContentPane(content);
            window.pack();
            window.setSize(TOAST_WIDTH, window.getHeight());

            int duration = level == ToastLevel.ERROR ? ERROR_DURATION_MS : NORMAL_DURATION_MS;
            timer = new Timer(duration, e -> dismiss());
            timer.setRepeats(false);
        }

        void restartTimer() {
            timer.restart();
        }

        void dismiss() {
            timer.stop();
            window.setVisible(false);
            window.dispose();
            remove(this);
        }

        private static Color accentFor(ToastLevel level) {
            return switch (level) {
                case SUCCESS -> new Color(0x43A047);
                case WARNING -> new Color(0xF9A825);
                case ERROR -> new Color(0xE53935);
                case INFO -> new Color(0x42A5F5);
            };
        }
    }

}
