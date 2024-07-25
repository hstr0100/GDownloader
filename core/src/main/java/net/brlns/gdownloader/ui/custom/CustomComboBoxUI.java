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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomComboBoxUI extends BasicComboBoxUI{

    @Override
    protected JButton createArrowButton(){
        JButton button = new JButton();
        button.setText("▼");
        button.setBackground(color(COMBO_BOX_BUTTON_FOREGROUND));
        button.setBorder(BorderFactory.createLineBorder(Color.WHITE));
        button.setForeground(color(COMBO_BOX_BUTTON_BACKGROUND));

        return button;
    }

    @Override
    protected void installListeners(){
        super.installListeners();

        LookAndFeel.uninstallBorder(comboBox);

        comboBox.addFocusListener(new FocusAdapter(){
            @Override
            public void focusGained(FocusEvent e){
                comboBox.setBackground(color(COMBO_BOX_SELECTION_BACKGROUND));
                comboBox.setForeground(color(COMBO_BOX_SELECTION_FOREGROUND));
            }

            @Override
            public void focusLost(FocusEvent e){
                comboBox.setBackground(color(COMBO_BOX_BACKGROUND));
                comboBox.setForeground(color(COMBO_BOX_FOREGROUND));
            }
        });
    }

    @Override
    protected ListCellRenderer<Object> createRenderer(){
        return new DefaultListCellRenderer(){
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if(isSelected){
                    component.setBackground(color(COMBO_BOX_SELECTION_BACKGROUND));
                    component.setForeground(color(COMBO_BOX_SELECTION_FOREGROUND));
                }else if(cellHasFocus){
                    //TODO
                    setBackground(Color.LIGHT_GRAY.darker());
                    setForeground(Color.BLACK);
                }else{
                    component.setBackground(color(COMBO_BOX_BACKGROUND));
                    component.setForeground(color(COMBO_BOX_FOREGROUND));
                }

                return component;
            }
        };
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus){
        Color oldColor = g.getColor();
        g.setColor(comboBox.hasFocus() ? color(COMBO_BOX_SELECTION_BACKGROUND) : color(COMBO_BOX_BACKGROUND));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g.setColor(oldColor);
    }
}
