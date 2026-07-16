/*
 * Copyright (C) 2026 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.ui.custom;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.UIManager;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.wrapText;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomTooltipOverlay extends JComponent {

    private static final int H_PADDING = 8;
    private static final int V_PADDING = 5;
    protected static final int ARC = 14;

    private static final int MAX_LINE_LENGTH = 72;

    private static final Font TOOLTIP_FONT;

    static {
        Font f = UIManager.getFont("ToolTip.font");
        TOOLTIP_FONT = (f != null) ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private List<String> lines = List.of();

    @SuppressWarnings("this-escape")
    public CustomTooltipOverlay() {
        setFocusable(false);
        setOpaque(false);
        setEnabled(false);

        setFont(TOOLTIP_FONT);
        setDoubleBuffered(false);
    }

    public void setText(String textIn) {
        String rawText = textIn != null ? textIn : "";
        lines = wrapText(rawText, MAX_LINE_LENGTH);

        Dimension pref = computePreferredSize();
        setSize(pref);
        setPreferredSize(pref);

        revalidate();
        repaint();
    }

    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    private Dimension computePreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int maxWidth = 0;

        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fm.stringWidth(line));
        }

        int width = maxWidth + H_PADDING * 2;
        int height = (fm.getHeight() * Math.max(1, lines.size())) + V_PADDING * 2;

        return new Dimension(width, height);
    }

    @Override
    public Dimension getPreferredSize() {
        return computePreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(color(TOOLTIP_BACKGROUND));
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);

            g2d.setColor(color(TOOLTIP_FOREGROUND));
            FontMetrics fm = g2d.getFontMetrics();
            int textY = V_PADDING + fm.getAscent();

            for (String line : lines) {
                g2d.drawString(line, H_PADDING, textY);
                textY += fm.getHeight();
            }
        } finally {
            g2d.dispose();
        }
    }
}
