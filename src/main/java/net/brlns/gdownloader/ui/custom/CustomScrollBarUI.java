package net.brlns.gdownloader.ui.custom;

import java.awt.*;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomScrollBarUI extends BasicScrollBarUI{

    @Override
    protected void configureScrollBarColors(){
        thumbColor = Color.WHITE;
        trackColor = Color.DARK_GRAY;
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
