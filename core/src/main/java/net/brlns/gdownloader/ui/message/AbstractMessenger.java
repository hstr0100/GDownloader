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
package net.brlns.gdownloader.ui.message;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ui.AudioEngine;

import static net.brlns.gdownloader.ui.UIUtils.runOnEDT;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractMessenger {

    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isShowingMessage = new AtomicBoolean();

    private final AtomicReference<Message> currentMessage = new AtomicReference<>();

    public void display(Message message) {
        //if (manager.getMain().getConfig().isDebugMode()) {
        //    log.info("{}: {} - {}", messageType, title, message);
        //}

        Message lastMessage = currentMessage.get();
        if (message.isDiscardDuplicates() && (messageQueue.contains(message)
            || lastMessage != null && lastMessage.equals(message))) {
            return;
        }

        messageQueue.add(message);

        if (!isShowingMessage.get()) {
            displayNextMessage();
        }
    }

    protected void displayNextMessage() {
        runOnEDT(() -> {
            close();

            if (messageQueue.isEmpty() || !canDisplay()) {
                currentMessage.set(null);
                isShowingMessage.set(false);
                return;
            }

            isShowingMessage.set(true);

            Message nextMessage = messageQueue.poll();
            currentMessage.set(nextMessage);

            internalDisplay(nextMessage);

            if (nextMessage.isPlayTone()
                && GDownloader.getInstance().getConfig().isPlaySounds()) {
                AudioEngine.playNotificationTone(nextMessage.getMessageType());
            }
        });
    }

    protected abstract void close();

    protected abstract void internalDisplay(Message message);

    protected abstract boolean canDisplay();

}
