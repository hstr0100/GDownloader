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
package net.brlns.gdownloader.ui.status;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.*;
import lombok.Value;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.TOAST_BACKGROUND;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class StatusIndicatorPanel extends JPanel {

    private final List<StatusIndicatorEnum> activeStatuses = new CopyOnWriteArrayList<>();

    private static final int CORNER_RADIUS = 15;
    private static final int PADDING = 7;
    private static final int ICON_SPACING = 7;

    public StatusIndicatorPanel() {
        setOpaque(false);

        SwingUtilities.invokeLater(() -> {
            ToolTipManager.sharedInstance().registerComponent(StatusIndicatorPanel.this);
        });
    }

    public void addStatus(StatusIndicatorEnum statusType) {
        if (activeStatuses.add(statusType)) {
            updateIndicatorVisibility();
            revalidate();
            repaint();
        }
    }

    public void removeStatus(StatusIndicatorEnum statusType) {
        if (activeStatuses.remove(statusType)) {
            updateIndicatorVisibility();
            revalidate();
            repaint();
        }
    }

    public void clearStatuses() {
        activeStatuses.clear();

        updateIndicatorVisibility();
        revalidate();
        repaint();
    }

    public boolean hasStatuses() {
        return !activeStatuses.isEmpty();
    }

    private void updateIndicatorVisibility() {
        boolean shouldBeVisible = hasStatuses();

        if (isVisible() != shouldBeVisible) {
            setVisible(shouldBeVisible);

            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
        }
    }

    private IconDimensions calculateIconDimensions() {
        int maxIconHeight = 0;
        int totalIconWidth = 0;
        int activeIconCount = 0;

        for (StatusIndicatorEnum type : StatusIndicatorEnum.values()) {
            if (activeStatuses.contains(type)) {
                Icon icon = type.getIcon();

                maxIconHeight = Math.max(maxIconHeight, icon.getIconHeight());
                totalIconWidth += icon.getIconWidth();
                activeIconCount++;
            }
        }

        return new IconDimensions(maxIconHeight, totalIconWidth, activeIconCount);
    }

    private PanelMetrics calculatePanelMetrics(IconDimensions dimensions) {
        int indicatorWidth = (dimensions.getCount() > 0)
            ? dimensions.getTotalWidth() + (dimensions.getCount() - 1) * ICON_SPACING + 2 * PADDING : 0;
        int indicatorHeight = (dimensions.getCount() > 0)
            ? dimensions.getMaxHeight() + 2 * PADDING : 0;
        int indicatorX = PADDING;
        int indicatorY = getHeight() - indicatorHeight - PADDING;

        return new PanelMetrics(indicatorWidth, indicatorHeight, indicatorX, indicatorY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!isVisible() || !hasStatuses()) {
            return;
        }

        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        IconDimensions dimensions = calculateIconDimensions();
        PanelMetrics metrics = calculatePanelMetrics(dimensions);

        if (metrics.getWidth() > 0 && metrics.getHeight() > 0) {
            g2d.setColor(color(TOAST_BACKGROUND));
            RoundRectangle2D roundedRect = new RoundRectangle2D.Float(
                metrics.getX(), metrics.getY(), metrics.getWidth(), metrics.getHeight(),
                CORNER_RADIUS, CORNER_RADIUS);
            g2d.fill(roundedRect);

            int currentX = metrics.getX() + PADDING;
            int iconY = metrics.getY() + PADDING
                + (metrics.getHeight() - 2 * PADDING - dimensions.getMaxHeight()) / 2;

            for (StatusIndicatorEnum type : StatusIndicatorEnum.values()) {
                if (activeStatuses.contains(type)) {
                    Icon icon = type.getIcon();
                    icon.paintIcon(this, g2d, currentX, iconY);
                    currentX += icon.getIconWidth() + ICON_SPACING;
                }
            }
        }

        g2d.dispose();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (!isVisible() || !hasStatuses()) {
            return null;
        }

        int mouseX = event.getX();
        int mouseY = event.getY();

        IconDimensions dimensions = calculateIconDimensions();
        PanelMetrics metrics = calculatePanelMetrics(dimensions);

        if (mouseX >= metrics.getX() && mouseX <= metrics.getX() + metrics.getWidth()
            && mouseY >= metrics.getY() && mouseY <= metrics.getY() + metrics.getHeight()) {

            int currentX = metrics.getX() + PADDING;
            int iconY = metrics.getY() + PADDING
                + (metrics.getHeight() - 2 * PADDING - dimensions.getMaxHeight()) / 2;

            for (StatusIndicatorEnum type : StatusIndicatorEnum.values()) {
                if (activeStatuses.contains(type)) {
                    Icon icon = type.getIcon();
                    int iconWidth = icon.getIconWidth();
                    int iconHeight = icon.getIconHeight();

                    if (mouseX >= currentX && mouseX <= currentX + iconWidth
                        && mouseY >= iconY && mouseY <= iconY + iconHeight) {
                        return type.getDisplayName();
                    }

                    currentX += iconWidth + ICON_SPACING;
                }
            }
        }

        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getWidth(), getHeight());
    }

    @Override
    public boolean contains(int x, int y) {
        if (!isVisible() || !hasStatuses()) {
            return false;
        }

        IconDimensions dimensions = calculateIconDimensions();
        PanelMetrics metrics = calculatePanelMetrics(dimensions);

        return x >= metrics.getX() && x <= metrics.getX() + metrics.getWidth()
            && y >= metrics.getY() && y <= metrics.getY() + metrics.getHeight();
    }

    @Value
    private static class IconDimensions {

        private int maxHeight;
        private int totalWidth;
        private int count;
    }

    @Value
    private static class PanelMetrics {

        private int width;
        private int height;
        private int x;
        private int y;
    }
}
