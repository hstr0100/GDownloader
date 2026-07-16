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
package net.brlns.gdownloader.ui.custom;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomOverflowArea extends JPanel {

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> priorityEntries = new ArrayList<>();

    private int lastAvailableWidth = -1;
    private int bufferWidth = 0;

    @SuppressWarnings("this-escape")
    public CustomOverflowArea(int gapIn, int bufferWidthIn) {
        super(new FlowLayout(FlowLayout.LEFT, gapIn, 0));
        bufferWidth = bufferWidthIn;

        setOpaque(false);
    }

    public void setBufferWidth(int bufferWidthIn) {
        bufferWidth = bufferWidthIn;

        lastAvailableWidth = -1;
    }

    public void addEntry(JComponent component, int priority) {
        Entry entry = new Entry(priority, component);

        entries.add(entry);
        priorityEntries.add(entry);

        priorityEntries.sort(Comparator.comparingInt(Entry::getPriority).reversed());

        add(component);
        component.setVisible(false);

        // ordering may have changed, force a relayout.
        lastAvailableWidth = -1;
    }

    public void removeEntry(JComponent component) {
        entries.removeIf(entry -> entry.getComponent() == component);
        priorityEntries.removeIf(entry -> entry.getComponent() == component);

        remove(component);

        lastAvailableWidth = -1;
    }

    public void layout(int availableWidth) {
        if (availableWidth == lastAvailableWidth) {
            return;
        }

        lastAvailableWidth = availableWidth;

        int hgap = ((FlowLayout)getLayout()).getHgap();
        int used = 0;
        int visibleCount = 0;

        for (Entry entry : priorityEntries) {
            JComponent component = entry.getComponent();
            int width = component.getPreferredSize().width;
            int widthWithGap = used == 0 ? width : width + hgap;

            boolean shouldShow = (used + widthWithGap + bufferWidth) <= availableWidth;
            entry.setShow(shouldShow);

            if (shouldShow) {
                used += widthWithGap;
                visibleCount++;
            }
        }

        for (Entry entry : entries) {
            JComponent component = entry.getComponent();
            component.setVisible(entry.isShow());
        }

        setVisible(visibleCount > 0);

        revalidate();
        repaint();
    }

    @Getter
    @RequiredArgsConstructor
    private static final class Entry {

        private final int priority;
        private final JComponent component;

        @Setter
        private boolean show = false;
    }
}
