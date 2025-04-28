/*
 * Copyright (C) 2024 hstr0100
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

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public enum VideoContainerEnum implements ISettingsEnum, IContainerEnum {
    DEFAULT,
    MP4,
    MKV,
    M4V,
    WEBM,
    AVI,
    FLV,
    MOV,
    GIF;

    @Override
    public String getValue() {
        if (isDefault()) {
            return MP4.getValue();
        }

        return name().toLowerCase();
    }

    @Override
    public String getTranslationKey() {
        return "";
    }

    @Override
    public String getDisplayName() {
        if (isDefault()) {
            return l10n("enums.containers.video.default");
        }

        return name().toLowerCase();
    }

    public boolean isDefault() {
        return this == DEFAULT;
    }

    @Override
    public String getMimeTypePrefix() {
        return "video";
    }

    public static boolean isGif(@NonNull Path path) {
        return path.getFileName().endsWith("." + GIF.getValue());
    }

    public static boolean isFileType(@NonNull Path path) {
        return isFileType(path.toFile());
    }

    public static boolean isFileType(@NonNull File file) {
        return Arrays.stream(values())
            .filter((container) -> !container.isDefault() && container != GIF)
            .anyMatch((container) -> file.getName()
            .endsWith("." + container.getValue()));
    }

    public static VideoContainerEnum[] YT_DLP_CONTAINERS;

    public static VideoContainerEnum[] getYtDlpContainers() {
        if (YT_DLP_CONTAINERS != null) {
            return YT_DLP_CONTAINERS;
        }

        return YT_DLP_CONTAINERS = Arrays.stream(values())
            .filter(c -> !c.isDefault() && c != M4V)
            .toArray(VideoContainerEnum[]::new);
    }
}
