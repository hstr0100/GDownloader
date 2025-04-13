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
package net.brlns.gdownloader.ui;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import net.brlns.gdownloader.lang.ITranslatable;
import net.brlns.gdownloader.ui.custom.*;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class UIUtils {

    public static void setComponentAndLabelVisible(JComponent component, boolean visible) {
        JLabel label = (JLabel)component.getClientProperty("associated-label");
        label.setVisible(visible);
        component.setVisible(visible);
    }

    public static void enableComponentsAndLabels(List<JComponent> components, boolean enable) {
        for (JComponent component : components) {
            enableComponentAndLabel(component, enable);
        }
    }

    public static void enableComponentAndLabel(JComponent component, boolean enabled) {
        JLabel label = (JLabel)component.getClientProperty("associated-label");
        enableComponents(label, enabled);
        enableComponents(component, enabled);
    }

    public static void enableComponents(List<Component> components, boolean enable) {
        for (Component component : components) {
            enableComponents(component, enable);
        }
    }

    public static void enableComponents(Component component, boolean enable) {
        component.setEnabled(enable);

        if (component instanceof Container container) {
            for (Component c : container.getComponents()) {
                enableComponents(c, enable);
            }
        }
    }

    public static JLabel createLabel(String text, UIColors uiColor) {
        JLabel label = new JLabel(l10n(text));
        label.setForeground(color(uiColor));

        return label;
    }

    public static JButton createButton(String text, String tooltipText,
        UIColors backgroundColor, UIColors textColor, UIColors hoverColor) {
        CustomButton button = new CustomButton(l10n(text),
            color(hoverColor),
            color(hoverColor).brighter());

        button.setToolTipText(l10n(tooltipText));

        button.setFocusPainted(false);
        button.setForeground(color(textColor));
        button.setBackground(color(backgroundColor));
        button.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));

        return button;
    }

    public static void customizeComboBox(JComboBox<?> component) {
        component.setUI(new CustomComboBoxUI());
    }

    public static void customizeL10nComboBox(JComboBox<?> component) {
        component.setUI(new CustomComboBoxUI());
        setL10nRenderer(component);
    }

    public static void customizeComponent(JComponent component, UIColors backgroundColor, UIColors textColor) {
        component.setForeground(color(textColor));
        component.setBackground(color(backgroundColor));

        switch (component) {
            case JCheckBox jCheckBox ->
                jCheckBox.setUI(new CustomCheckBoxUI());
            case JSpinner jSpinner ->
                jSpinner.setUI(new CustomSpinnerUI());
            default -> {
            }
        }
    }

    public static void customizeSlider(JSlider slider, UIColors backgroundColor, UIColors textColor) {
        slider.setForeground(color(textColor));
        slider.setBackground(color(backgroundColor));
        slider.setOpaque(true);
        slider.setBorder(BorderFactory.createEmptyBorder());
        slider.setUI(new CustomSliderUI(slider));
    }

    public static void setL10nRenderer(JComboBox<?> component) {
        component.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                try {
                    if (value != null) {
                        ITranslatable translatable = (ITranslatable)value;
                        setText(translatable.getDisplayName());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Unable to call getDisplayName() on " + value, e);
                }

                return this;
            }
        });
    }

}
