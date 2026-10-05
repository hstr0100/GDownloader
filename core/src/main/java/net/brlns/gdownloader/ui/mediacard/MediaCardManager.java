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
package net.brlns.gdownloader.ui.mediacard;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.IllegalComponentStateException;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.CloseReasonEnum;
import net.brlns.gdownloader.downloader.enums.QueueFilterEnum;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.QueueFilterChangedEvent;
import net.brlns.gdownloader.event.impl.SettingsChangeEvent;
import net.brlns.gdownloader.system.ShutdownRegistry;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.custom.CustomMediaCardUI;
import net.brlns.gdownloader.ui.custom.CustomMediaCardUI.MediaCardPanel;
import net.brlns.gdownloader.ui.dnd.WindowTransferHandler;
import net.brlns.gdownloader.ui.menu.RightClickMenuEntries;
import net.brlns.gdownloader.util.collection.ConcurrentLinkedHashSet;

import static net.brlns.gdownloader.GDownloader.spawn;
import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;
import static net.brlns.gdownloader.ui.UIUtils.scrollPaneToBottom;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.containsIgnoreCase;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class MediaCardManager {

    private static final int RENDER_BUFFER_PX = 600;
    private static final int COLUMN_CHANGE_BOTTOM_EPSILON_PX = 24;

    private static final long LIVE_SORT_REFRESH_INTERVAL_MS = 500;
    private static final long LIVE_SORT_MANUAL_DEBOUNCE_MS = 1500;

    private final GDownloader main;
    private final GUIManager manager;

    private final AtomicInteger mediaCardId = new AtomicInteger();

    private ScrollableQueuePanel mediaQueuePane;

    private final AtomicBoolean currentlyUpdatingMediaCards = new AtomicBoolean();
    private final AtomicLong lastMediaCardQueueUpdate = new AtomicLong();
    private final Queue<MediaCardUIUpdateEntry> mediaCardUIUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Map<Integer, MediaCard> mediaCards = new ConcurrentHashMap<>();

    private final List<Integer> orderedIds = new ArrayList<>();
    private final Set<Integer> orderedIdSet = new HashSet<>();
    private final AtomicBoolean filterPending = new AtomicBoolean();
    private final AtomicBoolean filtersDirty = new AtomicBoolean();
    private List<Integer> filteredIds = new ArrayList<>();
    private final Map<Integer, MediaCardPanel> renderedCards = new LinkedHashMap<>();

    private int lastProcessedWindowWidth = -1;

    private int lastFirstIndex = -1;
    private int lastLastIndex = -1;
    private boolean visibleWindowRendered;

    private int lastLayoutColumns = -1;
    private int lastLayoutRowHeight = -1;
    private int lastLayoutViewportHeight = -1;
    private boolean adjustingScroll;

    private int cachedRowHeight = -1;
    private CustomMediaCardUI measurementUi;

    private final AtomicReference<MediaCardPanel> hoveredCardPanel = new AtomicReference<>();
    private final AtomicReference<Point> lastMouseScreenPoint = new AtomicReference<>();
    private AWTEventListener globalMouseListener;

    private final ConcurrentLinkedHashSet<Integer> selectedMediaCards = new ConcurrentLinkedHashSet<>();
    private final AtomicReference<MediaCard> lastSelectedMediaCard = new AtomicReference<>(null);
    private final AtomicBoolean isMultiSelectMode = new AtomicBoolean();

    private final MediaCardGridLayout mediaCardGridLayout;

    private final AtomicReference<String> currentSearchQuery = new AtomicReference<>("");
    private final AtomicReference<QueueFilterEnum> currentStatusFilter = new AtomicReference<>(QueueFilterEnum.ALL);
    private final AtomicReference<Consumer<Integer>> matchCountListener = new AtomicReference<>();

    private JScrollPane queueScrollPane;

    private final Timer mediaCardQueueTimer;

    private final AtomicLong lastManualReorderTime = new AtomicLong();
    private final AtomicLong lastLiveSortRefresh = new AtomicLong();
    private final AtomicBoolean liveSortPending = new AtomicBoolean();

    private final Queue<Runnable> settledActions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean restoringQueue = new AtomicBoolean();

    public MediaCardManager(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        mediaCardGridLayout = new MediaCardGridLayout(
            () -> filteredIds.size(),
            this::getRowHeight,
            () -> updateVisibleWindow(true));

        mediaCardQueueTimer = new Timer(50, e -> {
            processMediaCardQueue();
            runSettledActions();
            applyDirtyFilters();
            maybeApplyLiveSort();
        });
        mediaCardQueueTimer.start();

        EventDispatcher.registerEDT(SettingsChangeEvent.class, (event) -> {
            int newPreference = event.getSettings().getMaxDownloadQueueColumns();
            if (newPreference != mediaCardGridLayout.getColumnPreference()) {
                setColumnLayoutPreference(newPreference);
            }

            invalidateRowHeightCache();
            updateVisibleWindow(true);
        });
    }

    @PreDestroy
    public void close() {
        if (mediaCardQueueTimer != null) {
            mediaCardQueueTimer.stop();
        }

        synchronized (mediaCardUIUpdateQueue) {
            mediaCardUIUpdateQueue.clear();
        }
    }

    public int getRenderedCardCount() {
        return renderedCards.size();
    }

    public void initializeQueueScrollPane(JScrollPane scrollPane) {
        queueScrollPane = scrollPane;

        queueScrollPane.getViewport().addChangeListener(e -> onViewportScrolled());

        installGlobalHoverTracking();
    }

    private void installGlobalHoverTracking() {
        if (globalMouseListener != null) {
            return;
        }

        globalMouseListener = event -> {
            if (!(event instanceof MouseEvent mouseEvent)) {
                return;
            }

            int id = mouseEvent.getID();
            if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_DRAGGED
                && id != MouseEvent.MOUSE_EXITED) {
                return;
            }

            Component source = mouseEvent.getComponent();
            if (source == null
                || SwingUtilities.getWindowAncestor(source) != manager.getAppWindow()) {
                return;
            }

            try {
                lastMouseScreenPoint.set(mouseEvent.getLocationOnScreen());
            } catch (IllegalComponentStateException e) {
                lastMouseScreenPoint.set(null);
            }

            recomputeHover();
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener,
            AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @PreDestroy
    public void removeListeners() {
        if (globalMouseListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener);

            globalMouseListener = null;
        }
    }

    private void onViewportScrolled() {
        assert SwingUtilities.isEventDispatchThread();

        if (adjustingScroll) {
            return;
        }

        updateVisibleWindow(false);
        recomputeHover();
    }

    private void recomputeHover() {
        assert SwingUtilities.isEventDispatchThread();
        if (mediaQueuePane == null || queueScrollPane == null) {
            return;
        }

        Point screenPoint = lastMouseScreenPoint.get();
        Rectangle viewportScreenBounds = null;

        if (queueScrollPane.isShowing()) {
            try {
                viewportScreenBounds = new Rectangle(
                    queueScrollPane.getViewport().getLocationOnScreen(),
                    queueScrollPane.getViewport().getSize());
            } catch (IllegalComponentStateException e) {
                return;
            }
        }

        MediaCardPanel currentHover = hoveredCardPanel.get();
        if (screenPoint != null && currentHover != null
            && currentHover.isShowing() && viewportScreenBounds != null) {
            try {
                Rectangle currentBounds = new Rectangle(
                    currentHover.getLocationOnScreen(), currentHover.getSize());

                if (currentBounds.contains(screenPoint) && viewportScreenBounds.contains(screenPoint)) {
                    return;
                }
            } catch (IllegalComponentStateException e) {
                // Fall through
            }
        }

        MediaCardPanel newHovered = null;

        if (screenPoint != null && viewportScreenBounds != null) {
            if (viewportScreenBounds.contains(screenPoint)) {
                Point localPoint = new Point(screenPoint);
                SwingUtilities.convertPointFromScreen(localPoint, mediaQueuePane);

                Component deepest = SwingUtilities.getDeepestComponentAt(
                    mediaQueuePane, localPoint.x, localPoint.y);
                newHovered = findEnclosingCard(deepest);
            }
        }

        applyHover(newHovered);
    }

    private void applyHover(@Nullable MediaCardPanel newHovered) {
        MediaCardPanel oldHovered = hoveredCardPanel.get();
        if (oldHovered == newHovered) {
            return;
        }

        hoveredCardPanel.set(newHovered);

        if (oldHovered != null) {
            MediaCard oldCard = oldHovered.getMediaCard();
            if (oldCard != null && !isMediaCardSelected(oldCard) && !isMultiSelectMode.get()) {
                oldHovered.setBackground(color(MEDIA_CARD));
                oldHovered.repaint();
            }
        }

        if (newHovered != null) {
            MediaCard newCard = newHovered.getMediaCard();
            if (newCard != null && !isMediaCardSelected(newCard) && !isMultiSelectMode.get()) {
                newHovered.setBackground(color(MEDIA_CARD_HOVER));
                newHovered.repaint();
            }
        }

        if (queueScrollPane.getViewport().getScrollMode() == JViewport.BACKINGSTORE_SCROLL_MODE) {
            queueScrollPane.getViewport().repaint();
        }
    }

    @Nullable
    private MediaCardPanel findEnclosingCard(@Nullable Component component) {
        Component current = component;
        while (current != null && current != mediaQueuePane) {
            if (current instanceof MediaCardPanel panel) {
                return panel;
            }

            current = current.getParent();
        }

        return null;
    }

    public void setColumnLayoutPreference(int columns) {
        mediaCardGridLayout.setColumnPreference(columns);

        runOnEDT(() -> {
            if (mediaQueuePane != null) {
                updateVisibleWindow(true);

                mediaQueuePane.revalidate();
                mediaQueuePane.repaint();
            }
        });
    }

    public int getColumnLayoutPreference() {
        return mediaCardGridLayout.getColumnPreference();
    }

    public JPanel getOrCreateMediaQueuePanel() {
        if (mediaQueuePane != null) {
            return mediaQueuePane;
        }

        mediaQueuePane = new ScrollableQueuePanel();
        mediaQueuePane.setLayout(mediaCardGridLayout);
        mediaQueuePane.setBackground(color(BACKGROUND));
        mediaQueuePane.setOpaque(true);
        mediaQueuePane.addMouseListener(manager.getDefaultMouseAdapter());

        InputMap inputMap = mediaQueuePane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = mediaQueuePane.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "selectAllCards");
        actionMap.put("selectAllCards", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllMediaCards();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedCards");
        actionMap.put("deleteSelectedCards", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedMediaCards();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undoDelete");
        actionMap.put("undoDelete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                main.getDownloadManager().undoLastDelete();
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_RELEASED) {
                if (!e.isControlDown() && !e.isShiftDown()) {
                    isMultiSelectMode.set(false);
                }
            }

            return false;
        });

        return mediaQueuePane;
    }

    public MediaCard addMediaCard(String... mediaLabel) {
        int id = mediaCardId.incrementAndGet();

        MediaCard mediaCard = new MediaCard(id);
        int windowWidth = manager.getAppWindow().getWidth();
        int screenWidth = manager.getAppWindow().getGraphicsConfiguration().getBounds().width;
        mediaCard.adjustScale(windowWidth, screenWidth);
        mediaCard.setLabel(mediaLabel);
        mediaCards.put(id, mediaCard);

        // Preload content pane
        runOnEDT(() -> getOrCreateMediaQueuePanel());

        mediaCardUIUpdateQueue.add(new MediaCardUIUpdateEntry(CARD_ADD, mediaCard));

        return mediaCard;
    }

    public void removeMediaCard(int id, CloseReasonEnum reason) {
        if (ShutdownRegistry.isClosed()) {
            return;
        }

        MediaCard mediaCard = mediaCards.remove(id);

        if (mediaCard != null) {
            mediaCard.close(reason);

            selectedMediaCards.remove(mediaCard.getId());

            mediaCardUIUpdateQueue.add(new MediaCardUIUpdateEntry(CARD_REMOVE, mediaCard));
        }
    }

    public boolean isEmpty() {
        return mediaCards.isEmpty();
    }

    public boolean hasPendingUIUpdates() {
        boolean queuePending = !mediaCardUIUpdateQueue.isEmpty() || currentlyUpdatingMediaCards.get();

        if (mediaCards.isEmpty()) {
            return queuePending;
        }

        return queuePending || !visibleWindowRendered;
    }

    public boolean isMediaCardSelected(MediaCard card) {
        return isMediaCardSelected(card.getId());
    }

    public boolean isMediaCardSelected(int cardId) {
        return selectedMediaCards.contains(cardId);
    }

    public void selectAllMediaCards() {
        selectedMediaCards.replaceAll(filteredIds);

        updateMediaCardSelectionState();
    }

    public void deleteSelectedMediaCards() {
        for (int cardId : selectedMediaCards) {
            removeMediaCard(cardId, CloseReasonEnum.MANUAL);
        }

        selectedMediaCards.clear();

        updateMediaCardSelectionState();
    }

    public void deselectAllMediaCards() {
        selectedMediaCards.clear();

        updateMediaCardSelectionState();
    }

    private void updateMediaCardSelectionState() {
        for (MediaCardPanel panel : renderedCards.values()) {
            MediaCard card = panel.getMediaCard();
            if (card == null) {
                continue;
            }

            panel.setBackground(isMediaCardSelected(card)
                ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
        }
    }

    private void selectMediaCardRange(MediaCard start, MediaCard end) {
        int startIndex = orderedIds.indexOf(start.getId());
        int endIndex = orderedIds.indexOf(end.getId());

        if (startIndex == -1 || endIndex == -1) {
            return;
        }

        int minIndex = Math.min(startIndex, endIndex);
        int maxIndex = Math.max(startIndex, endIndex);

        Set<Integer> filteredLookup = new HashSet<>(filteredIds);

        List<Integer> cardsToAdd = new ArrayList<>();
        for (int i = minIndex; i <= maxIndex; i++) {
            int id = orderedIds.get(i);

            if (filteredLookup.contains(id)) {
                cardsToAdd.add(id);
            }
        }

        selectedMediaCards.replaceAll(cardsToAdd);

        runOnEDT(() -> {
            updateMediaCardSelectionState();
        });
    }

    private void processMediaCardQueue() {
        if (mediaQueuePane == null || queueScrollPane == null
            || mediaCardUIUpdateQueue.isEmpty()
            || currentlyUpdatingMediaCards.get()
            // Give the EDT some room for breathing
            || (System.currentTimeMillis() - lastMediaCardQueueUpdate.get()) < 100) {
            return;
        }

        runOnEDT(() -> {
            if (log.isDebugEnabled()) {
                log.debug("Items in queue: {}", mediaCardUIUpdateQueue.size());
            }

            currentlyUpdatingMediaCards.set(true);

            boolean scrollToBottom = false;

            try {
                LinkedHashSet<Integer> added = new LinkedHashSet<>();
                Set<Integer> removed = new HashSet<>();

                int count = 0;

                int batchLimit = mediaCardUIUpdateQueue.size() > 5000 ? 5000 : 1000;

                MediaCardUIUpdateEntry entry;
                while (count < batchLimit && (entry = mediaCardUIUpdateQueue.poll()) != null) {// Process in batches of 1000 items every 100ms
                    count++;

                    int id = entry.getMediaCard().getId();

                    if (entry.getUpdateType() == CARD_ADD) {
                        added.add(id);
                        scrollToBottom = true;
                    } else if (!added.remove(id)) {
                        removed.add(id);
                    }
                }

                boolean needsFullRecompute = false;

                if (!removed.isEmpty()) {
                    needsFullRecompute = true;

                    orderedIds.removeIf(removed::contains);
                    orderedIdSet.removeAll(removed);

                    for (int id : removed) {
                        MediaCardPanel panel = renderedCards.remove(id);
                        if (panel != null) {
                            hoveredCardPanel.compareAndSet(panel, null);
                            mediaQueuePane.remove(panel);
                        }
                    }
                }

                List<Integer> appended = new ArrayList<>(added.size());
                if (!added.isEmpty()) {
                    for (int id : added) {
                        if (orderedIdSet.add(id)) {
                            orderedIds.add(id);
                            appended.add(id);
                        }
                    }
                }

                if (needsFullRecompute) {
                    recomputeFilteredIds();
                } else if (!appended.isEmpty()) {
                    appendFilteredIds(appended);
                }

                updateVisibleWindow(true);
            } finally {
                lastMediaCardQueueUpdate.set(System.currentTimeMillis());
                currentlyUpdatingMediaCards.set(false);

                mediaQueuePane.revalidate();
                mediaQueuePane.validate();
                queueScrollPane.validate();
                mediaQueuePane.repaint();

                manager.updateContentPane();

                // TODO: setting for this. if the window is hidden it should remain hidden
                //if (!manager.getAppWindow().isVisible()) {
                //    manager.getAppWindow().setVisible(true);
                //}
                if (main.getConfig().isAutoScrollToBottom() && scrollToBottom
                    && !restoringQueue.get()
                    && main.getDownloadManager().isAutoScrollApplicable()) {
                    scrollPaneToBottom(queueScrollPane);
                }
            }
        });
    }

    public void updateVisibleCards() {
        runOnEDT(() -> {
            if (mediaQueuePane == null) {
                return;
            }

            int windowWidth = manager.getAppWindow().getWidth();
            int screenWidth = manager.getAppWindow().getGraphicsConfiguration().getBounds().width;

            if (Math.abs(windowWidth - lastProcessedWindowWidth) <= 5) {
                return;
            }
            lastProcessedWindowWidth = windowWidth;

            invalidateRowHeightCache();

            for (Component component : mediaQueuePane.getComponents()) {
                if (component instanceof MediaCardPanel panel) {
                    MediaCard card = panel.getMediaCard();
                    if (card != null) {
                        card.adjustScale(windowWidth, screenWidth);

                        CustomMediaCardUI ui = card.getUi();
                        if (ui != null) {
                            ui.getCard().revalidate();
                            ui.getCard().repaint();
                        }
                    }
                }
            }

            updateVisibleWindow(true);

            mediaQueuePane.revalidate();
            mediaQueuePane.repaint();
        });
    }

    private void appendFilteredIds(List<Integer> appended) {
        String query = currentSearchQuery.get();
        QueueFilterEnum statusFilter = currentStatusFilter.get();

        boolean unfiltered = query.isEmpty() && statusFilter == QueueFilterEnum.ALL;

        for (int id : appended) {
            MediaCard card = mediaCards.get(id);
            if (card == null) {
                continue;
            }

            if (unfiltered
                || ((query.isEmpty() || matchesSearch(card, query)) && statusFilter.matches(card.getCategory()))) {
                filteredIds.add(id);
            }
        }

        Consumer<Integer> listener = matchCountListener.get();
        if (listener != null) {
            listener.accept(filteredIds.size());
        }
    }

    private void recomputeFilteredIds() {
        String query = currentSearchQuery.get();
        QueueFilterEnum statusFilter = currentStatusFilter.get();

        List<Integer> next = new ArrayList<>(orderedIds.size());

        for (int id : orderedIds) {
            MediaCard card = mediaCards.get(id);
            if (card == null) {
                continue;
            }

            if ((query.isEmpty() || matchesSearch(card, query)) && statusFilter.matches(card.getCategory())) {
                next.add(id);
            }
        }

        filteredIds = next;

        Consumer<Integer> listener = matchCountListener.get();
        if (listener != null) {
            listener.accept(next.size());
        }
    }

    private int getRowHeight() {
        if (cachedRowHeight > 0) {
            return cachedRowHeight;
        }

        if (measurementUi == null) {
            measurementUi = new CustomMediaCardUI(manager, manager.getAppWindow(),
                () -> {
                    // no-op
                },
                () -> {
                    // no-op
                },
                () -> {
                    // no-op
                },
                () -> {
                    // no-op
                }
            );
        }

        int windowWidth = manager.getAppWindow().getWidth();
        int screenWidth = manager.getAppWindow().getGraphicsConfiguration().getBounds().width;
        measurementUi.updateScale(MediaCard.computeScale(windowWidth, screenWidth));

        cachedRowHeight = Math.max(1, measurementUi.getCard().getPreferredSize().height);

        return cachedRowHeight;
    }

    private void invalidateRowHeightCache() {
        cachedRowHeight = -1;
    }

    private void updateVisibleWindow(boolean force) {
        if (mediaQueuePane == null || queueScrollPane == null) {
            return;
        }

        int viewportWidth = queueScrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            return;
        }

        int total = filteredIds.size();

        if (total == 0) {
            if (!force && renderedCards.isEmpty() && lastFirstIndex == -1) {
                return;
            }

            clearRenderedCards();

            lastFirstIndex = -1;
            lastLastIndex = -1;
            lastLayoutColumns = -1;
            lastLayoutRowHeight = -1;
            lastLayoutViewportHeight = -1;
            visibleWindowRendered = true;

            mediaQueuePane.setSize(viewportWidth, 0);
            mediaQueuePane.revalidate();
            mediaQueuePane.repaint();
            return;
        }

        int rowHeight = Math.max(1, getRowHeight());
        int columns = mediaCardGridLayout.determineColumns(viewportWidth, total);
        int totalRows = (total + columns - 1) / columns;

        Integer anchoredScrollY = computeAnchoredScrollY(columns, rowHeight, totalRows, total);

        Rectangle viewRect = queueScrollPane.getViewport().getViewRect();
        int effectiveViewY = anchoredScrollY != null ? anchoredScrollY : viewRect.y;

        int firstRow = Math.max(0, (effectiveViewY - RENDER_BUFFER_PX) / rowHeight);
        int lastRow = Math.min(totalRows - 1, (effectiveViewY + viewRect.height + RENDER_BUFFER_PX) / rowHeight);
        firstRow = Math.min(firstRow, lastRow);

        int firstIndex = firstRow * columns;
        int lastIndex = Math.min(total - 1, (lastRow + 1) * columns - 1);

        if (!force && anchoredScrollY == null && firstIndex == lastFirstIndex && lastIndex == lastLastIndex) {
            return;
        }

        lastFirstIndex = firstIndex;
        lastLastIndex = lastIndex;

        Set<Integer> nextVisible = new HashSet<>();

        for (int i = firstIndex; i <= lastIndex; i++) {
            int id = filteredIds.get(i);
            nextVisible.add(id);

            MediaCardPanel panel = renderedCards.get(id);
            if (panel == null) {
                MediaCard card = mediaCards.get(id);
                if (card == null) {
                    continue;
                }

                panel = materializeCard(card);
                renderedCards.put(id, panel);
            }

            panel.putClientProperty(MediaCardGridLayout.VIRTUAL_INDEX_PROPERTY, i);
        }

        Iterator<Map.Entry<Integer, MediaCardPanel>> it = renderedCards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, MediaCardPanel> e = it.next();

            if (!nextVisible.contains(e.getKey())) {
                dematerialize(e.getKey(), e.getValue());
                it.remove();
            }
        }

        visibleWindowRendered = true;

        mediaQueuePane.setSize(viewportWidth, totalRows * rowHeight);

        mediaQueuePane.revalidate();
        queueScrollPane.validate();

        if (anchoredScrollY != null) {
            int targetScrollY = anchoredScrollY;

            adjustingScroll = true;
            try {
                queueScrollPane.getViewport().setViewPosition(new Point(viewRect.x, targetScrollY));
            } finally {
                adjustingScroll = false;
            }
        }

        mediaQueuePane.repaint();

        lastLayoutColumns = columns;
        lastLayoutRowHeight = rowHeight;
        lastLayoutViewportHeight = viewRect.height;
    }

    @Nullable
    private Integer computeAnchoredScrollY(int newColumns, int newRowHeight, int newTotalRows, int itemCount) {
        if (lastLayoutColumns <= 0 || lastLayoutRowHeight <= 0
            || (newColumns == lastLayoutColumns && newRowHeight == lastLayoutRowHeight)) {
            return null;
        }

        JViewport viewport = queueScrollPane.getViewport();
        Rectangle viewRect = viewport.getViewRect();

        if (viewRect.height <= 0) {
            return null;
        }

        int oldColumns = lastLayoutColumns;
        int oldRowHeight = lastLayoutRowHeight;
        int oldViewportHeight = lastLayoutViewportHeight > 0
            ? lastLayoutViewportHeight : viewRect.height;
        int oldTotalRows = Math.max(1, (itemCount + oldColumns - 1) / oldColumns);
        int oldContentHeight = oldTotalRows * oldRowHeight;
        int oldMaxScrollY = Math.max(0, oldContentHeight - oldViewportHeight);

        int newContentHeight = newTotalRows * newRowHeight;
        int newMaxScrollY = Math.max(0, newContentHeight - viewRect.height);

        if (oldMaxScrollY > 0 && viewRect.y >= oldMaxScrollY - COLUMN_CHANGE_BOTTOM_EPSILON_PX) {
            return newMaxScrollY;
        }

        int anchorRow = Math.max(0, viewRect.y / oldRowHeight);
        anchorRow = Math.min(anchorRow, oldTotalRows - 1);

        double fraction = (viewRect.y - (double)anchorRow * oldRowHeight) / oldRowHeight;
        fraction = Math.max(0.0, Math.min(1.0, fraction));

        long anchorItemIndex = Math.min((long)anchorRow * oldColumns, Math.max(0, itemCount - 1));

        int newRow = (int)(anchorItemIndex / newColumns);

        int scrollY = (int)Math.round((newRow + fraction) * newRowHeight);

        return Math.max(0, Math.min(scrollY, newMaxScrollY));
    }

    private void clearRenderedCards() {
        if (renderedCards.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, MediaCardPanel> e : renderedCards.entrySet()) {
            dematerialize(e.getKey(), e.getValue());
        }

        renderedCards.clear();
    }

    private void dematerialize(int id, MediaCardPanel panel) {
        hoveredCardPanel.compareAndSet(panel, null);
        mediaQueuePane.remove(panel);

        MediaCard card = mediaCards.get(id);
        if (card != null) {
            card.clearUi();
        }
    }

    private MediaCardPanel materializeCard(MediaCard mediaCard) {
        int windowWidth = manager.getAppWindow().getWidth();
        int screenWidth = manager.getAppWindow().getGraphicsConfiguration().getBounds().width;
        mediaCard.adjustScale(windowWidth, screenWidth);

        CustomMediaCardUI ui = new CustomMediaCardUI(manager, manager.getAppWindow(), () -> {
            if (isMediaCardSelected(mediaCard.getId())) {
                deleteSelectedMediaCards();
            }

            removeMediaCard(mediaCard.getId(), CloseReasonEnum.MANUAL);
        },
            () -> Optional.ofNullable(mediaCard.getOnInfoClick())
                .ifPresent(runnable -> runnable.run()),
            () -> Optional.ofNullable(mediaCard.getOnStartClick())
                .ifPresent(runnable -> runnable.run()),
            () -> Optional.ofNullable(mediaCard.getOnFormatsClick())
                .ifPresent(runnable -> runnable.run())
        );

        MediaCardPanel card = ui.getCard();
        card.setTransferHandler(new WindowTransferHandler(manager));
        card.setMediaCard(mediaCard);

        mediaCard.setUi(ui);

        MouseAdapter listener = new MediaCardMouseAdapter(mediaCard);
        card.addMouseListener(listener);
        ui.getDragLabel().addMouseListener(listener);
        ui.getMediaNameLabel().addMouseListener(listener);

        ui.getInfoButton().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (mediaCard.getOnInfoHover() != null) {
                    mediaCard.getOnInfoHover().accept(true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (mediaCard.getOnInfoHover() != null) {
                    mediaCard.getOnInfoHover().accept(false);
                }
            }
        });

        card.setBackground(isMediaCardSelected(mediaCard)
            ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));

        mediaQueuePane.add(card);

        ui.getMediaNameLabel().updateTruncatedText();

        Runnable becomeVisible = mediaCard.getOnBecomeVisible();
        if (becomeVisible != null) {
            spawn(becomeVisible);
        }

        return card;
    }

    public boolean handleMediaCardDnD(MediaCard mediaCard, Component dropTarget) {
        Rectangle windowBounds = manager.getAppWindow().getBounds();
        Point dropLocation = dropTarget.getLocationOnScreen();

        if (windowBounds.contains(dropLocation)
            && dropTarget instanceof MediaCardPanel targetPanel) {

            MediaCard targetCard = targetPanel.getMediaCard();

            if (targetCard != null && targetCard.getDropTargetValidator().get()) {
                if (mediaCard.getOnSwap() != null) {
                    mediaCard.getOnSwap().accept(targetCard);
                }

                runOnEDT(() -> {
                    int targetIndex = orderedIds.indexOf(targetCard.getId());
                    if (targetIndex < 0) {
                        return;
                    }

                    orderedIds.remove(Integer.valueOf(mediaCard.getId()));
                    orderedIds.add(targetIndex, mediaCard.getId());

                    lastManualReorderTime.set(System.currentTimeMillis());
                    liveSortPending.set(true);

                    recomputeFilteredIds();
                    updateVisibleWindow(true);

                    mediaQueuePane.revalidate();
                    mediaQueuePane.repaint();
                });
            }

            return true;
        }

        return false;
    }

    public void reorderMediaCards(@NonNull List<Integer> newOrderIds) {
        if (newOrderIds.isEmpty() || queueScrollPane == null) {
            return;
        }

        runOnEDT(() -> {
            Set<Integer> seen = new HashSet<>();
            List<Integer> resolved = new ArrayList<>(newOrderIds.size());

            for (Integer cardId : newOrderIds) {
                if (!mediaCards.containsKey(cardId)) {
                    log.warn("Media card with ID {} not found for reordering, skipping", cardId);
                    continue;
                }

                if (seen.add(cardId)) {
                    resolved.add(cardId);
                }
            }

            for (Integer existingId : orderedIds) {
                if (seen.add(existingId)) {
                    resolved.add(existingId);
                }
            }

            if (resolved.isEmpty() || resolved.equals(orderedIds)) {
                return;
            }

            orderedIds.clear();
            orderedIds.addAll(resolved);

            recomputeFilteredIds();
            updateVisibleWindow(true);

            mediaQueuePane.revalidate();
            mediaQueuePane.repaint();

            if (!selectedMediaCards.isEmpty()) {
                updateMediaCardSelectionState();
            }
        });
    }

    public void requestLiveSort() {
        liveSortPending.set(true);
    }

    public void setRestoringQueue(boolean restoring) {
        restoringQueue.set(restoring);
    }

    public void runWhenSettled(@NonNull Runnable action) {
        settledActions.add(action);
    }

    private void runSettledActions() {
        if (settledActions.isEmpty()
            || mediaQueuePane == null || queueScrollPane == null
            || !mediaCardUIUpdateQueue.isEmpty()
            || currentlyUpdatingMediaCards.get()) {
            return;
        }

        Runnable action;
        while ((action = settledActions.poll()) != null) {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Failed to run post-load action", e);
            }
        }
    }

    private void maybeApplyLiveSort() {
        if (!liveSortPending.get()) {
            return;
        }

        if (!main.getDownloadManager().isLiveSortEnabled()) {
            liveSortPending.set(false);
            return;
        }

        if (mediaQueuePane == null || queueScrollPane == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastManualReorderTime.get() < LIVE_SORT_MANUAL_DEBOUNCE_MS) {
            return;
        }

        if (now - lastLiveSortRefresh.get() < LIVE_SORT_REFRESH_INTERVAL_MS) {
            return;
        }

        liveSortPending.set(false);
        lastLiveSortRefresh.set(now);

        main.getDownloadManager().syncSequencerOrder();

        List<Integer> sortedIds = main.getDownloadManager().getSortedMediaCardIds();
        if (!sortedIds.isEmpty() && !sortedIds.equals(orderedIds)) {
            reorderMediaCards(sortedIds);
        }
    }

    public int getMediaCardCount() {
        return mediaCards.size();
    }

    public int getVisibleMediaCardCount() {
        return filteredIds.size();
    }

    public boolean hasActiveSearchQuery() {
        return !currentSearchQuery.get().isEmpty();
    }

    public void filterMediaCards(@NonNull String query, @Nullable Consumer<Integer> onCountUpdate) {
        currentSearchQuery.set(query.trim().toLowerCase());
        matchCountListener.set(onCountUpdate);

        applyCardFilters();
    }

    public void setStatusFilter(@NonNull QueueFilterEnum filter) {
        if (currentStatusFilter.getAndSet(filter) != filter) {
            applyCardFilters();

            EventDispatcher.dispatch(QueueFilterChangedEvent.builder()
                .filter(filter)
                .build());
        }

        if (main.getConfig().isRememberQueueSortAndFilter()
            && main.getConfig().getQueueStatusFilter() != filter) {
            main.getConfig().setQueueStatusFilter(filter);
            main.updateConfig();
        }
    }

    public QueueFilterEnum getStatusFilter() {
        return currentStatusFilter.get();
    }

    public void onMediaCardCategoryChanged() {
        if (currentStatusFilter.get() == QueueFilterEnum.ALL
            && currentSearchQuery.get().isEmpty()) {
            return;
        }

        filtersDirty.set(true);
    }

    private void applyDirtyFilters() {
        if (!filtersDirty.get()
            || restoringQueue.get()
            || currentlyUpdatingMediaCards.get()
            || !mediaCardUIUpdateQueue.isEmpty()) {
            return;
        }

        if (filtersDirty.getAndSet(false)) {
            applyCardFilters();
        }
    }

    private void applyCardFilters() {
        if (!filterPending.compareAndSet(false, true)) {
            return;
        }

        runOnEDT(() -> {
            filterPending.set(false);

            if (ShutdownRegistry.isClosed()) {
                return;
            }

            if (mediaQueuePane == null) {
                return;
            }

            recomputeFilteredIds();
            updateVisibleWindow(true);

            mediaQueuePane.revalidate();
            mediaQueuePane.repaint();

            manager.updateContentPane();
        });
    }

    private boolean matchesSearch(MediaCard card, String query) {
        String[] labels = card.getLabelText();
        if (labels != null) {
            for (String label : labels) {
                if (containsIgnoreCase(label, query)) {
                    return true;
                }
            }
        }

        if (containsIgnoreCase(card.getUrlHint(), query)) {
            return true;
        }

        return containsIgnoreCase(card.getTooltipText(), query);
    }

    private class MediaCardMouseAdapter extends MouseAdapter {

        private final MediaCard mediaCard;
        private final CustomMediaCardUI ui;
        private final MediaCardPanel card;

        private long lastClick = System.currentTimeMillis();

        public MediaCardMouseAdapter(MediaCard mediaCardIn) {
            mediaCard = mediaCardIn;

            ui = mediaCardIn.getUi();
            card = ui.getCard();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (isMultiSelectMode.get() && selectedMediaCards.size() > 1) {
                return;
            }

            Component component = e.getComponent();
            if (component.equals(ui.getDragLabel())) {
                TransferHandler handler = card.getTransferHandler();

                if (handler != null) {// peace of mind
                    handler.exportAsDrag(card, e, TransferHandler.MOVE);
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                MediaCard lastCard = lastSelectedMediaCard.get();

                int cardId = mediaCard.getId();

                if (e.isControlDown()) {
                    isMultiSelectMode.set(true);

                    if (selectedMediaCards.contains(cardId)) {
                        selectedMediaCards.remove(cardId);
                    } else {
                        selectedMediaCards.add(cardId);
                    }

                    updateMediaCardSelectionState();
                } else if (e.isShiftDown() && lastCard != null) {
                    isMultiSelectMode.set(true);

                    selectMediaCardRange(lastCard, mediaCard);
                } else {
                    if (e.getClickCount() == 2) {
                        if (mediaCard.getOnLeftClick() != null && (System.currentTimeMillis() - lastClick) > 50) {
                            mediaCard.getOnLeftClick().run();

                            lastClick = System.currentTimeMillis();
                        }
                    }

                    selectedMediaCards.replaceAll(Collections.singletonList(cardId));
                    lastSelectedMediaCard.set(mediaCard);

                    updateMediaCardSelectionState();
                }
            } else if (SwingUtilities.isRightMouseButton(e)) {
                List<RightClickMenuEntries> dependents = new ArrayList<>();

                if (isMediaCardSelected(mediaCard)) {
                    for (int cardId : selectedMediaCards) {
                        MediaCard selected = mediaCards.get(cardId);
                        if (selected == null) {
                            log.error("Cannot find media card, id {}", cardId);
                            continue;
                        }

                        if (selected == mediaCard) {
                            continue;
                        }

                        dependents.add(RightClickMenuEntries.fromMap(selected.getOnRightClick().get()));
                    }
                }

                manager.showRightClickMenu(card,
                    RightClickMenuEntries.fromMap(mediaCard.getOnRightClick().get()),
                    dependents, e.getX(), e.getY());
            }
        }
    }

    // Inner classes and constants
    private static final byte CARD_REMOVE = 0x00;
    private static final byte CARD_ADD = 0x01;

    @Data
    private static class MediaCardUIUpdateEntry {

        private final byte updateType;
        private final MediaCard mediaCard;
    }
}
