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
package net.brlns.gdownloader.updater;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@RequiredArgsConstructor
public enum UpdateStatusEnum implements ISettingsEnum {
    CHECKING("enums.update_status.checking"),
    DOWNLOADING("enums.update_status.downloading"),
    UNPACKING("enums.update_status.unpacking"),
    DONE("enums.update_status.done"),
    FAILED("enums.update_status.failed");

    private final String translationKey;
}
