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
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.*;
import lombok.Data;
import net.brlns.gdownloader.lang.ITranslatable;
import net.brlns.gdownloader.ui.custom.*;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class UIUtils {

    private static final Map<ImageCacheKey, ImageIcon> IMAGE_CACHE = new ConcurrentHashMap<>();

    public static void setComponentAndLabelVisible(JComponent component, boolean visible) {
        JLabel label = (JLabel)component.getClientProperty("associated-label");
        if (label != null) {
            label.setVisible(visible);
        }

        component.setVisible(visible);
    }

    public static void enableComponentsAndLabels(List<JComponent> components, boolean enable) {
        for (JComponent component : components) {
            enableComponentAndLabel(component, enable);
        }
    }

    public static void enableComponentAndLabel(JComponent component, boolean enabled) {
        JLabel label = (JLabel)component.getClientProperty("associated-label");
        if (label != null) {
            enableComponents(label, enabled);
        }

        enableComponents(component, enabled);
    }

    public static void enableComponents(List<Component> components, boolean enable) {
        for (Component component : components) {
            enableComponents(component, enable);
        }
    }

    public static void enableComponents(Component component, boolean enable) {
        component.setEnabled(enable);

        if (component instanceof Container container
            && !(component instanceof CustomTranscodePanel)) {
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

    public static ImageIcon loadIcon(String path, UIColors color) {
        return loadIcon(path, color, 36);
    }

    public static ImageIcon loadIcon(String path, UIColors color, int scale) {
        Color themeColor = color(color);
        ImageCacheKey key = new ImageCacheKey(path, themeColor, scale);

        return IMAGE_CACHE.computeIfAbsent(key, (keyIn) -> {
            try (
                InputStream resourceStream = GUIManager.class.getResourceAsStream(path)) {
                BufferedImage originalImage = ImageIO.read(resourceStream);
                if (originalImage == null) {
                    throw new IOException("Failed to load image: " + path);
                }

                WritableRaster raster = originalImage.getRaster();

                for (int y = 0; y < originalImage.getHeight(); y++) {
                    for (int x = 0; x < originalImage.getWidth(); x++) {
                        int[] pixel = raster.getPixel(x, y, (int[])null);

                        int alpha = pixel[3];

                        if (alpha != 0) {
                            pixel[0] = themeColor.getRed();
                            pixel[1] = themeColor.getGreen();
                            pixel[2] = themeColor.getBlue();
                            pixel[3] = themeColor.getAlpha();

                            raster.setPixel(x, y, pixel);
                        }
                    }
                }

                Image image = originalImage.getScaledInstance(scale, scale, Image.SCALE_SMOOTH);

                ImageIcon icon = new ImageIcon(image);

                return icon;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // https://stackoverflow.com/questions/5147768/scroll-jscrollpane-to-bottom
    public static void scrollPaneToBottom(JScrollPane scrollPane) {
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
            verticalBar.setValue(verticalBar.getMaximum());

            verticalBar.addAdjustmentListener(new AdjustmentListener() {
                boolean firstTime = true;

                @Override
                public void adjustmentValueChanged(AdjustmentEvent e) {
                    if (firstTime) {
                        verticalBar.setValue(verticalBar.getMaximum());
                        firstTime = false;
                    }
                }
            });
        });
    }

    public static String wrapTextInHtml(UIColors fontColor, boolean bold, boolean centerText, String... lines) {
        String textColorHex = Integer.toHexString(color(fontColor).getRGB()).substring(2);

        Font baseFont = UIManager.getFont("Label.font");
        int fontSize = baseFont.getSize();
        String fontFamily = baseFont.getFamily();

        StringBuilder wrappedText = new StringBuilder();

        if (centerText) {
            wrappedText.append("<center>");
        }

        if (bold) {
            wrappedText.append("<b>");
        }

        for (String text : lines) {
            text = text.replace(System.lineSeparator(), "<br>")
                .replace("\n", "<br>")
                .replace("[PLAY]", "►");

            for (String line : text.split("<br>")) {
                if (line.isEmpty()) {
                    wrappedText.append("<br>");
                    continue;
                }

                String[] words = line.split(" ");
                for (String word : words) {
                    wrappedText.append(word).append(" ");
                }

                if (!wrappedText.toString().trim().endsWith("<br>")) {
                    wrappedText.append("<br>");
                }
            }
        }

        String result = wrappedText.toString().trim();
        if (result.endsWith("<br>")) {
            result = result.substring(0, result.length() - 4);
        }

        if (bold) {
            wrappedText.append("</b>");
        }

        if (centerText) {
            wrappedText.append("</center>");
        }

        return String.format("<html><body style='font-family: %s; font-size: %dpt; color: #%s;'>%s</body></html>",
            fontFamily, fontSize, textColorHex, result);
    }

    /**
     * Executes the given {@link Runnable} on the Event Dispatch Thread (EDT).
     * If the current thread is the EDT, the {@code runnable} will be executed immediately.
     * Otherwise, it will be scheduled to run later on the EDT using {@link SwingUtilities#invokeLater}.
     *
     * @param runnable the {@link Runnable} to be executed on the Event Dispatch Thread
     */
    public static void runOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    @Data
    private static class ImageCacheKey {

        private final String path;
        private final Color color;
        private final int scale;
    }
}
