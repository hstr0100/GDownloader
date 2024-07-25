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
import java.util.Dictionary;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomSliderUI extends BasicSliderUI{

    private static final Font FONT = new Font("Arial", Font.PLAIN, 12);

    public CustomSliderUI(JSlider slider){
        super(slider);
    }

    @Override
    protected Dimension getThumbSize(){
        return new Dimension(20, 20);
    }

    @Override
    public void paintTrack(Graphics g){
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle trackBounds = trackRect;
        g2d.setColor(color(SLIDER_TRACK));
        g2d.fillRect(trackBounds.x, trackBounds.y + (trackBounds.height / 2) - 2,
            trackBounds.width, 4);

        g2d.dispose();
    }

    @Override
    public void paintThumb(Graphics g){
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle thumbBounds = thumbRect;
        g2d.setColor(color(SLIDER_FOREGROUND));
        g2d.fillOval(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);

        g2d.dispose();
    }

    @Override
    public void paintTicks(Graphics g){
        Rectangle tickBounds = tickRect;
        g.setColor(color(SLIDER_FOREGROUND));

        for(int i = slider.getMinimum(); i <= slider.getMaximum(); i += slider.getMajorTickSpacing()){
            int x = xPositionForValue(i);
            g.drawLine(x, tickBounds.y, x, tickBounds.y + tickBounds.height);
        }
    }

    @Override
    public void paintLabels(Graphics g){
        g.setFont(FONT);
        g.setColor(color(SLIDER_FOREGROUND));

        @SuppressWarnings("unchecked")
        Dictionary<Integer, JLabel> labels = (Dictionary<Integer, JLabel>)slider.getLabelTable();

        if(labels != null){
            Rectangle labelBounds = labelRect;

            for(int i = slider.getMinimum(); i <= slider.getMaximum(); i += slider.getMajorTickSpacing()){
                JLabel label = labels.get(i);
                if(label != null){
                    int x = xPositionForValue(i);
                    g.drawString(label.getText(), x - label.getBounds().width / 2,
                        labelBounds.y + labelBounds.height);
                }
            }
        }
    }

    @Override
    public void paint(Graphics g, JComponent c){
        c.setBackground(color(BACKGROUND));

        super.paint(g, c);
    }

    @Override
    protected void installDefaults(JSlider slider){
        super.installDefaults(slider);

        slider.setFocusable(false);
    }
}
