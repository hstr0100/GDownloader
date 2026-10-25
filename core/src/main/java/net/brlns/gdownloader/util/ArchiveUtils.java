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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.DoubleConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ArchiveUtils {

    private static final int BUFFER_SIZE = 4096;
    private static final int TAR_BLOCK_SIZE = 512;

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

    public static void inflateTarGz(File file, Path destDir, boolean removeRoot, DoubleConsumer progressCallback) throws IOException {
        Path destination = destDir.toAbsolutePath().normalize();
        Files.createDirectories(destination);

        long totalBytes = Math.max(file.length(), 1);

        try (
            FileInputStream fileIn = new FileInputStream(file);
            GZIPInputStream tarIn = new GZIPInputStream(fileIn)) {
            byte[] header = new byte[TAR_BLOCK_SIZE];
            String overrideName = null;
            String topDirectoryName = null;

            while (tarIn.readNBytes(header, 0, TAR_BLOCK_SIZE) == TAR_BLOCK_SIZE && !isEmptyBlock(header)) {
                long size = parseTarSize(header);
                char type = (char)header[156];

                if (type == 'L' || type == 'x') {
                    String content = new String(tarIn.readNBytes((int)size), StandardCharsets.UTF_8);
                    tarIn.skipNBytes(tarPadding(size));

                    overrideName = type == 'L' ? readTarString(content) : readPaxPath(content, overrideName);

                    continue;
                }

                if (type == 'g') {
                    tarIn.skipNBytes(size + tarPadding(size));

                    continue;
                }

                String entryName = overrideName;
                if (entryName == null) {
                    String prefix = readTarString(header, 345, 155);
                    entryName = readTarString(header, 0, 100);

                    if (!prefix.isEmpty()) {
                        entryName = prefix + "/" + entryName;
                    }
                }

                overrideName = null;
                entryName = entryName.replace("\\", "/");

                if (entryName.startsWith("./")) {
                    entryName = entryName.substring(2);
                }

                if (topDirectoryName == null) {
                    topDirectoryName = getTopDirectoryName(entryName);
                }

                if (removeRoot && topDirectoryName != null && entryName.startsWith(topDirectoryName + "/")) {
                    entryName = entryName.substring(topDirectoryName.length() + 1);
                }

                boolean regularFile = type == '0' || type == '\0';

                if (!entryName.isEmpty() && regularFile) {
                    extractEntry(new BoundedInputStream(tarIn, size), entryName, destination);

                    if ((parseTarOctal(header, 100, 8) & 0111) != 0) {
                        destination.resolve(entryName).toFile().setExecutable(true, false);
                    }
                } else {
                    if (!entryName.isEmpty() && type == '5') {
                        extractEntry(InputStream.nullInputStream(), entryName.endsWith("/") ? entryName : entryName + "/", destination);
                    }

                    tarIn.skipNBytes(size);
                }

                tarIn.skipNBytes(tarPadding(size));

                double progress = (double)fileIn.getChannel().position() / totalBytes * 100;
                progressCallback.accept(Math.clamp(progress, 0d, 100d));
            }
        }

        progressCallback.accept(100);
    }

    private static boolean isEmptyBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }

        return true;
    }

    private static long parseTarSize(byte[] header) {
        if ((header[124] & 0x80) == 0) {
            return parseTarOctal(header, 124, 12);
        }

        long size = header[124] & 0x7F;
        for (int i = 125; i < 136; i++) {
            size = (size << 8) | (header[i] & 0xFF);
        }

        return size;
    }

    private static String readPaxPath(String records, String fallback) {
        for (String record : records.split("\n")) {
            int separator = record.indexOf(' ');

            if (separator != -1 && record.startsWith("path=", separator + 1)) {
                return record.substring(separator + 6);
            }
        }

        return fallback;
    }

    private static long parseTarOctal(byte[] buffer, int offset, int length) {
        String value = readTarString(buffer, offset, length).trim();

        return value.isEmpty() ? 0 : Long.parseLong(value, 8);
    }

    private static String readTarString(String value) {
        int end = value.indexOf('\0');

        return end == -1 ? value : value.substring(0, end);
    }

    private static String readTarString(byte[] buffer, int offset, int length) {
        int limit = Math.min(offset + length, buffer.length);
        int end = offset;

        while (end < limit && buffer[end] != 0) {
            end++;
        }

        return new String(buffer, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long tarPadding(long size) {
        return (TAR_BLOCK_SIZE - size % TAR_BLOCK_SIZE) % TAR_BLOCK_SIZE;
    }

    private static final class BoundedInputStream extends FilterInputStream {

        private long remaining;

        private BoundedInputStream(InputStream in, long limit) {
            super(in);

            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }

            int value = super.read();
            if (value != -1) {
                remaining--;
            }

            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }

            int count = super.read(buffer, offset, (int)Math.min(length, remaining));
            if (count > 0) {
                remaining -= count;
            }

            return count;
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

        Path root = outputDir.toAbsolutePath().normalize();
        Path outFile = root.resolve(entryName).normalize();

        if (!outFile.startsWith(root)) {
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
