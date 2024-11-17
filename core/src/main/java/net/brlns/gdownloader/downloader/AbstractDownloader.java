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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.Pair;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractDownloader {

    protected static final String GD_INTERNAL_FINISHED = "GD-Internal-Finished";

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

    public abstract Optional<File> getExecutablePath();

    public abstract void setExecutablePath(Optional<File> file);

    public abstract Optional<File> getFfmpegPath();

    public abstract void setFfmpegPath(Optional<File> file);

    public abstract boolean isMainDownloader();

    public abstract DownloaderIdEnum getDownloaderId();

    public List<DownloadTypeEnum> getDownloadTypes() {
        return DownloadTypeEnum.getForDownloaderId(getDownloaderId());
    }

    @Nullable
    protected Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception {
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);
        finalArgs.add(entry.getUrl());

        ProcessBuilder processBuilder = new ProcessBuilder(finalArgs);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        entry.setProcess(process);

        String lastOutput = "";

        try (
            ReadableByteChannel stdInput = Channels.newChannel(process.getInputStream())) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            StringBuilder output = new StringBuilder();
            char prevChar = '\0';

            while (manager.isRunning() && !entry.getCancelHook().get() && process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroy();
                    throw new InterruptedException("Download interrupted");
                }

                buffer.clear();
                int bytesRead = stdInput.read(buffer);
                if (bytesRead > 0) {
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        char ch = (char)buffer.get();
                        output.append(ch);

                        if (ch == '\n' || (ch == '\r' && prevChar != '\n')) {
                            lastOutput = output.toString().replace("\n", "");
                            output.setLength(0);
                        }

                        prevChar = ch;
                    }

                    processProgress(entry, lastOutput);
                }

                Thread.sleep(100);
            }

            entry.getDownloadStarted().set(false);

            long stopped = System.currentTimeMillis() - start;

            if (!manager.isRunning() || entry.getCancelHook().get()) {
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process halted after {}ms.", stopped);
                }

                return null;
            } else {
                int exitCode = process.waitFor();
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process took {}ms, exit code: {}", stopped, exitCode);
                }

                if (lastOutput.contains(GD_INTERNAL_FINISHED)) {
                    exitCode = 0;
                }

                return new Pair<>(exitCode, lastOutput);
            }
        } catch (IOException e) {
            log.info("IO error: {}", e.getMessage());

            return null;
        } finally {
            // Our ProcessMonitor will take care of closing the underlying process.
        }
    }
}
