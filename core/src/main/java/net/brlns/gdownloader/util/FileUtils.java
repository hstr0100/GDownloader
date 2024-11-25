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
package net.brlns.gdownloader.util;

import java.io.*;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class FileUtils {

    @Nullable
    public static File getOrCreate(File dir, String... path) {
        File file = new File(dir, String.join(File.separator, path));

        try {
            Files.createDirectories(file.getParentFile().toPath());

            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            log.error("Cannot create file: {}", file);
            GDownloader.handleException(e);
        }

        return file;
    }

    public static void writeResourceToFile(String resourcePath, File destination) {
        try {
            InputStream inputStream = FileUtils.class.getResourceAsStream(resourcePath);

            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }

            Files.createDirectories(destination.toPath().getParent());

            try (InputStream in = inputStream;
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            log.error("Unable to write resource to file: {}", resourcePath, e);
        }
    }

    private static final Object _logSync = new Object();

    public static void logToFile(File baseDir, String fileName, String text, Object... replacements) {
        FormattingTuple ft = MessageFormatter.arrayFormat(text, replacements);
        String message = ft.getMessage();

        File resolvedFile = baseDir.toPath()
            .resolve(fileName + ".txt").toFile();

        synchronized (_logSync) {
            try (FileWriter fw = new FileWriter(resolvedFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                for (String str : message.split(System.lineSeparator())) {
                    pw.println(str);
                }
            } catch (IOException e) {
                log.warn("Cannot log to file", e);
            }
        }
    }

}
