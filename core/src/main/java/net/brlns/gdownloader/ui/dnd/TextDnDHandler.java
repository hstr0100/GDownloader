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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.util.Nullable;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class TextDnDHandler implements ITransferHandler {

    protected final GUIManager manager;

    public TextDnDHandler(GUIManager managerIn) {
        manager = managerIn;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return TransferHandler.NONE;
    }

    @Override
    @Nullable
    public Transferable createTransferable(JComponent c) {
        return null;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.stringFlavor)
            || support.isDataFlavorSupported(DataFlavor.selectionHtmlFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        try {
            Transferable transferable = support.getTransferable();

            return manager.getMain().tryHandleDnD(transferable);
        } catch (Exception e) {
            manager.getMain().handleException(e, false);
        }

        return false;
    }
}
