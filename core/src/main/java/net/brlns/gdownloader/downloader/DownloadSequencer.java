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
package net.brlns.gdownloader.downloader;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.QueueCategoryEnum;
import net.brlns.gdownloader.downloader.enums.QueueSortOrderEnum;

import static net.brlns.gdownloader.downloader.enums.QueueCategoryEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DownloadSequencer {

    private static final long SEQUENCE_RESET_THRESHOLD = Long.MAX_VALUE - 1_000;

    private final AtomicLong sequenceGenerator = new AtomicLong(Long.MIN_VALUE);

    private final ConcurrentHashMap<Long, QueueEntry> entriesById = new ConcurrentHashMap<>();
    private final ConcurrentSkipListMap<EntryKey, QueueEntry> priorityQueue = new ConcurrentSkipListMap<>();
    private final EnumMap<QueueCategoryEnum, Set<Long>> categorySets = new EnumMap<>(QueueCategoryEnum.class);

    @Getter
    private QueueSortOrderEnum currentSortOrder = QueueSortOrderEnum.SEQUENCE;

    private final ReentrantLock sequencerLock = new ReentrantLock(true);

    public DownloadSequencer() {
        for (QueueCategoryEnum category : QueueCategoryEnum.values()) {
            categorySets.put(category, ConcurrentHashMap.newKeySet());
        }
    }

    public void setSortOrder(QueueSortOrderEnum sortOrder) {
        sequencerLock.lock();
        try {
            currentSortOrder = sortOrder;
            recreateAllKeys();
        } finally {
            sequencerLock.unlock();
        }
    }

    public boolean contains(@Nullable QueueEntry entry) {
        if (entry == null) {
            return false;
        }

        sequencerLock.lock();
        try {
            return entriesById.containsKey(entry.getDownloadId());
        } finally {
            sequencerLock.unlock();
        }
    }

    public QueueEntry addNewEntry(@NonNull QueueEntry entry) {
        sequencerLock.lock();
        try {
            checkSequenceOverflow();

            long downloadId = entry.getDownloadId();

            removeFromAllQueues(downloadId);

            entriesById.put(downloadId, entry);

            Long previousSequence = entry.getCurrentSequence();
            if (previousSequence == null) {
                long sequence = sequenceGenerator.getAndIncrement();
                entry.setCurrentSequence(sequence);
            } else {
                sequenceGenerator.set(Math.max(previousSequence + 1, sequenceGenerator.get()));
            }

            priorityQueue.put(createEntryKey(entry), entry);

            QueueCategoryEnum category = entry.getCurrentQueueCategory();
            if (category == null || category == RUNNING) {
                category = QUEUED;
            }

            entry.setCurrentQueueCategory(category);
            categorySets.get(category).add(downloadId);

            return entry;
        } finally {
            sequencerLock.unlock();
        }
    }

    public boolean removeEntry(QueueEntry entry) {
        if (entry == null) {
            return false;
        }

        sequencerLock.lock();
        try {
            long downloadId = entry.getDownloadId();
            QueueEntry removed = entriesById.remove(downloadId);

            if (removed != null) {
                EntryKey key = createEntryKey(removed);

                QueueEntry removedEntry = priorityQueue.remove(key);
                if (removedEntry == null) {
                    log.error("Key: {} was missing from priorityQueue, cannot remove entry by key match", key);
                    priorityQueue.keySet().removeIf(k -> k.getDownloadId() == key.getDownloadId());
                }

                for (QueueCategoryEnum category : QueueCategoryEnum.values()) {
                    categorySets.get(category).remove(downloadId);
                }

                return true;
            }

            return false;
        } finally {
            sequencerLock.unlock();
        }
    }

    @Nullable
    public QueueEntry getEntry(@NonNull Predicate<QueueEntry> predicate) {
        sequencerLock.lock();
        try {
            return entriesById.values().stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
        } finally {
            sequencerLock.unlock();
        }
    }

    public List<QueueEntry> getSnapshot() {
        sequencerLock.lock();
        try {
            List<QueueEntry> snapshot = new ArrayList<>();
            for (QueueEntry entry : priorityQueue.values()) {
                snapshot.add(entry);
            }

            return snapshot;
        } finally {
            sequencerLock.unlock();
        }
    }

    @Nullable
    public QueueEntry fetchNext() {
        if (categorySets.get(QUEUED).isEmpty()) {
            return null;
        }

        sequencerLock.lock();
        try {
            for (Map.Entry<EntryKey, QueueEntry> entry : priorityQueue.entrySet()) {
                QueueEntry queueEntry = entry.getValue();
                long downloadId = queueEntry.getDownloadId();

                if (queueEntry.getCurrentQueueCategory() == QUEUED
                    && categorySets.get(QUEUED).contains(downloadId)) {

                    updateEntryCategory(queueEntry, RUNNING);

                    return queueEntry;
                }
            }

            return null;
        } finally {
            sequencerLock.unlock();
        }
    }

    public void requeueFailed(@NonNull Consumer<QueueEntry> resetAction) {
        sequencerLock.lock();
        try {
            Set<Long> failedIds = new HashSet<>(categorySets.get(FAILED));

            for (Long id : failedIds) {
                QueueEntry entry = entriesById.get(id);
                if (entry == null) {
                    log.warn("Failed entry not found in entriesById: {}", id);
                    continue;
                }

                categorySets.get(FAILED).remove(id);

                entry.setCurrentQueueCategory(QUEUED);

                resetAction.accept(entry);

                categorySets.get(QUEUED).add(id);

                EntryKey key = createEntryKey(entry);
                priorityQueue.remove(key);
                priorityQueue.put(key, entry);
            }
        } finally {
            sequencerLock.unlock();
        }
    }

    public void removeAll(QueueCategoryEnum category, @NonNull Consumer<QueueEntry> removeAction) {
        sequencerLock.lock();
        try {
            Set<Long> toRemoveIds = new HashSet<>(categorySets.get(category));

            for (Long id : toRemoveIds) {
                QueueEntry entry = entriesById.get(id);
                if (entry == null) {
                    log.warn("Entry for removal was not found in entriesById: {}", id);
                    continue;
                }

                if (removeEntry(entry)) {
                    removeAction.accept(entry);
                } else {
                    log.warn("Failed to remove entry id: {}", id);
                }
            }
        } finally {
            sequencerLock.unlock();
        }
    }

    public boolean reorderEntries(QueueEntry entryToMove, QueueEntry entryTarget) {
        sequencerLock.lock();
        try {
            if (entryToMove == null || entryTarget == null) {
                return false;
            }

            long id1 = entryToMove.getDownloadId();
            long id2 = entryTarget.getDownloadId();

            if (id1 == id2) {
                return true;
            }

            priorityQueue.remove(createEntryKey(entryToMove));

            long seq1 = entryToMove.getCurrentSequence();
            long seq2 = entryTarget.getCurrentSequence();

            Map<Long, QueueEntry> toUpdate = new HashMap<>();

            if (seq1 > seq2) {
                shiftSequences(toUpdate, id1, seq2, seq1, 1);
            } else if (seq1 < seq2) {
                shiftSequences(toUpdate, id1, seq1, seq2, -1);
            }

            entryToMove.setCurrentSequence(seq2);

            for (QueueEntry entry : toUpdate.values()) {
                priorityQueue.put(createEntryKey(entry), entry);
            }

            priorityQueue.put(createEntryKey(entryToMove), entryToMove);

            return true;
        } finally {
            sequencerLock.unlock();
        }
    }

    public boolean changeCategory(QueueEntry entry, QueueCategoryEnum newCategory) {
        sequencerLock.lock();
        try {
            long downloadId = entry.getDownloadId();
            QueueEntry storedEntry = entriesById.get(downloadId);

            if (storedEntry == null) {
                log.warn("Can't change category, entry not in entriesById: {}", downloadId);
                return false;
            }

            QueueCategoryEnum currentCategory = storedEntry.getCurrentQueueCategory();

            if (currentCategory == newCategory) {
                return false;
            }

            return updateEntryCategory(storedEntry, newCategory);
        } finally {
            sequencerLock.unlock();
        }
    }

    public void updatePriority(QueueEntry entry, DownloadPriorityEnum priority) {
        sequencerLock.lock();
        try {
            long downloadId = entry.getDownloadId();
            QueueEntry storedEntry = entriesById.get(downloadId);

            if (storedEntry != null && storedEntry.getDownloadPriority() != priority) {
                priorityQueue.remove(createEntryKey(storedEntry));

                storedEntry.setDownloadPriority(priority);

                priorityQueue.put(createEntryKey(storedEntry), storedEntry);
            } else if (storedEntry == null) {
                log.warn("Couldn't update priority, entry not found in entriesById: {}", downloadId);
            }
        } finally {
            sequencerLock.unlock();
        }
    }

    public int getCount(QueueCategoryEnum category) {
        return categorySets.get(category).size();
    }

    public boolean isEmpty(QueueCategoryEnum category) {
        return categorySets.get(category).isEmpty();
    }

    public boolean isEmpty() {// Locking this is overkill
        return entriesById.isEmpty();
    }

    public List<QueueEntry> getEntries(QueueCategoryEnum category) {
        sequencerLock.lock();
        try {
            List<QueueEntry> result = new ArrayList<>();
            Set<Long> ids = categorySets.get(category);

            for (Long id : ids) {
                QueueEntry entry = entriesById.get(id);
                if (entry != null) {
                    result.add(entry);
                }
            }

            return result;
        } finally {
            sequencerLock.unlock();
        }
    }

    private void removeFromAllQueues(long downloadId) {
        QueueEntry oldEntry = entriesById.get(downloadId);
        if (oldEntry != null) {
            priorityQueue.remove(createEntryKey(oldEntry));

            for (QueueCategoryEnum category : QueueCategoryEnum.values()) {
                categorySets.get(category).remove(downloadId);
            }
        }
    }

    private boolean updateEntryCategory(QueueEntry entry, QueueCategoryEnum newCategory) {
        long downloadId = entry.getDownloadId();
        QueueCategoryEnum currentCategory = entry.getCurrentQueueCategory();

        if (!categorySets.get(currentCategory).remove(downloadId)) {
            log.warn("Entry was not in its category set: {} should be in {}",
                downloadId, currentCategory);

            for (QueueCategoryEnum cat : QueueCategoryEnum.values()) {
                categorySets.get(cat).remove(downloadId);
            }
        }

        entry.setCurrentQueueCategory(newCategory);

        categorySets.get(newCategory).add(downloadId);

        EntryKey key = createEntryKey(entry);
        priorityQueue.remove(key);
        priorityQueue.put(key, entry);

        return true;
    }

    private void shiftSequences(Map<Long, QueueEntry> entries,
        long excludeId, long startSeq, long endSeq, int shift) {
        Set<EntryKey> keysToRemove = new HashSet<>();

        for (Map.Entry<EntryKey, QueueEntry> mapEntry : priorityQueue.entrySet()) {
            QueueEntry qEntry = mapEntry.getValue();
            long itemSeq = qEntry.getCurrentSequence();
            long qId = qEntry.getDownloadId();

            boolean inRange = shift > 0
                ? (itemSeq >= startSeq && itemSeq < endSeq)
                : (itemSeq > startSeq && itemSeq <= endSeq);

            if (qId != excludeId && inRange) {
                entries.put(qId, qEntry);
                keysToRemove.add(mapEntry.getKey());
            }
        }

        for (EntryKey key : keysToRemove) {
            priorityQueue.remove(key);
        }

        for (QueueEntry entry : entries.values()) {
            entry.setCurrentSequence(entry.getCurrentSequence() + shift);
        }
    }

    private void checkSequenceOverflow() {
        if (sequenceGenerator.get() >= SEQUENCE_RESET_THRESHOLD) {
            log.info("Sequence generator approaching a overflow, recomputing sequences");
            recomputeSequences();
        }
    }

    public void recomputeSequences() {
        sequencerLock.lock();
        try {
            List<QueueEntry> sortedEntries = new ArrayList<>(entriesById.values());
            sortedEntries.sort(Comparator.comparing(QueueEntry::getCurrentSequence));

            priorityQueue.clear();

            sequenceGenerator.set(Long.MIN_VALUE);

            for (QueueEntry entry : sortedEntries) {
                long newSequence = sequenceGenerator.getAndIncrement();
                entry.setCurrentSequence(newSequence);
                priorityQueue.put(createEntryKey(entry), entry);
            }

            log.info("Sequence recomputation completed for {} entries", sortedEntries.size());
        } finally {
            sequencerLock.unlock();
        }
    }

    private void recreateAllKeys() {
        sequencerLock.lock();
        try {
            ConcurrentSkipListMap<EntryKey, QueueEntry> newPriorityQueue = new ConcurrentSkipListMap<>();

            for (Map.Entry<EntryKey, QueueEntry> entry : priorityQueue.entrySet()) {
                QueueEntry queueEntry = entry.getValue();
                queueEntry.setTemporarySortOrder(currentSortOrder);
                EntryKey newKey = createEntryKey(queueEntry);
                newPriorityQueue.put(newKey, queueEntry);
            }

            priorityQueue.clear();
            priorityQueue.putAll(newPriorityQueue);
        } finally {
            sequencerLock.unlock();
        }
    }

    private EntryKey createEntryKey(QueueEntry entry) {
        return new EntryKey(
            entry.getDownloadPriority().getWeight(),
            entry.getCurrentSequence(),
            entry.getDownloadId(),
            entry.getTemporarySortOrder(),
            entry
        );
    }

    @Data
    private static class EntryKey implements Comparable<EntryKey> {

        private final int weight;
        private final long sequence;
        private final long downloadId;
        private final QueueSortOrderEnum sortOrder;

        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private final QueueEntry entryReference;

        @Override
        public int compareTo(EntryKey other) {
            int priorityCmp = Integer.compare(other.getWeight(), getWeight());
            if (priorityCmp != 0) {
                return priorityCmp;
            }

            int sortOrderCmp = sortOrder.getComparator()
                .compare(getEntryReference(), other.getEntryReference());
            if (sortOrderCmp != 0) {
                return sortOrderCmp;
            }

            int sequenceCmp = Long.compare(getSequence(), other.getSequence());
            if (sequenceCmp != 0) {
                return sequenceCmp;
            }

            // Ultimate tiebreaker
            return Long.compare(getDownloadId(), other.getDownloadId());
        }
    }
}
