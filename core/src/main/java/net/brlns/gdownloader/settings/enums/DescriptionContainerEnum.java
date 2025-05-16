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
package net.brlns.gdownloader.settings.enums;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.NonNull;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public enum DescriptionContainerEnum implements ISettingsEnum, IContainerEnum {
    TXT,
    DESCRIPTION;

    @Override
    public String getValue() {
        return name().toLowerCase();
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return name().toLowerCase();
    }

    @Override
    public String getMimeTypePrefix() {
        return "text";
    }

    public static boolean isFileType(@NonNull Path path) {
        return isFileType(path.toFile());
    }

    public static boolean isFileType(@NonNull File file) {
        return Arrays.stream(values())
            .anyMatch((container) -> file.getName()
            .endsWith("." + container.getValue()));
    }
}
