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
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.LockUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class GalleryDlUpdater extends AbstractGitUpdater {

    private static final String USER = "mikf";
    private static final String REPO = "gallery-dl";

    public GalleryDlUpdater(GDownloader mainIn) {
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
        return ArchVersionEnum.getDefinitions().getGalleryDlBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return getGitHubBinaryName();
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return "gallery-dl";
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "gallery-dl.lock";
    }

    @Override
    public boolean isEnabled() {
        return main.getConfig().isGalleryDlEnabled();
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        main.getDownloadManager().setExecutablePath(DownloaderIdEnum.GALLERY_DL, executablePath);
    }

    @Override
    public String getName() {
        return "Gallery-DL";
    }

    @Override
    protected void init() throws Exception {
        LockUtils.renameLockIfExists("gallerydl_lock.txt", getLockFileName());
    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        File outputFile = super.doDownload(url, workDir);

        File configFile = new File(workDir, "gallery-dl.conf");

        if (!configFile.exists()) {
            FileUtils.writeResourceToFile("/gallery-dl.conf", configFile);
        }

        return outputFile;
    }
}
