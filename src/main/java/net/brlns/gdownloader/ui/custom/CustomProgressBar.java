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
import javax.swing.JProgressBar;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomProgressBar extends JProgressBar{

    @Getter
    @Setter
    private Color textColor;

    private static final Font FONT = new Font("Dialog", Font.BOLD, 12);

    public CustomProgressBar(int min, int max, Color textColor){
        super(min, max);

        this.textColor = textColor;
    }

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);

        //TODO fix the terrible antialias
        Graphics2D g2 = (Graphics2D)g;
        g2.setFont(FONT);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        String text = getString();
        if(text != null && !text.isEmpty()){
            g2.setColor(textColor);
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, x, y);
        }
    }
}
