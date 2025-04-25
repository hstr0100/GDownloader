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
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import lombok.RequiredArgsConstructor;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;

import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.GUIManager.createDialogButton;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class WelcomeScreen {

    private final GDownloader main;
    private final GUIManager manager;
    private final Settings settings;

    private JFrame frame;

    private JLabel ffmpegWarningLabel;

    public WelcomeScreen(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;
        settings = GDownloader.OBJECT_MAPPER.convertValue(main.getConfig(), Settings.class);
    }

    public void createAndShowGUI() {
        runOnEDT(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
                frame.requestFocus();
                return;
            }

            frame = new JFrame(l10n("gui.welcome-screen.title")) {
                @Override
                public void dispose() {
                    frame = null;

                    super.dispose();
                }
            };

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.setSize(780, 570);
            frame.setLayout(new BorderLayout());
            frame.setResizable(false);
            frame.setMinimumSize(new Dimension(frame.getWidth(), frame.getHeight()));
            frame.setIconImage(main.getGuiManager().getAppIcon());

            initComponents();

            frame.pack();
            frame.setLocationRelativeTo(null);

            frame.setVisible(true);
        });
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(20, 30, 20, 30));
        mainPanel.setBackground(color(BACKGROUND));
        frame.setContentPane(mainPanel);

        JPanel topPanel = createTopPanel();
        JPanel bottomPanel = createBottomPanel();

        mainPanel.add(topPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(color(BACKGROUND));

        JPanel headerPanel = createHeaderPanel();

        topPanel.add(headerPanel);

        return topPanel;
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setBackground(color(BACKGROUND));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textPanel = createHeaderTextPanel();
        JPanel iconPanel = createHeaderIconPanel();

        headerPanel.add(textPanel);
        headerPanel.add(Box.createHorizontalGlue());
        headerPanel.add(iconPanel);

        return headerPanel;
    }

    private JPanel createHeaderTextPanel() {
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(color(BACKGROUND));
        textPanel.setAlignmentY(Component.TOP_ALIGNMENT);

        JLabel titleLabel = new JLabel(l10n("gui.welcome-screen.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(24f));
        titleLabel.setForeground(color(FOREGROUND));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JEditorPane descriptionPane = createDescriptionEditorPane();

        textPanel.add(titleLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        textPanel.add(descriptionPane);
        textPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        textPanel.add(createLink(
            l10n("gui.welcome-screen.visit-homepage"),
            "https://github.com/hstr0100/GDownloader"
        ));

        return textPanel;
    }

    private JEditorPane createDescriptionEditorPane() {
        JEditorPane descriptionPane = new JEditorPane();
        descriptionPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        descriptionPane.setContentType("text/html");
        descriptionPane.setEditable(false);
        descriptionPane.setOpaque(false);
        descriptionPane.setBorder(null);
        descriptionPane.setForeground(color(FOREGROUND));

        String descriptionHtml = buildDescriptionHtml(l10n("gui.welcome-screen.description"));
        descriptionPane.setText(descriptionHtml);

        descriptionPane.addHyperlinkListener((e) -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                main.openUrlInBrowser(e.getURL().toString());
            }
        });

        Dimension preferredSize = descriptionPane.getPreferredSize();
        Dimension fixedSize = new Dimension(preferredSize.width, preferredSize.height);
        descriptionPane.setPreferredSize(fixedSize);
        descriptionPane.setMinimumSize(fixedSize);
        descriptionPane.setMaximumSize(fixedSize);

        return descriptionPane;
    }

    private JPanel createHeaderIconPanel() {
        JPanel iconPanel = new JPanel();
        iconPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        iconPanel.setBackground(color(BACKGROUND));
        iconPanel.setAlignmentY(Component.TOP_ALIGNMENT);

        Image appIcon = manager.getAppIcon();
        if (appIcon == null) {
            throw new IllegalStateException();
        }

        Image scaledImage = appIcon.getScaledInstance(120, 120, Image.SCALE_SMOOTH);

        JLabel iconLabel = new JLabel();
        iconLabel.setIcon(new ImageIcon(scaledImage));
        iconPanel.add(iconLabel);

        return iconPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(color(BACKGROUND));
        bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel warningCardContainer = createWarningCardPanel();
        JPanel optionsPanel = createOptionsPanel();
        JPanel buttonPanel = createButtonPanel();

        bottomPanel.add(warningCardContainer);
        bottomPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        bottomPanel.add(optionsPanel);
        bottomPanel.add(Box.createVerticalGlue());
        bottomPanel.add(buttonPanel);

        return bottomPanel;
    }

    private JPanel createWarningCardPanel() {
        JPanel warningCardContainer = new JPanel();
        warningCardContainer.setLayout(new BorderLayout());
        warningCardContainer.setBackground(color(BACKGROUND));
        warningCardContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedPanel warningPanel = new RoundedPanel(15, color(MEDIA_CARD));
        warningPanel.setLayout(new BoxLayout(warningPanel, BoxLayout.X_AXIS));

        Border innerPaddingBorder = new EmptyBorder(5, 15, 5, 15);
        Border warningPanelBorder = new AccentBorder(color(TOAST_WARNING), 6, 15, innerPaddingBorder);
        warningPanel.setBorder(warningPanelBorder);

        JLabel warningIconLabel = createWarningIconLabel();
        JPanel textContainer = createWarningTextPanel();

        warningPanel.add(warningIconLabel);
        warningPanel.add(Box.createRigidArea(new Dimension(15, 0)));
        warningPanel.add(textContainer);
        warningPanel.add(Box.createHorizontalGlue());

        warningCardContainer.add(warningPanel, BorderLayout.CENTER);

        Dimension fixedWarningSize = warningPanel.getPreferredSize();
        warningCardContainer.setPreferredSize(fixedWarningSize);
        warningCardContainer.setMinimumSize(fixedWarningSize);
        warningCardContainer.setMaximumSize(fixedWarningSize);

        return warningCardContainer;
    }

    private JLabel createWarningIconLabel() {
        JLabel warningIconLabel = new JLabel();
        warningIconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        warningIconLabel.setIcon(loadIcon(
            "/assets/toast-warning.png",
            TOAST_WARNING,
            24
        ));

        return warningIconLabel;
    }

    private JPanel createWarningTextPanel() {
        JPanel textContainer = new JPanel();
        textContainer.setLayout(new BoxLayout(textContainer, BoxLayout.Y_AXIS));
        textContainer.setOpaque(false);
        textContainer.setAlignmentY(Component.CENTER_ALIGNMENT);

        String[] lines = l10n("gui.welcome-screen.network_required").split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                textContainer.add(Box.createRigidArea(new Dimension(0, 5)));
            }

            JLabel warningLabel = new JLabel(lines[i]);
            warningLabel.setForeground(color(FOREGROUND));
            warningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            textContainer.add(warningLabel);
        }

        return textContainer;
    }

    private JPanel createOptionsPanel() {
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBackground(color(BACKGROUND));
        optionsPanel.setBorder(new EmptyBorder(5, 0, 15, 0));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox galleryDlCheckbox = new JCheckBox(
            l10n("settings.downloader.gallery_dl.enabled").replace(":", ""));
        customizeCheckBox(galleryDlCheckbox,
            settings::isGalleryDlEnabled,
            settings::setGalleryDlEnabled);

        JCheckBox spotDlCheckbox = new JCheckBox(
            l10n("settings.downloader.spotdl.enabled").replace(":", ""));
        customizeCheckBox(spotDlCheckbox,
            settings::isSpotDLEnabled,
            settings::setSpotDLEnabled);

        JCheckBox autoUpdateCheckbox = new JCheckBox(
            l10n("gui.welcome-screen.allow_automatic_updates"));
        customizeCheckBox(autoUpdateCheckbox,
            settings::isAutomaticUpdates,
            settings::setAutomaticUpdates);

        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        optionsPanel.add(galleryDlCheckbox);
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        optionsPanel.add(spotDlCheckbox);
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        optionsPanel.add(autoUpdateCheckbox);

        return optionsPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BorderLayout());
        buttonPanel.setBackground(color(BACKGROUND));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        ffmpegWarningLabel = createFFmpegWarningLabel();

        JButton continueButton = createDialogButton(
            l10n("gui.welcome-screen.continue"),
            BUTTON_BACKGROUND,
            BUTTON_FOREGROUND,
            BUTTON_HOVER
        );
        continueButton.setPreferredSize(new Dimension(130, 35));
        continueButton.addActionListener(e -> {
            main.updateConfig(settings);
            frame.dispose();

            main.initMainWindow();
        });

        buttonPanel.add(ffmpegWarningLabel, BorderLayout.WEST);
        buttonPanel.add(continueButton, BorderLayout.EAST);

        return buttonPanel;
    }

    private JLabel createFFmpegWarningLabel() {
        JLabel label = new JLabel(l10n("gui.status.ffmpeg_not_detected"));
        label.setForeground(Color.RED);
        label.setIconTextGap(5);
        label.setIcon(loadIcon(
            "/assets/toast-error.png",
            TOAST_ERROR,
            16
        ));

        label.setVisible(!GDownloader.isWindows()
            && !main.getFfmpegTranscoder().hasFFmpeg());

        return label;
    }

    private String buildDescriptionHtml(String template) {
        template = template.replace("\n", "<br>");

        String textColorHex = Integer.toHexString(color(FOREGROUND).getRGB()).substring(2);
        String linkColorHex = Integer.toHexString(color(LINK_COLOR).getRGB()).substring(2);

        Font baseFont = UIManager.getFont("Label.font");
        int fontSize = baseFont.getSize();
        String fontFamily = baseFont.getFamily();

        List<DownloaderIdEnum> downloaders = Arrays.stream(DownloaderIdEnum.values())
            .filter(d -> d != DownloaderIdEnum.DIRECT_HTTP)
            .collect(Collectors.toUnmodifiableList());

        for (int i = 0; i < downloaders.size(); i++) {
            DownloaderIdEnum downloader = downloaders.get(i);
            String placeholder = "{" + i + "}";
            String linkHtml = String.format("<a href=\"%s\" style='color: #%s;'>%s</a>",
                downloader.getHomepage(),
                linkColorHex,
                downloader.getDisplayName());

            template = template.replace(placeholder, linkHtml);
        }

        return String.format("<html><body style='font-family: %s; font-size: %dpt; color: #%s;'><b>%s</b></body></html>",
            fontFamily, fontSize, textColorHex, template);
    }

    private void customizeCheckBox(JCheckBox checkBox, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        checkBox.setUI(new CustomCheckBoxUI());
        checkBox.setBackground(color(BACKGROUND));
        checkBox.setForeground(color(FOREGROUND));
        checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkBox.setOpaque(false);
        checkBox.setSelected(getter.get());
        checkBox.addItemListener(e -> setter.accept(checkBox.isSelected()));
    }

    private JLabel createLink(String title, String url) {
        JLabel label = new JLabel("<html><u>" + title + "</u></html>");
        label.setForeground(color(LINK_COLOR));
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                main.openUrlInBrowser(url);
            }
        });

        return label;
    }

    private static class RoundedPanel extends JPanel {

        private final int arcRadius;
        private final Color backgroundColor;

        public RoundedPanel(int radius, Color bgColor) {
            super();

            arcRadius = radius;
            backgroundColor = bgColor;

            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D)g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(backgroundColor);

            g2d.fill(new RoundRectangle2D.Float(
                0, 0, getWidth() - 1, getHeight() - 1, arcRadius, arcRadius));
            g2d.dispose();
        }

        @Override
        public Insets getInsets() {
            Border border = getBorder();
            if (border != null) {
                return border.getBorderInsets(this);
            }

            return super.getInsets();
        }

        @Override
        public Dimension getPreferredSize() {
            LayoutManager layout = getLayout();
            if (layout != null) {
                Insets insets = getInsets();
                Dimension size = layout.preferredLayoutSize(this);

                return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
            }

            return super.getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            LayoutManager layout = getLayout();
            if (layout != null) {
                Insets insets = getInsets();
                Dimension size = layout.minimumLayoutSize(this);

                return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
            }

            return super.getMinimumSize();
        }

        @Override
        public Dimension getMaximumSize() {
            LayoutManager layout = getLayout();
            if (layout != null) {
                Dimension preferred = getPreferredSize();

                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }

            return super.getMaximumSize();
        }
    }

    @RequiredArgsConstructor
    private static class AccentBorder extends AbstractBorder {

        private final Color accentColor;
        private final int accentWidth;
        private final int arcRadius;
        private final Border innerBorder;

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D)g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Shape roundedShape = new RoundRectangle2D.Float(
                x, y, width - 1, height - 1, arcRadius, arcRadius);
            g2d.clip(roundedShape);

            g2d.setColor(accentColor);
            g2d.fillRect(x, y, accentWidth, height);

            g2d.dispose();

            if (innerBorder != null) {
                Insets insets = getBorderInsets(c);
                innerBorder.paintBorder(c, g, x + insets.left, y + insets.top,
                    width - insets.left - insets.right, height - insets.top - insets.bottom);
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            Insets innerInsets = innerBorder != null
                ? innerBorder.getBorderInsets(c)
                : new Insets(0, 0, 0, 0);

            return new Insets(
                innerInsets.top, innerInsets.left + accentWidth,
                innerInsets.bottom, innerInsets.right
            );
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets calculatedInsets = getBorderInsets(c);

            insets.set(
                calculatedInsets.top, calculatedInsets.left,
                calculatedInsets.bottom, calculatedInsets.right
            );

            return insets;
        }

        @Override
        public boolean isBorderOpaque() {
            return true;
        }
    }
}
