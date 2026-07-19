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
package net.brlns.gdownloader.ui.custom;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;

import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.ICON;
import static net.brlns.gdownloader.ui.themes.UIColors.LIVE_COLOR;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomThumbnailPanel extends JPanel {

    private static final Font FONT = new Font("TimesRoman", Font.PLAIN, 16);
    private static final Font LIVE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

    private BufferedImage image;
    private BufferedImage scaledImage;

    private int lastWidth = -1;
    private int lastHeight = -1;

    private ImageIcon placeholderIcon;
    private ImageIcon priorityIcon;

    private String durationText;
    private boolean live;

    private BufferedImage compositeCache;
    private int compositeWidth = -1;
    private int compositeHeight = -1;
    private boolean compositeDirty = true;

    @SuppressWarnings("this-escape")
    public CustomThumbnailPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
    }

    private ImageIcon fetchIcon(DownloadTypeEnum downloadType) {
        return switch (downloadType) {
            case VIDEO ->
                loadIcon("/assets/video.png", ICON, 78);
            case AUDIO, SPOTIFY ->
                loadIcon("/assets/music.png", ICON, 78);
            case GALLERY, THUMBNAILS ->
                loadIcon("/assets/picture.png", ICON, 78);
            case DIRECT, SUBTITLES, DESCRIPTION ->
                loadIcon("/assets/internet.png", ICON, 78);
            default ->
                loadIcon(GDownloader.getInstance().getConfig().isDownloadVideo()
                ? "/assets/video.png" : "/assets/music.png", ICON, 78);
        };
    }

    private ImageIcon fetchPriorityIcon(DownloadPriorityEnum downloadPriority) {
        return downloadPriority != DownloadPriorityEnum.NORMAL
            ? loadIcon(downloadPriority.getIconAsset(), ICON, 18) : null;
    }

    public void setPriorityIcon(DownloadPriorityEnum downloadPriority) {
        ImageIcon newIcon = fetchPriorityIcon(downloadPriority);
        if (priorityIcon == newIcon) {
            return;
        }

        priorityIcon = newIcon;
        invalidateComposite();
    }

    public void setPlaceholderIcon(DownloadTypeEnum downloadType) {
        if (image != null) {
            return;
        }

        ImageIcon iconIn = fetchIcon(downloadType);
        if (iconIn == null || placeholderIcon == iconIn) {
            return;
        }

        placeholderIcon = iconIn;

        invalidateComposite();
    }

    public void setImage(BufferedImage imageIn) {
        image = imageIn;
        scaledImage = null;

        invalidateComposite();
    }

    public void setLive(boolean liveIn) {
        if (live == liveIn) {
            return;
        }

        live = liveIn;

        invalidateComposite();
    }

    public boolean isLive() {
        return live;
    }

    public void setImageAndDuration(BufferedImage imageIn, long durationIn) {
        removeAll();

        image = imageIn;
        scaledImage = null;

        String newDurationText;
        if (durationIn == 0) {
            newDurationText = null;
        } else {
            newDurationText = String.format("%d:%02d:%02d",
                durationIn / 3600,
                (durationIn % 3600) / 60,
                durationIn % 60);
        }

        durationText = newDurationText;

        invalidateComposite();
        revalidate();
    }

    private void invalidateComposite() {
        compositeDirty = true;
        repaint();
    }

    private void updateScaledImage(int panelWidth, int panelHeight) {
        if (image == null || panelWidth <= 0 || panelHeight <= 0) {
            scaledImage = null;
            return;
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        double scaleX = (double)(panelWidth + 4) / imageWidth;
        double scaleY = (double)(panelHeight + 4) / imageHeight;

        double scale = Math.min(scaleX, scaleY);

        int sWidth = (int)(imageWidth * scale);
        int sHeight = (int)(imageHeight * scale);

        if (sWidth <= 0 || sHeight <= 0) {
            scaledImage = null;
            return;
        }

        scaledImage = new BufferedImage(sWidth, sHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaledImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, sWidth, sHeight, null);
        g2.dispose();

        lastWidth = panelWidth;
        lastHeight = panelHeight;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int panelWidth = getWidth();
        int panelHeight = getHeight();

        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        if (compositeDirty || compositeCache == null
            || compositeWidth != panelWidth || compositeHeight != panelHeight) {
            rebuildComposite(panelWidth, panelHeight);
        }

        if (compositeCache != null) {
            g.drawImage(compositeCache, 0, 0, this);
        }
    }

    private void rebuildComposite(int panelWidth, int panelHeight) {
        BufferedImage newComposite = new BufferedImage(
            panelWidth, panelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newComposite.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int arcSize = 10;
            RoundRectangle2D roundedRect = new RoundRectangle2D.Float(
                0, 0, panelWidth, panelHeight, arcSize, arcSize);

            g2d.setColor(getBackground());
            g2d.fill(roundedRect);
            g2d.clip(roundedRect);

            if (image != null) {
                if (scaledImage == null || panelWidth != lastWidth || panelHeight != lastHeight) {
                    updateScaledImage(panelWidth, panelHeight);
                }

                if (scaledImage != null) {
                    int scaledWidth = scaledImage.getWidth();
                    int scaledHeight = scaledImage.getHeight();

                    // Center the image, shift left by 2px
                    int x = (panelWidth - scaledWidth) / 2 - 2;
                    int y = (panelHeight - scaledHeight) / 2;

                    g2d.drawImage(scaledImage, x, y, this);

                    if (durationText != null) {
                        g2d.setFont(FONT);
                        g2d.setColor(Color.WHITE);

                        FontMetrics fm = g2d.getFontMetrics();
                        int textWidth = fm.stringWidth(durationText);
                        int textHeight = fm.getHeight();
                        int vPadding = 3;
                        int hPadding = 5;

                        int margin = 3;

                        int rectX = panelWidth - textWidth - (hPadding * 2) - margin;
                        int rectY = y + scaledHeight - textHeight - (vPadding * 2) - margin;

                        arcSize = 8;
                        RoundRectangle2D durationRect = new RoundRectangle2D.Float(
                            rectX, rectY, textWidth + hPadding * 2, textHeight + vPadding * 2,
                            arcSize, arcSize);

                        g2d.setColor(new Color(0, 0, 0, 190));
                        g2d.fill(durationRect);

                        g2d.setColor(Color.WHITE);
                        int textX = rectX + hPadding;
                        int textY = rectY + vPadding + fm.getAscent();
                        g2d.drawString(durationText, textX, textY);
                    }
                }
            } else if (placeholderIcon != null) {
                Image pImg = placeholderIcon.getImage();
                int pWidth = placeholderIcon.getIconWidth();
                int pHeight = placeholderIcon.getIconHeight();

                int x = (panelWidth - pWidth) / 2;
                int y = (panelHeight - pHeight) / 2;

                g2d.drawImage(pImg, x, y, pWidth, pHeight, this);
            }

            if (priorityIcon != null) {
                drawPriorityIcon(g2d, panelWidth, panelHeight);
            }

            if (live) {
                drawLiveBadge(g2d);
            }
        } finally {
            g2d.dispose();
        }

        compositeCache = newComposite;
        compositeWidth = panelWidth;
        compositeHeight = panelHeight;
        compositeDirty = false;
    }

    private void drawLiveBadge(Graphics2D g2d) {
        String liveText = l10n("gui.status.live");
        String dotChar = "●";

        g2d.setFont(LIVE_FONT);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(liveText);
        int dotWidth = fm.stringWidth(dotChar);

        int dotPadding = 5;
        int hPadding = 8;

        int iconSize = 18;
        int iconPadding = 6;
        int badgeHeight = iconSize + iconPadding * 2;

        int badgeWidth = dotWidth + dotPadding + textWidth + hPadding * 2;
        int badgeX = 5;
        int badgeY = 5;
        int arcSize = 8;

        RoundRectangle2D liveRect = new RoundRectangle2D.Float(
            badgeX, badgeY, badgeWidth, badgeHeight, arcSize, arcSize);

        Color liveColor = color(LIVE_COLOR);
        g2d.setColor(new Color(liveColor.getRed(), liveColor.getGreen(), liveColor.getBlue(), 220));
        g2d.fill(liveRect);

        g2d.setColor(Color.WHITE);

        int textBlockHeight = fm.getAscent() + fm.getDescent();
        int baselineY = badgeY + (badgeHeight - textBlockHeight) / 2 + fm.getAscent();

        int dotX = badgeX + hPadding;
        g2d.drawString(dotChar, dotX, baselineY);

        int textX = dotX + dotWidth + dotPadding;
        g2d.drawString(liveText, textX, baselineY);
    }

    private void drawPriorityIcon(Graphics2D g2d, int panelWidth, int panelHeight) {
        int iconSize = 18;
        int padding = 6;
        int boxSize = iconSize + padding * 2;
        int arcSize = 8;

        int priorityX = panelWidth - boxSize - 5;
        int priorityY = 5;

        RoundRectangle2D priorityRect = new RoundRectangle2D.Float(
            priorityX, priorityY, boxSize, boxSize, arcSize, arcSize);

        g2d.setColor(new Color(0, 0, 0, 190));
        g2d.fill(priorityRect);

        Image img = priorityIcon.getImage();
        int iconX = priorityX + (boxSize - iconSize) / 2;
        int iconY = priorityY + (boxSize - iconSize) / 2;
        g2d.drawImage(img, iconX, iconY, iconSize, iconSize, this);
    }
}
