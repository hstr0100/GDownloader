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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.ui.history;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.persistence.entity.DownloadHistoryEntity;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.SmoothScroller;
import net.brlns.gdownloader.ui.custom.CustomDynamicLabel;
import net.brlns.gdownloader.ui.custom.CustomLoadingSpinner;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomThumbnailPanel;
import net.brlns.gdownloader.ui.mediacard.ScrollableQueuePanel;
import net.brlns.gdownloader.ui.menu.MultiActionMenuEntry;
import net.brlns.gdownloader.ui.menu.RightClickMenuEntries;
import net.brlns.gdownloader.ui.menu.RunnableMenuEntry;
import net.brlns.gdownloader.ui.menu.SingleActionMenuEntry;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.ImageUtils;
import net.brlns.gdownloader.util.StringUtils;

import static net.brlns.gdownloader.downloader.enums.DownloaderIdEnum.*;
import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.containsIgnoreCase;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: lazy load thumbnails and cards to avoid MASSIVE ui issues on huge histories
// TODO: add icon to the main toolbar (overflow area)
@Slf4j
public class HistoryWindow {

    private static final int MIN_THUMBNAIL_WIDTH = 170;
    private static final int TEXT_PANEL_HEIGHT = 60;
    private static final int GAP = 10;

    private static final int SEARCH_DEBOUNCE_MS = 200;

    private static final String CARD_URL_PROPERTY = "historyCardUrl";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private final GDownloader main;
    private final GUIManager manager;

    private JFrame frame;
    private ScrollableQueuePanel cardsPanel;
    private JLabel emptyLabel;
    private JTextField searchField;
    private JLabel countLabel;
    private JScrollPane scrollPane;

