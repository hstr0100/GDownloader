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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.collection.LRUCache;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class FileUtils {

    private static final LRUCache<File, Optional<String>> MIME_TYPE_CACHE = new LRUCache<>(2000);

    public static final String TMP_FILE_IDENTIFIER = ".gdtmp";

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

    public static Path ensureUniqueFileName(@NonNull Path path) {
        Path directory = (path.getParent() != null) ? path.getParent() : Paths.get("");
        String fileName = path.getFileName().toString();

        String baseName = fileName;
        String extension = "";

        // Extract base name and extension
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && !fileName.startsWith(".")) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        Path uniquePath = path;

        // Append _number until a unique file name is found
        while (Files.exists(uniquePath)) {
            uniquePath = directory.resolve(baseName + "_" + counter + extension);
            counter++;
        }

        return uniquePath;
    }

    private static final ReentrantLock logLock = new ReentrantLock();

    public static void logToFile(File baseDir, String fileName, String text, Object... replacements) {
        FormattingTuple ft = MessageFormatter.arrayFormat(text, replacements);
        String message = ft.getMessage();

        File resolvedFile = baseDir.toPath()
            .resolve(fileName + ".txt").toFile();

        logLock.lock();
        try (FileWriter fw = new FileWriter(resolvedFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            for (String str : message.split(System.lineSeparator())) {
                pw.println(str);
            }
        } catch (IOException e) {
            log.warn("Cannot log to file", e);
        } finally {
            logLock.unlock();
        }
    }

    public static boolean removeLineIfExists(File file, String searchString) throws IOException {
        if (file == null || !file.exists() || searchString == null) {
            return false; // Exit silently
        }

        if (!file.isFile()) {
            if (log.isDebugEnabled()) {
                log.error("Not a regular file: " + file.getAbsolutePath());
            }

            return false;
        }

        List<String> lines = new ArrayList<>();
        boolean lineRemoved = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(searchString)) {
                    lineRemoved = true;
                    continue;
                }

                lines.add(currentLine);
            }
        } catch (IOException e) {
            throw e;
        }

        if (!lineRemoved) {
            return false;
        }

        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }

            throw e;
        }

        if (!file.delete()) {
            throw new IOException("Could not delete original file: " + file.getAbsolutePath());
        }

        if (!tempFile.renameTo(file)) {
            throw new IOException("Could not rename temp file to original filename");
        }

        return lineRemoved;
    }

    public static boolean isFileType(Path path, String extension) {
        return path.getFileName().toString().toLowerCase().endsWith("." + extension);
    }

    public static boolean isFileType(File file, String extension) {
        return file.getName().toLowerCase().endsWith("." + extension.toLowerCase());
    }

    public static boolean isMimeType(Path path, String mimeTypeOrFragment) {
        return isMimeType(path.toFile(), mimeTypeOrFragment);
    }

    public static boolean isMimeType(File file, String mimeTypeOrFragment) {
        Optional<String> probedMimeType = probeMimeType(file);
        if (!probedMimeType.isEmpty()) {
            return probedMimeType.get().contains(mimeTypeOrFragment);
        }

        return false;
    }

    public static Path relativize(Path originalDirectory, Path targetDirectory, Path file) {
        Path relativePath = originalDirectory.relativize(file);

        return targetDirectory.resolve(relativePath);
    }

    public static Path relativize(File originalDirectory, File targetDirectory, Path file) {
        return relativize(originalDirectory.toPath(), targetDirectory.toPath(), file);
    }

    public static String getBinaryName(String name) {
        return GDownloader.isWindows() ? name + ".exe" : name;
    }

    public static File deriveTempFile(@NonNull File inputFile) {
        return deriveTempFile(inputFile, null);
    }

    public static File deriveTempFile(@NonNull File inputFile, @Nullable String extension) {
        String suffix = TMP_FILE_IDENTIFIER + String.format(".%03x", (int)(Math.random() * 0xFFF));

        File outputFile = deriveFile(inputFile, suffix, extension);
        outputFile.deleteOnExit();

        return outputFile;
    }

    public static File deriveFile(@NonNull File inputFile, String suffix) {
        return deriveFile(inputFile, suffix, null);
    }

    public static File deriveFile(@NonNull File inputFileIn, String suffix, @Nullable String extension) {
        String normalizedPath = inputFileIn.getPath().replace('\\', '/');
        File inputFile = new File(normalizedPath);

        String parent = inputFile.getParent();
        if (parent == null) {
            parent = ".";
        }

        String baseName = inputFile.getName();
        int lastDotIndex = baseName.lastIndexOf('.');

        if (lastDotIndex >= 0) {
            String nameWithoutExt = baseName.substring(0, lastDotIndex);
            extension = extension != null ? extension
                : (lastDotIndex < baseName.length() - 1
                ? baseName.substring(lastDotIndex + 1)
                : "");

            return new File(parent, nameWithoutExt + suffix
                + (extension.isEmpty() ? "" : "." + extension));
        } else {
            extension = extension != null ? extension : "";
            return new File(parent, baseName + suffix
                + (extension.isEmpty() ? "" : "." + extension));
        }
    }

    public static String getFilenameWithoutExtension(@NonNull File inputFile) {
        String baseName = inputFile.getName();
        int lastDotIndex = baseName.lastIndexOf('.');

        if (lastDotIndex >= 0) {
            return baseName.substring(0, lastDotIndex);
        }

        return baseName;
    }

    public static boolean isLargerThan(Path path, int bytes) {
        return isLargerThan(path.toFile(), bytes);
    }

    public static boolean isLargerThan(File file, int bytes) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        return file.length() > bytes;
    }

    public static Optional<String> probeMimeType(File file) {
        return MIME_TYPE_CACHE.computeIfAbsent(file, (key) -> {
            try {
                return Optional.ofNullable(
                    Files.probeContentType(file.toPath()));
            } catch (IOException e) {
                log.debug("Unable to probe for file mime type: {}", file, e);
            }

            return Optional.empty();
        });
    }

    public static void moveFileIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void setAllFileTimesTo(Path filePath, @Nullable LocalDateTime localDateTime) {
        if (!Files.exists(filePath) || localDateTime == null) {
            return;
        }

        Instant newInstant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        FileTime newFileTime = FileTime.from(newInstant);

        try {
            Files.setLastModifiedTime(filePath, newFileTime);

            BasicFileAttributeView attributesView = Files.getFileAttributeView(filePath, BasicFileAttributeView.class);
            if (attributesView != null) {
                attributesView.setTimes(newFileTime, newFileTime, newFileTime);
            } else {
                log.error("File attributes not available for {}, cannot set file time.", filePath);
            }
        } catch (IOException e) {
            log.error("An error occurred setting file time: {}", e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.error("Setting file time is not supported by the file system: {}", e.getMessage());
        }
    }

    public static void copyAllFileTimes(Path sourcePath, Path targetPath) {
        if (!Files.exists(sourcePath) || !Files.exists(targetPath)) {
            return;// Exit silently
        }

        try {
            BasicFileAttributes sourceAttributes = Files.readAttributes(sourcePath, BasicFileAttributes.class);
            FileTime lastModifiedTime = sourceAttributes.lastModifiedTime();
            FileTime lastAccessTime = sourceAttributes.lastAccessTime();
            FileTime creationTime = sourceAttributes.creationTime();

            try {
                Files.setLastModifiedTime(targetPath, lastModifiedTime);
                BasicFileAttributeView attributesView = Files.getFileAttributeView(targetPath, BasicFileAttributeView.class);
                if (attributesView != null) {
                    attributesView.setTimes(lastModifiedTime, lastAccessTime, creationTime);
                } else {
                    log.error("File attributes not available for {}, cannot set file time.", targetPath);
                }
            } catch (IOException e) {
                log.error("An error occurred setting file time: {}", e.getMessage());
            } catch (UnsupportedOperationException e) {
                log.error("Setting file time is not supported by the file system: {}", e.getMessage());
            }
        } catch (IOException e) {
            log.error("An error occurred reading file attributes from {}: {}", sourcePath, e.getMessage());
        }
    }
}
