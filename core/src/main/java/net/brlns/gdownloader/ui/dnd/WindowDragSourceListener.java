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
package net.brlns.gdownloader.ui.dnd;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.ActionEvent;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.brlns.gdownloader.ui.GUIManager;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class WindowDragSourceListener implements DragSourceListener {

    private final GUIManager manager;
    private final Timer scrollTimer;

    public WindowDragSourceListener(GUIManager managerIn) {
        manager = managerIn;

        scrollTimer = new Timer(50, (ActionEvent e) -> {
            JViewport viewport = manager.getQueueScrollPane().getViewport();
            JScrollBar scrollBar = manager.getQueueScrollPane().getVerticalScrollBar();

            Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(mouseLocation, viewport);

            Rectangle viewRect = viewport.getViewRect();
            int edgeTolerance = 80;
            int farTolerance = 200;
            int scrollIncrement = 30;
            int maxScrollSpeed = 100;

            if (mouseLocation.y < -farTolerance) {
                scrollBar.setValue(scrollBar.getValue() - maxScrollSpeed);
            } else if (mouseLocation.y < edgeTolerance && viewRect.y > 0) {
                int distanceFromEdge = edgeTolerance - mouseLocation.y;
                int dynamicScroll = Math.min(scrollIncrement + distanceFromEdge / 2, maxScrollSpeed);
                scrollBar.setValue(scrollBar.getValue() - dynamicScroll);
            } else if (mouseLocation.y > viewRect.height + farTolerance) {
                scrollBar.setValue(scrollBar.getValue() + maxScrollSpeed);
            } else if (mouseLocation.y > viewRect.height - edgeTolerance
                && viewRect.y + viewRect.height < viewport.getView().getHeight()) {
                int distanceFromEdge = mouseLocation.y - (viewRect.height - edgeTolerance);
                int dynamicScroll = Math.min(scrollIncrement + distanceFromEdge / 2, maxScrollSpeed);
                scrollBar.setValue(scrollBar.getValue() + dynamicScroll);
            }
        });
    }

    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
        scrollTimer.start();
    }

    @Override
    public void dragOver(DragSourceDragEvent dsde) {
        // Not implemented
    }

    @Override
    public void dragExit(DragSourceEvent dse) {
        scrollTimer.stop();
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
        scrollTimer.stop();
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
        // Not implemented
    }
}
