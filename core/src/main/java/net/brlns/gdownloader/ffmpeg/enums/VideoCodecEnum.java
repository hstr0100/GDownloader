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
package net.brlns.gdownloader.ffmpeg.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum VideoCodecEnum {
    NO_CODEC("", ""),
    H264("h264", "H264"),
    H265("h265", "HEVC"),
    VP9("vp9", "VP9"),
    AV1("av1", "AV1");

    private final String name;
    private final String vaapiName;
}
