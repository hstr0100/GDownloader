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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomComboBoxUI extends BasicComboBoxUI{

    @Override
    protected JButton createArrowButton(){
        JButton button = new JButton();
        button.setText("▼");
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(Color.WHITE));
        button.setForeground(Color.DARK_GRAY);

        return button;
    }

    @Override
    public ListCellRenderer<? super Object> createRenderer(){
        return new CustomComboBoxRenderer();
    }

    public static class CustomComboBoxRenderer extends BasicComboBoxRenderer{

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

//            if(isSelected){
//                setBackground(Color.LIGHT_GRAY);
//                setForeground(Color.BLACK);
//            }else if(cellHasFocus){
//                setBackground(Color.LIGHT_GRAY.darker());
//                setForeground(Color.BLACK);
//            }else{
//                setBackground(list.getBackground());
//                setForeground(list.getForeground());
//            }
            return this;
        }
    }
}
