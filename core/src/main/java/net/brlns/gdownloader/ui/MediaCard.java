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

import jakarta.annotation.Nullable;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.enums.CloseReasonEnum;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.ui.custom.CustomMediaCardUI;
import net.brlns.gdownloader.ui.menu.IMenuEntry;
import net.brlns.gdownloader.util.collection.ConcurrentLinkedHashMap;

import static net.brlns.gdownloader.ui.MediaCard.UpdateType.*;
import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
public class MediaCard {

    private final int id;

    @Nullable
    private CustomMediaCardUI ui;

    private double percentage = 0;
    private double scale = 0;
    private String tooltipText;
    private String thumbnailTooltipText;
    private String[] labelText;
    private String progressBarText;
    private Color progressBarBackgroundColor;
    private Color progressBarTextColor;
    private BufferedImage thumbnailImage;
    private long thumbnailDuration;
    private DownloadTypeEnum placeholderIconType;
    private DownloadPriorityEnum downloadPriorityIconType;

    private Runnable onLeftClick;
    private Map<String, IMenuEntry> rightClickMenu = new ConcurrentLinkedHashMap<>();
    private Consumer<CloseReasonEnum> onClose;
    private Consumer<MediaCard> onSwap;
    private boolean closed;

    private Supplier<Boolean> validateDropTarget;

    protected static final int THUMBNAIL_WIDTH = 170;
    protected static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    public void close(CloseReasonEnum reason) {
        closed = true;

        if (onClose != null) {
            onClose.accept(reason);
        }
    }

    public void setUi(CustomMediaCardUI uiIn) {
        ui = uiIn;
        updateUI(ALL);
    }

    public void adjustScale(int panelWidth) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gs = ge.getDefaultScreenDevice();
        Rectangle screenBounds = gs.getDefaultConfiguration().getBounds();
        double screenWidth = screenBounds.getWidth();

        double targetWidth = screenWidth * 0.9;
        double scaleFactor = (panelWidth >= targetWidth) ? 1.2 : 1;

        scale = scaleFactor;
        updateUI(SCALE);
    }

    public void setPlaceholderIcon(DownloadTypeEnum downloadTypeIn) {
        placeholderIconType = downloadTypeIn;
        updateUI(PLACEHOLDER_ICON);
    }

    public void setPriorityIcon(DownloadPriorityEnum downloadPriorityIn) {
        downloadPriorityIconType = downloadPriorityIn;
        updateUI(PRIORITY_ICON);
    }

    public void setTooltip(String tooltipTextIn) {
        tooltipText = tooltipTextIn;
        updateUI(TOOLTIP);
    }

    public void setThumbnailTooltip(String tooltipTextIn) {
        thumbnailTooltipText = tooltipTextIn;
        updateUI(THUMBNAIL_TOOLTIP);
    }

    public void setLabel(String... labelIn) {
        labelText = labelIn;
        updateUI(LABEL_TEXT);
    }

    public void setPercentage(double percentageIn) {
        percentage = percentageIn;
        updateUI(PROGRESS_BAR);
    }

    public void setProgressBarText(String textIn) {
        progressBarText = textIn;
        updateUI(PROGRESS_BAR);
    }

    public void setProgressBarTextAndColors(String textIn, Color backgroundColorIn) {
        setProgressBarTextAndColors(textIn, backgroundColorIn, Color.WHITE);
    }

    public void setProgressBarTextAndColors(String textIn, Color backgroundColorIn, Color textColorIn) {
        progressBarText = textIn;
        progressBarBackgroundColor = backgroundColorIn;
        progressBarTextColor = textColorIn;
        updateUI(PROGRESS_BAR);
    }

    public void setThumbnailAndDuration(BufferedImage imgIn, long durationIn) {
        thumbnailImage = imgIn;
        thumbnailDuration = durationIn;
        updateUI(THUMBNAIL_IMAGE);
    }

    public void updateUI(UpdateType updateType) {
        if (ui == null) {
            return; // No UI available, skip updates
        }

        runOnEDT(() -> {
            switch (updateType) {
                case ALL -> {
                    for (UpdateType type : UpdateType.values()) {
                        if (type != ALL) {
                            updateUI(type);
                        }
                    }
                }
                case LABEL_TEXT -> {
                    if (labelText != null) {
                        ui.updateLabel(labelText);
                    }
                }
                case SCALE -> {
                    if (scale != 0) {
                        ui.updateScale(scale);
                    }
                }
                case TOOLTIP -> {
                    if (tooltipText != null) {
                        ui.updateTooltip(tooltipText);
                    }
                }
                case THUMBNAIL_TOOLTIP -> {
                    if (thumbnailTooltipText != null) {
                        ui.updateThumbnailTooltip(thumbnailTooltipText);
                    }
                }
                case PROGRESS_BAR -> {
                    if (progressBarText != null) {
                        ui.updateProgressBar(percentage, progressBarText, progressBarBackgroundColor, progressBarTextColor);
                    }
                }
                case THUMBNAIL_IMAGE -> {
                    if (thumbnailImage != null) {
                        ui.updateThumbnail(thumbnailImage, thumbnailDuration);
                    }
                }
                case PLACEHOLDER_ICON -> {
                    if (placeholderIconType != null) {
                        ui.updatePlaceholderIcon(placeholderIconType);
                    }
                }
                case PRIORITY_ICON -> {
                    if (downloadPriorityIconType != null) {
                        ui.updatePriorityIcon(downloadPriorityIconType);
                    }
                }
            }
        });
    }

    public static enum UpdateType {
        ALL,
        SCALE,
        LABEL_TEXT,
        TOOLTIP,
        THUMBNAIL_TOOLTIP,
        PROGRESS_BAR,
        THUMBNAIL_IMAGE,
        PLACEHOLDER_ICON,
        PRIORITY_ICON
    }
}
