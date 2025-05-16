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
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadPriorityEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;

import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.themes.UIColors.ICON;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomThumbnailPanel extends JPanel {

    private static final Font FONT = new Font("TimesRoman", Font.PLAIN, 16);

    private BufferedImage image;
    private ImageIcon placeholderIcon;
    private ImageIcon priorityIcon;

    private String durationText;

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
        priorityIcon = fetchPriorityIcon(downloadPriority);

        repaint();
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

        JLabel imageLabel = null;
        for (Component comp : getComponents()) {
            if (comp instanceof JLabel jLabel) {
                imageLabel = jLabel;
                break;
            }
        }

        if (imageLabel == null) {
            imageLabel = new JLabel(iconIn);
            imageLabel.setOpaque(false);
            add(imageLabel, BorderLayout.CENTER);
        } else {
            imageLabel.setIcon(iconIn);
        }

        revalidate();
        repaint();
    }

    public void setImage(BufferedImage imageIn) {
        image = imageIn;

        repaint();
    }

    public void setImageAndDuration(BufferedImage imageIn, long durationIn) {
        removeAll();

        image = imageIn;

        if (durationIn == 0) {
            durationText = null;
        } else {
            durationText = String.format("%d:%02d:%02d",
                durationIn / 3600,
                (durationIn % 3600) / 60,
                durationIn % 60);
        }

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int width = getWidth();
        int height = getHeight();

        int arcSize = 10;
        RoundRectangle2D roundedRect = new RoundRectangle2D.Float(
            0, 0, width, height, arcSize, arcSize);

        g2d.setColor(getBackground());
        g2d.fill(roundedRect);

        g2d.setClip(roundedRect);

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        if (image == null && priorityIcon != null) {
            drawPriorityIcon(g2d, panelWidth, panelHeight);
        }

        if (image != null) {
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            // 2px overflow on each side to account for slight antialiasing artifacts
            double scaleX = (double)(panelWidth + 4) / imageWidth;
            double scaleY = (double)(panelHeight + 4) / imageHeight;

            double scale = Math.min(scaleX, scaleY);

            int scaledWidth = (int)(imageWidth * scale);
            int scaledHeight = (int)(imageHeight * scale);

            // Center the image, shift left by 2px
            int x = (panelWidth - scaledWidth) / 2 - 2;
            int y = (panelHeight - scaledHeight) / 2;

            g2d.drawImage(image, x, y, scaledWidth, scaledHeight, this);

            if (priorityIcon != null) {
                drawPriorityIcon(g2d, panelWidth, panelHeight);
            }

            if (durationText != null) {
                g2d.setFont(FONT);
                g2d.setColor(Color.WHITE);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(durationText);
                int textHeight = fm.getHeight();
                int vPadding = 3;
                int hPadding = 5;

                x = panelWidth - scaledWidth;

                int rectX = x + scaledWidth - textWidth - hPadding * 2;
                int rectY = y + scaledHeight - textHeight - vPadding * 2;

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
        } else if (placeholderIcon != null) {
            Component[] components = getComponents();
            if (components.length > 0 && components[0] instanceof JLabel) {
                components[0].paint(g2d);
            }
        }

        g2d.dispose();
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
