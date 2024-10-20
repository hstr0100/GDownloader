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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.Nullable;

import static net.brlns.gdownloader.util.LockUtils.*;

/**
 * Modifications to this class should be made with caution, as it may affect compatibility with clients running older versions.
 *
 * If addressing update-related issues is necessary, please consider handling them through the {@link net.brlns.gdownloader.updater.SelfUpdater} class
 * whenever possible, rather than modifying this class directly. For example, you can modify and reconstruct the update ZIP
 * to ensure it remains compatible with this updater.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class UpdaterBootstrap{

    public static final String PREFIX = "gdownloader_ota_runtime_";

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
                log.info("Current running image is from ota {} v{}", runtimePath, version);
                return;
            }else{
                log.info("No ota file found, trying to locate current runtime");
            }

            if(!Files.exists(runtimePath)){
                log.info("No ota runtimes found, running current version.");
                return;
            }

            //This is a backwards-compatible check
            if(!diskLockExistsAndIsNewer(workDir, version)){
                log.info("Ota runtime on disk is not newer, running current version. (v{})", version);
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

            //Older portable versions already have a broken updater,
            //so introducing this change should not affect anything.
            if(GDownloader.isPortable()){
                arguments.add("--portable");
            }

            arguments.addAll(List.of(args));
            log.info("Launching {}", arguments);

            try{//Attempt to hand it off to the new version
                ProcessBuilder processBuilder = new ProcessBuilder(arguments);
                processBuilder.redirectErrorStream(true);//TODO: low-prio: look into why all of the output is coming from the error stream.

                //It took quite some brain cycles to figure out why the portable versions were failing to launch updates.
                //Turns out we need to get rid of this conflicting env variable.
                processBuilder.environment().remove("_JPACKAGE_LAUNCHER");

                Process process = processBuilder.start();

                try(BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))){
                    String line;
                    while((line = reader.readLine()) != null){
                        log.info("Output: {}", line);

                        if(line.contains("Starting...")){
                            log.info("Launched successfully, handing off");
                            System.exit(0);
                        }
                    }
                }catch(Exception e){
                    log.error("Input stream exception {}", e.getMessage());
                }

                int exitCode = process.waitFor();
                log.error("Cannot restart for update, process exited with code: {}", exitCode);
            }catch(IOException | InterruptedException e){
                log.error("Cannot restart for update {}", e.getLocalizedMessage());
            }
        }else{
            try{
                if(diskLockExistsAndIsNewer(workDir, version)){
                    Path zipOutputPath = Paths.get(workDir.toString(), "tmp_ota_zip");
                    log.info("Zip out path {}", zipOutputPath);

                    ArchiveUtils.inflateZip(path.toFile(), zipOutputPath, false, (progress) -> {
                        log.info("Unpack progress: {}%", progress);
                    });

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
                    log.error("Disk version is older or the same, deleting ota update and remaining in the current version (v{}).", version);
                    Files.delete(path);
                }
            }catch(IOException e){
                log.error("Cannot procceed, IO error with ota file {} {}", path, e.getMessage());
            }
        }
    }

    @Nullable
    protected static String getVersion(){
        return System.getProperty("jpackage.app-version");
    }

    @Nullable
    protected static String getLaunchCommand(){
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

    private static boolean diskLockExistsAndIsNewer(Path workDir, String version){
        try{
            File lock = new File(workDir.toString(), "ota_lock.txt");

            if(lockExists(lock)){
                String diskVersion = readLock(lock).split("_")[0];
                diskVersion = diskVersion.replace("v", "");
                log.info("Lock {} version: {}", workDir, diskVersion);

                if(isVersionNewer(version, diskVersion)){
                    log.info("{} is newer than: {}", diskVersion, version);
                    return true;
                }
            }
        }catch(IOException e){
            log.error("IOException trying to verify ota lock file {}", e.getMessage());
        }

        return false;
    }

    private static int extractNumber(String directoryName){
        return Integer.parseInt(directoryName.replaceAll("\\D+", ""));
    }

    public static boolean isVersionNewer(String currentVersion, String diskVersion){
        String[] currentParts = normalizeVersion(currentVersion).split("\\.");
        String[] diskParts = normalizeVersion(diskVersion).split("\\.");

        int length = Math.max(currentParts.length, diskParts.length);

        for(int i = 0; i < length; i++){
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int diskPart = i < diskParts.length ? parseVersionPart(diskParts[i]) : 0;

            if(currentPart < diskPart){
                return true;//newer
            }else if(currentPart > diskPart){
                return false;//older
            }
        }

        return false;//equal
    }

    private static int parseVersionPart(String part){
        String numericPart = part.split("[^\\d]")[0];
        return numericPart.matches("\\d+") ? Integer.parseInt(numericPart) : 0;
    }

    private static String normalizeVersion(String version){
        return version.replaceAll("[^0-9.]", "");
    }

}
