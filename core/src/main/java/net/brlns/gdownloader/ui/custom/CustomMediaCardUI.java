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
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.mediacard.MediaCard.StartButtonMode;
import net.brlns.gdownloader.ui.menu.RightClickMenu;

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
    private final Runnable onInfoClick;
    private final Runnable onStartClick;
    private final Runnable onFormatsClick;

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

    @Getter
    private JButton closeButton;

    private Component topStrut;
    private Component bottomStrut;

    @Getter
    private JButton infoButton;

    @Getter
    private JButton startButton;

    @Getter
    private JButton formatsButton;

    @Getter
    private JButton moreButton;

    private JWindow moreOptionsWindow;

    private final AtomicReference<StartButtonState> startButtonState = new AtomicReference<>();

    public CustomMediaCardUI(GUIManager managerIn, JFrame parentIn,
        @NonNull Runnable onCloseIn, @NonNull Runnable onInfoClickIn,
        @NonNull Runnable onStartClickIn, @NonNull Runnable onFormatsClickIn) {
        manager = managerIn;
        parent = parentIn;
        onClose = onCloseIn;
        onInfoClick = onInfoClickIn;
        onStartClick = onStartClickIn;
        onFormatsClick = onFormatsClickIn;

        initComponents();
    }

    @PreDestroy
    public void removeListeners() {
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
                Graphics2D g2d = (Graphics2D)g.create();
                try {
                    g2d.setColor(color(BACKGROUND));
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(getBackground());
                    g2d.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 10, 10);
                } finally {
                    g2d.dispose();
                }
            }

            @Override
            public Dimension getMaximumSize() {
                int availableWidth = parent.getWidth() - getInsets().left - getInsets().right;
                return new Dimension(availableWidth, super.getMaximumSize().height);
            }
        };

        card.setOpaque(true);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 5));
        card.setBackground(color(MEDIA_CARD));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

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
        gbc.weighty = 1;
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
        gbc.weighty = 1;
        card.add(thumbnailPanel, gbc);

        mediaNameLabel = new CustomDynamicLabel();
        mediaNameLabel.setForeground(color(FOREGROUND));
        gbc.insets = new Insets(10, 10, 5, 10);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        card.add(mediaNameLabel, gbc);

        progressBar = new CustomProgressBar(Color.WHITE);
        progressBar.setValue(100);
        progressBar.setStringPainted(true);
        progressBar.setString(l10n("enums.download_status.queued"));
        progressBar.setForeground(Color.GRAY);
        progressBar.setBackground(Color.GRAY);
        //progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(300, 21));
        progressBar.setMinimumSize(new Dimension(10, 21));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));

        gbc.insets = new Insets(9, 10, 11, 10);
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        card.add(progressBar, gbc);

        JPanel controlPanel = new JPanel();
        controlPanel.setOpaque(false);
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        infoButton = createIconButton(
            loadIcon("/assets/toast-info.png", ICON, 16),
            loadIcon("/assets/toast-info.png", ICON_HOVER, 16),
            null,
            e -> {
                closeMoreOptionsMenu();
                onInfoClick.run();
            });
        buildMenuRow(infoButton, "gui.view_media_info");

        formatsButton = createIconButton(
            loadIcon("/assets/formats.png", ICON, 16),
            loadIcon("/assets/formats.png", ICON_HOVER, 16),
            null,
            e -> {
                closeMoreOptionsMenu();
                onFormatsClick.run();
            });
        buildMenuRow(formatsButton, "gui.view_available_formats");

        moreButton = createIconButton(
            loadIcon("/assets/more.png", ICON, 16),
            loadIcon("/assets/more.png", ICON_ACTIVE, 16),
            "gui.more_options",
            e -> showMoreOptionsMenu());

        moreButton.setPreferredSize(new Dimension(16, 16));
        moreButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        closeButton = createIconButton(
            loadIcon("/assets/x-mark.png", ICON, 16),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 16),
            "gui.remove_from_queue.tooltip",
            e -> onClose.run());
        closeButton.setPreferredSize(new Dimension(16, 16));
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        topStrut = Box.createVerticalStrut(21);
        bottomStrut = Box.createVerticalStrut(21);

        StartButtonState defaultButtonState = new StartButtonState(
            loadIcon("/assets/play.png", ICON, 16),
            loadIcon("/assets/play.png", QUEUE_ACTIVE_ICON, 16),
            "gui.force_download_start");
        startButtonState.set(defaultButtonState);

        startButton = new JButton(defaultButtonState.getIcon());
        startButton.setUI(new BasicButtonUI());
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        startButton.setContentAreaFilled(false);
        startButton.setBorderPainted(false);
        startButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                StartButtonState state = startButtonState.get();
                startButton.setIcon(state.getHoverIcon());
                startButton.setToolTipText(l10n(state.getTooltipKey()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                StartButtonState state = startButtonState.get();
                startButton.setIcon(state.getIcon());
                startButton.setToolTipText(l10n(state.getTooltipKey()));
            }
        });
        startButton.addActionListener(e -> onStartClick.run());

        startButton.setToolTipText(l10n(defaultButtonState.getTooltipKey()));

        startButton.setPreferredSize(new Dimension(16, 16));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        controlPanel.add(Box.createVerticalGlue());
        controlPanel.add(moreButton);
        controlPanel.add(topStrut);
        controlPanel.add(closeButton);
        controlPanel.add(bottomStrut);
        controlPanel.add(startButton);
        controlPanel.add(Box.createVerticalGlue());

        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.CENTER;
        card.add(controlPanel, gbc);

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

    private void buildMenuRow(JButton button, String labelKey) {
        button.setText(" " + l10n(labelKey));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(10);
        button.setForeground(color(FOREGROUND));
        button.setBackground(color(MEDIA_CARD));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 18));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(color(MEDIA_CARD_THUMBNAIL));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color(MEDIA_CARD));
            }
        });
    }

    private void showMoreOptionsMenu() {
        if (moreOptionsWindow == null) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(true);
            panel.setBackground(color(MEDIA_CARD));
            panel.setBorder(BorderFactory.createLineBorder(color(BACKGROUND), 1));
            panel.add(infoButton);
            panel.add(formatsButton);

            moreOptionsWindow = new JWindow(parent);
            moreOptionsWindow.setBackground(color(MEDIA_CARD));
            moreOptionsWindow.setLayout(new BorderLayout());
            moreOptionsWindow.add(panel, BorderLayout.CENTER);
            moreOptionsWindow.pack();
        }

        RightClickMenu.positionPopupOnScreen(moreOptionsWindow,
            moreButton, -moreOptionsWindow.getWidth(), 0);
        RightClickMenu.attachAutoDismiss(moreOptionsWindow);
        moreOptionsWindow.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            moreOptionsWindow.revalidate();
            moreOptionsWindow.repaint();
        });
    }

    private void closeMoreOptionsMenu() {
        if (moreOptionsWindow != null) {
            moreOptionsWindow.setVisible(false);
        }
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

    public void updateLiveStatus(boolean live) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setLive(live);
    }

    public void updateScale(double factor) {
        assert SwingUtilities.isEventDispatchThread();
        Dimension thumbDimension = new Dimension(
            (int)(THUMBNAIL_WIDTH * factor),
            (int)(THUMBNAIL_HEIGHT * factor));

        Dimension cardDimension = new Dimension(
            (int)(cardMaximumSize.getWidth() * factor),
            (int)(cardMaximumSize.getHeight() * factor));

        int progressHeight = (int)(21 * factor);
        progressBar.setPreferredSize(new Dimension(300, progressHeight));
        progressBar.setMinimumSize(new Dimension(10, progressHeight));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, progressHeight));
        progressBar.revalidate();
        progressBar.repaint();

        int buttonSize = (int)(16 * factor);
        Dimension btnDimension = new Dimension(buttonSize, buttonSize);
        moreButton.setPreferredSize(btnDimension);
        moreButton.setMaximumSize(btnDimension);
        closeButton.setPreferredSize(btnDimension);
        closeButton.setMaximumSize(btnDimension);
        startButton.setPreferredSize(btnDimension);
        startButton.setMaximumSize(btnDimension);

        int strutHeight = (int)Math.ceil(21 * factor);
        Dimension strutDimension = new Dimension(0, strutHeight);
        topStrut.setPreferredSize(strutDimension);
        topStrut.setMaximumSize(strutDimension);
        topStrut.setMinimumSize(strutDimension);
        bottomStrut.setPreferredSize(strutDimension);
        bottomStrut.setMaximumSize(strutDimension);
        bottomStrut.setMinimumSize(strutDimension);

        card.setMaximumSize(cardDimension);
        thumbnailPanel.setPreferredSize(thumbDimension);
        thumbnailPanel.setMinimumSize(thumbDimension);

        card.revalidate();
        card.repaint();
    }

    public void updateStartButtonMode(StartButtonMode mode) {
        assert SwingUtilities.isEventDispatchThread();

        switch (mode) {
            case START -> {
                startButtonState.set(new StartButtonState(
                    loadIcon("/assets/play.png", ICON, 16),
                    loadIcon("/assets/play.png", QUEUE_ACTIVE_ICON, 16),
                    "gui.force_download_start"));
            }
            case STOP -> {
                startButtonState.set(new StartButtonState(
                    loadIcon("/assets/pause.png", ICON, 16),
                    loadIcon("/assets/pause.png", QUEUE_PAUSE_ICON, 16),
                    "gui.stop_download"));
            }
            case RESTART -> {
                startButtonState.set(new StartButtonState(
                    loadIcon("/assets/action-redo.png", ICON, 16),
                    loadIcon("/assets/action-redo.png", QUEUE_PAUSE_ICON, 16),
                    "gui.restart_download"));
            }
        }

        startButton.setIcon(startButtonState.get().getIcon());
        startButton.setToolTipText(l10n(startButtonState.get().getTooltipKey()));
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

    @Data
    public static class StartButtonState {

        private final ImageIcon icon;
        private final ImageIcon hoverIcon;
        private final String tooltipKey;
    }
}
