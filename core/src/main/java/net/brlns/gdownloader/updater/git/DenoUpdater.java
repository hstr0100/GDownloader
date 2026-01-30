/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.updater.git;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.YtDlpDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.DirectoryUtils;

import static net.brlns.gdownloader.GDownloader.isWindows;
import static net.brlns.gdownloader.updater.UpdateStatusEnum.UNPACKING;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class DenoUpdater extends AbstractGitUpdater {

    private static final String USER = "denoland";
    private static final String REPO = "deno";

    public DenoUpdater(GDownloader mainIn) {
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
    public String getGitHubBinaryName() {
        return ArchVersionEnum.getDefinitions().getDenoBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return isWindows() ? "deno.exe" : "deno";
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return getRuntimeBinaryName();
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "deno.lock";
    }

    @Override
    public boolean isEnabled() {
        return getGitHubBinaryName() != null;
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        // TODO: generics
        YtDlpDownloader ytDlpDownloader = (YtDlpDownloader)main.getDownloadManager().getDownloader(DownloaderIdEnum.YT_DLP);
        if (ytDlpDownloader != null) {
            ytDlpDownloader.setDenoPath(Optional.of(executablePath));
        }
    }

    @Override
    public String getName() {
        return "Deno JS Runtime";
    }

    @Override
    protected void init() throws Exception {

    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File zipPath = new File(workDir, fileName);
        log.info("Zip path {}", zipPath);

        Path zipOutputPath = Paths.get(workDir.getAbsolutePath(), "tmp_deno_zip");
        log.info("Zip out path {}", zipOutputPath);

        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Final path {}", outputFile);

        try {
            downloadFile(url, zipPath);

            notifyProgress(UNPACKING, 0);
            ArchiveUtils.inflateZip(zipPath, zipOutputPath, true, (progress) -> {
                notifyProgress(UNPACKING, progress);
            });

            String binaryName = getRuntimeBinaryName();
            if (binaryName == null) {
                throw new IllegalStateException("Runtime binary name cannot be null");
            }

            Path sourcePath = zipOutputPath.resolve(binaryName);
            log.info("Source binary path {}", sourcePath);

            if (!Files.exists(sourcePath)) {
                throw new FileNotFoundException("Could not find " + binaryName + " inside extracted zip at " + sourcePath);
            }

            Files.move(sourcePath, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (!isWindows()) {
                outputFile.setExecutable(true);
            }

            notifyProgress(UNPACKING, 100);
        } finally {
            if (zipPath.exists()) {
                zipPath.delete();
            }

            DirectoryUtils.deleteRecursively(zipOutputPath);
        }

        return outputFile;
    }

}
