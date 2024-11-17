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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.util.Pair;

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

    protected abstract boolean canConsumeUrl(String inputUrl);

    protected abstract boolean tryQueryVideo(QueueEntry queueEntry);

    protected abstract DownloadResult tryDownload(QueueEntry entry) throws Exception;

    protected abstract Map<String, Runnable> processMediaFiles(QueueEntry entry);

    protected abstract void processProgress(QueueEntry entry, String lastOutput);

    protected abstract Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception;

    public abstract Optional<File> getExecutablePath();

    public abstract void setExecutablePath(Optional<File> file);

    public abstract Optional<File> getFfmpegPath();

    public abstract void setFfmpegPath(Optional<File> file);

    public abstract boolean isMainDownloader();

    public abstract DownloaderIdEnum getDownloaderId();

    public List<DownloadTypeEnum> getDownloadTypes() {
        return DownloadTypeEnum.getForDownloaderId(getDownloaderId());
    }
}
