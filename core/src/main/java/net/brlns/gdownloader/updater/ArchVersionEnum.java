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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@Getter
@AllArgsConstructor
public enum ArchVersionEnum {
    // gallery-dl support is pretty much barely there due to their lack of native binaries.
    // Python installations are brittle and not something we want to mess with.
    MAC_LEGACY(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_macos_legacy")
        .os(OS.MAC)
        .build()),
    MAC_X64(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_macos")
        .spotDlBinary("-darwin")
        .os(OS.MAC)
        .build()),
    MAC_ARM64(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_macos")
        .spotDlBinary("-darwin")
        .os(OS.MAC)
        .build()),
    WINDOWS_X86(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_x86.exe")
        .spotDlBinary("-win32.exe")
        .os(OS.WINDOWS)
        .build()),
    // TODO: https://aka.ms/vs/17/release/vc_redist.x86.exe
    WINDOWS_X64(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp.exe")
        .galleryDlBinary("gallery-dl.exe")
        .spotDlBinary("-win32.exe")
        // Neither Apache Compress nor any java library that I know of supports the -mx=9 option used by FFmpeg's 7zs
        // Hence the need to download their huge zip instead.
        .ffmpegBinary("-full_build.zip")
        .selfBinary("windows_portable_x64.zip")
        .os(OS.WINDOWS)
        .build()),
    // TODO: Linux ffmpeg setup
    LINUX_X64(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_linux")
        .galleryDlBinary("gallery-dl.bin")
        .spotDlBinary("-linux")
        .selfBinary("linux_portable_amd64.zip")
        .selfAppImageBinary("x86_64.AppImage")
        .os(OS.LINUX)
        .build()),
    LINUX_ARM(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_linux_armv7l")
        .os(OS.LINUX)
        .build()),
    LINUX_ARM64(UpdateDefinitions.builder()
        .ytDlpBinary("yt-dlp_linux_aarch64")
        // As of 2025-04-26, updates for other architectures are only supported by AppImage
        //.selfBinary("linux_portable_arm64.zip")
        .selfAppImageBinary("aarch64.AppImage")
        .os(OS.LINUX)
        .build());

    private final UpdateDefinitions updateDefinitions;

    public static UpdateDefinitions getDefinitions() {
        ArchVersionEnum archVersion = getArchVersion();

        return archVersion.getUpdateDefinitions();
    }

    public static ArchVersionEnum getArchVersion() {
        ArchVersionEnum archVersion = null;

        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        switch (arch) {
            case "x86", "i386" -> {
                if (os.contains("win")) {
                    archVersion = ArchVersionEnum.WINDOWS_X86;
                }
            }

            case "amd64", "x86_64" -> {
                if (os.contains("nux")) {
                    archVersion = ArchVersionEnum.LINUX_X64;
                } else if (os.contains("mac")) {
                    if (isMacOsOlderThanBigSur()) {
                        archVersion = ArchVersionEnum.MAC_LEGACY;
                    } else {
                        archVersion = ArchVersionEnum.MAC_X64;
                    }
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
                if (os.contains("mac")) {
                    archVersion = ArchVersionEnum.MAC_ARM64;
                } else if (os.contains("nux")) {
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

    private static boolean isMacOsOlderThanBigSur() {
        String os = System.getProperty("os.name");
        String version = System.getProperty("os.version");

        if (!os.contains("mac")) {
            return false;
        }

        try {
            String[] versionParts = version.split("\\.");
            if (versionParts.length > 0) {
                int majorVersion = Integer.parseInt(versionParts[0]);

                return majorVersion < 11;
            } else {
                log.error("Could not parse macOS version string: {}", version);

                return false;
            }
        } catch (NumberFormatException e) {
            log.error("Error parsing macOS major version from string: {}", version, e);

            return false;
        }
    }

    @Getter
    @Builder
    public static class UpdateDefinitions {

        private final String ytDlpBinary;
        private final String galleryDlBinary;
        private final String spotDlBinary;
        private final String ffmpegBinary;
        private final String selfBinary;
        private final String selfAppImageBinary;

        private final OS os;
    }
}
