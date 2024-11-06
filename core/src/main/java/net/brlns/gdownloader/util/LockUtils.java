/*
 * Copyright (C) 2024 @hstr0100
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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class LockUtils{

    public static String getLockTag(String releaseTag){
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        return String.join("_", releaseTag, os, arch);
    }

    public static void createLock(File lockFile, String content) throws IOException{
        try(FileWriter writer = new FileWriter(lockFile)){
            writer.write(content);
        }
    }

    public static String readLock(File lockFile) throws IOException{
        if(!lockFile.exists()){
            throw new IOException("Lock file " + lockFile + " does not exist");
        }

        return new String(Files.readAllBytes(lockFile.toPath()));
    }

    public static boolean checkLock(File lockFile, String tag) throws IOException{
        if(!lockFile.exists()){
            return false;
        }

        String content = readLock(lockFile);

        return content.equals(tag);
    }

    public static boolean lockExists(File lockFile) throws IOException{
        return lockFile.exists();
    }

    public static boolean removeLock(File lockFile) throws IOException{
        return lockFile.delete();
    }

    public static boolean diskLockExistsAndIsNewer(Path workDir, String version){
        try{
            File lock = new File(workDir.toString(), "ota_lock.txt");

            if(lockExists(lock)){
                String diskVersion = readLock(lock).split("_")[0];
                diskVersion = diskVersion.replace("v", "");
                log.debug("Lock {} version: {}", workDir, diskVersion);

                if(isVersionNewer(version, diskVersion)){
                    log.debug("{} is newer than: {}", diskVersion, version);
                    return true;
                }
            }
        }catch(IOException e){
            log.error("IO error trying to verify lock file", e);
        }

        return false;
    }

    public static boolean isVersionNewer(String currentVersion, String diskVersion){
        String[] currentParts = normalizeVersion(currentVersion).split("\\.");
        String[] diskParts = normalizeVersion(diskVersion).split("\\.");

        int length = Math.max(currentParts.length, diskParts.length);

        for(int i = 0; i < length; i++){
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int diskPart = i < diskParts.length ? parseVersionPart(diskParts[i]) : 0;

            if(currentPart < diskPart){
                return true;// newer
            }else if(currentPart > diskPart){
                return false;// older
            }
        }

        return false;// equal
    }

    private static int parseVersionPart(String part){
        String numericPart = part.split("[^\\d]")[0];
        return numericPart.matches("\\d+") ? Integer.parseInt(numericPart) : 0;
    }

    private static String normalizeVersion(String version){
        return version.replaceAll("[^0-9.]", "");
    }
}
