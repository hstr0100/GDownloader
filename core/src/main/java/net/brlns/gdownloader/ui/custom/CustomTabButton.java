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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public class CustomTabButton extends JButton {

    private boolean selected;
    private boolean hovering;

    private Color restBackground;
    private Color selectedBackground;
    private Color hoverBackground;

    @SuppressWarnings("this-escape")
    public CustomTabButton(String displayName) {
        super(displayName);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    setHoveringState(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHoveringState(false);
            }
        });
    }

    public void setColors(Color restIn, Color selectedIn, Color hoverIn) {
        restBackground = restIn;
        selectedBackground = selectedIn;
        hoverBackground = hoverIn;

        setBackground(selected ? selectedBackground : restBackground);
        repaint();
    }

    public void setSelectedState(boolean selectedIn) {
        selected = selectedIn;
        hovering = false;

        if (restBackground != null) {
            setBackground(selected ? selectedBackground : restBackground);
        }

        repaint();
    }

    public void setHoveringState(boolean hoveringIn) {
        hovering = hoveringIn;

        if (restBackground != null) {
            setBackground(hovering ? hoverBackground : (selected ? selectedBackground : restBackground));
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (selected) {
                int padY = 2;
                int padX = padY / 2;
                int arc = 14;

                g2d.setColor(new Color(0, 0, 0, 35));
                g2d.fillRoundRect(padX, padY + 2, getWidth() - padX * 2, getHeight() - padY * 2, arc, arc);

                g2d.setColor(getBackground());
                g2d.fillRoundRect(padX, padY, getWidth() - padX * 2, getHeight() - padY * 2, arc, arc);
            } else if (hovering) {
                int padY = 3;
                int padX = padY / 2;
                int arc = 12;

                g2d.setColor(getBackground());
                g2d.fillRoundRect(padX, padY, getWidth() - padX * 2, getHeight() - padY * 2, arc, arc);
            }
        } finally {
            g2d.dispose();
        }

        super.paintComponent(g);
    }
}