    private final Timer searchDebounceTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> renderCards());

    private final List<DownloadHistoryEntity> loadedEntries = new ArrayList<>();
    private List<DownloadHistoryEntity> currentlyDisplayedEntries = new ArrayList<>();

    private final Set<String> selectedUrls = new LinkedHashSet<>();
    private String lastSelectedUrl;

    public HistoryWindow(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        searchDebounceTimer.setRepeats(false);
    }

    public void createAndShowGUI() {
        runOnEDT(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
                frame.requestFocus();

                reload();
                return;
            }

            frame = new JFrame(l10n("gui.history.window_title")) {
                @Override
                public void dispose() {
                    frame = null;

                    super.dispose();
                }
            };

            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1010, 718);
            frame.setLayout(new BorderLayout());
            frame.setLocationRelativeTo(null);
            frame.setMinimumSize(new Dimension(700, 400));
            frame.setIconImage(manager.getAppIcon());

            frame.add(buildHeaderPanel(), BorderLayout.NORTH);
            frame.add(buildContentPanel(), BorderLayout.CENTER);

            setupKeyBindings();

            frame.setVisible(true);

            reload();
        });
    }

    private void setupKeyBindings() {
        InputMap inputMap = cardsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = cardsPanel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "selectAll");
        actionMap.put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedUrls.clear();

                for (DownloadHistoryEntity entry : currentlyDisplayedEntries) {
                    selectedUrls.add(entry.getUrl());
                }

                lastSelectedUrl = currentlyDisplayedEntries.isEmpty()
                    ? null : currentlyDisplayedEntries.get(currentlyDisplayedEntries.size() - 1).getUrl();

                updateSelectionUI();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected");
        actionMap.put("deleteSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedFromHistory();
            }
        });
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(color(SIDE_PANEL_SELECTED));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JLabel titleLabel = new JLabel(l10n("gui.history.window_title"));
        titleLabel.setForeground(color(FOREGROUND));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);

        countLabel = new JLabel();
        countLabel.setForeground(color(LIGHT_TEXT));
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JLabel searchIcon = new JLabel(loadIcon("/assets/search.png", LIGHT_TEXT, 16));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        searchField = new JTextField(22);
        searchField.setBackground(color(SIDE_PANEL));
        searchField.setForeground(color(FOREGROUND));
        searchField.setCaretColor(color(FOREGROUND));
        searchField.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        searchField.setToolTipText(l10n("gui.history.search.tooltip"));
        searchField.setMaximumSize(new Dimension(220, 28));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSearchUpdate();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSearchUpdate();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSearchUpdate();
            }
        });

        JButton refreshButton = createIconButton(
            loadIcon("/assets/refresh.png", ICON),
            loadIcon("/assets/refresh.png", ICON_HOVER),
            "gui.history.refresh.tooltip",
            e -> reload());

        JButton clearAllButton = createIconButton(
            loadIcon("/assets/erase.png", ICON),
            loadIcon("/assets/erase.png", ICON_HOVER),
            "gui.history.clear_all.tooltip",
            e -> confirmClearAll());

        rightPanel.add(countLabel);
        rightPanel.add(searchIcon);
        rightPanel.add(searchField);
        rightPanel.add(refreshButton);
        rightPanel.add(clearAllButton);

        headerPanel.add(rightPanel, BorderLayout.EAST);

        return headerPanel;
    }

    private void scheduleSearchUpdate() {
        searchDebounceTimer.restart();
    }

    private JScrollPane buildContentPanel() {
        cardsPanel = new ScrollableQueuePanel(new ResponsiveGridLayout());
        cardsPanel.setBackground(color(BACKGROUND));
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        cardsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showBackgroundContextMenu(cardsPanel, e.getX(), e.getY());
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    clearSelection();
                }
            }
        });

        emptyLabel = new JLabel(l10n("gui.history.empty"), SwingConstants.CENTER);
        emptyLabel.setForeground(color(LIGHT_TEXT));

        scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(color(BACKGROUND));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        SmoothScroller.install(scrollPane);

        return scrollPane;
    }

    private void showBackgroundContextMenu(Component parent, int x, int y) {
        RightClickMenuEntries menu = new RightClickMenuEntries();

        menu.put(l10n("gui.history.refresh"),
            new RunnableMenuEntry(this::reload, () -> "/assets/refresh.png"));
        menu.put(l10n("gui.history.clear_all"),
            new RunnableMenuEntry(this::confirmClearAll, () -> "/assets/erase.png"));

        manager.showRightClickMenu(parent, menu, x, y);
    }

    private void reload() {
        runOnEDT(() -> {
            cardsPanel.removeAll();
            cardsPanel.setLayout(new GridBagLayout());

            cardsPanel.add(new CustomLoadingSpinner(48));

            cardsPanel.revalidate();
            cardsPanel.repaint();
        });

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            List<DownloadHistoryEntity> entries = main.getPersistenceManager().isHistoryInitialized()
                ? main.getPersistenceManager().getDownloadHistory().getAllOrderedByDate()
                : List.of();

            runOnEDT(() -> {
                loadedEntries.clear();
                loadedEntries.addAll(entries);

                Set<String> knownUrls = entries.stream()
                    .map(DownloadHistoryEntity::getUrl)
                    .collect(Collectors.toSet());
                selectedUrls.retainAll(knownUrls);

                renderCards();
            });
        });
    }

    private List<DownloadHistoryEntity> getFilteredEntries() {
        String filter = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";

        return loadedEntries.stream()
            .filter(entry -> filter.isEmpty()
            || containsIgnoreCase(entry.getTitle(), filter)
            || containsIgnoreCase(entry.getUrl(), filter)
            || containsIgnoreCase(entry.getHostDisplayName(), filter))
            .sorted(Comparator.comparingLong(DownloadHistoryEntity::getDownloadedAt).reversed())
            .collect(Collectors.toList());
    }

    private void renderCards() {
        List<DownloadHistoryEntity> filtered = getFilteredEntries();
        currentlyDisplayedEntries = filtered;

        if (countLabel != null) {
            countLabel.setText(String.valueOf(filtered.size()));
        }

        Set<String> visibleUrls = filtered.stream()
            .map(DownloadHistoryEntity::getUrl)
            .collect(Collectors.toSet());

        selectedUrls.retainAll(visibleUrls);
        if (lastSelectedUrl != null && !visibleUrls.contains(lastSelectedUrl)) {
            lastSelectedUrl = null;
        }

        cardsPanel.removeAll();

        if (filtered.isEmpty()) {
            cardsPanel.setLayout(new BorderLayout());
            cardsPanel.add(emptyLabel, BorderLayout.CENTER);
        } else {
            if (!(cardsPanel.getLayout() instanceof ResponsiveGridLayout)) {
                cardsPanel.setLayout(new ResponsiveGridLayout());
            }

            for (DownloadHistoryEntity entry : filtered) {
                cardsPanel.add(buildCard(entry, filtered));
            }
        }

        updateSelectionUI();

        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private JPanel buildCard(DownloadHistoryEntity entry, List<DownloadHistoryEntity> currentList) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D)g.create();
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(getBackground());
                    g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                } finally {
                    g2d.dispose();
                }
            }
        };

        card.putClientProperty(CARD_URL_PROPERTY, entry.getUrl());
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 6));
        card.setBackground(isSelected(entry) ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String rawUrl = entry.getOriginalUrl() != null ? entry.getOriginalUrl() : entry.getUrl();
        // TODO: URLUtils.getDisplayUrl() (strip protocol, www, query and fragments)
        String displayUrl = rawUrl.replaceFirst("^(https?://)?(www\\.)?", "").split("\\?")[0];
        card.setToolTipText(displayUrl);

        CustomThumbnailPanel thumbnailPanel = new CustomThumbnailPanel();
        thumbnailPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
        thumbnailPanel.setToolTipText(displayUrl);

        JButton closeButton = createIconButton(
            loadIcon("/assets/x-mark.png", ICON, 16),
            loadIcon("/assets/x-mark.png", ICON_CLOSE, 16),
            "gui.history.remove",
            e -> {
                if (isSelected(entry) && selectedUrls.size() > 1) {
                    removeSelectedFromHistory();
                } else {
                    removeFromHistory(entry);
                }
            });
        closeButton.setVisible(false);
        thumbnailPanel.add(closeButton);

        BufferedImage thumbnail = ImageUtils.base64ToBufferedImage(entry.getBase64EncodedThumbnail());
        if (thumbnail != null) {
            thumbnailPanel.setImage(thumbnail);
        } else {
            thumbnailPanel.setPlaceholderIcon(inferDownloadType(entry));
        }

        card.add(thumbnailPanel, BorderLayout.CENTER);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setToolTipText(displayUrl);

        CustomDynamicLabel titleLabel = new CustomDynamicLabel();
        titleLabel.setForeground(color(FOREGROUND));
        titleLabel.setLineWrapping(false);
        titleLabel.setFullText(notNullOrEmpty(entry.getTitle()) ? entry.getTitle() : displayUrl);
        titleLabel.setToolTipText(notNullOrEmpty(entry.getTitle()) ? entry.getTitle() : displayUrl);

        JLabel subtitleLabel = new JLabel(buildSubtitle(entry));
        subtitleLabel.setForeground(color(LIGHT_TEXT));
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11f));
        subtitleLabel.setToolTipText(displayUrl);

        textPanel.add(titleLabel);
        textPanel.add(subtitleLabel);

        card.add(textPanel, BorderLayout.SOUTH);
        card.setToolTipText(displayUrl);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(entry, card, currentList, e);
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openFirstAvailableFile(entry);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    handleLeftClick(entry, currentList, e);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isSelected(entry)) {
                    card.setBackground(color(MEDIA_CARD_HOVER));
                    card.repaint();
                }

                closeButton.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isSelected(entry)) {
                    card.setBackground(color(MEDIA_CARD));
                    card.repaint();
                }

                Rectangle bounds = new Rectangle(0, 0, card.getWidth(), card.getHeight());
                if (!bounds.contains(e.getPoint())) {
                    closeButton.setVisible(false);
                }
            }
        });

        return card;
    }

    private boolean isSelected(DownloadHistoryEntity entry) {
        return selectedUrls.contains(entry.getUrl());
    }

    private void handleLeftClick(DownloadHistoryEntity entry, List<DownloadHistoryEntity> currentList, MouseEvent e) {
        String url = entry.getUrl();

        if (e.isControlDown()) {
            if (!selectedUrls.add(url)) {
                selectedUrls.remove(url);
            }

            lastSelectedUrl = url;
        } else if (e.isShiftDown() && lastSelectedUrl != null) {
            int start = indexOfUrl(currentList, lastSelectedUrl);
            int end = indexOfUrl(currentList, url);

            if (start != -1 && end != -1) {
                selectedUrls.clear();

                int min = Math.min(start, end);
                int max = Math.max(start, end);
                for (int i = min; i <= max; i++) {
                    selectedUrls.add(currentList.get(i).getUrl());
                }
            }
        } else {
            selectedUrls.clear();
            selectedUrls.add(url);

            lastSelectedUrl = url;
        }

        updateSelectionUI();
    }

    private int indexOfUrl(List<DownloadHistoryEntity> list, String url) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getUrl().equals(url)) {
                return i;
            }
        }

        return -1;
    }

    private void clearSelection() {
        if (selectedUrls.isEmpty()) {
            return;
        }

        selectedUrls.clear();
        updateSelectionUI();
    }

    private void updateSelectionUI() {
        for (Component c : cardsPanel.getComponents()) {
            if (c instanceof JPanel panel) {
                Object url = panel.getClientProperty(CARD_URL_PROPERTY);
                if (url instanceof String urlString) {
                    panel.setBackground(selectedUrls.contains(urlString)
                        ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
                    panel.repaint();
                }
            }
        }
    }

    private void handleRightClick(DownloadHistoryEntity entry, JPanel card,
        List<DownloadHistoryEntity> currentList, MouseEvent e) {
        if (!isSelected(entry)) {
            selectedUrls.clear();
            selectedUrls.add(entry.getUrl());

            updateSelectionUI();
        }

        if (selectedUrls.size() > 1) {
            List<RightClickMenuEntries> dependents = new ArrayList<>();

            for (DownloadHistoryEntity other : currentList) {
                if (!other.getUrl().equals(entry.getUrl()) && selectedUrls.contains(other.getUrl())) {
                    dependents.add(buildCardContextMenu(other));
                }
            }

            manager.showRightClickMenu(card, buildCardContextMenu(entry), dependents, e.getX(), e.getY());
        } else {
            manager.showRightClickMenu(card, buildCardContextMenu(entry), e.getX(), e.getY());
        }
    }

    private String buildSubtitle(DownloadHistoryEntity entry) {
        String date = DATE_FORMATTER.format(
            Instant.ofEpochMilli(entry.getDownloadedAt()).atZone(ZoneId.systemDefault()));

        String host = notNullOrEmpty(entry.getHostDisplayName())
            ? entry.getHostDisplayName()
            : (entry.getDownloaderId() != null ? entry.getDownloaderId().getDisplayName() : "");

        return notNullOrEmpty(host) ? date + " \u00b7 " + host : date;
    }

    private DownloadTypeEnum inferDownloadType(DownloadHistoryEntity entry) {
        DownloaderIdEnum downloaderId = entry.getDownloaderId();
        if (downloaderId == null) {
            return DownloadTypeEnum.VIDEO;
        }

        return switch (downloaderId) {
            case GALLERY_DL ->
                DownloadTypeEnum.GALLERY;
            case SPOTDL ->
                DownloadTypeEnum.SPOTIFY;
            case DIRECT_HTTP ->
                DownloadTypeEnum.DIRECT;
            default ->
                DownloadTypeEnum.VIDEO;
        };
    }

    private RightClickMenuEntries buildCardContextMenu(DownloadHistoryEntity entry) {
        RightClickMenuEntries menu = new RightClickMenuEntries();

        List<File> existingFiles = entry.getFilePaths().stream()
            .map(File::new)
            .filter(File::exists)
            .collect(Collectors.toList());

        if (!existingFiles.isEmpty()) {
            menu.put(l10n("gui.history.open_file"),
                new SingleActionMenuEntry(() -> openFirstAvailableFile(entry),
                    () -> "/assets/play.png"));

            menu.put(l10n("gui.history.open_file_location"),
                new SingleActionMenuEntry(() -> main.open(existingFiles.get(0).getParentFile()),
                    () -> "/assets/directory.png"));
        }

        menu.put(l10n("gui.open_in_browser"),
            new SingleActionMenuEntry(() -> main.openUrlInBrowser(entry.getOriginalUrl() != null
            ? entry.getOriginalUrl() : entry.getUrl()),
                () -> "/assets/internet.png"));

        menu.put(l10n("gui.history.copy_link"),
            new MultiActionMenuEntry<>(
                () -> notNullOrEmpty(entry.getOriginalUrl()) ? entry.getOriginalUrl() : entry.getUrl(),
                (entries) -> {
                    main.getClipboardManager().copyTextToClipboard(new ArrayList<>(entries));
                },
                () -> "/assets/copy-link.png"
            ));

        menu.put(l10n("gui.history.redownload"),
            new RunnableMenuEntry(() -> {
                main.getDownloadManager().redownloadFromHistory(entry);

                String title = StringUtils.safeTruncate(
                    notNullOrEmpty(entry.getTitle())
                    ? entry.getTitle() : entry.getUrl(), 64);

                ToastMessenger.show(frame, Message.builder()
                    .title("gui.history.notification_title")
                    .message("gui.history.redownload.toast", title)
                    .durationMillis(3000)
                    .messageType(MessageTypeEnum.INFO)
                    .discardDuplicates(true)
                    .build());
            }, () -> "/assets/restart.png"));

        menu.put(l10n("gui.history.remove"),
            new RunnableMenuEntry(() -> removeFromHistory(entry),
                () -> "/assets/bin.png"));

        return menu;
    }

    private void openFirstAvailableFile(DownloadHistoryEntity entry) {
        for (String path : entry.getFilePaths()) {
            File file = new File(path);

            if (file.exists()) {
                main.open(file);

                return;
            }
        }

        ToastMessenger.show(frame, Message.builder()
            .title("gui.history.notification_title")
            .message("gui.history.no_files.toast")
            .durationMillis(3000)
            .messageType(MessageTypeEnum.WARNING)
            .build());
    }

    private void removeFromHistory(DownloadHistoryEntity entry) {
        removeEntriesFromHistory(List.of(entry));
    }

    private void removeSelectedFromHistory() {
        if (selectedUrls.isEmpty()) {
            return;
        }

        List<DownloadHistoryEntity> toRemove = loadedEntries.stream()
            .filter(entry -> selectedUrls.contains(entry.getUrl()))
            .collect(Collectors.toList());

        removeEntriesFromHistory(toRemove);
    }

    private void removeEntriesFromHistory(List<DownloadHistoryEntity> entries) {
        if (entries.isEmpty()) {
            return;
        }

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            if (main.getPersistenceManager().isHistoryInitialized()) {
                for (DownloadHistoryEntity entry : entries) {
                    main.getPersistenceManager().getDownloadHistory().remove(entry.getUrl());
                }
            }

            runOnEDT(() -> {
                loadedEntries.removeAll(entries);

                for (DownloadHistoryEntity entry : entries) {
                    selectedUrls.remove(entry.getUrl());
                }

                renderCards();

                ToastMessenger.show(frame, Message.builder()
                    .title("gui.history.notification_title")
                    .message("gui.history.removed.toast")
                    .durationMillis(2000)
                    .messageType(MessageTypeEnum.INFO)
                    .build());
            });
        });
    }

    private void confirmClearAll() {
        if (loadedEntries.isEmpty()) {
            return;
        }

        confirmClearAll(frame);
    }

    public void confirmClearAllFromMainWindow() {
        confirmClearAll(manager.getAppWindow());
    }

    private void confirmClearAll(JFrame toastParent) {
        manager.showConfirmDialog(
            l10n("dialog.confirm"),
            l10n("gui.history.clear_all.confirm"),
            15000,
            false,
            new GUIManager.DialogButton("", (boolean setDefault) -> {
            }),
            new GUIManager.DialogButton(l10n("gui.history.clear_all"), (boolean setDefault) -> {
                GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
                    if (main.getPersistenceManager().isHistoryInitialized()) {
                        main.getPersistenceManager().getDownloadHistory().clearAll();
                    }

                    runOnEDT(() -> {
                        loadedEntries.clear();
                        selectedUrls.clear();
                        lastSelectedUrl = null;

                        if (frame != null) {
                            renderCards();
                        }

                        ToastMessenger.show(toastParent, Message.builder()
                            .title("gui.history.notification_title")
                            .message("gui.history.cleared.toast")
                            .durationMillis(2500)
                            .messageType(MessageTypeEnum.INFO)
                            .build());
                    });
                });
            }));
    }

    private class ResponsiveGridLayout implements LayoutManager {

        private final Timer debounceTimer;
        private boolean forceLayout = true;
        private int lastWidth = -1;

        public ResponsiveGridLayout() {
            debounceTimer = new Timer(150, e -> {
                forceLayout = true;

                if (cardsPanel != null) {
                    cardsPanel.revalidate();
                    cardsPanel.repaint();
                }
            });

            debounceTimer.setRepeats(false);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {

        }

        @Override
        public void removeLayoutComponent(Component comp) {

        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return computeLayout(parent, false);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            int currentWidth = parent.getWidth();

            if (lastWidth == -1 || forceLayout || lastWidth == currentWidth) {
                lastWidth = currentWidth;

                computeLayout(parent, true);
                forceLayout = false;

                return;
            }

            lastWidth = currentWidth;
            debounceTimer.restart();
        }

        private Dimension computeLayout(Container parent, boolean apply) {
            Insets insets = parent.getInsets();
            int scrollbarWidth = scrollPane != null && scrollPane.getVerticalScrollBar().isVisible()
                ? scrollPane.getVerticalScrollBar().getWidth() : 16;

            int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);
            if (availableWidth == 0 && scrollPane != null) {
                availableWidth = Math.max(0, scrollPane.getViewport().getWidth() - insets.left - insets.right - scrollbarWidth);
            }

            int visibleCount = 0;
            for (Component c : parent.getComponents()) {
                if (c.isVisible()) {
                    visibleCount++;
                }
            }

            if (visibleCount == 0) {
                return new Dimension(availableWidth, 0);
            }

            int columns = Math.max(1, (availableWidth + GAP) / (MIN_THUMBNAIL_WIDTH + 16 + GAP));

            int cellWidth = Math.max(MIN_THUMBNAIL_WIDTH + 16, (availableWidth - (columns - 1) * GAP) / columns);
            int thumbnailHeight = (int)((cellWidth - 16) / 16.0 * 9.0);
            int cellHeight = thumbnailHeight + TEXT_PANEL_HEIGHT;

            int x = insets.left;
            int y = insets.top;
            int col = 0;

            for (Component c : parent.getComponents()) {
                if (c.isVisible()) {
                    if (apply) {
                        c.setBounds(x, y, cellWidth, cellHeight);
                    }

                    col++;
                    if (col >= columns) {
                        col = 0;
                        x = insets.left;
                        y += cellHeight + GAP;
                    } else {
                        x += cellWidth + GAP;
                    }
                }
            }

            if (col > 0) {
                y += cellHeight + GAP;
            }

            return new Dimension(availableWidth, Math.max(0, y - GAP + insets.bottom));
        }
    }

}
