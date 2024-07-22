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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ArchiveUtils{

    public static void inflateZip(File file, Path destDir, boolean removeRoot) throws IOException{
        if(Files.notExists(destDir)){
            Files.createDirectories(destDir);
        }

        try(ZipInputStream zipIn = new ZipInputStream(new FileInputStream(file))){
            ZipEntry entry;
            String topDirectoryName = null;

            while((entry = zipIn.getNextEntry()) != null){
                if(topDirectoryName == null){
                    topDirectoryName = getTopDirectoryName(entry.getName());

                    if(topDirectoryName == null){
                        extractEntry(zipIn, entry.getName(), destDir);
                    }
                }else{
                    String entryName = entry.getName();
                    entryName = entryName.replace("\\", "/");

                    if(entryName.startsWith(topDirectoryName + "/") && removeRoot){
                        entryName = entryName.substring(topDirectoryName.length() + 1);
                    }

                    extractEntry(zipIn, entryName, destDir);
                }

                zipIn.closeEntry();
            }
        }
    }

    @Nullable
    private static String getTopDirectoryName(String entryName){
        int separatorIndex = entryName.indexOf('/');
        if(separatorIndex != -1){
            return entryName.substring(0, separatorIndex);
        }

        return null;
    }

    private static void extractEntry(InputStream zipIn, String entryName, Path outputDir) throws IOException{
        entryName = entryName.replace("\\", "/");

        Path outFile = outputDir.resolve(entryName);
        if(entryName.endsWith("/")){
            Files.createDirectories(outFile);
        }else{
            Path parentDir = outFile.getParent();
            if(parentDir != null && Files.notExists(parentDir)){
                Files.createDirectories(parentDir);
            }

            Files.copy(zipIn, outFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
