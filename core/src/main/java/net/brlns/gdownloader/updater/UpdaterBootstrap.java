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

import jakarta.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.updater.git.AbstractGitUpdater;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.LockUtils;
import net.brlns.gdownloader.util.Version;

import static net.brlns.gdownloader.updater.UpdaterBootstrap.PackageType.*;
import static net.brlns.gdownloader.util.DirectoryUtils.*;
import static net.brlns.gdownloader.util.LockUtils.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * Modifications to this class should be made with caution, as it may affect compatibility with clients running older versions.
 *
 * If addressing update-related issues is necessary, please consider handling them through the {@link net.brlns.gdownloader.updater.git.SelfUpdater} class
 * whenever possible, rather than modifying this class directly. For example, you can modify and reconstruct the update ZIP
 * to ensure it remains compatible with this updater.
 *
 * As of 2025-04-26, this class now also handles .jar and .AppImage updates, while retaining full compatibility with all previous versions.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class UpdaterBootstrap {

    public static final String PREFIX = "gdownloader_ota_runtime_";

    public static void tryOta(String[] args, boolean fromOta) {
        Path workDir = GDownloader.getWorkDirectory().toPath();

        String version = Version.VERSION;
        if (version == null) {
            log.error("OTA not available, could not determine version number");
            return;
        }

        try {
            LockUtils.renameLockIfExists("ota_lock.txt", "ota.lock");
        } catch (Exception e) {
            log.error("Failed to migrate old lock file, cause: {}, ignoring...", e.getMessage());
        }

        PackageType packageType = determinePackageType();
        if (packageType == null) {
            log.error("Failed to determine package type for udpater");
            return;
        }

        handleOta(args, fromOta, workDir, version, packageType);
    }

    @Nullable
    private static PackageType determinePackageType() {
        if (GDownloader.isFromJpackage()) {
            return PackageType.JPACKAGE;
        } else if (GDownloader.isFromAppImage()) {
            return PackageType.APPIMAGE;
        } else if (GDownloader.isFromJar()) {
            return PackageType.JAR;
        }

        // This is odd
        return null;
    }

    private static void handleOta(String[] args, boolean fromOta, Path workDir,
        String version, PackageType packageType) {

        Path updatePath = Paths.get(workDir.toString(), packageType.getOtaFileName());

        if (!Files.exists(updatePath)) {
            handleExistingRuntime(args, fromOta, workDir, version, packageType);
        } else {
            handleUpdateFile(args, updatePath, workDir, version, packageType);
        }
    }

    private static void handleExistingRuntime(String[] args, boolean fromOta, Path workDir,
        String version, PackageType packageType) {

        Path runtimePath = getNewestDirectoryEntry(PREFIX, workDir);

        String launcherCommand = getLauncherCommand(packageType);
        if (fromOta || launcherCommand != null && launcherCommand.contains(PREFIX)) {
            log.info("Current running image is from OTA {} v{}", runtimePath, version);
            return;
        } else {
            log.info("No OTA file found, trying to locate current runtime");
        }

        if (!Files.exists(runtimePath)) {
            log.info("No OTA runtimes found, running current version");
            return;
        }

        // This is a backwards-compatible check
        if (!diskLockExistsAndIsNewer(workDir, version)) {
            log.info("OTA runtime on disk is not newer, staying in current version (v{})", version);
            return;
        }

        List<String> executableArgs = getExecutableCommandIfAvailable(runtimePath);
        if (executableArgs == null) {
            log.info("OTA arguments could not be determined, staying in current version (v{})", version);
            return;
        }

        launchUpdatedInstance(executableArgs, launcherCommand, args);
    }

    private static List<String> getExecutableCommandIfAvailable(Path runtimePath) {
        PackageType inferredPackageType = null;
        File binary = null;
        for (PackageType discoveredPackageType : PackageType.values()) {
            String binaryName = discoveredPackageType.getBinaryFileName();
            if (binaryName == null) {
                continue;
            }

            File foundFile = new File(runtimePath.toFile(), binaryName);
            if (foundFile.exists()) {
                if (!foundFile.canExecute() && discoveredPackageType != JAR) {
                    log.error("Cannot execute updated binary. Skipping...");
                    continue;
                }

                inferredPackageType = discoveredPackageType;
                binary = foundFile;
                break;
            }
        }

        if (inferredPackageType != null) {
            return getExecutableArgs(inferredPackageType, binary);
        }

        return null;
    }

    private static List<String> getExecutableArgs(PackageType packageType, File binary) {
        return switch (packageType) {
            case JAR ->
                getJarLaunchCommand(binary);
            default ->
                List.of(binary.getAbsolutePath());
        };
    }

    @Nullable
    private static String getLauncherCommand(PackageType packageType) {
        return switch (packageType) {
            case JPACKAGE ->
                getJpackageLauncher();
            case APPIMAGE ->
                getAppImageLauncher();
            case JAR -> {
                // Here we're just passing the raw jar as the launcher as this is correctly handled by newer clients.
                // If for some bizarre reason this class launches an older client, that client is gonna implode itself in confusion.
                File launcherFile = getJarLocation();
                yield launcherFile != null ? launcherFile.getAbsolutePath() : null;
            }
            default ->
                throw new IllegalArgumentException();
        };
    }

    private static void handleUpdateFile(String[] args, Path updatePath, Path workDir,
        String version, PackageType packageType) {
        try {
            if (diskLockExistsAndIsNewer(workDir, version)) {
                Path newEntry = createNewDirectoryEntry(PREFIX, workDir);
                log.info("Next directory: {}", newEntry);

                switch (packageType) {
                    case JPACKAGE ->
                        extractZipPackage(workDir, updatePath, newEntry);
                    default ->
                        moveUpdateFile(updatePath, newEntry.resolve(updatePath.getFileName()));
                }

                AbstractGitUpdater.makeExecutable(newEntry);

                log.info("Removing older entries");
                deleteOlderDirectoryEntries(PREFIX, workDir, 2);

                if (Files.exists(updatePath)) {
                    log.info("Removing OTA file");
                    Files.delete(updatePath);
                }

                log.info("OTA complete, restarting");
                tryOta(args, false);
            } else {
                log.error("Disk version is older or the same, deleting OTA update and remaining in current version (v{})", version);
                Files.delete(updatePath);
            }
        } catch (IOException e) {
            log.error("Cannot proceed, IO error with OTA file {}", updatePath, e);
        }
    }

    private static void extractZipPackage(Path workDir, Path updatePath, Path destDir) throws IOException {
        Path zipOutputPath = Paths.get(workDir.toString(), "tmp_ota_zip");
        log.info("Zip output path: {}", zipOutputPath);

        ArchiveUtils.inflateZip(updatePath.toFile(), zipOutputPath, false, (progress) -> {
            log.info("Unpack progress: {}%", progress);
        });

        moveUpdateFile(zipOutputPath, destDir);
    }

    private static void moveUpdateFile(Path updatePath, Path destDir) throws IOException {
        log.info("Trying to perform OTA update");
        Files.move(updatePath, destDir, StandardCopyOption.REPLACE_EXISTING);
        log.info("OTA file copied");
    }

    private static void launchUpdatedInstance(List<String> launchArguments, String originalLauncher, String[] args) {
        ProcessArguments arguments = new ProcessArguments(
            launchArguments,
            "--from-ota");

        if (originalLauncher != null) {
            arguments.add("--launcher", originalLauncher);
        }

        // Older portable versions already have a broken updater,
        // so introducing this change should not affect anything.
        if (GDownloader.isPortable()) {
            arguments.add("--portable");
        }

        arguments.addAll(args);
        log.info("Launching {}", arguments);

        try {// Attempt to hand it off to the new version
            ProcessBuilder processBuilder = new ProcessBuilder(arguments);
            processBuilder.redirectErrorStream(true);// TODO: low-prio: look into why all of the output is coming from the error stream.

            // It took quite some brain cycles to figure out why the portable versions were failing to launch updates.
            // Turns out we need to get rid of this conflicting env variable.
            processBuilder.environment().remove("_JPACKAGE_LAUNCHER");

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Output: {}", line);

                    if (line.contains("Starting...")) {
                        log.info("Launched successfully, handing off");
                        System.exit(0);
                    }
                }
            } catch (Exception e) {
                log.error("Input stream exception", e);
            }

            int exitCode = process.waitFor();
            log.error("Cannot restart for update, process exited with code: {}", exitCode);
        } catch (IOException | InterruptedException e) {
            log.error("Cannot restart for update", e);
        }
    }

    @Nullable
    public static String getJpackageLauncher() {
        return System.getProperty("jpackage.app-path");
    }

    @Nullable
    public static String getAppImageLauncher() {
        return System.getenv("APPIMAGE");
    }

    @Nullable
    protected static List<String> getJarLauncher() {
        File jarLocation = getJarLocation();
        if (jarLocation != null) {
            return getJarLaunchCommand(jarLocation);
        }

        return null;
    }

    @Nullable
    public static List<String> getJarLaunchCommand(File jarLocation) {
        if (jarLocation == null) {
            log.error("Cannot create JAR launch command: JAR location is null");
            return null;
        }

        String javaHome = System.getProperty("java.home");

        if (nullOrEmpty(javaHome) || GDownloader.isFromAppImage()) {
            javaHome = System.getenv("JAVA_HOME");
        }

        if (notNullOrEmpty(javaHome)) {
            Path javaHomePath = Paths.get(javaHome);
            Path javaBinaryPath = javaHomePath.resolve("bin")
                .resolve(FileUtils.getBinaryName("java"));

            String jarString = jarLocation.getAbsolutePath();

            return List.of(javaBinaryPath.toString(), "-jar", jarString);
        } else {
            log.error("JAVA_HOME is not set. Cannot determine relaunch command for .jar.");
        }

        return null;
    }

    @Nullable
    public static File getJarLocation() {
        try {
            URI jarPath = GDownloader.class.getProtectionDomain().getCodeSource().getLocation().toURI();

            if (jarPath.toString().endsWith(".jar")) {
                return new File(jarPath);
            }
        } catch (URISyntaxException e) {
            // Ignore
        }

        return null;
    }

    @Getter
    @RequiredArgsConstructor
    protected static enum PackageType {
        JPACKAGE("gdownloader_ota.zip"),
        APPIMAGE(GDownloader.REGISTRY_APP_NAME.toLowerCase() + "_latest.AppImage"),
        JAR(GDownloader.REGISTRY_APP_NAME.toLowerCase() + "_latest.jar");

        private final String otaFileName;

        @Nullable
        public String getBinaryFileName() {
            if (this == JPACKAGE) {
                if (GDownloader.isWindows()) {
                    return GDownloader.REGISTRY_APP_NAME + ".exe";
                } else if (GDownloader.isLinux()) {
                    return "bin" + File.separator + GDownloader.REGISTRY_APP_NAME;
                } else {// Macn't
                    return null;
                }
            } else {
                return otaFileName;
            }
        }
    }
}
