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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import net.brlns.gdownloader.server.AppServer;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "id",
    defaultImpl = UnknownResult.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StatusResult.class, name = StatusResult.ID),
    @JsonSubTypes.Type(value = UnknownResult.class, name = UnknownResult.ID)
})
@Data
public abstract class AbstractResult {

    private final int protocol = AppServer.PROTOCOL_VERSION;

    private final String id;

}
