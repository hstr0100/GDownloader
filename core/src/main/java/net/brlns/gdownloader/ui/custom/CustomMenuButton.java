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
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.brlns.gdownloader.ui.GUIManager;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomMenuButton extends JButton {

    @SuppressWarnings("this-escape")
    public CustomMenuButton(String text, @Nullable String iconAsset) {
        setForeground(color(FOREGROUND));
        setBackground(color(MEDIA_CARD));
        setFocusPainted(false);
        setOpaque(true);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setHorizontalAlignment(SwingConstants.LEFT);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        JLabel textLabel = new JLabel(text);
        textLabel.setForeground(color(FOREGROUND));
        textLabel.setHorizontalAlignment(SwingConstants.LEFT);

        if (iconAsset != null) {
            ImageIcon icon = GUIManager.loadIcon(iconAsset, ICON, 16);
            textLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

            JLabel iconLabel = new JLabel(icon);
            contentPanel.add(iconLabel, BorderLayout.WEST);
        }

        contentPanel.add(textLabel, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(color(MEDIA_CARD_HOVER));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(color(MEDIA_CARD));
            }
        });

        super.setContentAreaFilled(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (getModel().isPressed()) {
            g.setColor(color(MENU_ITEM_PRESSED));
        } else if (getModel().isArmed() || getModel().isRollover()) {
            g.setColor(color(MENU_ITEM_ARMED));
        } else {
            g.setColor(getBackground());
        }

        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

}
