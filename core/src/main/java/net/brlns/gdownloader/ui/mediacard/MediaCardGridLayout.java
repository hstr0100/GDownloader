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
package net.brlns.gdownloader.ui.mediacard;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import javax.swing.JComponent;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Convincing Swing that it's 2026... or was. One pixel at a time.
 * Before you ask: no, I'm not rewriting it in Electron.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class MediaCardGridLayout implements LayoutManager {

    public static final String VIRTUAL_INDEX_PROPERTY = "mediaCardVirtualIndex";

    private static final int MIN_CARD_WIDTH = 500;
    private static final int MAX_COLUMNS = 10;

    private static final int HGAP = 8;

    private static final int RESIZE_DEBOUNCE_MS = 150;

    private final AtomicInteger columnPreference = new AtomicInteger();

    private final IntSupplier itemCountSupplier;
    private final IntSupplier rowHeightSupplier;
    private final Runnable onWidthSettled;

    private final Timer debounceTimer;
    private int lastWidth = -1;
    private boolean forceLayout = true;

    public MediaCardGridLayout(IntSupplier itemCountSupplierIn,
        IntSupplier rowHeightSupplierIn, Runnable onWidthSettledIn) {

        itemCountSupplier = itemCountSupplierIn;
        rowHeightSupplier = rowHeightSupplierIn;
        onWidthSettled = onWidthSettledIn;

        debounceTimer = new Timer(RESIZE_DEBOUNCE_MS, e -> {
            forceLayout = true;

            onWidthSettled.run();
        });

        debounceTimer.setRepeats(false);
    }

    public void setColumnPreference(int columns) {
        if (columns < 0 || columns > MAX_COLUMNS) {
            log.warn("Column count must be 0 for automatic, or 1-{} - clamping.", MAX_COLUMNS);
        }

        columnPreference.set(Math.clamp(columns, 0, MAX_COLUMNS));
    }

    public int getColumnPreference() {
        return columnPreference.get();
    }

    public int determineColumns(int availableWidth, int itemCount) {
        if (itemCount <= 1) {
            return 1;
        }

        int maxThatFit = Math.max(1, (availableWidth + HGAP) / (MIN_CARD_WIDTH + HGAP));
        maxThatFit = Math.min(maxThatFit, MAX_COLUMNS);

        int preference = columnPreference.get();
        int desired = (preference == 0) ? maxThatFit : Math.min(preference, maxThatFit);

        return Math.min(desired, itemCount);
    }

    public int cellWidth(int availableWidth, int columns) {
        return columns <= 1 ? availableWidth
            : Math.max(1, (availableWidth - HGAP * (columns - 1)) / columns);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {

    }

    @Override
    public void removeLayoutComponent(Component comp) {

    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);

            int itemCount = itemCountSupplier.getAsInt();
            if (itemCount <= 0) {
                return new Dimension(availableWidth, 0);
            }

            int columns = determineColumns(availableWidth, itemCount);
            int rows = (itemCount + columns - 1) / columns;
            int rowHeight = Math.max(1, rowHeightSupplier.getAsInt());

            return new Dimension(availableWidth, insets.top + insets.bottom + rows * rowHeight);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            int currentWidth = parent.getWidth();

            if (!(lastWidth == -1 || forceLayout || lastWidth == currentWidth)) {
                lastWidth = currentWidth;
                debounceTimer.restart();

                return;
            }

            lastWidth = currentWidth;
            forceLayout = false;

            applyBounds(parent);
        }
    }

    private void applyBounds(Container parent) {
        Insets insets = parent.getInsets();
        int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);

        int itemCount = itemCountSupplier.getAsInt();
        if (itemCount <= 0) {
            return;
        }

        int columns = determineColumns(availableWidth, itemCount);
        int cellWidth = cellWidth(availableWidth, columns);
        int rowHeight = Math.max(1, rowHeightSupplier.getAsInt());

        for (Component comp : parent.getComponents()) {
            if (!(comp instanceof JComponent jcomp)) {
                continue;
            }

            Object indexProp = jcomp.getClientProperty(VIRTUAL_INDEX_PROPERTY);
            if (!(indexProp instanceof Integer index)) {
                continue;
            }

            int row = index / columns;
            int col = index % columns;

            int x = insets.left + col * (cellWidth + HGAP);
            int y = insets.top + row * rowHeight;

            comp.setBounds(x, y, cellWidth, rowHeight);
        }
    }
}
