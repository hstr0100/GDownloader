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

import jakarta.annotation.PreDestroy;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import lombok.Getter;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.GUIManager;

import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class CustomMediaCardUI {

    public static final int THUMBNAIL_WIDTH = 170;
    public static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    private final GUIManager manager;
    private final JFrame parent;
    private final Runnable onClose;

    @Getter
    private JPanel card;
    @Getter
    private JLabel dragLabel;
    private Dimension cardMaximumSize;
    @Getter
    private CustomDynamicLabel mediaNameLabel;
    private CustomThumbnailPanel thumbnailPanel;
    private CustomProgressBar progressBar;

    private WindowStateListener windowStateListener;
    private ComponentAdapter componentResizeListener;

    public CustomMediaCardUI(GUIManager managerIn, JFrame parentIn, Runnable onCloseIn) {
        manager = managerIn;
        parent = parentIn;
        onClose = onCloseIn;

        initComponents();
    }

    @PreDestroy
    public void removeListeners() {
        parent.removeComponentListener(mediaNameLabel.getListener());

        if (windowStateListener != null) {
            parent.removeWindowStateListener(windowStateListener);
        }

        if (componentResizeListener != null) {
            parent.removeComponentListener(componentResizeListener);
        }
    }

    private void initComponents() {
        card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = null;
                try {
                    g2d = (Graphics2D)g.create();

                    int arcSize = 10;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(getBackground());
                    g2d.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, arcSize, arcSize);
                } finally {
                    if (g2d != null) {
                        g2d.dispose();
                    }
                }
            }

            @Override
            public Dimension getMaximumSize() {
                int availableWidth = parent.getWidth() - getInsets().left - getInsets().right;
                return new Dimension(availableWidth, super.getMaximumSize().height);
            }
        };

        card.setOpaque(false);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 5));
        card.setBackground(color(MEDIA_CARD));

        int fontSize = manager.getMain().getConfig().getFontSize();
        cardMaximumSize = new Dimension(0, fontSize >= 15 ? 150 + (fontSize - 15) * 3 : 135);
        card.setMaximumSize(cardMaximumSize);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;

        // Dragidy-draggy-nub-thingy
        JPanel dragPanel = new JPanel(new BorderLayout());
        dragPanel.setPreferredSize(new Dimension(24, 24));
        dragPanel.setMinimumSize(new Dimension(24, 24));
        dragPanel.setMaximumSize(new Dimension(24, 24));
        dragPanel.setBackground(new Color(0, 0, 0, 0));

        ImageIcon dragIcon = loadIcon("/assets/drag.png", ICON, 24);
        dragLabel = new JLabel(dragIcon);
        dragLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dragPanel.add(dragLabel, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(dragPanel, gbc);

        // Thumbnail
        thumbnailPanel = new CustomThumbnailPanel();
        thumbnailPanel.setPreferredSize(new Dimension(
            CustomMediaCardUI.THUMBNAIL_WIDTH, CustomMediaCardUI.THUMBNAIL_HEIGHT));
        thumbnailPanel.setMinimumSize(new Dimension(
            CustomMediaCardUI.THUMBNAIL_WIDTH, CustomMediaCardUI.THUMBNAIL_HEIGHT));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
        thumbnailPanel.setPlaceholderIcon(DownloadTypeEnum.ALL);
        thumbnailPanel.setPriorityIcon(DownloadPriorityEnum.NORMAL);

        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        card.add(thumbnailPanel, gbc);

        mediaNameLabel = new CustomDynamicLabel();
        mediaNameLabel.setForeground(color(FOREGROUND));
        gbc.insets = new Insets(10, 10, 5, 10);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        card.add(mediaNameLabel, gbc);

        parent.addComponentListener(mediaNameLabel.getListener());

        progressBar = new CustomProgressBar(Color.WHITE);
        progressBar.setValue(100);
        progressBar.setStringPainted(true);
        progressBar.setString(l10n("enums.download_status.queued"));
        progressBar.setForeground(Color.GRAY);
        progressBar.setBackground(Color.GRAY);
        //progressBar.setBorderPainted(false);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));

        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.BOTH;
        card.add(progressBar, gbc);

        JButton closeButton = createIconButton(
            loadIcon("/assets/x-mark.png", ICON, 16),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 16),
            "gui.remove_from_queue.tooltip",
            e -> onClose.run());
        closeButton.setPreferredSize(new Dimension(16, 16));

        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        card.add(closeButton, gbc);

        windowStateListener = (WindowEvent e) -> {
            updateScale(calculateScale(parent.getWidth()));
        };
        parent.addWindowStateListener(windowStateListener);

        componentResizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateScale(calculateScale(parent.getWidth()));
            }
        };
        parent.addComponentListener(componentResizeListener);
    }

    public void updateLabel(String... labelText) {
        assert SwingUtilities.isEventDispatchThread();
        mediaNameLabel.setFullText(labelText);
    }

    public void updateTooltip(String tooltipText) {
        assert SwingUtilities.isEventDispatchThread();
        mediaNameLabel.setToolTipText(tooltipText);
    }

    public void updateThumbnailTooltip(String tooltipText) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setToolTipText(tooltipText);
    }

    public void updateProgressBar(double percentage, String text, Color backgroundColor, Color textColor) {
        assert SwingUtilities.isEventDispatchThread();
        progressBar.setValue((int)percentage);
        progressBar.setString(text);
        progressBar.setForeground(backgroundColor);
        progressBar.setTextColor(textColor);
    }

    public void updateThumbnail(BufferedImage img, long duration) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setImageAndDuration(img, duration);
    }

    public void updatePlaceholderIcon(DownloadTypeEnum downloadType) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setPlaceholderIcon(downloadType);
    }

    public void updatePriorityIcon(DownloadPriorityEnum downloadPriority) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setPriorityIcon(downloadPriority);
    }

    public void updateScale(double factor) {
        assert SwingUtilities.isEventDispatchThread();
        Dimension thumbDimension = new Dimension(
            (int)(THUMBNAIL_WIDTH * factor),
            (int)(THUMBNAIL_HEIGHT * factor));

        Dimension cardDimension = new Dimension(
            (int)(cardMaximumSize.getWidth() * factor),
            (int)(cardMaximumSize.getHeight() * factor));

        card.setMaximumSize(cardDimension);
        thumbnailPanel.setPreferredSize(thumbDimension);
        thumbnailPanel.setMinimumSize(thumbDimension);
    }

    private double calculateScale(int panelWidth) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gs = ge.getDefaultScreenDevice();
        Rectangle screenBounds = gs.getDefaultConfiguration().getBounds();
        double screenWidth = screenBounds.getWidth();

        double targetWidth = screenWidth * 0.9;
        double scaleFactor = (panelWidth >= targetWidth) ? 1.2 : 1;

        return scaleFactor;
    }
}
