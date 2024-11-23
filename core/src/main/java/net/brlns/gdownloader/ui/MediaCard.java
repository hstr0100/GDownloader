/*
 * Copyright (C) 2024 hstr0100
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JPanel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.custom.CustomDynamicLabel;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.ui.custom.CustomThumbnailPanel;
import net.brlns.gdownloader.ui.menu.IMenuEntry;
import net.brlns.gdownloader.util.collection.ConcurrentLinkedHashMap;

import static net.brlns.gdownloader.ui.GUIManager.runOnEDT;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
public class MediaCard {

    private final int id;

    private final JPanel card;
    private final Dimension cardMaximumSize;
    private final CustomDynamicLabel mediaLabel;
    private final CustomThumbnailPanel thumbnailPanel;
    private final CustomProgressBar progressBar;

    private double percentage = 0;

    private Runnable onLeftClick;
    private Map<String, IMenuEntry> rightClickMenu = new ConcurrentLinkedHashMap<>();
    private Runnable onClose;
    private Consumer<Integer> onDrag;
    private boolean closed;

    private Supplier<Boolean> validateDropTarget;

    protected static final int THUMBNAIL_WIDTH = 170;
    protected static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    public void close() {
        closed = true;

        if (onClose != null) {
            onClose.run();
        }
    }

    public void adjustScale(int panelWidth) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gs = ge.getDefaultScreenDevice();
        Rectangle screenBounds = gs.getDefaultConfiguration().getBounds();
        double screenWidth = screenBounds.getWidth();

        double targetWidth = screenWidth * 0.9;
        double scaleFactor = (panelWidth >= targetWidth) ? 1.2 : 1;

        scale(scaleFactor);
    }

    private void scale(double factor) {
        Dimension thumbDimension = new Dimension(
            (int)(MediaCard.THUMBNAIL_WIDTH * factor),
            (int)(MediaCard.THUMBNAIL_HEIGHT * factor));

        Dimension cardDimension = new Dimension(
            (int)(cardMaximumSize.getWidth() * factor),
            (int)(cardMaximumSize.getHeight() * factor));

        runOnEDT(() -> {
            card.setMaximumSize(cardDimension);
            thumbnailPanel.setPreferredSize(thumbDimension);
            thumbnailPanel.setMinimumSize(thumbDimension);
        });
    }

    public void setPlaceholderIcon(DownloadTypeEnum downloadType) {
        runOnEDT(() -> {
            thumbnailPanel.setPlaceholderIcon(downloadType);
        });
    }

    public void setTooltip(String tooltipText) {
        runOnEDT(() -> {
            mediaLabel.setToolTipText(tooltipText);
        });
    }

    public void setThumbnailTooltip(String tooltipText) {
        runOnEDT(() -> {
            thumbnailPanel.setToolTipText(tooltipText);
        });
    }

    public void setLabel(String... label) {
        runOnEDT(() -> {
            mediaLabel.setFullText(label);
        });
    }

    public void setPercentage(double percentageIn) {
        percentage = percentageIn;

        runOnEDT(() -> {
            progressBar.setValue((int)percentageIn);
        });
    }

    public void setProgressBarText(String text) {
        runOnEDT(() -> {
            progressBar.setString(text);
        });
    }

    public void setProgressBarTextAndColors(String text, Color backgroundColor) {
        setProgressBarTextAndColors(text, backgroundColor, Color.WHITE);
    }

    public void setProgressBarTextAndColors(String text, Color backgroundColor, Color textColor) {
        runOnEDT(() -> {
            progressBar.setString(text);
            progressBar.setForeground(backgroundColor);
            progressBar.setTextColor(textColor);
        });
    }

    public void setThumbnailAndDuration(BufferedImage img, long duration) {
        runOnEDT(() -> {
            thumbnailPanel.setImageAndDuration(img, duration);
        });
    }
}
