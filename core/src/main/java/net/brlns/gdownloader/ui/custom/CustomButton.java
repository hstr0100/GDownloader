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

import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JButton;
import lombok.Getter;
import lombok.Setter;
import net.brlns.gdownloader.util.Nullable;

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
        if (getModel().isPressed()) {
            g.setColor(pressedBackgroundColor);
        } else if (getModel().isRollover()) {
            g.setColor(hoverBackgroundColor);
        } else {
            g.setColor(getBackground());
        }

        g.fillRect(0, 0, getWidth(), getHeight());

        super.paintComponent(g);
    }

    @Override
    public void setContentAreaFilled(boolean b) {

    }
}
