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
package net.brlns.gdownloader.updater;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.Nullable;

import static net.brlns.gdownloader.util.LockUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class UpdaterBootstrap{

    private static final String PREFIX = "gdownloader_ota_runtime_";

    public static void tryOta(String[] args, boolean fromOta){
        Path workDir = GDownloader.getWorkDirectory().toPath();
        String appPath = getAppPath();
        if(appPath == null){
            log.error("Ota not available for platform");
            return;
        }

        String version = getVersion();
        if(version == null){
            log.error("Ota not available, not running from jpackage");
            return;
        }

        Path path = Paths.get(workDir.toString(), "gdownloader_ota.zip");
        if(!Files.exists(path)){
            Path runtimePath = getNewestEntry(workDir);

            String launchCommand = getLaunchCommand();

            if(fromOta || launchCommand != null && launchCommand.contains(PREFIX)){
                log.error("Current running image is from ota {} v{}", runtimePath, version);
                return;
            }else{
                log.error("No ota file found, trying to locate current runtime");
            }

            if(!Files.exists(runtimePath)){
                log.error("Current installed version is the latest");
                return;
            }

            File binary = new File(runtimePath.toFile(), appPath);

            if(!binary.exists() || !binary.canExecute()){
                log.error("Cannot open or execute {}", binary.getAbsolutePath());
                return;
            }

            List<String> arguments = new ArrayList<>();
            arguments.add(binary.getAbsolutePath());
            arguments.add("--from-ota");

            if(launchCommand != null){
                arguments.add("--launcher");
                arguments.add(launchCommand);
            }

            arguments.addAll(Arrays.asList(args));

            log.info("Launching {}", arguments);

            try{//Hand it off to the new version
                ProcessBuilder processBuilder = new ProcessBuilder(arguments.stream().toArray(String[]::new));
                processBuilder.start();

                log.info("Launched successfully, handing off");
                System.exit(0);
            }catch(IOException e){
                log.error("Cannot restart for update {}", e.getLocalizedMessage());
            }
        }else{
            try{
                File lock = new File(workDir.toString(), "ota_lock.txt");

                if(lockExists(lock)){//TODO check if the lock confirms the version on disk is actually newer
                    Path zipOutputPath = Paths.get(workDir.toString(), "tmp_ota_zip");
                    log.info("Zip out path {}", zipOutputPath);

                    ArchiveUtils.inflateZip(path.toFile(), zipOutputPath, false);

                    log.info("Trying to perform ota update");

                    Path newEntry = createNewEntry(workDir);
                    log.info("Next directory: {}", newEntry);

                    Files.move(zipOutputPath, newEntry, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Directory copied");

                    AbstractGitUpdater.makeExecutable(newEntry);

                    log.info("Removing older entries");
                    deleteOlderEntries(workDir);

                    log.info("Removing ota file");
                    Files.delete(path);

                    log.info("Ota complete, restarting");
                    tryOta(args, false);
                }else{
                    log.error("Lock file does not exist, we are up to date");
                }
            }catch(IOException e){
                log.error("Cannot procceed, IO error with ota file {} {}", path, e.getMessage());
            }
        }
    }

    @Nullable
    private static String getVersion(){
        return System.getProperty("jpackage.app-version");
    }

    @Nullable
    private static String getLaunchCommand(){
        return System.getProperty("jpackage.app-path");
    }

    @Nullable
    private static String getAppPath(){
        if(GDownloader.isWindows()){
            return GDownloader.REGISTRY_APP_NAME + ".exe";
        }else if(GDownloader.isLinux()){
            return "bin" + File.separator + GDownloader.REGISTRY_APP_NAME;
        }else{//Macn't
            return null;
        }
    }

    /**
     * Shenanigans to deal with heavily fail-prone filesystem operations on Windows
     */
    private static int getHighest(Path workDir){
        List<String> directoryNames = getDirectoryNames(workDir);
        if(directoryNames.isEmpty()){
            return 0;
        }

        return directoryNames.stream()
            .mapToInt(UpdaterBootstrap::extractNumber)
            .max()
            .orElse(0);
    }

    private static Path getNewestEntry(Path workDir){
        int highest = getHighest(workDir);

        return Paths.get(workDir.toString(), PREFIX + highest);
    }

    private static void deleteOlderEntries(Path workDir){
        List<String> directoryNames = getDirectoryNames(workDir);
        directoryNames.sort(Comparator.comparingInt(UpdaterBootstrap::extractNumber));

        int highest = getHighest(workDir);

        for(int i = 0; i < directoryNames.size() - 2; i++){
            int number = extractNumber(directoryNames.get(i));
            if(number < highest){
                GDownloader.deleteRecursively(
                    Paths.get(workDir.toString(), directoryNames.get(i)));
            }
        }
    }

    private static Path createNewEntry(Path workDir) throws IOException{
        int highest = getHighest(workDir);

        return Files.createDirectory(Paths.get(workDir.toString(), PREFIX + (highest + 1)));
    }

    private static List<String> getDirectoryNames(Path workDir){
        List<String> directoryNames = new ArrayList<>();

        File dir = workDir.toFile();
        if(dir.isDirectory()){
            for(File file : dir.listFiles()){
                if(file.isDirectory() && file.getName().startsWith(PREFIX)){
                    directoryNames.add(file.getName());
                }
            }
        }

        return directoryNames;
    }

    private static int extractNumber(String directoryName){
        return Integer.parseInt(directoryName.replaceAll("\\D+", ""));
    }

}
