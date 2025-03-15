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
package net.brlns.gdownloader.updater;

import jakarta.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 *
 * yt-dlp's aarch64 binary does not include curl-cffi, rendering browser impersonation inoperable.
 * Similarly, gallery-dl does not provide an aarch64 or armv7/v6 binary.
 * For these platforms, we need to fall back to system binaries.
 */
@Slf4j
public final class SystemExecutableLocator {

    @Nullable
    public static File locateExecutable(String executableName) {
        String executable = executableName;
        if (GDownloader.isWindows()) {
            executable += ".exe";
        }

        // Check PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String path : pathEnv.split(File.pathSeparator)) {
                File file = new File(path, executable);
                if (file.exists() && file.canExecute()) {
                    log.info("Found {} executable at PATH: {}", executableName, file);
                    return file;
                }
            }
        }

        // Check common installation directories as fallback
        List<String> commonPaths = Arrays.asList(
            "/usr/local/bin", // Linux/macOS
            "/usr/bin", // Linux/macOS
            System.getProperty("user.home") + "/.local/bin", // Linux/macOS user-specific
            System.getProperty("user.home") + "/bin", // Linux/macOS user-specific
            "C:\\Program Files\\yt-dlp", // Windows
            "C:\\Program Files\\gallery-dl" // Windows
        );

        for (String path : commonPaths) {
            File file = new File(path, executable);
            if (file.exists() && file.canExecute()) {
                log.info("Found {} executable at common directory: {}", executableName, file);
                return file;
            }
        }

        // Welp
        return null;
    }
}
