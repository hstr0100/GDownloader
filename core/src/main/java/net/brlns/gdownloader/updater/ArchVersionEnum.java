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
package net.brlns.gdownloader.updater;

import java.util.Locale;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@Slf4j
public enum ArchVersionEnum {
    MAC_X86("yt-dlp_macos_legacy", null, null, OS.MAC),
    MAC_X64("yt-dlp_macos", null, null, OS.MAC),
    WINDOWS_X86("yt-dlp_x86.exe", null, null, OS.WINDOWS),
    // Neither Apache Compress nor any java library that I know of supports the -mx=9 option used by FFmpeg's 7zs
    WINDOWS_X64("yt-dlp.exe", "-full_build.zip", "windows_portable_x64.zip", OS.WINDOWS),
    // TODO: Linux ffmpeg setup
    LINUX_X64("yt-dlp_linux", null, "linux_portable_amd64.zip", OS.LINUX),
    LINUX_ARM("yt-dlp_linux_armv7l", null, null, OS.LINUX),
    LINUX_ARM64("yt-dlp_linux_aarch64", null, null, OS.LINUX);

    private final String ytDlpBinary;
    private final String ffmpegBinary;
    private final String selfBinary;

    private final OS os;

    private ArchVersionEnum(
        String ytDlpBinaryIn,
        String ffmpegBinaryIn,
        String selfBinaryIn,
        OS osIn) {

        ytDlpBinary = ytDlpBinaryIn;
        ffmpegBinary = ffmpegBinaryIn;
        selfBinary = selfBinaryIn;

        os = osIn;
    }

    public static ArchVersionEnum getArchVersion() {
        ArchVersionEnum archVersion = null;

        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        switch (arch) {
            case "x86", "i386" -> {
                if (os.contains("mac")) {
                    archVersion = ArchVersionEnum.MAC_X86;
                } else if (os.contains("win")) {
                    archVersion = ArchVersionEnum.WINDOWS_X86;
                }
            }

            case "amd64", "x86_64" -> {
                if (os.contains("nux")) {
                    archVersion = ArchVersionEnum.LINUX_X64;
                } else if (os.contains("mac")) {
                    archVersion = ArchVersionEnum.MAC_X64;
                } else if (os.contains("win")) {
                    archVersion = ArchVersionEnum.WINDOWS_X64;
                }
            }

            case "arm", "aarch32" -> {
                if (os.contains("nux")) {
                    archVersion = ArchVersionEnum.LINUX_ARM;
                }
            }

            case "arm64", "aarch64" -> {
                if (os.contains("nux")) {
                    archVersion = ArchVersionEnum.LINUX_ARM64;
                }
            }

            default -> {
                log.error("Unknown architecture: {}", arch);
            }
        }

        if (archVersion == null) {
            throw new UnsupportedOperationException("Unsupported operating system: " + os + " " + arch);
        }

        return archVersion;
    }

    public static enum OS {
        WINDOWS,
        LINUX,
        MAC;
    }
}
