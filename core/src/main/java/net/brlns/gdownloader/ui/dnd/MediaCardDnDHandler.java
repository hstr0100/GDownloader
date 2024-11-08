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
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.MediaCard;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class MediaCardDnDHandler implements IDnDHandler {

    private static final DataFlavor MEDIA_CARD_FLAVOR = new DataFlavor(MediaCard.class, "MediaCard");

    private final GUIManager manager;

    public MediaCardDnDHandler(GUIManager managerIn) {
        manager = managerIn;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return TransferHandler.MOVE;
    }

    @Override
    public Transferable createTransferable(JComponent c) {
        if (c instanceof JPanel panel) {
            if (panel.getClientProperty("MEDIA_CARD") instanceof MediaCard card) {
                return new MediaCardTransferable(card);
            }
        }

        return null;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(MEDIA_CARD_FLAVOR);
    }

    @Override
    public boolean importData(TransferSupport support) {
        try {
            Transferable transferable = support.getTransferable();

            if (!transferable.isDataFlavorSupported(MEDIA_CARD_FLAVOR)) {
                throw new IllegalStateException();
            }

            MediaCard card = (MediaCard)transferable.getTransferData(MEDIA_CARD_FLAVOR);

            return manager.handleMediaCardDnD(card, support.getComponent());
        } catch (Exception e) {
            manager.getMain().handleException(e, false);
        }

        return false;
    }

    private class MediaCardTransferable implements Transferable {

        private final MediaCard card;

        public MediaCardTransferable(MediaCard card) {
            this.card = card;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {MEDIA_CARD_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return MEDIA_CARD_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!MEDIA_CARD_FLAVOR.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }

            return card;
        }
    }
}
