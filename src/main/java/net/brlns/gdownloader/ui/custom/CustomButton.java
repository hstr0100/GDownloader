package net.brlns.gdownloader.ui.custom;

import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JButton;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CustomButton extends JButton{

    @Getter
    @Setter
    private Color hoverBackgroundColor;

    @Getter
    @Setter
    private Color pressedBackgroundColor;

    public CustomButton(){
        this(null);
    }

    public CustomButton(String text){
        super(text);

        super.setContentAreaFilled(false);
    }

    @Override
    protected void paintComponent(Graphics g){
        if(getModel().isPressed()){
            g.setColor(pressedBackgroundColor);
        }else if(getModel().isRollover()){
            g.setColor(hoverBackgroundColor);
        }else{
            g.setColor(getBackground());
        }

        g.fillRect(0, 0, getWidth(), getHeight());

        super.paintComponent(g);
    }

    @Override
    public void setContentAreaFilled(boolean b){

    }
}
