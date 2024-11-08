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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.util.Nullable;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class WindowTransferHandler extends TransferHandler {

    private final List<ITransferHandler> dndHandlers = new ArrayList<>();

    public WindowTransferHandler(GUIManager manager) {
        dndHandlers.add(new TextDnDHandler(manager));
        dndHandlers.add(new MediaCardDnDHandler(manager));
    }

    @Override
    public int getSourceActions(JComponent c) {
        for (ITransferHandler handler : dndHandlers) {
            int sourceActions = handler.getSourceActions(c);
            if (sourceActions != NONE) {
                return sourceActions;
            }
        }

        return NONE;
    }

    @Override
    @Nullable
    protected Transferable createTransferable(JComponent c) {
        for (ITransferHandler handler : dndHandlers) {
            Transferable transferable = handler.createTransferable(c);
            if (transferable != null) {
                return transferable;
            }
        }

        return null;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        for (ITransferHandler handler : dndHandlers) {
            if (handler.canImport(support)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
        for (ITransferHandler handler : dndHandlers) {
            if (!handler.canImport(support)) {
                continue;
            }

            if (handler.importData(support)) {
                return true;
            }
        }

        return false;
    }
}
