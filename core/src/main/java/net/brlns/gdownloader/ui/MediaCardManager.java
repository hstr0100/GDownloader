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

import jakarta.annotation.Nullable;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.CloseReasonEnum;
import net.brlns.gdownloader.ui.custom.CustomMediaCardUI;
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

    private JPanel mediaQueuePane;

    private final AtomicBoolean currentlyUpdatingMediaCards = new AtomicBoolean();
    private final AtomicLong lastMediaCardQueueUpdate = new AtomicLong();
    private final Queue<MediaCardUIUpdateEntry> mediaCardUIUpdateQueue = new ConcurrentLinkedQueue<>();
    private final Map<Integer, MediaCard> mediaCards = new ConcurrentHashMap<>();

    private final ConcurrentLinkedHashSet<Integer> selectedMediaCards = new ConcurrentLinkedHashSet<>();
    private final AtomicReference<MediaCard> lastSelectedMediaCard = new AtomicReference<>(null);
    private final AtomicBoolean isMultiSelectMode = new AtomicBoolean();

    private JScrollPane queueScrollPane;

    public MediaCardManager(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        Timer mediaCardQueueTimer = new Timer(50, e -> processMediaCardQueue());
        mediaCardQueueTimer.start();
    }

    public void initializeQueueScrollPane(JScrollPane scrollPane) {
        queueScrollPane = scrollPane;
    }

    public JPanel getOrCreateMediaQueuePanel() {
        if (mediaQueuePane != null) {
            return mediaQueuePane;
        }

        mediaQueuePane = new JPanel();
        mediaQueuePane.setLayout(new BoxLayout(mediaQueuePane, BoxLayout.Y_AXIS));
        mediaQueuePane.setBackground(color(BACKGROUND));
        mediaQueuePane.setOpaque(false);
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

    public boolean isMediaCardSelected(MediaCard card) {
        return isMediaCardSelected(card.getId());
    }

    public boolean isMediaCardSelected(int cardId) {
        return selectedMediaCards.contains(cardId);
    }

    public void selectAllMediaCards() {
        selectedMediaCards.replaceAll(mediaCards.keySet());

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
                JPanel card = ui.getCard();
                card.setBackground(isSelected ? color(MEDIA_CARD_SELECTED) : color(MEDIA_CARD));
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
            mediaQueuePane.setIgnoreRepaint(true);

            boolean scrollToBottom = false;

            try {
                int count = 0;
                while (!mediaCardUIUpdateQueue.isEmpty()) {
                    if (++count == 256) {// Process in batches of 255 items every 100ms
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
                        });

                        mediaCard.setUi(ui);

                        JPanel card = ui.getCard();
                        card.setTransferHandler(new WindowTransferHandler(manager));

                        MouseAdapter listener = new MediaCardMouseAdapter(mediaCard);
                        card.addMouseListener(listener);
                        ui.getDragLabel().addMouseListener(listener);
                        ui.getMediaNameLabel().addMouseListener(listener);

                        card.putClientProperty("MEDIA_CARD", mediaCard);

                        mediaQueuePane.add(card);

                        manager.updateContentPane();

                        scrollToBottom = true;
                    } else if (entry.getUpdateType() == CARD_REMOVE) {
                        CustomMediaCardUI ui = mediaCard.getUi();
                        if (ui != null) {
                            try {
                                if (mediaCards.isEmpty()) {
                                    mediaQueuePane.removeAll();

                                    manager.updateContentPane();
                                } else {
                                    mediaQueuePane.remove(ui.getCard());
                                }

                                ui.removeListeners();
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

                if (mediaCardUIUpdateQueue.isEmpty()) {
                    mediaQueuePane.setIgnoreRepaint(false);

                    mediaQueuePane.revalidate();
                    mediaQueuePane.repaint();

                    // TODO: setting for this. if the window is hidden it should remain hidden
                    //if (!manager.getAppWindow().isVisible()) {
                    //    manager.getAppWindow().setVisible(true);
                    //}
                }

                if (main.getConfig().isAutoScrollToBottom() && scrollToBottom) {
                    scrollPaneToBottom(queueScrollPane);
                }

                int currentMode = queueScrollPane.getViewport().getScrollMode();
                if (mediaQueuePane.getComponentCount() > 100 && currentMode != JViewport.BACKINGSTORE_SCROLL_MODE) {
                    queueScrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
                } else if (currentMode != JViewport.SIMPLE_SCROLL_MODE) {
                    queueScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
                }
            }
        });
    }

    public boolean handleMediaCardDnD(MediaCard mediaCard, Component dropTarget) {
        CustomMediaCardUI ui = mediaCard.getUi();

        if (ui != null) {
            JPanel sourcePanel = ui.getCard();
            Rectangle windowBounds = manager.getAppWindow().getBounds();
            Point dropLocation = dropTarget.getLocationOnScreen();

            if (windowBounds.contains(dropLocation) && dropTarget instanceof JPanel jPanel) {
                MediaCard targetCard = (MediaCard)jPanel.getClientProperty("MEDIA_CARD");
                if (targetCard != null && targetCard.getValidateDropTarget().get()) {
                    if (mediaCard.getOnSwap() != null) {
                        mediaCard.getOnSwap().accept(targetCard);
                    }

                    runOnEDT(() -> {
                        int targetIndex = getComponentIndex(jPanel);

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

    private int getComponentIndex(JPanel component) {
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
        if (index < 0 || index > mediaQueuePane.getComponents().length) {
            log.error("Index {} is out of bounds", index);
            return null;
        }

        Component component = mediaQueuePane.getComponents()[index];

        return (MediaCard)((JPanel)component).getClientProperty("MEDIA_CARD");
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
                    if (component instanceof JPanel panel) {
                        MediaCard card = (MediaCard)panel.getClientProperty("MEDIA_CARD");
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

    private class MediaCardMouseAdapter extends MouseAdapter {

        private final MediaCard mediaCard;
        private final CustomMediaCardUI ui;
        private final JPanel card;

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

                        dependents.add(RightClickMenuEntries.fromMap(selected.getRightClickMenu()));
                    }
                }

                manager.showRightClickMenu(card, RightClickMenuEntries.fromMap(mediaCard.getRightClickMenu()),
                    dependents, e.getX(), e.getY());
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (!isMediaCardSelected(mediaCard) && !isMultiSelectMode.get()) {
                card.setBackground(color(MEDIA_CARD_HOVER));
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!isMediaCardSelected(mediaCard) && !isMultiSelectMode.get()) {
                card.setBackground(color(MEDIA_CARD));
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
