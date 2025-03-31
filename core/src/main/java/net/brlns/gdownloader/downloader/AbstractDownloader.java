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
package net.brlns.gdownloader.downloader;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.util.FileUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractDownloader {

    protected final GDownloader main;
    protected final DownloadManager manager;

    public AbstractDownloader(DownloadManager managerIn) {
        main = managerIn.getMain();
        manager = managerIn;
    }

    public abstract boolean isEnabled();

    protected abstract boolean canConsumeUrl(String inputUrl);

    protected abstract boolean tryQueryMetadata(QueueEntry queueEntry);

    protected abstract DownloadResult tryDownload(QueueEntry entry) throws Exception;

    protected abstract void processMediaFiles(QueueEntry entry);

    public abstract Optional<File> getExecutablePath();

    public abstract void setExecutablePath(Optional<File> file);

    public abstract Optional<File> getFfmpegPath();

    public abstract void setFfmpegPath(Optional<File> file);

    public abstract boolean isMainDownloader();

    public abstract List<DownloadTypeEnum> getArchivableTypes();

    public abstract void removeArchiveEntry(QueueEntry queueEntry);

    public abstract DownloaderIdEnum getDownloaderId();

    public List<DownloadTypeEnum> getDownloadTypes() {
        return DownloadTypeEnum.getForDownloaderId(getDownloaderId());
    }

    @PreDestroy
    public abstract void close();

    @Nullable
    public File getArchiveFile(DownloadTypeEnum downloadType) {
        List<DownloadTypeEnum> supported = getArchivableTypes();

        if (supported.contains(downloadType)) {
            File oldArchive = new File(GDownloader.getWorkDirectory(),
                getDownloaderId().getDisplayName()
                + "_archive.txt");

            File newArchive = new File(GDownloader.getWorkDirectory(),
                getDownloaderId().getDisplayName()
                + "_archive_"
                + downloadType.name().toLowerCase()
                + ".txt");

            if (oldArchive.exists()) {
                oldArchive.renameTo(newArchive);
            }

            return FileUtils.getOrCreate(newArchive);
        }

        return null;
    }

    @Nullable
    public File getCookieJarFile() {
        if (!main.getConfig().isReadCookiesFromCookiesTxt()) {
            return null;
        }

        File cookieJar = new File(GDownloader.getWorkDirectory(),
            getDownloaderId().getDisplayName() + "_cookies.txt");

        try {
            if (cookieJar.exists() && cookieJar.isFile()) {
                if (cookieJar.length() > 0) {
                    return cookieJar;
                }
            } else {
                if (cookieJar.createNewFile()) {
                    log.info("Created empty {} cookies.txt file at: {}",
                        getDownloaderId().getDisplayName(), cookieJar);
                }
            }
        } catch (IOException e) {
            GDownloader.handleException(e);
        }

        return null;
    }
}
