/*
 * Copyright (C) 2024 hstr0100
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
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JPanel;
import javax.swing.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.Nullable;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CustomProgressBar extends JPanel {

    private static final Font FONT = new Font("Dialog", Font.BOLD, 14);

    @Getter
    @Setter
    private boolean stringPainted;

    @Getter
    private Color textColor;

    @Getter
    private String string;

    @Getter
    private int value = 0;

    private static double phase = 0;
    private static final Timer bounceTimer;
    private static final Set<CustomProgressBar> activeReferences
        = Collections.newSetFromMap(new WeakHashMap<>());

    static {
        bounceTimer = new Timer(10, e -> {
            phase += 0.05;
            if (phase > 2 * Math.PI) {
                phase -= 2 * Math.PI;
            }

            synchronized (activeReferences) {
                for (CustomProgressBar bar : activeReferences) {
                    bar.repaint();
                }
            }
        });
    }

    public CustomProgressBar() {
        this(null);
    }

    @SuppressWarnings("this-escape")
    public CustomProgressBar(@Nullable Color textColorIn) {
        textColor = textColorIn;

        setPreferredSize(new Dimension(300, 20));
        setDoubleBuffered(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(getForeground());

        int progressBarWidth = getWidth();
        int blockWidth = progressBarWidth / 6; // 1 / 6

        if (value == -1) {
            // Use a sine curve to slow down near the edges
            double normalizedPosition = (Math.sin(phase) + 1) / 2;
            int blockX = (int)(normalizedPosition * (progressBarWidth - blockWidth));

            g2d.fillRect(blockX, 0, blockWidth, getHeight());
        } else {
            // Draw normal progress bar when value is not -1
            int width = (int)(progressBarWidth * (value / 100.0));
            g2d.fillRect(0, 0, width, getHeight());
        }

        if (stringPainted) {
            String stringToPaint = string.replace("-1.0%", "N/A");
            g2d.setColor(textColor);
            g2d.setFont(FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(stringToPaint)) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(stringToPaint, x, y);
        }
    }

    public void setValue(int valueIn) {
        synchronized (activeReferences) {
            if (this.value == -1 && valueIn != -1) {
                activeReferences.remove(this);
                if (activeReferences.isEmpty()) {
                    bounceTimer.stop();
                }
            } else if (this.value != -1 && valueIn == -1) {
                activeReferences.add(this);
                if (activeReferences.size() == 1) {
                    bounceTimer.start();
                }
            }
        }

        value = valueIn;

        repaint();
    }

    public void setString(String stringIn) {
        string = stringIn;

        repaint();
    }

    public void setTextColor(Color textColorIn) {
        textColor = textColorIn;

        repaint();
    }
}
