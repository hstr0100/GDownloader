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

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ArchiveUtils {

    private static final int BUFFER_SIZE = 4096;

    public static void inflateZip(File file, Path destDir, boolean removeRoot, DoubleConsumer progressCallback) throws IOException {
        if (Files.notExists(destDir)) {
            Files.createDirectories(destDir);
        }

        int totalEntries = countEntries(file);

        try (
            ZipInputStream zipIn = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            String topDirectoryName = null;

            int extractedEntries = 0;

            while ((entry = zipIn.getNextEntry()) != null) {
                if (topDirectoryName == null) {
                    topDirectoryName = getTopDirectoryName(entry.getName());

                    if (topDirectoryName == null) {
                        extractEntry(zipIn, entry.getName(), destDir);
                    }
                } else {
                    String entryName = entry.getName();
                    entryName = entryName.replace("\\", "/");

                    if (entryName.startsWith(topDirectoryName + "/") && removeRoot) {
                        entryName = entryName.substring(topDirectoryName.length() + 1);
                    }

                    extractEntry(zipIn, entryName, destDir);
                }

                zipIn.closeEntry();

                extractedEntries++;
                double progress = (double)extractedEntries / totalEntries * 100;
                progressCallback.accept(Math.clamp(progress, 0d, 100d));
            }
        }
    }

    private static int countEntries(File file) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            return zipFile.size();
        }
    }

    @Nullable
    private static String getTopDirectoryName(String entryName) {
        int separatorIndex = entryName.indexOf('/');
        if (separatorIndex != -1) {
            return entryName.substring(0, separatorIndex);
        }

        return null;
    }

    private static void extractEntry(InputStream zipIn, String entryName, Path outputDir) throws IOException {
        entryName = entryName.replace("\\", "/");

        Path outFile = outputDir.resolve(entryName).normalize();
        if (!outFile.startsWith(outputDir)) {
            throw new IOException("Zip entry is outside of the output dir: " + entryName);
        }

        if (entryName.endsWith("/")) {
            Files.createDirectories(outFile);
        } else {
            Path parentDir = outFile.getParent();
            if (parentDir != null && Files.notExists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.copy(zipIn, outFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deflateToZip(File fileToCompress, Path outputZipPath) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(
            new FileOutputStream(outputZipPath.toFile()));
             FileInputStream fis = new FileInputStream(fileToCompress)) {

            ZipEntry zipEntry = new ZipEntry(fileToCompress.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[BUFFER_SIZE];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }

            zipOut.closeEntry();
        }
    }
}
