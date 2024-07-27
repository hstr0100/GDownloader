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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ui.custom.CustomProgressBar;
import net.brlns.gdownloader.ui.custom.CustomThumbnailPanel;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Slf4j
public class MediaCard{

    private final int id;

    private final JPanel panel;
    private final JLabel mediaLabel;
    private final CustomThumbnailPanel thumbnailPanel;
    private final CustomProgressBar progressBar;

    private double percentage = 0;

    private Runnable onLeftClick;
    private Map<String, Runnable> rightClickMenu = new HashMap<>();
    private Runnable onClose;
    private boolean closed;

    protected static final int THUMBNAIL_WIDTH = 160;
    protected static final int THUMBNAIL_HEIGHT = (int)(THUMBNAIL_WIDTH / 16.0 * 9.0);

    public void close(){
        closed = true;

        if(onClose != null){
            onClose.run();
        }
    }

    public void scaleThumbnail(double factor){
        Dimension dimension = new Dimension(
            (int)(MediaCard.THUMBNAIL_WIDTH * factor),
            (int)(MediaCard.THUMBNAIL_HEIGHT * factor));

        thumbnailPanel.setPreferredSize(dimension);
        thumbnailPanel.setMinimumSize(dimension);
    }

    public void setTooltip(String tooltipText){
        mediaLabel.setToolTipText(tooltipText);
    }

    public void setThumbnailTooltip(String tooltipText){
        thumbnailPanel.setToolTipText(tooltipText);
    }

    public void setLabel(String... label){
        mediaLabel.setText(GUIManager.wrapText(51, label));
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
                thumbnailPanel.setImageAndDuration(img, duration);
            }else{
                log.error("ImageIO.read returned null for {}", url);
            }
        }catch(IOException | URISyntaxException e){
            log.error("ImageIO.read exception {} {}", e.getLocalizedMessage(), url);
        }
    }
}
