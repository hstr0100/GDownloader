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

import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JPanel;
import javax.swing.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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

    @Getter
    private Color backdropColor;

    private Color cachedUnfilledColor;
    private Color cachedFilledColor;
    private Color lastTextColor;
    private Color lastBgColor;
    private Color lastFgColor;

    private int cachedProgressWidth = -1;
    private int cachedBlockX = -1;
    private int cachedWidth = -1;

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
                    bar.updateIndeterminateState(phase);
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
        setOpaque(false);
    }

    public void setBackdropColor(@Nullable Color colorIn) {
        if (Objects.equals(backdropColor, colorIn)) {
            return;
        }

        backdropColor = colorIn;
        setOpaque(colorIn != null);

        repaint();
    }

    protected void updateIndeterminateState(double currentPhase) {
        if (value != -1) {
            return;
        }

        int width = getWidth();
        if (width <= 0) {
            return;
        }

        int blockWidth = width / 6;
        double normalizedPosition = (Math.sin(currentPhase) + 1) / 2;
        int expectedBlockX = (int)(normalizedPosition * (width - blockWidth));

        if (expectedBlockX != cachedBlockX || width != cachedWidth) {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        cachedWidth = width;

        if (backdropColor != null) {
            g2d.setColor(backdropColor);
            g2d.fillRect(0, 0, width, height);
        }

        int arcSize = 8;
        RoundRectangle2D backgroundRect = new RoundRectangle2D.Float(
            0, 0, width, height, arcSize, arcSize);

        g2d.setColor(getBackground());
        g2d.fill(backgroundRect);

        g2d.clip(backgroundRect);

        g2d.setColor(getForeground());

        int progressBarWidth = width;
        int progressWidth = 0;

        if (value == -1) {
            int blockWidth = progressBarWidth / 6;
            // Use a sine curve to slow down near the edges
            double normalizedPosition = (Math.sin(phase) + 1) / 2;
            int blockX = (int)(normalizedPosition * (progressBarWidth - blockWidth));

            cachedBlockX = blockX;

            RoundRectangle2D progressRect = new RoundRectangle2D.Float(
                blockX, 0, blockWidth, height, arcSize, arcSize);
            g2d.fill(progressRect);
        } else {
            // Draw normal progress bar when value is not -1
            progressWidth = (int)(progressBarWidth * (value / 100.0));

            cachedProgressWidth = progressWidth;

            if (progressWidth > 0) {
                if (progressWidth < width) {
                    g2d.fillRect(0, 0, progressWidth, height);
                } else {
                    g2d.fill(backgroundRect);
                }
            }
        }

        if (stringPainted && string != null) {
            String stringToPaint = string.replace(": -1.0%", "");
            Color baseTextColor = textColor != null ? textColor : Color.BLACK;
            Color currentBg = getBackground();

            boolean textColorChanged = !Objects.equals(baseTextColor, lastTextColor);

            if (cachedUnfilledColor == null || textColorChanged || !Objects.equals(currentBg, lastBgColor)) {
                cachedUnfilledColor = getFGColor(baseTextColor, currentBg);
                lastBgColor = currentBg;
            }

            g2d.setFont(FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int x = (width - fm.stringWidth(stringToPaint)) / 2;
            int y = (height + fm.getAscent() - fm.getDescent()) / 2;

            if (value == -1) {
                g2d.setColor(cachedUnfilledColor);
                g2d.drawString(stringToPaint, x, y);
            } else {
                Color currentFg = getForeground();

                if (cachedFilledColor == null || textColorChanged || !Objects.equals(currentFg, lastFgColor)) {
                    cachedFilledColor = getFGColor(baseTextColor, currentFg);
                    lastFgColor = currentFg;
                }

                if (progressWidth < width) {
                    Graphics2D g2dUnfilled = (Graphics2D)g2d.create();
                    g2dUnfilled.clipRect(progressWidth, 0, width - progressWidth, height);
                    g2dUnfilled.setColor(cachedUnfilledColor);
                    g2dUnfilled.drawString(stringToPaint, x, y);
                    g2dUnfilled.dispose();
                }

                if (progressWidth > 0) {
                    Graphics2D g2dFilled = (Graphics2D)g2d.create();
                    g2dFilled.clipRect(0, 0, progressWidth, height);
                    g2dFilled.setColor(cachedFilledColor);
                    g2dFilled.drawString(stringToPaint, x, y);
                    g2dFilled.dispose();
                }
            }

            lastTextColor = baseTextColor;
        }

        g2d.dispose();
    }

    private Color getFGColor(Color text, Color bg) {
        if (text == null) {
            text = Color.BLACK;
        }

        if (bg == null) {
            return text;
        }

        double textLum = calculateLuminance(text);
        double bgLum = calculateLuminance(bg);

        if (getContrast(textLum, bgLum) >= 2.1) {
            return text;
        }

        if (bgLum > 0.4) {
            // we don't use theme-defined colors for progress bars
            return new Color(61, 61, 61);
        } else {
            return Color.WHITE;
        }
    }

    private double calculateLuminance(Color c) {
        double r = linearize(c.getRed() / 255.0);
        double g = linearize(c.getGreen() / 255.0);
        double b = linearize(c.getBlue() / 255.0);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private double linearize(double v) {
        return (v <= 0.03928) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private double getContrast(double lum1, double lum2) {
        return (Math.max(lum1, lum2) + 0.05) / (Math.min(lum1, lum2) + 0.05);
    }

    public void setValue(int valueIn) {
        if (value == valueIn) {
            return;
        }

        synchronized (activeReferences) {
            if (value == -1 && valueIn != -1) {
                activeReferences.remove(this);
                if (activeReferences.isEmpty()) {
                    bounceTimer.stop();
                }
            } else if (value != -1 && valueIn == -1) {
                activeReferences.add(this);
                if (activeReferences.size() == 1) {
                    bounceTimer.start();
                }
            }
        }

        value = valueIn;

        if (value != -1) {
            int width = getWidth();
            if (width > 0) {
                int expectedProgressWidth = (int)(width * (value / 100.0));

                if (expectedProgressWidth != cachedProgressWidth || width != cachedWidth) {
                    repaint();
                }
            } else {
                repaint();
            }
        } else {
            repaint();
        }
    }

    public void setString(String stringIn) {
        if (Objects.equals(string, stringIn)) {
            return;
        }

        string = stringIn;

        repaint();
    }

    public void setTextColor(Color textColorIn) {
        if (Objects.equals(textColor, textColorIn)) {
            return;
        }

        textColor = textColorIn;

        repaint();
    }
}
