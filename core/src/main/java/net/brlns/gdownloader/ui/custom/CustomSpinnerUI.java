/*
 * Copyright (C) 2025 hstr0100
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicSpinnerUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomSpinnerUI extends BasicSpinnerUI {

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);

        JSpinner spinner = (JSpinner)c;
        spinner.setBackground(color(COMBO_BOX_BACKGROUND));
        spinner.setForeground(color(COMBO_BOX_FOREGROUND));
        spinner.setBorder(BorderFactory.createLineBorder(color(COMBO_BOX_FOREGROUND)));
        spinner.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                spinner.setBackground(color(COMBO_BOX_SELECTION_BACKGROUND));
                spinner.setForeground(color(COMBO_BOX_SELECTION_FOREGROUND));
            }

            @Override
            public void focusLost(FocusEvent e) {
                spinner.setBackground(color(COMBO_BOX_BACKGROUND));
                spinner.setForeground(color(COMBO_BOX_FOREGROUND));
            }
        });
    }

    @Override
    protected Component createNextButton() {
        JButton button = createArrowButton("▲");
        installNextButtonListeners(button);

        return button;
    }

    @Override
    protected Component createPreviousButton() {
        JButton button = createArrowButton("▼");
        installPreviousButtonListeners(button);

        return button;
    }

    private JButton createArrowButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(color(COMBO_BOX_BUTTON_FOREGROUND));
        button.setBorder(BorderFactory.createLineBorder(Color.WHITE));
        button.setForeground(color(COMBO_BOX_BUTTON_BACKGROUND));
        button.setFocusPainted(false);
        button.setOpaque(true);

        return button;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JSpinner spinner = (JSpinner)c;
        Color originalColor = g.getColor();

        g.setColor(spinner.hasFocus()
            ? color(COMBO_BOX_SELECTION_BACKGROUND)
            : color(COMBO_BOX_BACKGROUND));
        g.fillRect(0, 0, spinner.getWidth(), spinner.getHeight());
        g.setColor(originalColor);

        super.paint(g, c);
    }

    @Override
    protected JComponent createEditor() {
        JComponent editor = super.createEditor();
        editor.setBackground(color(COMBO_BOX_BACKGROUND));
        editor.setForeground(color(COMBO_BOX_FOREGROUND));
        editor.setBorder(BorderFactory.createEmptyBorder());

        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField textField = defaultEditor.getTextField();
            textField.setForeground(color(TEXT_AREA_FOREGROUND));
            textField.setBackground(color(TEXT_AREA_BACKGROUND));
            textField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }

        return editor;
    }
}
