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
package net.brlns.gdownloader.server.result;

import jakarta.annotation.Nullable;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum ResultEnum {
    // Server
    SUCCESS("success"),
    FAILED("failed"),
    UNHANDLED("unhandled"),
    // Client
    TIMEOUT("timeout"),
    NOT_RUNNING("not-running"),
    IO_ERROR("io-error");

    private final String id;

    private ResultEnum(String idIn) {
        id = idIn;
    }

    @Nullable
    public static ResultEnum fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }

        for (ResultEnum result : values()) {
            if (result.getId().equals(id)) {
                return result;
            }
        }

        return null;
    }
}
