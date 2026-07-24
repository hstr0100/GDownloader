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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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

import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;
import static net.brlns.gdownloader.ui.UIUtils.scrollPaneToBottom;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class MediaCardManager {

    private final GDownloader main;
    private final GUIManager manager;

    private final AtomicInteger mediaCardId = new AtomicInteger();

    private ScrollableQueuePanel mediaQueuePane;

    private final AtomicBoolean currentlyUpdatingMediaCards = new AtomicBoolean();
    private final AtomicLong lastMediaCardQueueUpdate = new AtomicLong();
    private final Queue<MediaCardUIUpdateEntry> mediaCardUIUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Map<Integer, MediaCard> mediaCards = new ConcurrentHashMap<>();

    private final AtomicReference<MediaCardPanel> hoveredCardPanel = new AtomicReference<>();
    private final AtomicReference<Point> lastMouseScreenPoint = new AtomicReference<>();
    private AWTEventListener globalMouseListener;

    private final ConcurrentLinkedHashSet<Integer> selectedMediaCards = new ConcurrentLinkedHashSet<>();
    private final AtomicReference<MediaCard> lastSelectedMediaCard = new AtomicReference<>(null);
    private final AtomicBoolean isMultiSelectMode = new AtomicBoolean();

    private final MediaCardGridLayout mediaCardGridLayout = new MediaCardGridLayout();

    private final AtomicReference<String> currentSearchQuery = new AtomicReference<>("");
    private final AtomicReference<QueueFilterEnum> currentStatusFilter = new AtomicReference<>(QueueFilterEnum.ALL);
    private volatile Consumer<Integer> matchCountListener;

    private JScrollPane queueScrollPane;

    private final Timer mediaCardQueueTimer;
    private final Timer visibilityCheckTimer;

    public MediaCardManager(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        mediaCardQueueTimer = new Timer(50, e -> processMediaCardQueue());
        mediaCardQueueTimer.start();

        visibilityCheckTimer = new Timer(150, e -> checkViewportVisibility());
        visibilityCheckTimer.start();

        EventDispatcher.registerEDT(SettingsChangeEvent.class, (event) -> {
            int newPreference = event.getSettings().getMaxDownloadQueueColumns();
            if (newPreference != mediaCardGridLayout.getColumnPreference()) {
                setColumnLayoutPreference(newPreference);
            }
        });
    }

    @PreDestroy
    public void close() {
        if (mediaCardQueueTimer != null) {
            mediaCardQueueTimer.stop();
        }

        if (visibilityCheckTimer != null) {
            visibilityCheckTimer.stop();
        }

        synchronized (mediaCardUIUpdateQueue) {
            mediaCardUIUpdateQueue.clear();
        }
    }

    public int getRenderedCardCount() {
        return mediaQueuePane != null ? mediaQueuePane.getComponentCount() : 0;
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
        mediaCard.adjustScale(manager.getAppWindow().getWidth());
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
        return !mediaCardUIUpdateQueue.isEmpty() || currentlyUpdatingMediaCards.get();
    }

    public boolean isMediaCardSelected(MediaCard card) {
        return isMediaCardSelected(card.getId());
    }

    public boolean isMediaCardSelected(int cardId) {
        return selectedMediaCards.contains(cardId);
    }

    public void selectAllMediaCards() {
        List<Integer> visibleCardIds = new ArrayList<>();
        for (MediaCard mediaCard : mediaCards.values()) {
            CustomMediaCardUI ui = mediaCard.getUi();

            if (ui != null && ui.getCard().isVisible()) {
                visibleCardIds.add(mediaCard.getId());
            }
        }

        selectedMediaCards.replaceAll(visibleCardIds);

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
        for (MediaCard mediaCard : mediaCards.values()) {
            boolean isSelected = isMediaCardSelected(mediaCard);

            CustomMediaCardUI ui = mediaCard.getUi();
            if (ui != null) {
                ui.getCard().setBackground(isSelected
                    ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
            }
        }
    }

    private void selectMediaCardRange(MediaCard start, MediaCard end) {
        if (start.getUi() == null || end.getUi() == null) {
            throw new IllegalStateException("Expected MediaCard UI to be initialized");
        }

        int startIndex = getComponentIndex(start.getUi().getCard());
        int endIndex = getComponentIndex(end.getUi().getCard());

        if (startIndex == -1 || endIndex == -1) {
            return;
        }

        int minIndex = Math.min(startIndex, endIndex);
        int maxIndex = Math.max(startIndex, endIndex);

        List<Integer> cardsToAdd = new ArrayList<>();
        for (int i = minIndex; i <= maxIndex; i++) {
            MediaCard card = getMediaCardAt(i);

            if (card == null) {
                log.error("Cannot find card for index {}", i);
                continue;
            }

            CustomMediaCardUI cardUi = card.getUi();
            if (cardUi == null || !cardUi.getCard().isVisible()) {
                continue;
            }

            cardsToAdd.add(card.getId());
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
                int count = 0;
                while (!mediaCardUIUpdateQueue.isEmpty()) {
                    if (++count == 500) {// Process in batches of 500 items every 100ms
                        break;
                    }

                    MediaCardUIUpdateEntry entry = mediaCardUIUpdateQueue.poll();
                    if (entry == null) {
                        continue;
                    }

                    MediaCard mediaCard = entry.getMediaCard();

                    if (entry.getUpdateType() == CARD_ADD) {
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

                        mediaCard.setUi(ui);

                        MediaCardPanel card = ui.getCard();
                        card.setTransferHandler(new WindowTransferHandler(manager));

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

                        card.setMediaCard(mediaCard);

                        String currentQuery = currentSearchQuery.get();

                        QueueFilterEnum statusFilter = currentStatusFilter.get();
                        if (!currentQuery.isEmpty() || statusFilter != QueueFilterEnum.ALL) {
                            boolean visible = (currentQuery.isEmpty() || matchesSearch(mediaCard, currentQuery))
                                && statusFilter.matches(mediaCard.getCategory());

                            card.setVisible(visible);
                        }

                        mediaQueuePane.add(card);

                        card.revalidate();
                        ui.getMediaNameLabel().updateTruncatedText();

                        scrollToBottom = true;
                    } else if (entry.getUpdateType() == CARD_REMOVE) {
                        CustomMediaCardUI ui = mediaCard.getUi();
                        if (ui != null) {
                            hoveredCardPanel.compareAndSet(ui.getCard(), null);

                            try {
                                if (mediaCards.isEmpty()) {
                                    mediaQueuePane.removeAll();
                                } else {
                                    mediaQueuePane.remove(ui.getCard());
                                }
                            } catch (StackOverflowError e) {
                                // Decades-old AWT issue. We should not have to raise the stack limit for this.
                                // AWTEventMulticaster.remove(AWTEventMulticaster.java:153)
                                // AWTEventMulticaster.removeInternal(AWTEventMulticaster.java:983)
                                // Rinse and repeat ∞
                                GDownloader.handleException(e, "StackOverflowError when calling remove() or removeComponentListener().");
                            }
                        }
                    }
                }
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
                if (main.getConfig().isAutoScrollToBottom() && scrollToBottom) {
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

            for (Component component : mediaQueuePane.getComponents()) {
                if (component.isVisible() && component instanceof MediaCardPanel panel) {
                    MediaCard card = panel.getMediaCard();
                    if (card != null) {
                        card.adjustScale(windowWidth);

                        CustomMediaCardUI ui = card.getUi();
                        if (ui != null) {
                            ui.getCard().revalidate();
                            ui.getCard().repaint();
                        }
                    }
                }
            }
        });
    }

    private void checkViewportVisibility() {
        if (queueScrollPane == null || mediaQueuePane == null) {
            return;
        }

        runOnEDT(() -> {
            Rectangle viewRect = queueScrollPane.getViewport().getViewRect();
            int buffer = (int)(viewRect.height * 2.5);

            int exX = viewRect.x;
            int exY = viewRect.y - buffer;
            int exMaxX = exX + viewRect.width;
            int exMaxY = exY + viewRect.height + buffer * 2;

            List<Runnable> toRefresh = null;
            int componentCount = mediaQueuePane.getComponentCount();

            for (int i = 0; i < componentCount; i++) {
                Component component = mediaQueuePane.getComponent(i);

                if (!(component instanceof MediaCardPanel panel) || !component.isVisible()) {
                    continue;
                }

                MediaCard card = panel.getMediaCard();
                if (card == null || card.getOnBecomeVisible() == null) {
                    continue;
                }

                int cX = component.getX();
                int cY = component.getY();
                int cMaxX = cX + component.getWidth();
                int cMaxY = cY + component.getHeight();

                if (cX < exMaxX && cMaxX > exX && cY < exMaxY && cMaxY > exY) {
                    if (toRefresh == null) {
                        toRefresh = new ArrayList<>();
                    }

                    toRefresh.add(card.getOnBecomeVisible());
                }
            }

            if (toRefresh != null) {
                final List<Runnable> finalToRefresh = toRefresh;
                GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
                    for (Runnable runnable : finalToRefresh) {
                        runnable.run();
                    }
                });
            }
        });
    }

    public boolean handleMediaCardDnD(MediaCard mediaCard, Component dropTarget) {
        CustomMediaCardUI ui = mediaCard.getUi();

        if (ui != null) {
            MediaCardPanel sourcePanel = ui.getCard();
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
                        int targetIndex = getComponentIndex(targetPanel);

                        mediaQueuePane.remove(sourcePanel);
                        mediaQueuePane.add(sourcePanel, targetIndex);
                        mediaQueuePane.revalidate();
                        mediaQueuePane.repaint();
                    });
                }

                return true;
            }
        }

        return false;
    }

    private int getComponentIndex(Component component) {
        Component[] components = mediaQueuePane.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == component) {
                return i;
            }
        }

        return -1;
    }

    @Nullable
    private MediaCard getMediaCardAt(int index) {
        if (index < 0 || index >= mediaQueuePane.getComponents().length) {
            log.error("Index {} is out of bounds", index);
            return null;
        }

        Component component = mediaQueuePane.getComponents()[index];

        if (component instanceof MediaCardPanel panel) {
            return panel.getMediaCard();
        }

        return null;
    }

    public void reorderMediaCards(@NonNull List<Integer> newOrderIds) {
        if (newOrderIds.isEmpty() || queueScrollPane == null) {
            return;
        }

        runOnEDT(() -> {
            try {
                mediaQueuePane.setIgnoreRepaint(true);

                List<Component> components = new ArrayList<>();
                Map<Integer, Component> idToComponentMap = new HashMap<>();
                Map<Integer, Integer> currentOrderMap = new HashMap<>();

                int index = 0;
                for (Component component : mediaQueuePane.getComponents()) {
                    if (component instanceof MediaCardPanel panel) {
                        MediaCard card = panel.getMediaCard();
                        if (card != null) {
                            components.add(component);
                            idToComponentMap.put(card.getId(), component);
                            currentOrderMap.put(card.getId(), index++);
                        } else {
                            log.warn("A panel did not contain a media card reference");
                        }
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug("Found {} existing media card components, size reported by reorderer list: {}",
                        components.size(), newOrderIds.size());
                }

                for (int i = 0; i < newOrderIds.size(); i++) {
                    Integer cardId = newOrderIds.get(i);

                    if (!idToComponentMap.containsKey(cardId)) {
                        // The UI is not kept in perfect sync with the sequencer.
                        log.warn("Media card with ID {} not found for reordering", cardId);
                        return;
                    }
                }

                List<Range> rangesToReorder = findOutOfOrderRanges(newOrderIds, currentOrderMap);
                if (log.isDebugEnabled()) {
                    log.debug("Found {} ranges that need reordering", rangesToReorder.size());
                }

                if (!rangesToReorder.isEmpty()) {
                    for (Range range : rangesToReorder) {
                        for (int i = range.getStart(); i <= range.getEnd(); i++) {
                            Integer cardId = newOrderIds.get(i);
                            Component component = idToComponentMap.get(cardId);
                            if (component != null) {
                                mediaQueuePane.remove(component);
                            } else {
                                log.warn("Component for media card ID {} not found", cardId);
                            }
                        }
                    }

                    int componentIndex = 0;
                    for (int i = 0; i < newOrderIds.size(); i++) {
                        Integer cardId = newOrderIds.get(i);
                        Component component = idToComponentMap.get(cardId);

                        if (isInAnyRange(i, rangesToReorder)) {
                            if (component != null) {
                                mediaQueuePane.add(component, componentIndex);
                            } else {
                                log.warn("Component for media card ID {} not found", cardId);
                            }
                        }

                        componentIndex++;
                    }

                    mediaQueuePane.revalidate();
                    mediaQueuePane.repaint();

                    if (queueScrollPane.getViewport().getScrollMode()
                        == JViewport.BACKINGSTORE_SCROLL_MODE) {
                        queueScrollPane.getViewport().revalidate();
                        queueScrollPane.getViewport().repaint();
                    }

                    if (!selectedMediaCards.isEmpty()) {
                        updateMediaCardSelectionState();
                    }
                }
            } catch (Exception e) {
                GDownloader.handleException(e, "Failed to reorder UI cards", false);
            } finally {
                mediaQueuePane.setIgnoreRepaint(false);
            }
        });
    }

    private List<Range> findOutOfOrderRanges(List<Integer> newOrderIds, Map<Integer, Integer> currentOrderMap) {
        List<Range> ranges = new ArrayList<>();
        int rangeStart = -1;
        boolean inRange = false;

        for (int i = 0; i < newOrderIds.size(); i++) {
            Integer cardId = newOrderIds.get(i);
            Integer currentIndex = currentOrderMap.get(cardId);

            if (currentIndex == null || currentIndex != i) {
                if (!inRange) {
                    rangeStart = i;
                    inRange = true;
                }
            } else if (inRange) {
                ranges.add(new Range(rangeStart, i - 1));
                inRange = false;
            }
        }

        if (inRange) {
            ranges.add(new Range(rangeStart, newOrderIds.size() - 1));
        }

        return ranges;
    }

    private boolean isInAnyRange(int index, List<Range> ranges) {
        return ranges.stream().anyMatch(range -> range.inRange(index));
    }

    public int getMediaCardCount() {
        return mediaCards.size();
    }

    public int getVisibleMediaCardCount() {
        int count = 0;

        for (MediaCard card : mediaCards.values()) {
            CustomMediaCardUI ui = card.getUi();
            if (ui != null && ui.getCard().isVisible()) {
                count++;
            }
        }

        return count;
    }

    public boolean hasActiveSearchQuery() {
        return !currentSearchQuery.get().isEmpty();
    }

    public void filterMediaCards(@NonNull String query, @Nullable Consumer<Integer> onCountUpdate) {
        currentSearchQuery.set(query.trim().toLowerCase());
        matchCountListener = onCountUpdate;

        applyCardFilters();
    }

    public void setStatusFilter(@NonNull QueueFilterEnum filter) {
        if (currentStatusFilter.getAndSet(filter) != filter) {
            applyCardFilters();

            EventDispatcher.dispatch(QueueFilterChangedEvent.builder()
                .filter(filter)
                .build());
        }
    }

    public QueueFilterEnum getStatusFilter() {
        return currentStatusFilter.get();
    }

    public void onMediaCardCategoryChanged() {
        applyCardFilters();
    }

    private void applyCardFilters() {
        runOnEDT(() -> {
            if (ShutdownRegistry.isClosed()) {
                return;
            }

            if (mediaQueuePane == null) {
                return;
            }

            String currentQuery = currentSearchQuery.get();
            QueueFilterEnum statusFilter = currentStatusFilter.get();

            int count = 0;
            for (MediaCard card : mediaCards.values()) {
                CustomMediaCardUI ui = card.getUi();
                if (ui == null) {
                    continue;
                }

                boolean visible = (currentQuery.isEmpty() || matchesSearch(card, currentQuery))
                    && statusFilter.matches(card.getCategory());

                MediaCardPanel cardPanel = ui.getCard();

                if (cardPanel.isVisible() != visible) {
                    cardPanel.setVisible(visible);
                }

                if (visible) {
                    count++;
                }
            }

            mediaQueuePane.revalidate();
            mediaQueuePane.repaint();

            Consumer<Integer> listener = matchCountListener;
            if (listener != null) {
                listener.accept(count);
            }

            manager.updateContentPane();
        });
    }

    private boolean matchesSearch(MediaCard card, String query) {
        String[] labels = card.getLabelText();
        if (labels != null) {
            for (String label : labels) {
                if (label != null && label.toLowerCase().contains(query)) {
                    return true;
                }
            }
        }

        if (card.getUrlHint().contains(query)) {
            return true;
        }

        String tooltip = card.getTooltipText();
        if (tooltip != null && tooltip.toLowerCase().contains(query)) {
            return true;
        }

        return false;
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

    @Data
    private static class Range {

        private final int start;
        private final int end;

        public boolean inRange(int index) {
            return index >= start && index <= end;
        }
    }
}
