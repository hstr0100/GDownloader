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
import java.util.Locale;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
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

    public static boolean checkLock(File lockFile, String tag) throws IOException{
        if(!lockFile.exists()){
            return false;
        }

        String content = new String(Files.readAllBytes(lockFile.toPath()));

        return content.equals(tag);
    }

    public static boolean lockExists(File lockFile) throws IOException{
        return lockFile.exists();
    }

    public static boolean removeLock(File lockFile) throws IOException{
        return lockFile.delete();
    }
}
