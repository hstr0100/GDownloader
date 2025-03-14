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
package net.brlns.gdownloader.ui.custom;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Data;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
public class CustomMediaCardUI {

    public static final int THUMBNAIL_WIDTH = 170;
    public static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    private final JPanel card;
    private final Dimension cardMaximumSize;
    private final CustomDynamicLabel mediaNameLabel;
    private final CustomThumbnailPanel thumbnailPanel;
    private final CustomProgressBar progressBar;

    public CustomMediaCardUI(JPanel cardIn, Dimension cardMaximumSizeIn, CustomDynamicLabel mediaNameLabelIn,
        CustomThumbnailPanel thumbnailPanelIn, CustomProgressBar progressBarIn) {

        card = cardIn;
        cardMaximumSize = cardMaximumSizeIn;
        mediaNameLabel = mediaNameLabelIn;
        thumbnailPanel = thumbnailPanelIn;
        progressBar = progressBarIn;
    }

    public void updateLabel(String... labelText) {
        assert SwingUtilities.isEventDispatchThread();
        mediaNameLabel.setFullText(labelText);
    }

    public void updateTooltip(String tooltipText) {
        assert SwingUtilities.isEventDispatchThread();
        mediaNameLabel.setToolTipText(tooltipText);
    }

    public void updateThumbnailTooltip(String tooltipText) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setToolTipText(tooltipText);
    }

    public void updateProgressBar(double percentage, String text, Color backgroundColor, Color textColor) {
        assert SwingUtilities.isEventDispatchThread();
        progressBar.setValue((int)percentage);
        progressBar.setString(text);
        progressBar.setForeground(backgroundColor);
        progressBar.setTextColor(textColor);
    }

    public void updateThumbnail(BufferedImage img, long duration) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setImageAndDuration(img, duration);
    }

    public void updatePlaceholderIcon(DownloadTypeEnum downloadType) {
        assert SwingUtilities.isEventDispatchThread();
        thumbnailPanel.setPlaceholderIcon(downloadType);
    }

    public void updateScale(double factor) {
        assert SwingUtilities.isEventDispatchThread();
        Dimension thumbDimension = new Dimension(
            (int)(THUMBNAIL_WIDTH * factor),
            (int)(THUMBNAIL_HEIGHT * factor));

        Dimension cardDimension = new Dimension(
            (int)(cardMaximumSize.getWidth() * factor),
            (int)(cardMaximumSize.getHeight() * factor));

        card.setMaximumSize(cardDimension);
        thumbnailPanel.setPreferredSize(thumbDimension);
        thumbnailPanel.setMinimumSize(thumbDimension);
    }
}
