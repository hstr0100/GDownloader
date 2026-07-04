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

import java.awt.*;
import javax.swing.JComponent;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomChip extends JComponent {

    private final String text;

    private final Color background;
    private final Color foreground;

    @SuppressWarnings("this-escape")
    public CustomChip(String textIn, Color backgroundIn, Color foregroundIn) {
        text = textIn;

        background = backgroundIn;
        foreground = foregroundIn;

        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());

        return new Dimension(fm.stringWidth(text) + 16, fm.getHeight() + 6);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(background);
            g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());

            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            g2d.setColor(foreground);
            g2d.drawString(text, x, y);
        } finally {
            g2d.dispose();
        }
    }
}
