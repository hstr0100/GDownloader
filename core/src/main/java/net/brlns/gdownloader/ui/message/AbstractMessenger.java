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
import net.brlns.gdownloader.ui.AudioEngine;
import net.brlns.gdownloader.ui.GUIManager;

import static net.brlns.gdownloader.ui.GUIManager.runOnEDT;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractMessenger {

    protected final GUIManager manager;

    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isShowingMessage = new AtomicBoolean();

    private final AtomicReference<Message> currentMessage = new AtomicReference<>();

    public AbstractMessenger(GUIManager managerIn) {
        manager = managerIn;
    }

    public void show(String title, String message, int durationMillis,
        MessageTypeEnum messageType, boolean playTone, boolean discardDuplicates) {
        //if (manager.getMain().getConfig().isDebugMode()) {
        //    log.info("{}: {} - {}", messageType, title, message);
        //}

        Message messagePojo = new Message(title, message, durationMillis, messageType, playTone);
        Message lastMessage = currentMessage.get();
        if (discardDuplicates && (messageQueue.contains(messagePojo)
            || lastMessage != null && lastMessage.equals(messagePojo))) {
            return;
        }

        messageQueue.add(messagePojo);

        if (!isShowingMessage.get()) {
            displayNextMessage();
        }
    }

    protected void displayNextMessage() {
        runOnEDT(() -> {
            close();

            if (messageQueue.isEmpty()) {
                currentMessage.set(null);
                isShowingMessage.set(false);
                return;
            }

            isShowingMessage.set(true);

            Message nextMessage = messageQueue.poll();
            currentMessage.set(nextMessage);

            show(nextMessage);

            if (nextMessage.isPlayTone() && manager.getMain().getConfig().isPlaySounds()) {
                AudioEngine.playNotificationTone();
            }
        });
    }

    public abstract void close();

    public abstract void show(Message message);

}
