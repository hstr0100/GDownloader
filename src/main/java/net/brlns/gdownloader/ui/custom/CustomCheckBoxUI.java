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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.plaf.basic.BasicCheckBoxUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomCheckBoxUI extends BasicCheckBoxUI{

    @Override
    public void installUI(JComponent c){
        super.installUI(c);

        JCheckBox checkBox = (JCheckBox)c;
        checkBox.setOpaque(false);
        checkBox.setFont(UIManager.getFont("CheckBox.font"));
        checkBox.setBackground(UIManager.getColor("CheckBox.background"));
        checkBox.setForeground(UIManager.getColor("CheckBox.foreground"));
        checkBox.setBorder(UIManager.getBorder("CheckBox.border"));
        checkBox.setIcon(new CustomCheckBoxIcon());
        checkBox.setSelectedIcon(new CustomCheckBoxIcon());

        checkBox.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                checkBox.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e){
                checkBox.repaint();
            }
        });
    }

    @Override
    public void paint(Graphics g, JComponent c){
        AbstractButton b = (AbstractButton)c;
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        super.paint(g2, c);
    }

    private class CustomCheckBoxIcon implements Icon{

        private static final int SIZE = 16;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y){
            AbstractButton button = (AbstractButton)c;
            ButtonModel model = button.getModel();

            g.setColor(button.getMousePosition() != null
                ? color(CHECK_BOX_HOVER)
                : color(SLIDER_FOREGROUND));

            if(model.isSelected()){
                g.fillOval(x, y, SIZE, SIZE);
            }else{
                g.drawOval(x, y, SIZE - 1, SIZE - 1);
            }
        }

        @Override
        public int getIconWidth(){
            return SIZE;
        }

        @Override
        public int getIconHeight(){
            return SIZE;
        }
    }
}
