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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomScrollBarUI extends BasicScrollBarUI{

    @Override
    protected void configureScrollBarColors(){
        thumbColor = color(FOREGROUND);
        trackColor = color(BACKGROUND);
    }

    @Override
    protected JButton createDecreaseButton(int orientation){
        return createDummyButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation){
        return createDummyButton();
    }

    private JButton createDummyButton(){
        JButton dummyButton = new JButton();
        dummyButton.setPreferredSize(new Dimension(0, 0));
        dummyButton.setMinimumSize(new Dimension(0, 0));
        dummyButton.setMaximumSize(new Dimension(0, 0));

        return dummyButton;
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds){
        if(thumbBounds.isEmpty() || !scrollbar.isEnabled()){
            return;
        }

        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(thumbColor);
        g2.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);

        g2.dispose();
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds){
        if(trackBounds.isEmpty() || !scrollbar.isEnabled()){
            return;
        }

        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(trackColor);
        g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);

        g2.dispose();
    }

    @Override
    protected Dimension getMinimumThumbSize(){
        return new Dimension(8, 8);
    }

    @Override
    public Dimension getPreferredSize(JComponent c){
        if(scrollbar.getOrientation() == JScrollBar.VERTICAL){
            return new Dimension(8, super.getPreferredSize(c).height);
        }else{
            return new Dimension(super.getPreferredSize(c).width, 8);
        }
    }
}
