/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.downloader.hosts;

import java.util.List;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public interface IHostResolver {

    String getId();

    String getDisplayName();

    boolean isEnabled(HostResolverContext context);

    boolean canHandle(String url);

    List<ResolvedFile> resolve(String url, HostResolverContext context) throws HostResolverException;

}
