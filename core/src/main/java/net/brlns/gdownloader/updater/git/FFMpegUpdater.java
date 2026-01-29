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
package net.brlns.gdownloader.updater.git;

import jakarta.annotation.Nullable;
import java.io.File;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.LockUtils;

import static net.brlns.gdownloader.updater.UpdateStatusEnum.UNPACKING;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class FFMpegUpdater extends AbstractGitUpdater {

    private static final String USER = "GyanD";
    private static final String REPO = "codexffmpeg";

    public FFMpegUpdater(GDownloader mainIn) {
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
        return ArchVersionEnum.getDefinitions().getFfmpegBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return "ffmpeg";
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return getRuntimeBinaryName();
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "ffmpeg.lock";
    }

    @Override
    public boolean isEnabled() {
        return GDownloader.isWindows();
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        main.getFfmpegTranscoder().setFfmpegPath(Optional.of(executablePath));
    }

    @Override
    public String getName() {
        return "FFMPEG";
    }

    @Override
    protected void init() throws Exception {
        LockUtils.renameLockIfExists("ffmpeg_lock.txt", getLockFileName());
    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File zipPath = new File(workDir, fileName);
        log.info("Zip path {}", zipPath);

        Path zipOutputPath = Paths.get(workDir.getAbsolutePath(), "tmp_ffmpeg_zip");
        log.info("Zip out path {}", zipOutputPath);

        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Final path {}", outputFile);

        try {
            downloadFile(url, zipPath);

            notifyProgress(UNPACKING, 0);
            ArchiveUtils.inflateZip(zipPath, zipOutputPath, true, (progress) -> {
                notifyProgress(UNPACKING, progress);
            });

            Path sourcePath = zipOutputPath.resolve("bin");
            log.info("Source binary path {}", sourcePath);

            DirectoryUtils.deleteRecursively(outputFile.toPath());
            Files.move(sourcePath, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

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
