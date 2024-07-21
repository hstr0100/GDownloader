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

import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum ArchVersionEnum{
    MAC_X86("yt-dlp_macos_legacy", null, OS.MAC),
    MAC_X64("yt-dlp_macos", null, OS.MAC),
    WINDOWS_X86("yt-dlp_x86.exe", null, OS.WINDOWS),
    WINDOWS_X64("yt-dlp.exe", "ffmpeg.exe", OS.WINDOWS),
    LINUX_X64("yt-dlp_linux", null, OS.LINUX),//You're on your own for ffmpeg buddy @TODO: add binary
    LINUX_ARM("yt-dlp_linux_armv7l", null, OS.LINUX),
    LINUX_ARM64("yt-dlp_linux_aarch64", null, OS.LINUX);

    private final String ytDlpBinary;
    private final String ffmpegBinary;
    private final OS os;

    private ArchVersionEnum(String ytDlpBinaryIn, String ffmpegBinaryIn, OS osIn){
        ytDlpBinary = ytDlpBinaryIn;
        ffmpegBinary = ffmpegBinaryIn;
        os = osIn;
    }

    public static enum OS{
        WINDOWS,
        LINUX,
        MAC;
    }

}
