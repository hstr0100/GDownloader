package net.brlns.gdownloader.ui.custom;

import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.plaf.basic.BasicToolTipUI;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomToolTip extends JToolTip{

    @Override
    public void updateUI(){
        setUI(new BasicToolTipUI(){
            @Override
            public void paint(Graphics g, JComponent c){
                Graphics2D g2d = (Graphics2D)g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 10, 10);
                g2d.setColor(Color.WHITE);
                g2d.drawString(c.getToolTipText(), 5, 15);
            }

            @Override
            public Dimension getPreferredSize(JComponent c){
                FontMetrics fm = c.getFontMetrics(c.getFont());
                String tipText = c.getToolTipText();
                return new Dimension(fm.stringWidth(tipText) + 10, fm.getHeight() + 5);
            }
        });
    }
}
