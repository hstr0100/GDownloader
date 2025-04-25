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
package net.brlns.gdownloader.ui.status;

import javax.swing.*;
import lombok.Getter;
import net.brlns.gdownloader.settings.enums.ISettingsEnum;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.ui.UIUtils.loadIcon;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum StatusIndicatorEnum implements ISettingsEnum {
    NETWORK_OFFLINE(
        "/assets/wireless.png",
        "gui.status.network_offline",
        UIColors.TOAST_WARNING),
    FFMPEG_NOT_FOUND(
        "/assets/toast-error.png",
        "gui.status.ffmpeg_not_detected",
        UIColors.TOAST_ERROR);

    private final String imagePath;
    private final String translationKey;
    private final UIColors color;

    private StatusIndicatorEnum(String imagePathIn, String translationKeyIn, UIColors colorIn) {
        imagePath = imagePathIn;
        translationKey = translationKeyIn;
        color = colorIn;
    }

    public Icon getIcon() {
        return loadIcon(imagePath, color, 16);
    }
}
