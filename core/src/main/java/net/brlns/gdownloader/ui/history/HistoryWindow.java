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
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.persistence.entity.DownloadHistoryEntity;
import net.brlns.gdownloader.persistence.entity.DownloadHistorySummary;
import net.brlns.gdownloader.persistence.repository.DownloadHistoryRepository;
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
import net.brlns.gdownloader.util.collection.LRUCache;

import static net.brlns.gdownloader.downloader.enums.DownloaderIdEnum.*;
import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.installPlaceholder;
import static net.brlns.gdownloader.ui.UIUtils.isPlaceholder;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.URLUtils.getDisplayUrl;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class HistoryWindow {

    private static final int MIN_THUMBNAIL_WIDTH = 170;
    private static final int TEXT_PANEL_HEIGHT = 60;
    private static final int GAP = 10;

    private static final int SEARCH_DEBOUNCE_MS = 200;

    private static final String CARD_URL_PROPERTY = "historyCardUrl";
    private static final String VIRTUAL_INDEX_PROPERTY = "historyVirtualIndex";
    private static final String THUMBNAIL_PANEL_PROPERTY = "historyThumbnailPanel";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    private static final int PAGE_SIZE = 300;
    private static final int MAX_CACHED_PAGES = 60;
    private static final int MAX_CACHED_ENTITIES = 400;
    private static final int BUFFER_ROWS = 2;

    private static final int MAX_CONTEXT_MENU_DEPENDENTS = 200;

    private static final int MAX_CONCURRENT_PAGE_FETCHES = 4;
    private static final int MAX_CONCURRENT_ENTITY_BATCHES = 4;

    private final GDownloader main;
    private final GUIManager manager;

    private JFrame frame;
    private ScrollableQueuePanel cardsPanel;
    private JLabel emptyLabel;
    private JTextField searchField;
    private JLabel countLabel;
    private JScrollPane scrollPane;

    private final ResponsiveGridLayout gridLayout = new ResponsiveGridLayout();

    private final Timer searchDebounceTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> reload());

    private final AtomicInteger totalCount = new AtomicInteger();
    private String currentFilter = "";

    private int loadGeneration = 0;
    private final AtomicBoolean isLoading = new AtomicBoolean();

    private int lastStartIndex = -1;
    private int lastEndIndex = -1;

    private final LRUCache<Integer, List<DownloadHistorySummary>> summaryPageCache
        = new LRUCache<>(MAX_CACHED_PAGES);

    private final Set<Integer> inFlightPages = new HashSet<>();
    private final Deque<Integer> pendingPageFetches = new ArrayDeque<>();
    private int activePageFetches = 0;

    private final Set<String> inFlightEntityUrls = new HashSet<>();
    private final Deque<List<String>> pendingEntityBatches = new ArrayDeque<>();
    private int activeEntityBatches = 0;

    private final LRUCache<String, DownloadHistoryEntity> entityCache
        = new LRUCache<>(MAX_CACHED_ENTITIES);

    private final Map<Integer, JComponent> renderedCards = new LinkedHashMap<>();

    private final Set<String> selectedUrls = new LinkedHashSet<>();
    private String lastSelectedUrl;
    private int lastSelectedIndex = -1;

    public HistoryWindow(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        searchDebounceTimer.setRepeats(false);
    }

    private boolean isRepoInitialized() {
        return main.getPersistenceManager().isHistoryInitialized();
    }

    private DownloadHistoryRepository getRepo() {
        return main.getPersistenceManager().getDownloadHistory();
    }

    public void createAndShowGUI() {
        runOnEDT(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
                frame.requestFocus();

                return;
            }

            frame = new JFrame(l10n("gui.history")) {
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

            if (cardsPanel != null) {
                cardsPanel.setFocusable(true);
                cardsPanel.requestFocusInWindow();
            }

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
                selectAllAsync();
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

        JLabel titleLabel = new JLabel(l10n("gui.history"));
        titleLabel.setForeground(color(FOREGROUND));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);

        countLabel = new JLabel();
        countLabel.setForeground(color(LIGHT_TEXT));
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new BorderLayout());
        searchPanel.setOpaque(true);
        searchPanel.setBackground(color(SIDE_PANEL));
        searchPanel.setBorder(BorderFactory.createLineBorder(color(SIDE_PANEL_HEADER_FOOTER)));

        JLabel searchIcon = new JLabel(loadIcon("/assets/search.png", LIGHT_TEXT, 16));
        searchIcon.setOpaque(false);
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        searchField = new JTextField(22);
        searchField.setBackground(color(SIDE_PANEL));
        searchField.setForeground(color(FOREGROUND));
        searchField.setCaretColor(color(FOREGROUND));
        searchField.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        searchField.setToolTipText(l10n("gui.history.search.tooltip"));
        searchField.setPreferredSize(new Dimension(350, 30));

        installPlaceholder(searchField,
            l10n("gui.history.search.tooltip"),
            color(FOREGROUND), color(LIGHT_TEXT));

        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

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
        rightPanel.add(searchPanel);
        rightPanel.add(refreshButton);
        rightPanel.add(clearAllButton);

        headerPanel.add(rightPanel, BorderLayout.EAST);

        return headerPanel;
    }

    private void scheduleSearchUpdate() {
        String newFilter = readSearchFilter();

        if (!newFilter.equals(currentFilter)) {
            searchDebounceTimer.restart();
        }
    }

    private JScrollPane buildContentPanel() {
        cardsPanel = new ScrollableQueuePanel(gridLayout);
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

        scrollPane.getViewport().addChangeListener(e -> updateVisibleWindow(false));

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

    private String readSearchFilter() {
        if (searchField == null || isPlaceholder(searchField, l10n("gui.history.search.tooltip"))) {
            return "";
        }

        return searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private void reload() {
        reload(false);
    }

    private void reload(boolean preserveScroll) {
        loadGeneration++;
        int generation = loadGeneration;

        String filter = readSearchFilter();

        Point savedPosition = (preserveScroll && scrollPane != null)
            ? scrollPane.getViewport().getViewPosition()
            : new Point(0, 0);

        runOnEDT(() -> {
            isLoading.set(true);
            currentFilter = filter;

            summaryPageCache.clear();
            inFlightPages.clear();
            pendingPageFetches.clear();
            entityCache.clear();
            inFlightEntityUrls.clear();
            pendingEntityBatches.clear();

            if (!preserveScroll) {
                renderedCards.clear();
                cardsPanel.removeAll();
                cardsPanel.setLayout(new GridBagLayout());

                cardsPanel.add(new CustomLoadingSpinner(48));

                cardsPanel.revalidate();
                cardsPanel.repaint();

                if (scrollPane != null) {
                    scrollPane.getViewport().setViewPosition(savedPosition);
                }
            }

            lastStartIndex = -1;
            lastEndIndex = -1;
        });

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            long count = isRepoInitialized()
                ? getRepo().getCount(filter)
                : 0L;

            runOnEDT(() -> {
                if (generation != loadGeneration) {
                    return;
                }

                isLoading.set(false);
                totalCount.set((int)Math.min(count, Integer.MAX_VALUE - 16));

                if (countLabel != null) {
                    countLabel.setText(String.valueOf(count));
                }

                updateVisibleWindow(true);

                if (preserveScroll && scrollPane != null) {
                    SwingUtilities.invokeLater(() -> {
                        int maxY = Math.max(0, scrollPane.getViewport().getView().getHeight() - scrollPane.getViewport().getHeight());
                        savedPosition.y = Math.min(savedPosition.y, maxY);
                        scrollPane.getViewport().setViewPosition(savedPosition);
                    });
                }
            });
        });
    }

    private GridMetrics computeGridMetrics() {
        Insets insets = cardsPanel.getInsets();
        int availableWidth = Math.max(0, cardsPanel.getWidth() - insets.left - insets.right);

        int baseCardWidth = MIN_THUMBNAIL_WIDTH + 16;
        int maxColumns = 7;
        int maxCardWidth = (int)(baseCardWidth * 1.5);

        int columns = Math.max(1, (availableWidth + GAP) / (baseCardWidth + GAP));
        columns = Math.min(columns, maxColumns);

        int cellWidth = Math.max(baseCardWidth, (availableWidth - (columns - 1) * GAP) / columns);
        cellWidth = Math.min(cellWidth, maxCardWidth);

        int gridWidth = (columns * cellWidth) + ((columns - 1) * GAP);
        int offsetX = insets.left + Math.max(0, (availableWidth - gridWidth) / 2);

        int thumbnailHeight = (int)((cellWidth - 16) / 16.0 * 9.0);
        int cellHeight = thumbnailHeight + TEXT_PANEL_HEIGHT;

        return new GridMetrics(columns, cellWidth, cellHeight, offsetX, insets.top, availableWidth);
    }

    private void updateVisibleWindow(boolean force) {
        if (cardsPanel == null || scrollPane == null || isLoading.get()) {
            return;
        }

        int total = totalCount.get();

        if (total <= 0) {
            if (!(cardsPanel.getLayout() instanceof BorderLayout)) {
                renderedCards.clear();

                cardsPanel.removeAll();
                cardsPanel.setLayout(new BorderLayout());
                cardsPanel.add(emptyLabel, BorderLayout.CENTER);
                cardsPanel.revalidate();
                cardsPanel.repaint();
            }

            lastStartIndex = -1;
            lastEndIndex = -1;

            return;
        }

        if (!(cardsPanel.getLayout() instanceof ResponsiveGridLayout)) {
            cardsPanel.removeAll();
            renderedCards.clear();
            cardsPanel.setLayout(gridLayout);

            force = true;
        }

        GridMetrics metrics = computeGridMetrics();
        int totalRows = (int)Math.ceil(total / (double)metrics.columns());
        int rowHeight = Math.max(1, metrics.cellHeight() + GAP);

        Rectangle viewRect = scrollPane.getViewport().getViewRect();

        int firstRow = Math.max(0, (viewRect.y - metrics.insetsTop()) / rowHeight - BUFFER_ROWS);
        int lastRow = Math.min(totalRows - 1,
            (viewRect.y + viewRect.height - metrics.insetsTop()) / rowHeight + BUFFER_ROWS);
        firstRow = Math.min(firstRow, lastRow);

        int startIndex = firstRow * metrics.columns();
        int endIndex = Math.min(total - 1, (lastRow + 1) * metrics.columns() - 1);

        if (!force && startIndex == lastStartIndex && endIndex == lastEndIndex) {
            return;
        }

        lastStartIndex = startIndex;
        lastEndIndex = endIndex;

        Map<Integer, JComponent> newRendered = new LinkedHashMap<>();
        List<String> needThumbnails = new ArrayList<>();

        for (int index = startIndex; index <= endIndex; index++) {
            Optional<DownloadHistorySummary> summary = getCachedSummary(index);
            JComponent existing = renderedCards.remove(index);
            boolean needsThumb = summary.isPresent() && entityCache.get(summary.get().getUrl()) == null;

            if (existing != null && matchesExpected(existing, summary)) {
                newRendered.put(index, existing);
            } else {
                if (existing != null) {
                    cardsPanel.remove(existing);
                }

                JComponent comp = summary.isPresent()
                    ? buildCard(summary.get(), index) : buildPlaceholderCard(index);

                cardsPanel.add(comp);
                newRendered.put(index, comp);
            }

            if (needsThumb) {
                needThumbnails.add(summary.get().getUrl());
            }
        }

        for (JComponent leftover : renderedCards.values()) {
            cardsPanel.remove(leftover);
        }

        renderedCards.clear();
        renderedCards.putAll(newRendered);

        updateSelectionUI();

        cardsPanel.revalidate();
        cardsPanel.repaint();

        if (!needThumbnails.isEmpty()) {
            fetchEntitiesForWindow(needThumbnails);
        }
    }

    private boolean matchesExpected(JComponent existing, Optional<DownloadHistorySummary> summary) {
        Object urlProp = existing.getClientProperty(CARD_URL_PROPERTY);

        if (summary.isEmpty()) {
            return urlProp == null;
        }

        return summary.get().getUrl().equals(urlProp);
    }

    private Optional<DownloadHistorySummary> getCachedSummary(int index) {
        if (index < 0 || index >= totalCount.get()) {
            return Optional.empty();
        }

        int page = index / PAGE_SIZE;
        List<DownloadHistorySummary> pageRows = summaryPageCache.get(page);

        if (pageRows == null) {
            ensurePageLoaded(page);

            return Optional.empty();
        }

        int withinPage = index - page * PAGE_SIZE;
        if (withinPage < 0 || withinPage >= pageRows.size()) {
            return Optional.empty();
        }

        return Optional.of(pageRows.get(withinPage));
    }

    private void ensurePageLoaded(int page) {
        if (!inFlightPages.add(page)) {
            return;
        }

        pendingPageFetches.addLast(page);

        pumpPageFetchQueue();
    }

    private void pumpPageFetchQueue() {
        while (activePageFetches < MAX_CONCURRENT_PAGE_FETCHES && !pendingPageFetches.isEmpty()) {
            int page = pendingPageFetches.pollFirst();
            activePageFetches++;

            dispatchPageFetch(page);
        }
    }

    private void dispatchPageFetch(int page) {
        int offset = page * PAGE_SIZE;
        String filterSnapshot = currentFilter;
        int generation = loadGeneration;

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            List<DownloadHistorySummary> rows = isRepoInitialized()
                ? getRepo().getSummaryPage(offset, PAGE_SIZE, filterSnapshot)
                : List.of();

            runOnEDT(() -> {
                inFlightPages.remove(page);
                activePageFetches--;

                if (generation == loadGeneration) {
                    summaryPageCache.put(page, rows);
                    updateVisibleWindow(true);
                }

                pumpPageFetchQueue();
            });
        });
    }

    private void fetchEntitiesForWindow(List<String> urls) {
        List<String> missing = urls.stream()
            .distinct()
            .filter(u -> entityCache.get(u) == null)
            .filter(inFlightEntityUrls::add)
            .collect(Collectors.toList());

        if (missing.isEmpty()) {
            return;
        }

        pendingEntityBatches.addLast(missing);

        pumpEntityFetchQueue();
    }

    private void pumpEntityFetchQueue() {
        while (activeEntityBatches < MAX_CONCURRENT_ENTITY_BATCHES && !pendingEntityBatches.isEmpty()) {
            List<String> batch = pendingEntityBatches.pollFirst();
            activeEntityBatches++;

            dispatchEntityFetch(batch);
        }
    }

    private void dispatchEntityFetch(List<String> missing) {
        int generation = loadGeneration;

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            Map<String, DownloadHistoryEntity> resolved = isRepoInitialized()
                ? getRepo().getEntitiesByUrls(missing)
                : Map.of();

            runOnEDT(() -> {
                missing.forEach(inFlightEntityUrls::remove);
                activeEntityBatches--;

                if (generation == loadGeneration) {
                    resolved.forEach(entityCache::put);
                    applyResolvedThumbnails(resolved);
                }

                pumpEntityFetchQueue();
            });
        });
    }

    private void applyResolvedThumbnails(Map<String, DownloadHistoryEntity> resolved) {
        for (JComponent comp : renderedCards.values()) {
            Object urlProp = comp.getClientProperty(CARD_URL_PROPERTY);
            if (!(urlProp instanceof String url) || !resolved.containsKey(url)) {
                continue;
            }

            Object thumbProp = comp.getClientProperty(THUMBNAIL_PANEL_PROPERTY);
            if (thumbProp instanceof CustomThumbnailPanel thumbnailPanel) {
                BufferedImage thumbnail = ImageUtils.base64ToBufferedImage(
                    resolved.get(url).getBase64EncodedThumbnail());

                if (thumbnail != null) {
                    thumbnailPanel.setImage(thumbnail);
                }
            }
        }
    }

    private void resolveEntities(Collection<String> urls, Consumer<Map<String, DownloadHistoryEntity>> callback) {
        Map<String, DownloadHistoryEntity> resolved = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (String url : urls) {
            DownloadHistoryEntity cached = entityCache.get(url);

            if (cached != null) {
                resolved.put(url, cached);
            } else {
                missing.add(url);
            }
        }

        if (missing.isEmpty()) {
            callback.accept(resolved);

            return;
        }

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            Map<String, DownloadHistoryEntity> fetched = isRepoInitialized()
                ? getRepo().getEntitiesByUrls(missing)
                : Map.of();

            runOnEDT(() -> {
                fetched.forEach(entityCache::put);
                resolved.putAll(fetched);
                callback.accept(resolved);
            });
        });
    }

    private DownloadHistoryEntity toLightEntity(DownloadHistorySummary s) {
        DownloadHistoryEntity e = new DownloadHistoryEntity();
        e.setUrl(s.getUrl());
        e.setOriginalUrl(s.getOriginalUrl());
        e.setTitle(s.getTitle());
        e.setHostDisplayName(s.getHostDisplayName());
        e.setDownloaderId(s.getDownloaderId());
        e.setDownloadedAt(s.getDownloadedAt());

        return e;
    }

    private JComponent buildPlaceholderCard(int virtualIndex) {
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

        card.putClientProperty(VIRTUAL_INDEX_PROPERTY, virtualIndex);
        card.setOpaque(false);
        card.setBackground(color(MEDIA_CARD));

        return card;
    }

    private JPanel buildCard(DownloadHistorySummary entry, int virtualIndex) {
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
        card.putClientProperty(VIRTUAL_INDEX_PROPERTY, virtualIndex);
        card.setOpaque(false);
        card.setLayout(new BorderLayout(0, 6));
        card.setBackground(isSelected(entry) ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        String displayUrl = getDisplayUrl(entry.getOriginalUrl() != null
            ? entry.getOriginalUrl() : entry.getUrl());

        card.setToolTipText(displayUrl);

        CustomThumbnailPanel thumbnailPanel = new CustomThumbnailPanel();
        thumbnailPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        thumbnailPanel.setBackground(color(MEDIA_CARD_THUMBNAIL));
        thumbnailPanel.setToolTipText(displayUrl);
        card.putClientProperty(THUMBNAIL_PANEL_PROPERTY, thumbnailPanel);

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

        JPanel closeButtonPanel = createGradientWrapper(closeButton);
        closeButtonPanel.setVisible(false);
        thumbnailPanel.add(closeButtonPanel);

        DownloadHistoryEntity cachedEntity = entityCache.get(entry.getUrl());
        BufferedImage thumbnail = cachedEntity != null
            ? ImageUtils.base64ToBufferedImage(cachedEntity.getBase64EncodedThumbnail()) : null;

        if (thumbnail != null) {
            thumbnailPanel.setImage(thumbnail);
        } else {
            thumbnailPanel.setPlaceholderIcon(inferDownloadType(entry.getDownloaderId()));
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
                    handleRightClick(entry, card, virtualIndex, e);
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    resolveEntities(Set.of(entry.getUrl()), resolved -> {
                        DownloadHistoryEntity full = resolved.get(entry.getUrl());

                        if (full != null) {
                            openFirstAvailableFile(full);
                        }
                    });
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    handleLeftClick(entry, virtualIndex, e);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isSelected(entry)) {
                    card.setBackground(color(MEDIA_CARD_HOVER));
                    card.repaint();
                }

                closeButtonPanel.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isSelected(entry)) {
                    card.setBackground(color(MEDIA_CARD));
                    card.repaint();
                }

                Rectangle bounds = new Rectangle(0, 0, card.getWidth(), card.getHeight());
                if (!bounds.contains(e.getPoint())) {
                    closeButtonPanel.setVisible(false);
                }
            }
        });

        return card;
    }

    private boolean isSelected(DownloadHistorySummary entry) {
        return selectedUrls.contains(entry.getUrl());
    }

    private void handleLeftClick(DownloadHistorySummary entry, int virtualIndex, MouseEvent e) {
        String url = entry.getUrl();

        if (e.isControlDown()) {
            if (!selectedUrls.add(url)) {
                selectedUrls.remove(url);
            }

            lastSelectedUrl = url;
            lastSelectedIndex = virtualIndex;

            updateSelectionUI();
        } else if (e.isShiftDown() && lastSelectedIndex >= 0) {
            selectRangeAsync(lastSelectedIndex, virtualIndex);
        } else {
            selectedUrls.clear();
            selectedUrls.add(url);

            lastSelectedUrl = url;
            lastSelectedIndex = virtualIndex;

            updateSelectionUI();
        }
    }

    private void selectRangeAsync(int indexA, int indexB) {
        int min = Math.min(indexA, indexB);
        int max = Math.max(indexA, indexB);
        int count = max - min + 1;
        String filterSnapshot = currentFilter;
        int generation = loadGeneration;

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            List<String> urls = isRepoInitialized()
                ? getRepo().getUrlsPage(min, count, filterSnapshot)
                : List.of();

            runOnEDT(() -> {
                if (generation != loadGeneration) {
                    return;
                }

                selectedUrls.clear();
                selectedUrls.addAll(urls);

                updateSelectionUI();
            });
        });
    }

    private void selectAllAsync() {
        if (totalCount.get() == 0) {
            return;
        }

        String filterSnapshot = currentFilter;
        int generation = loadGeneration;
        int lastIndex = totalCount.get() - 1;

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            List<String> urls = isRepoInitialized()
                ? getRepo().getAllUrls(filterSnapshot)
                : List.of();

            runOnEDT(() -> {
                if (generation != loadGeneration) {
                    return;
                }

                selectedUrls.clear();
                selectedUrls.addAll(urls);

                lastSelectedUrl = urls.isEmpty() ? null : urls.get(urls.size() - 1);
                lastSelectedIndex = urls.isEmpty() ? -1 : lastIndex;

                updateSelectionUI();
            });
        });
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

    private void handleRightClick(DownloadHistorySummary entry, JPanel card, int virtualIndex, MouseEvent e) {
        if (!isSelected(entry)) {
            selectedUrls.clear();
            selectedUrls.add(entry.getUrl());

            lastSelectedUrl = entry.getUrl();
            lastSelectedIndex = virtualIndex;

            updateSelectionUI();
        }

        int x = e.getX();
        int y = e.getY();
        boolean multiSelect = selectedUrls.size() > 1;

        Set<String> resolveTargets = new LinkedHashSet<>();
        resolveTargets.add(entry.getUrl());

        if (multiSelect) {
            int added = 0;
            for (String url : selectedUrls) {
                if (added >= MAX_CONTEXT_MENU_DEPENDENTS) {
                    break;
                }

                if (resolveTargets.add(url)) {
                    added++;
                }
            }
        }

        resolveEntities(resolveTargets, resolved -> {
            DownloadHistoryEntity clicked = resolved.getOrDefault(entry.getUrl(), toLightEntity(entry));

            if (multiSelect) {
                List<RightClickMenuEntries> dependents = new ArrayList<>();

                for (String otherUrl : resolveTargets) {
                    if (otherUrl.equals(entry.getUrl())) {
                        continue;
                    }

                    DownloadHistoryEntity otherEntity = resolved.get(otherUrl);
                    if (otherEntity != null) {
                        dependents.add(buildCardContextMenu(otherEntity));
                    }
                }

                manager.showRightClickMenu(card, buildCardContextMenu(clicked), dependents, x, y);
            } else {
                manager.showRightClickMenu(card, buildCardContextMenu(clicked), x, y);
            }
        });
    }

    private String buildSubtitle(DownloadHistorySummary entry) {
        String date = DATE_FORMATTER.format(
            Instant.ofEpochMilli(entry.getDownloadedAt()).atZone(ZoneId.systemDefault()));

        String host = notNullOrEmpty(entry.getHostDisplayName())
            ? entry.getHostDisplayName()
            : (entry.getDownloaderId() != null ? entry.getDownloaderId().getDisplayName() : "");

        return notNullOrEmpty(host) ? date + " \u00b7 " + host : date;
    }

    private DownloadTypeEnum inferDownloadType(DownloaderIdEnum downloaderId) {
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
            new RunnableMenuEntry(() -> removeUrlsFromHistory(Set.of(entry.getUrl())),
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

    private void removeFromHistory(DownloadHistorySummary entry) {
        removeUrlsFromHistory(Set.of(entry.getUrl()));
    }

    private void removeSelectedFromHistory() {
        if (selectedUrls.isEmpty()) {
            return;
        }

        removeUrlsFromHistory(new LinkedHashSet<>(selectedUrls));
    }

    private void removeUrlsFromHistory(Set<String> urls) {
        if (urls.isEmpty()) {
            return;
        }

        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            if (isRepoInitialized()) {
                getRepo().removeUrls(urls);
            }

            runOnEDT(() -> {
                selectedUrls.removeAll(urls);

                for (String url : urls) {
                    entityCache.remove(url);
                }

                if (lastSelectedUrl != null && urls.contains(lastSelectedUrl)) {
                    lastSelectedUrl = null;
                    lastSelectedIndex = -1;
                }

                reload(true);

                ToastMessenger.show(frame, Message.builder()
                    .title("gui.history.notification_title")
                    .message("gui.history.removed.toast", urls.size())
                    .durationMillis(2000)
                    .messageType(MessageTypeEnum.INFO)
                    .build());
            });
        });
    }

    private void confirmClearAll() {
        if (totalCount.get() == 0) {
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
                    if (isRepoInitialized()) {
                        getRepo().clearAll();
                    }

                    runOnEDT(() -> {
                        selectedUrls.clear();
                        lastSelectedUrl = null;
                        lastSelectedIndex = -1;
                        entityCache.clear();

                        if (frame != null) {
                            reload();
                        }

                        ToastMessenger.show(toastParent, Message.builder()
                            .title("gui.history.notification_title")
                            .message("gui.history.cleared.toast")
                            .durationMillis(2500)
                            .messageType(MessageTypeEnum.INFO)
                            .build());
                    });
                });
            })
        );
    }

    private JPanel createGradientWrapper(JComponent content) {
        JPanel wrapper = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2d = (Graphics2D)g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;

                float radius = 15.0f;

                RadialGradientPaint paint = new RadialGradientPaint(
                    new Point(cx, cy),
                    radius,
                    new float[] {0.0f, 1.0f},
                    new Color[] {new Color(0, 0, 0, 120), new Color(0, 0, 0, 0)}
                );

                g2d.setPaint(paint);
                g2d.fillOval(cx - (int)radius, cy - (int)radius, (int)(radius * 2), (int)(radius * 2));

                g2d.dispose();
            }
        };

        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        wrapper.add(content);

        return wrapper;
    }

    private record GridMetrics(
        int columns,
        int cellWidth,
        int cellHeight,
        int offsetX,
        int insetsTop,
        int availableWidth) {

    }

    private final class ResponsiveGridLayout implements LayoutManager {

        private final Timer debounceTimer;
        private boolean forceLayout = true;
        private int lastWidth = -1;

        private ResponsiveGridLayout() {
            debounceTimer = new Timer(150, e -> {
                forceLayout = true;

                if (cardsPanel != null) {
                    cardsPanel.revalidate();
                    cardsPanel.repaint();

                    updateVisibleWindow(true);
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
            GridMetrics metrics = computeGridMetrics();

            int total = totalCount.get();

            if (total <= 0) {
                return new Dimension(metrics.availableWidth(), 0);
            }

            int totalRows = (int)Math.ceil(total / (double)metrics.columns());
            int height = metrics.insetsTop() + totalRows * metrics.cellHeight()
                + Math.max(0, totalRows - 1) * GAP;

            Insets insets = parent.getInsets();
            return new Dimension(metrics.availableWidth(), height + insets.bottom);
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

                applyBounds(parent);
                forceLayout = false;

                return;
            }

            lastWidth = currentWidth;
            debounceTimer.restart();
        }

        private void applyBounds(Container parent) {
            GridMetrics metrics = computeGridMetrics();

            for (Component comp : parent.getComponents()) {
                if (!(comp instanceof JComponent jcomp)) {
                    continue;
                }

                Object indexProp = jcomp.getClientProperty(VIRTUAL_INDEX_PROPERTY);
                if (!(indexProp instanceof Integer index)) {
                    continue;
                }

                int row = index / metrics.columns();
                int col = index % metrics.columns();

                int x = metrics.offsetX() + col * (metrics.cellWidth() + GAP);
                int y = metrics.insetsTop() + row * (metrics.cellHeight() + GAP);

                comp.setBounds(x, y, metrics.cellWidth(), metrics.cellHeight());
            }
        }
    }
}
