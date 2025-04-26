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
package net.brlns.gdownloader.util;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class Version {

    @Getter
    @Nullable
    public static final String VERSION;

    static {
        String version = System.getProperty("jpackage.app-version");

        if (version == null) {
            try (
                InputStream is = Version.class.getResourceAsStream("/version.properties")) {

                Properties props = new Properties();
                props.load(is);

                version = props.getProperty("version");
            } catch (IOException | NullPointerException e) {
                log.info("Failed to read version file", e);
            }
        }

        VERSION = version;
    }

    private Version() {
    }
}
