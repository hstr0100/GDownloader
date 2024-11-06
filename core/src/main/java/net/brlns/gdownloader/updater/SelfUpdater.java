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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.Nullable;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SelfUpdater extends AbstractGitUpdater {

    private static final String USER = "hstr0100";
    private static final String REPO = "GDownloader";

    public SelfUpdater(GDownloader mainIn) {
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
        return ArchVersionEnum.getArchVersion().getSelfBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return "gdownloader_ota.zip";
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "ota_lock.txt";
    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File zipPath = new File(workDir, fileName);
        log.info("Zip path {}", zipPath);

        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Out path {}", outputFile);

        downloadFile(url, zipPath);

        Files.move(zipPath.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return outputFile;
    }

    @Override
    public boolean isSupported() {
        return !GDownloader.isFromJar();
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        // Not used
    }

    @Override
    public String getName() {
        return "GDownloader";
    }

}
