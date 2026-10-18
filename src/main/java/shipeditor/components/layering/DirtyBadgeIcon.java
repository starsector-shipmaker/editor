package shipeditor.components.layering;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab icon showing one small colored chip per unsaved category of a layer,
 * e.g. "H" for hull, "V" for variant, "S" for skin.
 */
final class DirtyBadgeIcon implements Icon {

    /**
     * A single badge: the displayed letter, its color and the human-readable category name.
     */
    record Badge(String letter, Color color, String description) {
    }

    private static final int CHIP_SIZE = 12;
    private static final int GAP = 2;
    private static final int ARC = 4;
    private static final float FONT_SIZE = 9f;

    private final List<Badge> badges;

    DirtyBadgeIcon(List<Badge> badges) {
        this.badges = new ArrayList<>(badges);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = g2.getFont().deriveFont(Font.BOLD, FONT_SIZE);
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics();
            g2.setStroke(new BasicStroke(1f));

            int drawX = x;
            for (Badge badge : badges) {
                g2.setColor(badge.color());
                g2.fillRoundRect(drawX, y, CHIP_SIZE, CHIP_SIZE, ARC, ARC);
                g2.setColor(Color.WHITE);
                int textX = drawX + (CHIP_SIZE - metrics.stringWidth(badge.letter())) / 2;
                int textY = y + (CHIP_SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(badge.letter(), textX, textY);
                drawX += CHIP_SIZE + GAP;
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        if (badges.isEmpty()) {
            return 0;
        }
        return badges.size() * CHIP_SIZE + (badges.size() - 1) * GAP;
    }

    @Override
    public int getIconHeight() {
        return CHIP_SIZE;
    }

}
