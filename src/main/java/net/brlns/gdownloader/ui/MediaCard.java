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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
public class MediaCard{

    private final int id;

    private final JPanel panel;
    private final JLabel mediaLabel;
    private final JPanel thumbnailPanel;
    private final CustomProgressBar progressBar;

    private double percentage = 0;

    private GUIManager.CallFunction onClick;
    private GUIManager.CallFunction onClose;
    private boolean closed;

    //TODO dynamic
    protected static final int THUMBNAIL_WIDTH = 120;
    protected static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    public void close(){
        closed = true;

        if(onClose != null){
            onClose.apply();
        }
    }

    public void setTooltip(String tooltipText){
        mediaLabel.setToolTipText(tooltipText);
    }

    public void setThumbnailTooltip(String tooltipText){
        thumbnailPanel.setToolTipText(tooltipText);
    }

    public void setLabel(String... label){
        mediaLabel.setText(GUIManager.wrapText(50, label));
    }

    public void setPercentage(double percentageIn){
        percentage = percentageIn;

        progressBar.setValue((int)percentageIn);
    }

    public void setString(String string){
        progressBar.setString(string);
    }

    public void setColor(Color color){
        progressBar.setForeground(color);
    }

    public void setTextColor(Color color){
        progressBar.setTextColor(color);
    }

    public void setThumbnailAndDuration(String url, long duration){
        try{
            BufferedImage img = ImageIO.read(new URI(url).toURL());

            if(img != null){
                Image scaledImg = img.getScaledInstance(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Image.SCALE_SMOOTH);

                BufferedImage bufferedImage = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = bufferedImage.createGraphics();

                g2d.drawImage(scaledImg, 0, 0, null);

                String formattedDuration = String.format("%d:%02d:%02d",
                    duration / 3600,
                    (duration % 3600) / 60,
                    duration % 60);

                Font font = new Font("Arial", Font.PLAIN, 12);
                g2d.setFont(font);
                g2d.setColor(Color.WHITE);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(formattedDuration);
                int textHeight = fm.getHeight();
                int padding = 5;

                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillRect(THUMBNAIL_WIDTH - textWidth - padding * 2, THUMBNAIL_HEIGHT - textHeight - padding * 2,
                    textWidth + padding * 2, textHeight + padding * 2);

                g2d.setColor(Color.WHITE);
                g2d.drawString(formattedDuration, THUMBNAIL_WIDTH - textWidth - padding, THUMBNAIL_HEIGHT - padding);

                g2d.dispose();

                thumbnailPanel.removeAll();
                JLabel imageLabel = new JLabel(new ImageIcon(bufferedImage));
                thumbnailPanel.add(imageLabel, BorderLayout.CENTER);

                thumbnailPanel.revalidate();
                thumbnailPanel.repaint();
            }else{
                log.error("ImageIO.read returned null for {}", url);
            }
        }catch(IOException | URISyntaxException e){
            log.error("ImageIO.read exception {} {}", e.getLocalizedMessage(), url);
        }
    }
}
