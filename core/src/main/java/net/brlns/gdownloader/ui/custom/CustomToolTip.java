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

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicToolTipUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomToolTip extends JToolTip {

    static {
        UIManager.put("ToolTip.background", color(TOOLTIP_BACKGROUND));
        UIManager.put("ToolTip.foreground", color(TOOLTIP_FOREGROUND));
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }

    @SuppressWarnings("this-escape")
    public CustomToolTip() {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @Override
    public void updateUI() {
        setUI(new BasicToolTipUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2d = (Graphics2D)g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(color(TOOLTIP_BACKGROUND));
                g2d.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 10, 10);
                g2d.setColor(color(TOOLTIP_FOREGROUND));
                g2d.drawString(c.getToolTipText(), 5, 15);
            }

            @Override
            public Dimension getPreferredSize(JComponent c) {
                FontMetrics fm = c.getFontMetrics(c.getFont());
                String tipText = c.getToolTipText();
                return new Dimension(fm.stringWidth(tipText) + 10, fm.getHeight() + 5);
            }
        });
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {

    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {

    }
}
