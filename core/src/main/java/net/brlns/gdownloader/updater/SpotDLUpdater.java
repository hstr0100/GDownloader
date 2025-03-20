/*
 * Copyright (C) 2025 hstr0100
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.util.FileUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SpotDLUpdater extends AbstractGitUpdater {

    private static final String USER = "spotDL";
    private static final String REPO = "spotify-downloader";

    public SpotDLUpdater(GDownloader mainIn) {
        super(mainIn);
    }

    @Override
    protected String getUser() {
        return USER;
    }

    @Override
    protected String getRepo() {
        return REPO;
    }

    @Override
    @Nullable
    public String getBinaryName() {
        return ArchVersionEnum.getArchVersion().getSpotDlBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return GDownloader.isWindows() ? "spotdl.exe" : "spotdl";
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return "spotdl";
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "spotdl.lock";
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        main.getDownloadManager().setExecutablePath(DownloaderIdEnum.SPOTDL, executablePath);
    }

    @Override
    public String getName() {
        return "spotDL";
    }

    @Override
    protected void init() throws Exception {
        setupConfigFile(GDownloader.getWorkDirectory());
    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        File outputFile = super.doDownload(url, workDir);
        File finalFile = new File(workDir, getRuntimeBinaryName());

        outputFile.renameTo(finalFile);

        setupConfigFile(workDir);

        return finalFile;
    }

    public static void setupConfigFile(File workDir) throws IOException {
        if (GDownloader.isWindows()) {
            // Windows: We can't symlink without the proper rights. For some reason probably, or not.
            // instead let's copy the file to C:\Users\<USER>\.spotdl\config.json
            String userHome = System.getProperty("user.home");
            File configDir = new File(userHome, ".spotdl");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File windowsConfigFile = new File(configDir, "config.json");
            if (!windowsConfigFile.exists()) {
                FileUtils.writeResourceToFile("/spotdl.json", windowsConfigFile);
            }
        } else if (GDownloader.isMac() || GDownloader.isLinux()) {
            // Linux/Mac: Create a symlink from ~/.spotdl/config.json
            String userHome = System.getProperty("user.home");
            Path configFilePath = Paths.get(userHome, ".spotdl", "config.json");
            Path symlinkPath = Paths.get(workDir.getAbsolutePath(), "spotdl.json");

            Files.createDirectories(configFilePath.getParent());

            if (!Files.exists(symlinkPath)) {
                if (!Files.exists(configFilePath)) {
                    FileUtils.writeResourceToFile("/spotdl.json", configFilePath.toFile());
                }

                Files.createSymbolicLink(symlinkPath, configFilePath);
            }
        } else {
            log.error("Unsupported operating system for spotDL config management");
        }
    }
}
