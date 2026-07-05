/*
 * Copyright (C) 2026 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.ui.mediacard;

import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.brlns.gdownloader.downloader.QueueEntry;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.structs.FormatInfo;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.custom.CustomChip;
import net.brlns.gdownloader.ui.custom.CustomMediaCardUI;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomThumbnailPanel;
import net.brlns.gdownloader.ui.custom.CustomWrapLayout;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.GUIManager.createDialogButton;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;
import static net.brlns.gdownloader.ui.UIUtils.showWindowSafely;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class MediaInfoPopup {

    // TODO: For today, I'm hardcoding these, check later
    private static final int PREVIEW_HIDE_DELAY_MS = 100;

    private static final int DETAILS_DEFAULT_WIDTH = 780;
    private static final int DETAILS_DEFAULT_HEIGHT = 550;

    private static final int FORMAT_SELECTOR_DEFAULT_WIDTH = 1100;
    private static final int FORMAT_SELECTOR_DEFAULT_HEIGHT = 480;

    private static final Map<GUIManager, MediaInfoPopup> _instances
        = Collections.synchronizedMap(new WeakHashMap<>());

    private final GUIManager manager;

    private final Set<Window> openWindows = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean windowListenerInstalled;

    private JDialog activePreview;
    private QueueEntry activePreviewEntry;
    private Timer previewHideTimer;
    private boolean previewMouseOver;

    private JDialog activeDetails;
    private Rectangle detailsBounds;

    private JDialog activeFormatSelector;
    private Rectangle formatSelectorBounds;

    private MediaInfoPopup(GUIManager managerIn) {
        manager = managerIn;
    }

    private static MediaInfoPopup getInstance(GUIManager manager) {
        return _instances.computeIfAbsent(manager, MediaInfoPopup::new);
    }

    public static void showFormatSelector(GUIManager manager, QueueEntry entry) {
        getInstance(manager).doShowFormatSelector(entry);
    }

    private void doShowFormatSelector(QueueEntry entry) {
        runOnEDT(() -> {
            MediaInfo info = entry.getMediaInfo();
            if (info == null || !info.isValid() || info.getFormats().isEmpty()) {
                ToastMessenger.show(Message.builder()
                    .message("gui.media_info.no_formats_available")
                    .durationMillis(3000)
                    .messageType(MessageTypeEnum.INFO)
                    .discardDuplicates(true)
                    .build());

                return;
            }

            ensureGlobalWindowListener();
            closeFormatSelector();

            JDialog dialog = buildFormatSelectorDialog(entry, info);
            openWindows.add(dialog);
            activeFormatSelector = dialog;

            showWindowSafely(dialog);
        });
    }

    private JDialog buildFormatSelectorDialog(QueueEntry entry, MediaInfo info) {
        JDialog dialog = new JDialog(manager.getAppWindow(),
            l10n("gui.media_info.section.formats"),
            Dialog.ModalityType.MODELESS);

        dialog.setIconImage(manager.getAppIcon());
        dialog.setResizable(true);
        dialog.getContentPane().setBackground(color(BACKGROUND));
        dialog.setBackground(color(BACKGROUND));
        dialog.setAlwaysOnTop(manager.isFullScreen());

        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                formatSelectorBounds = dialog.getBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                formatSelectorBounds = dialog.getBounds();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openWindows.remove(dialog);

                if (activeFormatSelector == dialog) {
                    activeFormatSelector = null;
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(color(BACKGROUND));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(buildFormatsTable(entry, info), BorderLayout.CENTER);

        dialog.setContentPane(root);

        Optional.ofNullable(formatSelectorBounds).ifPresentOrElse(
            dialog::setBounds,
            () -> {
                dialog.setSize(FORMAT_SELECTOR_DEFAULT_WIDTH, FORMAT_SELECTOR_DEFAULT_HEIGHT);
                dialog.setMinimumSize(new Dimension(200, 200));
                dialog.setLocationRelativeTo(null);
            }
        );

        return dialog;
    }

    private void closeFormatSelector() {
        if (activeFormatSelector != null) {
            openWindows.remove(activeFormatSelector);

            activeFormatSelector.dispose();
            activeFormatSelector = null;
        }
    }

    public static void showPreview(GUIManager manager, QueueEntry queueEntry) {
        getInstance(manager).doShowPreview(queueEntry);
    }

    public static void hidePreview(GUIManager manager, QueueEntry entry) {
        getInstance(manager).doHidePreview(entry);
    }

    public static void showDetails(GUIManager manager, QueueEntry entry) {
        getInstance(manager).doShowDetails(entry);
    }

    private void doShowPreview(QueueEntry queueEntry) {
        runOnEDT(() -> {
            ensureGlobalWindowListener();

            MediaInfo info = queueEntry.getMediaInfo();
            if (info == null || !info.isValid()) {
                return;
            }

            if (activePreviewEntry == queueEntry && activePreview != null) {
                if (previewHideTimer != null) {
                    previewHideTimer.stop();
                }

                return;
            }

            closePreview();

            JDialog dialog = buildPreviewDialog(queueEntry, info);
            if (dialog == null) {
                // Nothing worth showing
                return;
            }

            openWindows.add(dialog);

            Component anchor = Optional.ofNullable(queueEntry.getMediaCard().getUi())
                .<Component>map(CustomMediaCardUI::getInfoButton)
                .orElse(manager.getAppWindow());

            positionRelativeTo(dialog, anchor);
            showWindowSafely(dialog);

            activePreview = dialog;
            activePreviewEntry = queueEntry;
        });
    }

    private void doHidePreview(QueueEntry entry) {
        runOnEDT(() -> {
            if (activePreviewEntry != entry) {
                return;
            }

            if (previewHideTimer == null) {
                previewHideTimer = new Timer(PREVIEW_HIDE_DELAY_MS, e -> {
                    if (!previewMouseOver) {
                        closePreview();
                    }
                });

                previewHideTimer.setRepeats(false);
            }

            previewHideTimer.restart();
        });
    }

    private void doShowDetails(QueueEntry entry) {
        runOnEDT(() -> {
            ensureGlobalWindowListener();

            MediaInfo info = entry.getMediaInfo();
            if (info == null || !info.isValid()) {
                ToastMessenger.show(Message.builder()
                    .message("gui.media_info.no_media_info_available")
                    .durationMillis(3000)
                    .messageType(MessageTypeEnum.INFO)
                    .discardDuplicates(true)
                    .build());

                return;
            }

            closePreview();
            closeDetails();

            JDialog dialog = buildDetailsDialog(entry, info);
            openWindows.add(dialog);
            activeDetails = dialog;
            showWindowSafely(dialog);
        });
    }

    private void ensureGlobalWindowListener() {
        if (windowListenerInstalled) {
            return;
        }

        windowListenerInstalled = true;
        JFrame appWindow = manager.getAppWindow();

        appWindow.addWindowStateListener((WindowEvent e) -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                closeAllPopups();
            }
        });

        appWindow.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                closeAllPopups();
            }
        });
    }

    private void closeAllPopups() {
        closePreview();

        List<Window> snapshot = new ArrayList<>(openWindows);
        openWindows.clear();

        for (Window window : snapshot) {
            window.dispose();
        }
    }

    private void closePreview() {
        if (activePreview != null) {
            openWindows.remove(activePreview);
            activePreview.dispose();
            activePreview = null;
        }

        activePreviewEntry = null;
        previewMouseOver = false;

        if (previewHideTimer != null) {
            previewHideTimer.stop();
        }
    }

    private void closeDetails() {
        if (activeDetails != null) {
            openWindows.remove(activeDetails);
            activeDetails.dispose();
            activeDetails = null;
        }
    }

    private void positionRelativeTo(JDialog dialog, Component anchor) {
        try {
            Point anchorPoint = anchor.getLocationOnScreen();
            Rectangle screen = dialog.getGraphicsConfiguration() != null
                ? dialog.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            int x = anchorPoint.x + (anchor.getWidth() / 2) - (dialog.getWidth() / 2);
            int y = anchorPoint.y - dialog.getHeight() - 12;

            x = Math.max(screen.x + 8, Math.min(x, screen.x + screen.width - dialog.getWidth() - 8));

            if (y < screen.y + 8) {
                y = anchorPoint.y + anchor.getHeight() + 32;
            }

            dialog.setLocation(x, y);
        } catch (IllegalComponentStateException e) {
            dialog.setLocationRelativeTo(manager.getAppWindow());
        }
    }

    @Nullable
    private JDialog buildPreviewDialog(QueueEntry entry, MediaInfo info) {
        JPanel stats = buildQuickStatsRow(info);
        if (stats == null) {
            return null;
        }

        JDialog dialog = new JDialog(manager.getAppWindow(), Dialog.ModalityType.MODELESS);
        dialog.getContentPane().setBackground(color(BACKGROUND));
        dialog.setBackground(color(BACKGROUND));
        dialog.setUndecorated(true);
        dialog.setFocusableWindowState(false);
        dialog.setType(Window.Type.POPUP);
        dialog.setAlwaysOnTop(manager.isFullScreen());

        JPanel panel = new RoundedPanel(14);
        panel.setOpaque(false);
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 14, 8, 14));

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                previewMouseOver = true;
                if (previewHideTimer != null) {
                    previewHideTimer.stop();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                previewMouseOver = false;

                doHidePreview(entry);
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        panel.add(stats, gbc);

        dialog.setContentPane(panel);
        dialog.pack();
        applyRoundedShape(dialog, 14);

        return dialog;
    }

    @Nullable
    private static JPanel buildQuickStatsRow(MediaInfo info) {
        List<String> stats = new ArrayList<>();

        if (info.getViewCount() > 0) {
            stats.add(l10n("gui.media_info.stat.views", formatCount(info.getViewCount())));
        }

        if (info.getLikeCount() != null) {
            stats.add(l10n("gui.media_info.stat.likes", formatCount(info.getLikeCount())));
        }

        if (info.getCommentCount() != null) {
            stats.add(l10n("gui.media_info.stat.comments", formatCount(info.getCommentCount())));
        }

        if (stats.isEmpty() && !info.isCurrentlyLive() && !info.wasLiveStream()) {
            return null;
        }

        JPanel row = transparentPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (info.isCurrentlyLive()) {
            row.add(new CustomChip("● " + l10n("gui.status.live"), color(LIVE_COLOR), Color.WHITE));
        } else if (info.wasLiveStream()) {
            row.add(new CustomChip("● " + l10n("gui.status.was_live"), color(CHIP_BG), color(FOREGROUND)));
        }

        for (String s : stats) {
            row.add(buildSelectableLabel(s, color(FOREGROUND), Font.PLAIN, 11f));
        }

        return row;
    }

    private JDialog buildDetailsDialog(QueueEntry entry, MediaInfo info) {
        String titleText = firstNonEmpty(
            info.getTitle(), entry.getUrl(), l10n("gui.media_info.untitled"));

        JDialog dialog = new JDialog(manager.getAppWindow(), titleText, Dialog.ModalityType.MODELESS);
        dialog.getContentPane().setBackground(color(BACKGROUND));
        dialog.setBackground(color(BACKGROUND));
        dialog.setIconImage(manager.getAppIcon());
        dialog.setResizable(true);
        dialog.setAlwaysOnTop(manager.isFullScreen());

        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                detailsBounds = dialog.getBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                detailsBounds = dialog.getBounds();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openWindows.remove(dialog);

                if (activeDetails == dialog) {
                    activeDetails = null;
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(color(BACKGROUND));

        JPanel header = buildDetailsHeader(entry, info);
        header.setOpaque(true);
        header.setBackground(color(SIDE_PANEL_SELECTED));
        header.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(true);
        centerWrapper.setBackground(color(BACKGROUND));
        centerWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
        centerWrapper.add(buildDetailsScrollArea(entry, info), BorderLayout.CENTER);

        JPanel footer = buildDetailsFooter(entry, info, dialog);
        footer.setOpaque(true);
        footer.setBackground(color(SIDE_PANEL_SELECTED));
        footer.setBorder(new EmptyBorder(10, 10, 10, 10));

        root.add(header, BorderLayout.NORTH);
        root.add(centerWrapper, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();

        Optional.ofNullable(detailsBounds).ifPresentOrElse(
            dialog::setBounds,
            () -> {
                dialog.setSize(DETAILS_DEFAULT_WIDTH, DETAILS_DEFAULT_HEIGHT);
                dialog.setMinimumSize(new Dimension(200, 200));
                dialog.setLocationRelativeTo(null);
            }
        );

        return dialog;
    }

    private JPanel buildDetailsHeader(QueueEntry entry, MediaInfo info) {
        JPanel header = transparentPanel(new BorderLayout(18, 0));
        header.add(buildThumbnail(entry, info, 320, 180), BorderLayout.WEST);

        JPanel titleBlock = transparentPanel(null);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBorder(new EmptyBorder(4, 0, 0, 0));

        String titleText = firstNonEmpty(info.getTitle(), entry.getUrl());
        JTextArea title = buildWrappingText(
            titleText != null ? titleText : l10n("gui.media_info.untitled"),
            color(FOREGROUND), Font.BOLD, 18f);

        titleBlock.add(title);

        JPanel channelRow = transparentPanel(new CustomWrapLayout(FlowLayout.LEFT, 6, 4));
        channelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        channelRow.setBorder(new EmptyBorder(6, -6, 0, 0));

        String channelText = firstNonEmpty(info.getChannel(), info.getUploader());

        if (channelText != null) {
            channelRow.add(buildSelectableLabel(channelText, color(FOREGROUND).darker(), Font.PLAIN, 13f));
        }

        if (info.isChannelIsVerified()) {
            channelRow.add(new CustomChip(l10n("gui.media_info.verified"), color(VERIFIED_COLOR), Color.WHITE));
        }

        if (info.isCurrentlyLive()) {
            channelRow.add(new CustomChip(l10n("gui.status.live"), color(LIVE_COLOR), Color.WHITE));
        } else if (info.wasLiveStream()) {
            channelRow.add(new CustomChip(l10n("gui.status.was_live"), color(CHIP_BG), color(FOREGROUND)));
        }

        if (info.getAgeLimit() > 0) {
            channelRow.add(new CustomChip(info.getAgeLimit() + "+", color(WARN_COLOR), Color.BLACK));
        }

        if (channelRow.getComponentCount() > 0) {
            titleBlock.add(channelRow);
        }

        if (info.getChannelFollowerCount() != null) {
            JComponent followers = buildSelectableLabel(
                l10n("gui.media_info.subscribers", formatCount(info.getChannelFollowerCount())),
                color(FOREGROUND).darker(), Font.PLAIN, 12f);
            followers.setAlignmentX(Component.LEFT_ALIGNMENT);
            followers.setBorder(new EmptyBorder(4, 0, 0, 0));
            titleBlock.add(followers);
        }

        header.add(titleBlock, BorderLayout.CENTER);

        return header;
    }

    private static CustomThumbnailPanel buildThumbnail(QueueEntry entry, MediaInfo info, int width, int height) {
        CustomThumbnailPanel thumbnailPanel = new CustomThumbnailPanel();
        thumbnailPanel.setPreferredSize(new Dimension(width, height));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));

        Optional.ofNullable(entry.getMediaCard().getThumbnailImage()).ifPresentOrElse(
            thumbImage -> {
                if (info.getDuration() > 0) {
                    thumbnailPanel.setImageAndDuration(thumbImage, info.getDuration());
                } else {
                    thumbnailPanel.setImage(thumbImage);
                }
            },
            () -> thumbnailPanel.setPlaceholderIcon(DownloadTypeEnum.ALL)
        );

        return thumbnailPanel;
    }

    private JScrollPane buildDetailsScrollArea(QueueEntry entry, MediaInfo info) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        addSection(content, "gui.media_info.section.description", buildDescriptionArea(info));
        addSection(content, "gui.media_info.section.tags", buildTagsRow(info));
        addSection(content, "gui.media_info.section.categories", buildCategoriesRow(info));
        addSection(content, "gui.media_info.section.source", buildSourceGrid(info));
        addSection(content, "gui.media_info.section.statistics", buildStatisticsGrid(info));
        addSection(content, "gui.media_info.section.technical", buildTechnicalGrid(info));

        JComponent formatsTable = buildFormatsTable(entry, info);
        if (formatsTable != null) {
            addCollapsibleSection(content, "gui.media_info.section.formats", formatsTable);
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // Prevent layout shifts, Swing would love to do that if given the chance.
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scroll.getVerticalScrollBar();
            if (vertical != null) {
                vertical.setValue(0);
            }
        });

        return scroll;
    }

    private static void addSection(JPanel content, String title, @Nullable JComponent body) {
        if (body == null) {
            return;
        }

        JPanel section = transparentPanel(new BorderLayout());
        section.setBorder(new EmptyBorder(0, 0, 16, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComponent titleLabel = buildSelectableLabel(l10n(title).toUpperCase(), color(FOREGROUND).darker(), Font.BOLD, 12f);
        titleLabel.setBorder(new EmptyBorder(0, 0, 8, 0));

        section.add(titleLabel, BorderLayout.NORTH);
        section.add(body, BorderLayout.CENTER);

        content.add(section);
    }

    private static void addCollapsibleSection(JPanel content, String title, @Nullable JComponent body) {
        if (body == null) {
            return;
        }

        JPanel section = transparentPanel(new BorderLayout());
        section.setBorder(new EmptyBorder(0, 0, 16, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titlePanel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titlePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titlePanel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JComponent titleLabel = buildSelectableLabel(
            l10n(title).toUpperCase(), color(FOREGROUND).darker(), Font.BOLD, 12f);
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel expandLabel = new JLabel("▼");
        expandLabel.setForeground(color(FOREGROUND).darker());
        expandLabel.setFont(expandLabel.getFont().deriveFont(Font.BOLD, 12f));
        expandLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        titlePanel.add(titleLabel);
        titlePanel.add(expandLabel);

        JPanel itemPanel = transparentPanel(new BorderLayout());
        itemPanel.setVisible(false);
        itemPanel.add(body, BorderLayout.WEST);

        MouseAdapter toggleListener = new MouseAdapter() {
            private boolean isExpanded = false;

            @Override
            public void mouseClicked(MouseEvent e) {
                isExpanded = !isExpanded;
                itemPanel.setVisible(isExpanded);
                expandLabel.setText(isExpanded ? "▲" : "▼");

                Optional.ofNullable(SwingUtilities.getWindowAncestor(section))
                    .ifPresent(window -> {
                        window.revalidate();
                        window.repaint();
                    });
            }
        };

        titlePanel.addMouseListener(toggleListener);
        titleLabel.addMouseListener(toggleListener);
        expandLabel.addMouseListener(toggleListener);

        section.add(titlePanel, BorderLayout.NORTH);
        section.add(itemPanel, BorderLayout.CENTER);

        content.add(section);
    }

    @Nullable
    private static JComponent buildStatisticsGrid(MediaInfo info) {
        KeyValueGrid grid = new KeyValueGrid();

        if (info.getViewCount() > 0) {
            grid.addRow("gui.media_info.field.views",
                formatCount(info.getViewCount()) + " (" + info.getViewCount() + ")");
        }

        if (info.getLikeCount() != null) {
            grid.addRow("gui.media_info.field.likes",
                formatCount(info.getLikeCount()) + " (" + info.getLikeCount() + ")");
        }

        if (info.getCommentCount() != null) {
            grid.addRow("gui.media_info.field.comments",
                formatCount(info.getCommentCount()) + " (" + info.getCommentCount() + ")");
        }

        if (info.getAverageRating() != null) {
            grid.addRow("gui.media_info.field.average_rating", String.format("%.2f", info.getAverageRating()));
        }

        if (notNullOrEmpty(info.getDisplayDuration())) {
            grid.addRow("gui.media_info.field.duration", info.getDisplayDuration());
        }

        String uploadDate = formatUploadDate(info);
        if (uploadDate != null) {
            grid.addRow("gui.media_info.field.upload_date", uploadDate);
        }

        if (info.getReleaseYear() != null) {
            grid.addRow("gui.media_info.field.release_year", String.valueOf(info.getReleaseYear()));
        }

        if (info.getAgeLimit() > 0) {
            grid.addRow("gui.media_info.field.age_limit", info.getAgeLimit() + "+");
        }

        return grid.buildOrNull();
    }

    @Nullable
    private static JComponent buildTechnicalGrid(MediaInfo info) {
        KeyValueGrid grid = new KeyValueGrid();

        String resolution = notNullOrEmpty(info.getResolution()) ? info.getResolution()
            : (info.getWidth() > 0 && info.getHeight() > 0
            ? info.getWidth() + "x" + info.getHeight() : null);

        if (resolution != null) {
            grid.addRow("gui.media_info.field.resolution", resolution);
        }

        if (info.getFps() > 0) {
            grid.addRow("gui.media_info.field.frame_rate", info.getFps() + " fps");
        }

        if (notNullOrEmpty(info.getDynamicRange())) {
            grid.addRow("gui.media_info.field.dynamic_range", info.getDynamicRange());
        }

        if (notNullOrEmpty(info.getSelectedVcodec()) && !"none".equals(info.getSelectedVcodec())) {
            grid.addRow("gui.media_info.field.video_codec", info.getSelectedVcodec());
        }

        if (notNullOrEmpty(info.getSelectedAcodec()) && !"none".equals(info.getSelectedAcodec())) {
            grid.addRow("gui.media_info.field.audio_codec", info.getSelectedAcodec());
        }

        if (info.getSelectedTbr() != null && info.getSelectedTbr() > 0) {
            grid.addRow("gui.media_info.field.total_bitrate", formatBitrate(info.getSelectedTbr()));
        }

        if (info.getSelectedVbr() != null && info.getSelectedVbr() > 0) {
            grid.addRow("gui.media_info.field.video_bitrate", formatBitrate(info.getSelectedVbr()));
        }

        if (info.getSelectedAbr() != null && info.getSelectedAbr() > 0) {
            grid.addRow("gui.media_info.field.audio_bitrate", formatBitrate(info.getSelectedAbr()));
        }

        if (info.getSelectedAsr() != null && info.getSelectedAsr() > 0) {
            grid.addRow("gui.media_info.field.sample_rate", info.getSelectedAsr() + " Hz");
        }

        if (info.getSelectedAudioChannels() != null && info.getSelectedAudioChannels() > 0) {
            grid.addRow("gui.media_info.field.audio_channels", String.valueOf(info.getSelectedAudioChannels()));
        }

        if (info.getFilesizeApprox() > 0) {
            grid.addRow("gui.media_info.field.file_size", formatFileSize(info.getFilesizeApprox()));
        }

        if (!info.getThumbnails().isEmpty()) {
            grid.addRow("gui.media_info.field.thumbnails_available", String.valueOf(info.getThumbnails().size()));
        }

        return grid.buildOrNull();
    }

    private JComponent buildFormatsTable(QueueEntry entry, MediaInfo info) {
        List<FormatInfo> formats = info.getFormats().stream()
            .filter(f -> f != null && !f.isStoryboard())
            .toList();

        if (formats.isEmpty()) {
            return null;
        }

        List<String> columns = new ArrayList<>(List.of(
            "gui.media_info.column.id",
            "gui.media_info.column.ext",
            "gui.media_info.column.resolution",
            "gui.media_info.column.note",
            "gui.media_info.column.vcodec",
            "gui.media_info.column.acodec",
            "gui.media_info.column.fps",
            "gui.media_info.column.bitrate",
            "gui.media_info.column.size",
            ""// DL Icon
        ));

        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(true);
        table.setBackground(color(BACKGROUND));
        table.setAlignmentX(Component.LEFT_ALIGNMENT);

        int row = 0;
        for (int col = 0; col < columns.size(); col++) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = col;
            gbc.gridy = row;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.weightx = (col == columns.size() - 1) ? 1.0 : 0.0;

            String text = columns.get(col).isEmpty() ? "" : l10n(columns.get(col));
            JComponent header = buildTableCell(
                text,
                color(FOREGROUND),
                Font.BOLD,
                12f,
                SIDE_PANEL_HEADER_FOOTER);

            table.add(header, gbc);
        }

        row++;

        for (FormatInfo format : formats) {
            UIColors rowBg = (row % 2 == 0) ? SETTINGS_ROW_BACKGROUND_DARK : SETTINGS_ROW_BACKGROUND_LIGHT;

            String type = format.isAudioOnly()
                ? l10n("gui.media_info.format.audio")
                : format.isVideoOnly()
                ? l10n("gui.media_info.format.video")
                : l10n("gui.media_info.format.muxed");

            List<String> values = List.of(
                firstNonEmpty(format.getFormatId(), "-"),
                firstNonEmpty(format.getExt(), "-"),
                firstNonEmpty(format.getResolution(), "-") + " (" + type + ")",
                firstNonEmpty(format.getFormatNote(), "-"),
                codecOrDash(format.getVcodec()),
                codecOrDash(format.getAcodec()),
                format.getFps() != null && format.getFps() > 0
                ? String.format("%.0f", format.getFps()) : "-",
                formatFormatBitrate(format),
                formatFormatSize(format)
            );

            for (int col = 0; col < values.size(); col++) {
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = col;
                gbc.gridy = row;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.anchor = GridBagConstraints.NORTHWEST;

                JComponent cell = buildTableCell(
                    values.get(col),
                    color(FOREGROUND),
                    Font.PLAIN,
                    12f,
                    rowBg);

                table.add(cell, gbc);
            }

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = values.size();
            gbc.gridy = row;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;

            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            actionPanel.setOpaque(true);
            actionPanel.setBackground(color(rowBg));
            actionPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 16));

            JButton downloadButton = createIconButton(
                loadIcon("/assets/download-icon.png", ICON, 16),
                loadIcon("/assets/download-icon.png", ICON_HOVER, 16),
                "gui.media_info.download_this_format",
                e -> {
                    manager.getMain().getDownloadManager().downloadSpecificFormat(entry, format);
                    Optional.ofNullable(SwingUtilities.getWindowAncestor(table))
                        .filter(JDialog.class::isInstance)
                        .map(JDialog.class::cast)
                        .ifPresent(JDialog::dispose);
                });

            downloadButton.setPreferredSize(new Dimension(16, 16));
            downloadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            actionPanel.add(downloadButton);

            table.add(actionPanel, gbc);

            row++;
        }

        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.gridx = 0;
        fillerGbc.gridy = row;
        fillerGbc.gridwidth = columns.size();
        fillerGbc.weightx = 1.0;
        fillerGbc.weighty = 1.0;
        fillerGbc.fill = GridBagConstraints.BOTH;
        table.add(Box.createGlue(), fillerGbc);

        JScrollPane tableScroll = new JScrollPane(table) {
            @Override
            public Dimension getPreferredSize() {
                Dimension originalPref = super.getPreferredSize();
                Dimension tablePref = table.getPreferredSize();
                originalPref.width = Math.min(originalPref.width, tablePref.width + 10);

                return originalPref;
            }
        };

        tableScroll.setOpaque(false);
        tableScroll.getViewport().setOpaque(false);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        tableScroll.getHorizontalScrollBar().setUnitIncrement(16);
        tableScroll.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        tableScroll.getVerticalScrollBar().setUnitIncrement(16);
        tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        tableScroll.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        tableScroll.addMouseWheelListener(e -> {
            if (e.isShiftDown()) {
                return;
            }

            JScrollPane parentScroll = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, tableScroll);
            if (parentScroll != null) {
                parentScroll.dispatchEvent(SwingUtilities.convertMouseEvent(tableScroll, e, parentScroll));
            }
        });

        return tableScroll;
    }

    private static JComponent buildTableCell(String text, Color foreground, int style, float size, UIColors bgColor) {
        JTextField field = new JTextField(text != null ? text : "");
        field.setEditable(false);
        field.setFocusable(true);
        field.setOpaque(true);
        field.setBackground(color(bgColor));
        field.setForeground(foreground);
        field.setFont(field.getFont().deriveFont(style, size));
        field.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 16));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setCaretPosition(0);

        return field;
    }

    private static String codecOrDash(String codec) {
        return notNullOrEmpty(codec) && !"none".equals(codec) ? codec : "-";
    }

    private static String formatFormatBitrate(FormatInfo format) {
        Double bitrate = format.getTbr() != null && format.getTbr() > 0
            ? format.getTbr()
            : format.getVbr() != null && format.getVbr() > 0
            ? format.getVbr()
            : format.getAbr();

        return bitrate != null && bitrate > 0 ? formatBitrate(bitrate) : "-";
    }

    private static String formatFormatSize(FormatInfo format) {
        Long size = format.getFilesize() != null && format.getFilesize() > 0
            ? format.getFilesize() : format.getFilesizeApprox();

        return size != null && size > 0 ? formatFileSize(size) : "-";
    }

    @Nullable
    private JComponent buildSourceGrid(MediaInfo info) {
        KeyValueGrid grid = new KeyValueGrid();

        if (notNullOrEmpty(info.getId())) {
            grid.addRow("gui.media_info.field.id", info.getId());
        }

        if (notNullOrEmpty(info.getExtractor())) {
            grid.addRow("gui.media_info.field.extractor",
                notNullOrEmpty(info.getExtractorKey())
                ? info.getExtractor() + " (" + info.getExtractorKey() + ")"
                : info.getExtractor());
        }

        if (notNullOrEmpty(info.getHostDisplayName())) {
            grid.addRow("gui.media_info.field.host", info.getHostDisplayName());
        }

        if (notNullOrEmpty(info.getAvailability())) {
            grid.addRow("gui.media_info.field.availability", info.getAvailability());
        }

        if (notNullOrEmpty(info.getPlaylistTitle())) {
            grid.addRow("gui.media_info.field.playlist", info.getPlaylistTitle());
        }

        if (notNullOrEmpty(info.getUploader())) {
            grid.addRow("gui.media_info.field.uploader",
                notNullOrEmpty(info.getUploaderId())
                ? info.getUploader() + " (" + info.getUploaderId() + ")"
                : info.getUploader());
        }

        if (notNullOrEmpty(info.getUploaderUrl())) {
            grid.addLinkRow("gui.media_info.field.uploader_url", info.getUploaderUrl(), manager);
        }

        if (notNullOrEmpty(info.getChannelId())) {
            grid.addRow("gui.media_info.field.channel_id", info.getChannelId());
        }

        if (notNullOrEmpty(info.getChannelUrl())) {
            grid.addLinkRow("gui.media_info.field.channel_url", info.getChannelUrl(), manager);
        }

        if (notNullOrEmpty(info.getWebpageUrl())) {
            grid.addLinkRow("gui.media_info.field.webpage_url", info.getWebpageUrl(), manager);
        }

        if (notNullOrEmpty(info.getOriginalUrl()) && !info.getOriginalUrl().equals(info.getWebpageUrl())) {
            grid.addLinkRow("gui.media_info.field.original_url", info.getOriginalUrl(), manager);
        }

        return grid.buildOrNull();
    }

    @Nullable
    private JComponent buildCategoriesRow(MediaInfo info) {
        if (info.getCategories().isEmpty()) {
            return null;
        }

        boolean clickable = isYouTube(info);

        JPanel row = transparentPanel(new CustomWrapLayout(FlowLayout.LEFT, 6, 4));
        for (String category : info.getCategories()) {
            if (category != null && !category.isBlank()) {
                CustomChip chip = new CustomChip(category, color(CHIP_BG), color(FOREGROUND));
                if (clickable) {
                    makeClickable(chip, youtubeSearchUrl(category));
                }

                row.add(chip);
            }
        }

        return row.getComponentCount() > 0 ? row : null;
    }

    @Nullable
    private JComponent buildTagsRow(MediaInfo info) {
        if (info.getTags().isEmpty()) {
            return null;
        }

        boolean clickable = isYouTube(info);

        JPanel row = transparentPanel(new CustomWrapLayout(FlowLayout.LEFT, 6, 4));
        boolean any = false;

        for (String tag : info.getTags()) {
            if (tag != null && !tag.isBlank()) {
                CustomChip chip = new CustomChip(tag, color(CHIP_BG), color(FOREGROUND));
                if (clickable) {
                    makeClickable(chip, youtubeSearchUrl(tag));
                }

                row.add(chip);
                any = true;
            }
        }

        return any ? row : null;
    }

    private static boolean isYouTube(MediaInfo info) {
        return containsIgnoreCase(info.getExtractor(), "youtube")
            || containsIgnoreCase(info.getExtractorKey(), "youtube");
    }

    private static String youtubeSearchUrl(String query) {
        return "https://www.youtube.com/results?search_query="
            + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    private void makeClickable(JComponent component, String url) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                manager.getMain().openUrlInBrowser(url);
            }
        });
    }

    @Nullable
    private static JComponent buildDescriptionArea(MediaInfo info) {
        if (info.getDescription() == null || info.getDescription().isBlank()) {
            return null;
        }

        return buildWrappingText(
            info.getDescription().strip(),
            color(FOREGROUND).darker(),
            Font.PLAIN,
            13f);
    }

    private JPanel buildDetailsFooter(QueueEntry entry, MediaInfo info, JDialog dialog) {
        JPanel footer = transparentPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));

        Optional.ofNullable(firstNonEmpty(info.getWebpageUrl(), entry.getUrl()))
            .ifPresent(originalUrl -> {
                JButton openButton = createDialogButton(
                    l10n("gui.open_in_browser"),
                    BUTTON_BACKGROUND,
                    BUTTON_FOREGROUND,
                    BUTTON_HOVER);

                openButton.setPreferredSize(new Dimension(200, 42));
                openButton.addActionListener(e -> {
                    manager.getMain().openUrlInBrowser(originalUrl);
                    dialog.dispose();
                });
                footer.add(openButton);
            });

        JButton closeButton = createDialogButton(
            l10n("gui.close.tooltip"),
            BUTTON_BACKGROUND,
            BUTTON_FOREGROUND,
            BUTTON_HOVER);

        closeButton.setPreferredSize(new Dimension(200, 42));
        closeButton.addActionListener(e -> dialog.dispose());
        footer.add(closeButton);

        return footer;
    }

    private static JTextArea buildWrappingText(String text, Color foreground, int style, float size) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(true);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(foreground);
        area.setFont(area.getFont().deriveFont(style, size));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        area.setBorder(null);
        area.setSelectionColor(area.getSelectionColor());

        return area;
    }

    private static JComponent buildSelectableLabel(String text, Color foreground, int style, float size) {
        JTextField field = new JTextField(text != null ? text : "");
        field.setEditable(false);
        field.setFocusable(true);
        field.setOpaque(false);
        field.setForeground(foreground);
        field.setFont(field.getFont().deriveFont(style, size));
        field.setBorder(null);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setCaretPosition(0);

        return field;
    }

    private static JComponent buildLinkLabel(String url, GUIManager manager) {
        JTextArea area = buildWrappingText(url, color(FOREGROUND), Font.PLAIN, 13f);
        area.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                manager.getMain().openUrlInBrowser(url);
            }
        });

        return area;
    }

    private static void applyRoundedShape(JDialog dialog, int arc) {
        if (dialog.getWidth() > 0 && dialog.getHeight() > 0) {
            dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), arc, arc));
        }
    }

    private static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = layout != null ? new JPanel(layout) : new JPanel();
        panel.setOpaque(false);

        return panel;
    }

    @Nullable
    private static String formatUploadDate(MediaInfo info) {
        try {
            LocalDate date = info.getUploadDateAsLocalDate();

            return date != null ? date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) : null;
        } catch (Exception e) {
            return notNullOrEmpty(info.getUploadDate()) ? info.getUploadDate() : null;
        }
    }

    private static final class RoundedPanel extends JPanel {

        private final int arc;

        private RoundedPanel(int arcIn) {
            arc = arcIn;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D)g.create();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(color(MEDIA_CARD));
                g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2d.setColor(color(BACKGROUND));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } finally {
                g2d.dispose();
                super.paintComponent(g);
            }
        }
    }

    private static final class KeyValueGrid {

        private final JPanel panel = transparentPanel(new GridBagLayout());
        private int row;

        private void addRow(String label, String value) {
            if (value == null || value.isBlank()) {
                return;
            }

            GridBagConstraints labelGbc = new GridBagConstraints();
            labelGbc.gridx = 0;
            labelGbc.gridy = row;
            labelGbc.anchor = GridBagConstraints.NORTHWEST;
            labelGbc.insets = new Insets(0, 0, 8, 14);

            JComponent labelComponent = buildSelectableLabel(l10n(label), color(FOREGROUND).darker(), Font.PLAIN, 13f);
            panel.add(labelComponent, labelGbc);

            GridBagConstraints valueGbc = new GridBagConstraints();
            valueGbc.gridx = 1;
            valueGbc.gridy = row;
            valueGbc.weightx = 1;
            valueGbc.fill = GridBagConstraints.HORIZONTAL;
            valueGbc.anchor = GridBagConstraints.NORTHWEST;
            valueGbc.insets = new Insets(0, 0, 8, 0);

            JTextArea valueComponent = buildWrappingText(value, color(FOREGROUND), Font.PLAIN, 13f);
            panel.add(valueComponent, valueGbc);

            row++;
        }

        private void addLinkRow(String label, String url, GUIManager manager) {
            if (url == null || url.isBlank()) {
                return;
            }

            GridBagConstraints labelGbc = new GridBagConstraints();
            labelGbc.gridx = 0;
            labelGbc.gridy = row;
            labelGbc.anchor = GridBagConstraints.NORTHWEST;
            labelGbc.insets = new Insets(0, 0, 8, 14);

            JComponent labelComponent = buildSelectableLabel(l10n(label), color(FOREGROUND).darker(), Font.PLAIN, 13f);
            panel.add(labelComponent, labelGbc);

            GridBagConstraints valueGbc = new GridBagConstraints();
            valueGbc.gridx = 1;
            valueGbc.gridy = row;
            valueGbc.weightx = 1;
            valueGbc.fill = GridBagConstraints.HORIZONTAL;
            valueGbc.anchor = GridBagConstraints.NORTHWEST;
            valueGbc.insets = new Insets(0, 0, 8, 0);

            JComponent valueComponent = buildLinkLabel(url, manager);
            panel.add(valueComponent, valueGbc);

            row++;
        }

        @Nullable
        private JComponent buildOrNull() {
            return row > 0 ? panel : null;
        }
    }
}
