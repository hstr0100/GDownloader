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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Convincing Swing that it's 2026... or was. One pixel at a time.
 * Before you ask: no, I'm not rewriting it in Electron.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class MediaCardGridLayout implements LayoutManager {

    private static final int MIN_CARD_WIDTH = 500;
    private static final int MAX_COLUMNS = 10;

    private static final int HGAP = 8;
    private static final int VGAP = 0;

    private final AtomicInteger columnPreference = new AtomicInteger();

    public void setColumnPreference(int columns) {
        if (columns < 0 || columns > MAX_COLUMNS) {
            log.warn("Column count must be 0 for automatic, or 1-{} - clamping.", MAX_COLUMNS);
        }

        columnPreference.set(Math.clamp(columns, 0, MAX_COLUMNS));
    }

    public int getColumnPreference() {
        return columnPreference.get();
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
            return compute(parent, false);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            compute(parent, true);
        }
    }

    private Dimension compute(Container parent, boolean apply) {
        Insets insets = parent.getInsets();
        int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);

        List<Component> visible = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component.isVisible()) {
                visible.add(component);
            }
        }

        if (visible.isEmpty()) {
            return new Dimension(availableWidth, 0);
        }

        int columns = determineColumnCount(availableWidth, visible.size());

        int y = insets.top;

        if (columns <= 1) {
            for (Component component : visible) {
                int height = component.getPreferredSize().height;

                if (apply) {
                    component.setBounds(insets.left, y, availableWidth, height);
                }

                y += height + VGAP;
            }

            return new Dimension(availableWidth, Math.max(0, y - VGAP - insets.top));
        }

        int cellWidth = Math.max(1, (availableWidth - HGAP * (columns - 1)) / columns);

        int index = 0;
        while (index < visible.size()) {
            int rowEnd = Math.min(index + columns, visible.size());

            int rowHeight = 0;
            for (int i = index; i < rowEnd; i++) {
                rowHeight = Math.max(rowHeight, visible.get(i).getPreferredSize().height);
            }

            if (apply) {
                int x = insets.left;
                for (int i = index; i < rowEnd; i++) {
                    visible.get(i).setBounds(x, y, cellWidth, rowHeight);
                    x += cellWidth + HGAP;
                }
            }

            y += rowHeight + VGAP;
            index = rowEnd;
        }

        return new Dimension(availableWidth, Math.max(0, y - VGAP - insets.top));
    }

    private int determineColumnCount(int availableWidth, int visibleCount) {
        if (visibleCount <= 1) {
            return 1;
        }

        int maxThatFit = Math.max(1, (availableWidth + HGAP) / (MIN_CARD_WIDTH + HGAP));
        maxThatFit = Math.min(maxThatFit, MAX_COLUMNS);

        int preference = columnPreference.get();
        int desired = (preference == 0) ? maxThatFit : Math.min(preference, maxThatFit);

        return Math.min(desired, visibleCount);
    }
}
