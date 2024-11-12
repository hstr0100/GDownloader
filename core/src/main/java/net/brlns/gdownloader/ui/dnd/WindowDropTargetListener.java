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

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import net.brlns.gdownloader.ui.GUIManager;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class WindowDropTargetListener implements DropTargetListener {

    private final GUIManager manager;

    public WindowDropTargetListener(GUIManager managerIn) {
        manager = managerIn;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        // Not implemented
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        // Not implemented
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        // Not implemented
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        // Not implemented
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        boolean result = false;

        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);

            Transferable transferable = dtde.getTransferable();

            result = manager.getMain().getClipboardManager().tryHandleDnD(transferable);
        } catch (Exception e) {
            manager.getMain().handleException(e);
        } finally {
            dtde.dropComplete(result);
        }
    }
}
