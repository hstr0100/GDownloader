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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.collection.LRUCache;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DirectoryDeduplicator {

    private static final int BUFFER_SIZE = 8192;

    // We're pretty light-weight on memory so far, even while using pure Java collections.
    // We're safe to push this one a bit.
    private static final LRUCache<File, String> HASH_CACHE = new LRUCache<>(2000);

    /**
     * Deduplicates the specified directory using SHA-256.
     *
     * Deletes files with duplicate content and removes empty directories.
     */
    public static void deduplicateDirectory(File directory) {
        assert directory.isDirectory();

        if (!directory.isDirectory()) {
            log.error("The provided path is not a valid directory.");
            return;
        }

        log.info("Deduplicating {}", directory);

        try {
            Set<String> fileHashes = new HashSet<>();

            traverseDirectory(directory, fileHashes);

            log.info("Deduplication complete {}", directory);
        } catch (Exception e) {
            log.error("Deduplication failed", e);
        }
    }

    /**
     * Traverse the directory, compute SHA-256 for each file, and delete duplicates.
     */
    private static boolean traverseDirectory(File directory, Set<String> fileHashes) throws IOException, NoSuchAlgorithmException {
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }

        boolean isDirectoryEmpty = true;

        // Files go first
        for (File file : files) {
            if (file.isFile()) {
                isDirectoryEmpty = false;
                String fileHash = getFileHash(file);
                if (fileHashes.contains(fileHash)) {
                    log.info("Duplicate file found, deleting: {}", file.getAbsolutePath());
                    file.delete();
                } else {
                    fileHashes.add(fileHash);
                }
            }
        }

        // Directories go next
        for (File file : files) {
            if (file.isDirectory()) {
                boolean subDirEmpty = traverseDirectory(file, fileHashes);
                if (subDirEmpty) {
                    log.info("Deleting empty directory: {}", file.getAbsolutePath());
                    file.delete();
                } else {
                    isDirectoryEmpty = false;
                }
            }
        }

        return isDirectoryEmpty;
    }

    /**
     * Calculates the SHA-256 hash of a file.
     */
    private static String getFileHash(File file) throws RuntimeException {
        return HASH_CACHE.computeIfAbsent(file, (key) -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                // Use a buffered reader with a generous buffer size to avoid some nasty I/O trashing here
                try (BufferedInputStream bis
                    = new BufferedInputStream(new FileInputStream(key))) {
                    byte[] byteArray = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = bis.read(byteArray)) != -1) {
                        digest.update(byteArray, 0, bytesRead);
                    }
                }

                byte[] hashBytes = digest.digest();
                return StringUtils.bytesToHex(hashBytes);
            } catch (NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
