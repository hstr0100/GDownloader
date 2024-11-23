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
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;

import static net.brlns.gdownloader.ui.GUIManager.loadIcon;
import static net.brlns.gdownloader.ui.themes.UIColors.ICON;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomThumbnailPanel extends JPanel {

    private static final Font FONT = new Font("TimesRoman", Font.PLAIN, 16);

    private BufferedImage image;
    private ImageIcon placeholderIcon;

    private String durationText;

    @SuppressWarnings("this-escape")
    public CustomThumbnailPanel() {
        setLayout(new BorderLayout());
    }

    private ImageIcon fetchIcon(DownloadTypeEnum downloadType) {
        return switch (downloadType) {
            case VIDEO ->
                loadIcon("/assets/video.png", ICON, 78);
            case AUDIO ->
                loadIcon("/assets/music.png", ICON, 78);
            case GALLERY, THUMBNAILS ->
                loadIcon("/assets/picture.png", ICON, 78);
            case DIRECT, SUBTITLES ->
                loadIcon("/assets/internet.png", ICON, 78);
            default ->
                loadIcon(GDownloader.getInstance().getConfig().isDownloadVideo()
                ? "/assets/video.png" : "/assets/music.png", ICON, 78);
        };
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

        durationText = String.format("%d:%02d:%02d",
            durationIn / 3600,
            (durationIn % 3600) / 60,
            durationIn % 60);

        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image != null) {
            Graphics2D g2d = (Graphics2D)g.create();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int panelWidth = getWidth();
            int panelHeight = getHeight();
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            double scaleX = (double)panelWidth / imageWidth;
            double scaleY = (double)panelHeight / imageHeight;

            double scale = Math.min(scaleX, scaleY);

            int scaledWidth = (int)(imageWidth * scale);
            int scaledHeight = (int)(imageHeight * scale);

            int x = (panelWidth - scaledWidth) / 2;
            int y = (panelHeight - scaledHeight) / 2;

            g2d.drawImage(image, x, y, scaledWidth, scaledHeight, this);

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

                g2d.setColor(new Color(0, 0, 0, 190));
                g2d.fillRect(rectX, rectY, textWidth + hPadding * 2, textHeight + vPadding * 2);

                g2d.setColor(Color.WHITE);
                int textX = rectX + hPadding;
                int textY = rectY + vPadding + fm.getAscent();
                g2d.drawString(durationText, textX, textY);
            }

            g2d.dispose();
        }
    }
}
