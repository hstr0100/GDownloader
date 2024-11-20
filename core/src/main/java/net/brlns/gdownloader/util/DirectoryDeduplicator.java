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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DirectoryDeduplicator {

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

            deleteEmptyDirectories(directory);

            log.info("Deduplication complete {}", directory);
        } catch (Exception e) {
            log.error("Deduplication failed", e);
        }
    }

    /**
     * Traverse the directory, compute SHA-256 for each file, and delete duplicates.
     */
    private static void traverseDirectory(File directory, Set<String> fileHashes) throws IOException, NoSuchAlgorithmException {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        // Files go first
        for (File file : files) {
            if (file.isFile()) {
                String fileHash = getFileHash(file);
                if (fileHashes.contains(fileHash)) {
                    log.info("Duplicate file found, deleting: {}", file.getAbsolutePath());
                    file.delete();
                } else {
                    fileHashes.add(fileHash);
                }
            }
        }

        // Directories go last, prioritizing files in the upper directories
        for (File file : files) {
            if (file.isDirectory()) {
                traverseDirectory(file, fileHashes);
            }
        }
    }

    /**
     * Deletes empty directories after deduplication.
     */
    private static void deleteEmptyDirectories(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files != null && files.length == 0) {
            log.info("Deleting empty directory: {}", directory.getAbsolutePath());
            directory.delete();
        } else if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteEmptyDirectories(file);
                }
            }

            files = directory.listFiles();
            if (files != null && files.length == 0) {
                log.info("Deleting empty directory: {}", directory.getAbsolutePath());
                directory.delete();
            }
        }
    }

    /**
     * Calculates the SHA-256 hash of a file.
     */
    private static String getFileHash(File file) throws IOException, NoSuchAlgorithmException {
        String result = HASH_CACHE.get(file);

        if (result != null) {
            //if (log.isDebugEnabled()) {
            //    log.debug("Hash cache hit for {}", file);
            //}

            return result;
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        result = bytesToHex(hashBytes);
        HASH_CACHE.put(file, result);

        return result;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }
}
