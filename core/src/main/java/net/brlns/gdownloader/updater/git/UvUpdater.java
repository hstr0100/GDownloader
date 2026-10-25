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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.util.ArchiveUtils;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;

import static net.brlns.gdownloader.updater.UpdateStatusEnum.UNPACKING;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class UvUpdater extends AbstractGitUpdater {

    private static final String USER = "astral-sh";
    private static final String REPO = "uv";

    @Getter
    private volatile File executable;

    public UvUpdater(GDownloader mainIn) {
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
    public String getReleaseBinaryName() {
        return ArchVersionEnum.getDefinitions().getUvBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return FileUtils.getBinaryName("uv");
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return getRuntimeBinaryName();
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "uv.lock";
    }

    @Override
    public boolean isEnabled() {
        return getReleaseBinaryName() != null;
    }

    @Override
    public boolean isPreferSystemExecutable() {
        return main.getConfig().isPreferSystemExecutables();
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        executable = executablePath;
    }

    @Override
    public String getName() {
        return "uv";
    }

    @Override
    protected void init() throws Exception {

    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File archivePath = new File(workDir, fileName);
        log.info("Archive path {}", archivePath);

        Path archiveOutputPath = Paths.get(workDir.getAbsolutePath(), "tmp_uv_archive");
        log.info("Archive out path {}", archiveOutputPath);

        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Final path {}", outputFile);

        try {
            downloadFile(url, archivePath);

            notifyProgress(UNPACKING, 0);
            if (fileName.endsWith(".zip")) {
                ArchiveUtils.inflateZip(archivePath, archiveOutputPath, true, (progress) -> {
                    notifyProgress(UNPACKING, progress);
                });
            } else {
                ArchiveUtils.inflateTarGz(archivePath, archiveOutputPath, true, (progress) -> {
                    notifyProgress(UNPACKING, progress);
                });
            }

            Path sourcePath = archiveOutputPath.resolve(getRuntimeBinaryName());
            log.info("Source binary path {}", sourcePath);

            if (!Files.exists(sourcePath)) {
                throw new FileNotFoundException("Could not find " + getRuntimeBinaryName() + " inside extracted archive at " + sourcePath);
            }

            Files.move(sourcePath, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            notifyProgress(UNPACKING, 100);
        } finally {
            if (archivePath.exists()) {
                archivePath.delete();
            }

            DirectoryUtils.deleteRecursively(archiveOutputPath);
        }

        return outputFile;
    }
}
