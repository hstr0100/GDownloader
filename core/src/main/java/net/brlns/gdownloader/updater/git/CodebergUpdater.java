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
package net.brlns.gdownloader.updater.git;

import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class CodebergUpdater extends AbstractGitUpdater {

    public CodebergUpdater(GDownloader main) {
        super(main);
    }

    @Override
    protected String getAPIEndpoint() {
        return String.format("https://codeberg.org/api/v1/repos/%s/%s/releases/latest", getUser(), getRepo());
    }
}
