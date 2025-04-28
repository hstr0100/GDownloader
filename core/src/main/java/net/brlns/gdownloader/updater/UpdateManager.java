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

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.PerformUpdateCheckEvent;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.updater.git.*;
import net.brlns.gdownloader.util.NetworkConnectivityListener;
import net.brlns.gdownloader.util.NoFallbackAvailableException;

import static net.brlns.gdownloader.GDownloader.GLOBAL_THREAD_POOL;
import static net.brlns.gdownloader.GDownloader.handleException;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class UpdateManager {

    @Getter
    private final List<IUpdater> updaters = new CopyOnWriteArrayList<>();

    private final GDownloader main;

    public UpdateManager(GDownloader mainIn) {
        main = mainIn;

        init();
    }

    @PostConstruct
    private void init() {
        registerUpdater(new SelfUpdater(main));
        registerUpdater(new SelfAppImageUpdater(main));
        registerUpdater(new SelfJarUpdater(main));

        registerUpdater(new YtDlpUpdater(main));
        registerUpdater(new GalleryDlUpdater(main));
        registerUpdater(new SpotDLUpdater(main));
        registerUpdater(new FFMpegUpdater(main));
    }

    public void registerUpdater(IUpdater updater) {
        updaters.add(updater);
    }

    public void unregisterUpdater(IUpdater updater) {
        updaters.remove(updater);
    }

    public boolean checkForUpdates() {
        return checkForUpdates(false);
    }

    public boolean checkForUpdates(boolean isBooting) {
        DownloadManager downloadManager = main.getDownloadManager();
        if (!isBooting) {
            if (downloadManager.isBlocked()) {// This means we are already checking for updates
                return false;
            }

            PopupMessenger.show(Message.builder()
                .title("gui.update.notification_title")
                .message("gui.update.checking")
                .durationMillis(2000)
                .messageType(MessageTypeEnum.INFO)
                .discardDuplicates(true)
                .build());
        }

        downloadManager.block();
        downloadManager.stopDownloads();

        EventDispatcher.dispatch(PerformUpdateCheckEvent.builder()
            .checking(true)
            .networkOnline(false)
            .build());

        GLOBAL_THREAD_POOL.execute(() -> {
            try {
                NetworkConnectivityListener.waitForConnectivity();

                EventDispatcher.dispatch(PerformUpdateCheckEvent.builder()
                    .checking(true)
                    .networkOnline(true)
                    .build());

                CountDownLatch latch = new CountDownLatch(updaters.size());

                for (IUpdater updater : updaters) {
                    if (updater.isEnabled()) {
                        GLOBAL_THREAD_POOL.execute(() -> {
                            try {
                                log.info("Starting updater " + updater.getClass().getName());
                                updater.check(!isBooting);
                            } catch (NoFallbackAvailableException e) {
                                log.error("Updater for " + updater.getClass().getName()
                                    + " failed and no fallback is available. Your OS might be unsupported.");
                            } catch (Exception e) {
                                handleException(e);
                            } finally {
                                latch.countDown();
                            }
                        });
                    } else {
                        log.info("Updater " + updater.getClass().getName() + " is not supported on this platform or runtime method.");
                        latch.countDown();
                    }
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                }

                log.info("Finished checking for updates");

                boolean updated = updaters.stream()
                    .anyMatch(IUpdater::isUpdated);

                if (!isBooting) {
                    PopupMessenger.show(Message.builder()
                        .title("gui.update.notification_title")
                        .message(updated
                            ? "gui.update.new_updates_installed"
                            : "gui.update.updated")
                        .durationMillis(2000)
                        .messageType(MessageTypeEnum.INFO)
                        .playTone(true)
                        .build());
                }

                for (IUpdater updater : updaters) {
                    if (updater.isUpdated() && updater.isRestartRequired()) {
                        log.info("Restarting to apply updates.");
                        main.restart();
                        break;
                    }
                }

                if (!downloadManager.isMainDownloaderInitialized()) {
                    log.error("Failed to initialize yt-dlp, the program cannot continue. Exiting...");

                    if (isBooting) {
                        main.shutdown();
                    }
                }
            } finally {
                downloadManager.unblock();

                if (isBooting) {
                    main.runPostUpdateInitTasks();
                }

                EventDispatcher.dispatch(PerformUpdateCheckEvent.builder()
                    .checking(false)
                    .networkOnline(true)
                    .build());
            }
        });

        return true;
    }

}
