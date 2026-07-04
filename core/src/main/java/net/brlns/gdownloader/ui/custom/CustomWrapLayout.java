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

import java.awt.*;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomWrapLayout extends FlowLayout {

    public CustomWrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);

        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }

            int targetWidth = container.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dimension = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }

                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    addRow(dimension, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }

                if (rowWidth != 0) {
                    rowWidth += hgap;
                }

                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }

            addRow(dimension, rowWidth, rowHeight);

            dimension.width += horizontalInsetsAndGap;
            dimension.height += insets.top + insets.bottom + vgap * 2;

            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null && target.isValid()) {
                dimension.width -= (hgap + 1);
            }

            return dimension;
        }
    }

    private void addRow(Dimension dimension, int rowWidth, int rowHeight) {
        dimension.width = Math.max(dimension.width, rowWidth);

        if (dimension.height > 0) {
            dimension.height += getVgap();
        }

        dimension.height += rowHeight;
    }
}
