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
package net.brlns.gdownloader.clipboard;

import jakarta.annotation.Nullable;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.message.ToastMessenger;
import net.brlns.gdownloader.util.collection.ExpiringSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * Java does not provide a reliable way to detect clipboard changes.
 * We have to rely on native bindings to make this work properly.
 *
 * TODO: Linux/Mac JNA bindings
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class ClipboardManager {

    private final GDownloader main;

    private final AtomicBoolean clipboardBlocked = new AtomicBoolean(true);

    private final Clipboard clipboard;
    private final Map<FlavorType, String> lastClipboardState = new EnumMap<>(FlavorType.class);

    private final ReentrantLock clipboardLock = new ReentrantLock();

    private final List<IClipboardListener> clipboardListeners = new ArrayList<>();

    private final ExpiringSet<String> urlIgnoreSet = new ExpiringSet<>(TimeUnit.SECONDS, 1);

    public ClipboardManager(GDownloader mainIn) {
        main = mainIn;
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        if (GDownloader.isWindows()) {
            clipboardListeners.add(new Win32NativeClipboardListener());
        }

        //TODO: X11/Wayland. X11 will be a mess.
        clipboardListeners.add(new MaybeChangedClipboardListener());

        if (GDownloader.HAS_JNATIVEHOOK) {
            clipboardListeners.add(new CtrlCNativeClipboardListener());
        }
    }

    public void unblock() {
        // Ignore any data from before the program started.
        invalidateClipboard();

        clipboardBlocked.set(false);
    }

    public void tickClipboard() {
        if (!main.getConfig().isMonitorClipboardForLinks()) {
            return;
        }

        boolean changed = clipboardListeners.stream()
            .anyMatch(IClipboardListener::clipboardHasChanged);

        if (changed) {
            triggerRevalidation();
        } else {
            updateClipboard();
        }
    }

    public void copyTextToClipboard(@NonNull String text) {
        copyTextToClipboard(List.of(text));
    }

    public void copyTextToClipboard(@NonNull List<String> texts) {
        if (texts.isEmpty()) {
            return;
        }

        clipboardLock.lock();
        try {
            clipboard.setContents(new StringSelection(String.join(System.lineSeparator(), texts)), null);

            invalidateClipboard();

            String displayText = texts.size() == 1 ? texts.get(texts.size() - 1)
                : l10n("gui.copied_to_clipboard.lines", texts.size());

            PopupMessenger.show(Message.builder()
                .title("gui.copied_to_clipboard.notification_title")
                .messageRaw(displayText)
                .durationMillis(2000)
                .messageType(MessageTypeEnum.INFO)
                .build());
        } finally {
            clipboardLock.unlock();
        }
    }

    public boolean tryHandleDnD(@Nullable Transferable transferable) {
        return updateClipboard(transferable, true); // force is true because this is considered manual input.
    }

    public void pasteURLsFromClipboard() {
        if (!main.getDownloadManager().isBlocked()) {
            updateClipboard(null, true);

            if (clipboardContainedURLs()) {
                ToastMessenger.show(Message.builder()
                    .message("gui.add_from_clipboard.toast_empty")
                    .durationMillis(3000)
                    .messageType(MessageTypeEnum.WARNING)
                    .discardDuplicates(true)
                    .build());
            } else {
                ToastMessenger.show(Message.builder()
                    .message("gui.add_from_clipboard.toast_pasted")
                    .durationMillis(3000)
                    .messageType(MessageTypeEnum.INFO)
                    .discardDuplicates(true)
                    .build());
            }
        }
    }

    public boolean updateClipboard() {
        return updateClipboard(null, false);
    }

    public boolean updateClipboard(@Nullable Transferable transferable, boolean force) {
        if (clipboardBlocked.get() || main.getDownloadManager().isBlocked()) {
            return false;
        }

        if (!main.getConfig().isMonitorClipboardForLinks() && !force) {
            return false;
        }

        clipboardLock.lock();
        try {
            if (transferable == null) {
                transferable = clipboard.getContents(null);
            }

            if (transferable == null) {
                return false;
            }

            boolean success = false;
            for (FlavorType flavorType : FlavorType.values()) {
                if (transferable.isDataFlavorSupported(flavorType.getFlavor())) {
                    try {
                        String data = (String)transferable.getTransferData(flavorType.getFlavor());

                        if (!force) {
                            processClipboardData(flavorType, data);
                        } else {
                            handleClipboardInput(data, force);
                        }

                        success = true;
                    } catch (Exception e) {
                        log.warn("Cannot obtain {} transfer data: {}", flavorType, e.getMessage());
                    }
                }
            }

            return success;
        } finally {
            clipboardLock.unlock();
        }
    }

    public void revalidateClipboard() {
        clipboardLock.lock();
        try {
            for (FlavorType flavorType : FlavorType.values()) {
                lastClipboardState.put(flavorType, "revalidating");
            }
        } finally {
            clipboardLock.unlock();
        }
    }

    public void invalidateClipboard() {
        clipboardLock.lock();
        try {
            Transferable transferable = clipboard.getContents(null);
            if (transferable == null) {
                return;
            }

            for (FlavorType flavorType : FlavorType.values()) {
                if (transferable.isDataFlavorSupported(flavorType.getFlavor())) {
                    try {
                        String data = (String)transferable.getTransferData(flavorType.getFlavor());
                        lastClipboardState.put(flavorType, data);
                    } catch (Exception e) {
                        if (main.getConfig().isDebugMode()) {
                            log.error("Exception while invalidating clipboard", e);
                        }
                    }
                }
            }

            clipboardListeners.stream()
                .forEach((listener) -> listener.skipFor(TimeUnit.SECONDS, 4));
        } finally {
            clipboardLock.unlock();
        }
    }

    private void triggerRevalidation() {
        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            //Wait a bit for data to propagate.
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            revalidateClipboard();
            updateClipboard();
        });
    }

    private void handleClipboardInput(String data, boolean force) {
        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            List<CompletableFuture<Boolean>> list = new ArrayList<>();

            for (String url : extractUrlsFromString(data)) {
                if (url.startsWith("http") && !urlIgnoreSet.contains(url)) {
                    urlIgnoreSet.add(url);

                    list.add(main.getDownloadManager().captureUrl(url, force));
                }

                // Small extra utility
                if (main.getConfig().isLogMagnetLinks() && url.startsWith("magnet")) {
                    main.logUrl("magnets", url);
                }
            }

            CompletableFuture<Void> futures = CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));

            futures.thenRun(() -> {
                List<Boolean> results = list.stream()
                    .map(future -> {
                        try {
                            return future.join();
                        } catch (CompletionException e) {
                            GDownloader.handleException(e.getCause());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

                int captured = 0;
                for (boolean result : results) {
                    if (result) {
                        captured++;
                    }
                }

                if (captured > 0) {
                    if (main.getConfig().isDisplayLinkCaptureNotifications()) {
                        PopupMessenger.show(Message.builder()
                            .title("gui.clipboard_monitor.captured_title")
                            .message("gui.clipboard_monitor.captured", captured)
                            .durationMillis(1500)
                            .messageType(MessageTypeEnum.INFO)
                            .build());
                    }

                    // If notications are off, requesting focus could probably also be an undesired behavior,
                    // However, I think we should keep at least this one visual cue.
                    main.getGuiManager().requestFocus();
                }
            });

            try {
                futures.get(10l, TimeUnit.MINUTES);
            } catch (InterruptedException | ExecutionException e) {
                GDownloader.handleException(e);
            } catch (TimeoutException e) {
                log.warn("Timed out waiting for futures");
            }
        });
    }

    private void processClipboardData(FlavorType flavorType, String data) {
        if (!lastClipboardState.containsKey(flavorType)) {
            lastClipboardState.put(flavorType, "");
        }

        String last = lastClipboardState.get(flavorType);
        if (!last.equals(data)) {
            lastClipboardState.put(flavorType, data);

            handleClipboardInput(data, false);
        }
    }

    private Set<String> extractUrlsFromString(String content) {
        Set<String> result = new HashSet<>();

        Document doc = Jsoup.parse(content);

        Elements links = doc.select("a[href]");
        Elements media = doc.select("[src]");

        if (main.getConfig().isDebugMode()) {
            log.debug("Found {} Links and {} Media", links.size(), media.size());
        }

        if (links.isEmpty() && media.isEmpty()) {
            String regex = "(http[^\\s]*|magnet:[^\\s]*)(?=\\s|$|http|magnet:)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);

            while (matcher.find()) {
                String url = matcher.group(1);

                if (isValidURL(url)) {
                    result.add(url);
                }
            }
        }

        links.forEach((link) -> {
            result.add(link.attr("href"));
        });

        media.forEach((src) -> {
            result.add(src.attr("src"));
        });

        return result;
    }

    /**
     * This should be called after updateClipboard() when lastClipboardState is populated.
     */
    public boolean clipboardContainedURLs() {
        DataFlavor[] flavors = clipboard.getAvailableDataFlavors();
        if (flavors.length == 0) {
            return true;
        }

        clipboardLock.lock();
        try {
            Transferable transferable = clipboard.getContents(null);
            if (transferable == null) {
                return true;
            }

            boolean hasUrl = false;
            for (String data : lastClipboardState.values()) {
                if (data.contains("http")) {
                    hasUrl = true;
                }
            }

            return !hasUrl;
        } finally {
            clipboardLock.unlock();
        }
    }

    private static boolean isValidURL(String urlString) {
        try {
            new URI(urlString).toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Getter
    public static enum FlavorType {
        STRING(DataFlavor.stringFlavor),
        HTML(DataFlavor.selectionHtmlFlavor);

        private final DataFlavor flavor;

        private FlavorType(DataFlavor flavorIn) {
            flavor = flavorIn;
        }
    }
}
