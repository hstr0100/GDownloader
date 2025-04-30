/*
 * Copyright (C) 2025 hstr0100
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

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class CustomLoadingSpinner extends JComponent {

    private final int size;
    private final Timer timer;

    private double rotation = 0;
    private long lastUpdateTime;

    public CustomLoadingSpinner(int sizeIn) {
        size = sizeIn;
        lastUpdateTime = System.currentTimeMillis();

        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        timer = new Timer(33, e -> {// 30 FPS
            long currentTime = System.currentTimeMillis();
            double elapsed = (currentTime - lastUpdateTime) / 1000.0;
            lastUpdateTime = currentTime;

            rotation = (rotation + (120 * elapsed)) % 360;
            repaint();
        });

        timer.setCoalesce(true);
        timer.start();

        // Consume mouse motion events to prevent choppiness
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                e.consume();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D)g.create();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        AffineTransform originalTransform = g2d.getTransform();

        g2d.translate(centerX, centerY);
        g2d.rotate(Math.toRadians(rotation));

        for (int i = 0; i < 12; i++) {
            float alpha = 0.1f + (i / 12.0f) * 0.9f;
            g2d.setColor(new Color(0.7f, 0.7f, 0.7f, alpha));

            g2d.rotate(Math.toRadians(30));

            g2d.fillRoundRect(size / 2 - 15, -2, 10, 4, 2, 2);
        }

        g2d.setTransform(originalTransform);
        g2d.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        e.consume();

        super.processMouseMotionEvent(e);
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        e.consume();

        super.processMouseEvent(e);
    }
}
