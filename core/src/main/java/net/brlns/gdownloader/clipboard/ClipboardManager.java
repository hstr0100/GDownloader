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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            submitClipboardScan(null, false);
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
        if (clipboardBlocked.get() || main.getDownloadManager().isBlocked()) {
            return false;
        }

        // force is true because this is considered manual input.
        submitClipboardScan(transferable, true);
        // accept the drop optimistically
        return true;
    }

    public void pasteURLsFromClipboard() {
        if (main.getDownloadManager().isBlocked()) {
            return;
        }

        submitClipboardScan(null, true).thenAccept(captured -> {
            if (captured == 0) {
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
        });
    }

    public void updateClipboard() {
        submitClipboardScan(null, false);
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

            lastClipboardState.putAll(fetchTransferableData(transferable));
            clipboardListeners.forEach(listener -> listener.skipFor(TimeUnit.SECONDS, 4));
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
                Thread.currentThread().interrupt();
                return;
            }

            revalidateClipboard();
            submitClipboardScan(null, false);
        });
    }

    private Map<FlavorType, String> fetchTransferableData(Transferable transferable) {
        Map<FlavorType, String> dataMap = new EnumMap<>(FlavorType.class);
        for (FlavorType flavorType : FlavorType.values()) {
            if (transferable.isDataFlavorSupported(flavorType.getFlavor())) {
                try {
                    String data = (String)transferable.getTransferData(flavorType.getFlavor());
                    if (data != null) {
                        dataMap.put(flavorType, data);
                    }
                } catch (Exception e) {
                    log.warn("Cannot obtain {} transfer data: {}", flavorType, e.getMessage());
                }
            }
        }

        return dataMap;
    }

    private CompletableFuture<Integer> submitClipboardScan(@Nullable Transferable transferableIn, boolean force) {
        if (clipboardBlocked.get() || main.getDownloadManager().isBlocked()) {
            return CompletableFuture.completedFuture(0);
        }

        if (!force && !main.getConfig().isMonitorClipboardForLinks()) {
            return CompletableFuture.completedFuture(0);
        }

        clipboardLock.lock();
        final Map<FlavorType, String> extractedData;
        try {
            Transferable transferable = (transferableIn != null) ? transferableIn : clipboard.getContents(null);
            if (transferable == null) {
                return CompletableFuture.completedFuture(0);
            }

            extractedData = fetchTransferableData(transferable);
        } finally {
            clipboardLock.unlock();
        }

        CompletableFuture<Integer> result = new CompletableFuture<>();
        GDownloader.GLOBAL_THREAD_POOL.execute(() -> {
            try {
                Set<String> urls = new HashSet<>();
                for (Map.Entry<FlavorType, String> entry : extractedData.entrySet()) {
                    FlavorType flavorType = entry.getKey();
                    String data = entry.getValue();

                    if (force || hasChanged(flavorType, data)) {
                        urls.addAll(extractUrlsFromString(data));
                    }
                }

                if (urls.isEmpty()) {
                    result.complete(0);
                    return;
                }

                captureUrls(urls, force).whenComplete((captured, ex) -> {
                    if (ex != null) {
                        GDownloader.handleException(ex);
                        result.complete(0);
                    } else {
                        result.complete(captured);
                    }
                });
            } catch (Exception e) {
                GDownloader.handleException(e);
                result.complete(0);
            }
        });

        return result;
    }

    private boolean hasChanged(FlavorType flavorType, String data) {
        clipboardLock.lock();
        try {
            String last = lastClipboardState.put(flavorType, data);

            return last == null || !last.equals(data);
        } finally {
            clipboardLock.unlock();
        }
    }

    private CompletableFuture<Integer> captureUrls(Set<String> urls, boolean force) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (String url : urls) {
            if (url.startsWith("http") && !urlIgnoreSet.contains(url)) {
                urlIgnoreSet.add(url);

                futures.add(main.getDownloadManager().captureUrl(url, force));
            } else if (main.getConfig().isLogMagnetLinks() && url.startsWith("magnet")) {
                // Small extra utility
                main.logUrl("magnets", url);
            }
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .handle((ignored, ex) -> {
                if (ex != null) {
                    GDownloader.handleException(ex);
                }

                int captured = 0;
                for (CompletableFuture<Boolean> future : futures) {
                    if (!future.isCompletedExceptionally()
                        && Boolean.TRUE.equals(future.getNow(false))) {
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

                    // If notifications are off, requesting focus could probably also be an undesired behavior,
                    // However, I think we should keep at least this one visual cue.
                    main.getGuiManager().requestFocus();
                }

                return captured;
            });
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

        links.forEach(link -> result.add(link.attr("href")));
        media.forEach(src -> result.add(src.attr("src")));

        return result;
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
