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

import lombok.Builder;
import lombok.Data;

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
public class Message {

    private final String title;
    private final String message;

    private final int durationMillis;
    private final MessageTypeEnum messageType;

    @Builder.Default
    private final boolean playTone = false;

    @Builder.Default
    private final boolean discardDuplicates = false;

    public static class MessageBuilder {

        public MessageBuilder titleRaw(String titleIn) {
            title = titleIn;
            return this;
        }

        public MessageBuilder title(String titleKey, Object... arguments) {
            title = l10n(titleKey, arguments);
            return this;
        }

        public MessageBuilder messageRaw(String messageIn) {
            message = messageIn;
            return this;
        }

        public MessageBuilder message(String messageKey, Object... arguments) {
            message = l10n(messageKey, arguments);
            return this;
        }
    }
}
