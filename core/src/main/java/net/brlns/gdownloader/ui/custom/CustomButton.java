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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomButton extends JButton {

    @Getter
    @Setter
    private Color hoverBackgroundColor;

    @Getter
    @Setter
    private Color pressedBackgroundColor;

    @SuppressWarnings("this-escape")
    public CustomButton(@Nullable String text,
        Color hoverBackgroundColorIn, Color pressedBackgroundColorIn) {
        super(text);

        hoverBackgroundColor = hoverBackgroundColorIn;
        pressedBackgroundColor = pressedBackgroundColorIn;

        super.setContentAreaFilled(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (getModel().isPressed()) {
            g2d.setColor(pressedBackgroundColor);
        } else if (getModel().isRollover()) {
            g2d.setColor(hoverBackgroundColor);
        } else {
            g2d.setColor(getBackground());
        }

        int arcSize = 8;
        RoundRectangle2D backgroundRect = new RoundRectangle2D.Float(
            0, 0, getWidth(), getHeight(), arcSize, arcSize);

        g2d.fill(backgroundRect);

        g2d.setClip(backgroundRect);

        super.paintComponent(g2d);

        g2d.dispose();
    }

    @Override
    public void setContentAreaFilled(boolean b) {

    }
}
