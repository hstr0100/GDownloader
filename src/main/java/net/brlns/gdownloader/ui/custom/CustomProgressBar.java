package net.brlns.gdownloader.ui.custom;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    public CustomProgressBar(int min, int max, Color textColor){
        super(min, max);

        this.textColor = textColor;
    }

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D)g;
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
