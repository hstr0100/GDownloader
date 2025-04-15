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
package net.brlns.gdownloader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.desktop.QuitStrategy;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.clipboard.ClipboardManager;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.event.EventDispatcher;
import net.brlns.gdownloader.event.impl.NativeMouseClickEvent;
import net.brlns.gdownloader.ffmpeg.FFmpegSelfTester;
import net.brlns.gdownloader.ffmpeg.FFmpegTranscoder;
import net.brlns.gdownloader.lang.Language;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.process.ProcessMonitor;
import net.brlns.gdownloader.server.AppClient;
import net.brlns.gdownloader.server.AppServer;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.BrowserEnum;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.updater.*;
import net.brlns.gdownloader.util.*;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;
import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

// TODO media converter
// TODO implement CD Ripper
// TODO d&d files for conversion to different formats, we already have ffmpeg anyway
//
// TODO max simultaneous downloads should be independent per website
// TODO investigate adding AppImage build
// TODO scale on resolution DPI
// TODO save last window size in config
// TODO Advanced users can edit the config.json directly to add extra yt-dlp arguments like proxy settings. but maybe expose those settings to the ui.
// TODO verify checksums during updates, add bouncycastle, check signatures
// TODO write a component factory for GUIManager
// TODO git actions build for different platforms
// FEEDBACK Should choose to download video and audio independently on each card
// DROPPED check updates on a timer, but do not ever restart when anything is in the queue.
// TODO --no-playlist when single video option is active
// TODO Artifacting seems to be happening on the scroll pane with AMD video cards
// TODO open a window asking which videos in a playlist to download or not
// TODO RearrangeableDeque's offerLast should be linked to the cards in the UI
// TODO Better visual eye candy for when dragging cards
// TODO Javadoc, a whole lot of it.
// TODO Twitch settings purposefully default to suboptimal quality due to huge file sizes. Maybe consider adding a warning about this in the GUI.
// TODO Split GUI into a different subproject from core logic.
// TODO Investigate screen reader support (https:// www.nvaccess.org/download/)
// TODO Send notifications when a NO_METHOD is triggered, explaining why it was triggered.
// TODO Test downloading sections of a livestream (currently it gets stuck on status PREPARING). Note: it also leaves a zombie ffmpeg process behind dealing with the hls stream.
// TODO The issue above is a yt-dlp bug https:// github.com/yt-dlp/yt-dlp/issues/7927
// TODO Implement rate-limiting options internally; the way it's currently implemented does not account for concurrent or non-playlist downloads.
// TODO Notify the user whenever a setting that requires restart was changed.
// TODO Add an url ignore list / Allow filters to be disabled
// TODO Add option to clear all installed updates and start fresh. (Tackling certain issues where failed updates could break downloads)
// TODO Wget integration
// TODO NTFS File path length workaround for gallery-dl
// TODO Split main window from GUIManager
// TODO Debug no console output from gallery-dl on Windows when using channels
// TODO gallery-dl does not accept an argument specifying yt-dlp/ffmpeg location, figure out a workaround to pass the correct path to it
// TODO Fastutil collections
// TODO Proxy settings should be add to the UI, fields should be validated on the fly
// TODO Tabs in settings for the different downloaders
// TODO Crawl for valid links that can be consumed by direct-http
// TODO Two column layout when in full screen
// TODO Fix notification line wrapping
// TODO Downloader priority settings
// TODO Dynamically reorder downloads based on their status or order in queue -> RUNNING, QUEUED, <others>, FAILED
// TODO Notifications are appearing below the main window when in fullscreen mode.
// TODO About page
// TODO Confirm dialog before clearing DL queue
// TODO Move config files, downloaders and their respective data to subfolders
// TODO ctrl+z to undo removals
// prio
// TODO reseting download links should also refresh filter references
// TODO right click > sort by
// TODO display number in download queue
/**
 * GDownloader - GUI wrapper for yt-dlp
 *
 * Icons sourced from https://www.iconsdb.com/white-icons/ at a size of 128x128
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class GDownloader {

    /**
     * Constants and application states
     */
    public static final String REGISTRY_APP_NAME = "GDownloader";// Changing this might result in orphaned registry keys.

    public static final String CACHE_DIRETORY_NAME = "tmp";
    public static final String OLD_CACHE_DIRETORY_NAME = "gdownloader_cache";

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public final static PriorityVirtualThreadExecutor GLOBAL_THREAD_POOL
        = new PriorityVirtualThreadExecutor();

    @Getter
    private static GDownloader instance;

    private static String launcher;

    @Getter
    private static boolean portable;

    @Getter
    private boolean systemTrayInitialized;

    private SystemTray tray;
    private TrayIcon trayIcon = null;

    private File configFile;

    @Getter
    private Settings config;

    @Getter
    private ClipboardManager clipboardManager;

    @Getter
    private DownloadManager downloadManager;

    @Getter
    private PersistenceManager persistenceManager;

    @Getter
    private List<AbstractGitUpdater> updaters = new ArrayList<>();

    @Getter
    private GUIManager guiManager;

    @Getter
    private FFmpegTranscoder ffmpegTranscoder;

    @Getter
    private ProcessMonitor processMonitor;

    @Getter
    private boolean initialized = false;

    @Getter(AccessLevel.PRIVATE)
    private final ScheduledExecutorService mainTicker;

    private final AtomicBoolean restartRequested = new AtomicBoolean(false);

    @Getter
    @Setter
    private AppClient appClient;

    @Getter
    private AppServer appServer;

    public GDownloader() {
        // Initialize the config file
        File workDir = getWorkDirectory();
        configFile = new File(workDir, "config.json");

        // TODO: move log rotation to utils
        File logFile = new File(workDir, "gdownloader_log.txt");
        File previousLogFile = new File(workDir, "gdownloader_log_previous.txt");

        if (logFile.exists()) {
            if (previousLogFile.exists()) {
                previousLogFile.delete();
            }

            logFile.renameTo(previousLogFile);
        }

        LoggerUtils.setLogFile(logFile);

        if (!configFile.exists()) {
            config = new Settings();
            updateConfig();
        }

        try {
            config = OBJECT_MAPPER.readValue(configFile, Settings.class);
            config.doMigration();
        } catch (IOException e) {
            config = new Settings();
            updateConfig();

            // We have to init the language to display the exception.
            Language.initLanguage(config);

            handleException(e, true);
        }

        log.info("Loaded config file");

        if (config.isDebugMode()) {
            printDebugInformation();
        }

        mainTicker = Executors.newScheduledThreadPool(1);

        Language.initLanguage(config);
        updateConfig();

        log.info(l10n("_startup"));

        ThemeProvider.setTheme(config.getTheme());

        if (!config.isUseSystemFont()) {
            setUIFont(new FontUIResource("Dialog", Font.BOLD, config.getFontSize()));
        } else {
            setUIFontSize(config.getFontSize());
        }

        try {
            setupAppServer();

            processMonitor = new ProcessMonitor();

            persistenceManager = new PersistenceManager(this);
            persistenceManager.init();

            ffmpegTranscoder = new FFmpegTranscoder(processMonitor);

            clipboardManager = new ClipboardManager(this);
            downloadManager = new DownloadManager(this);

            guiManager = new GUIManager(this);

            // Register to the system tray
            if (SystemTray.isSupported()) {
                try {
                    tray = SystemTray.getSystemTray();

                    Image image = Toolkit.getDefaultToolkit().createImage(
                        getClass().getResource(guiManager.getCurrentTrayIconPath()));

                    trayIcon = new TrayIcon(image, REGISTRY_APP_NAME, buildPopupMenu());
                    trayIcon.setImageAutoSize(true);

                    trayIcon.addActionListener((ActionEvent e) -> {
                        initUi();
                    });

                    tray.add(trayIcon);

                    systemTrayInitialized = true;
                } catch (AWTException e) {
                    log.error("Error initializing the system tray");
                    handleException(e);
                }
            } else {
                log.error("System tray not supported? did you run this on a calculator?");
            }

            updaters.add(new SelfUpdater(this));
            updaters.add(new YtDlpUpdater(this));
            updaters.add(new FFMpegUpdater(this));

            if (config.isGalleryDlEnabled()) {
                updaters.add(new GalleryDlUpdater(this));
            }

            if (config.isSpotDLEnabled()) {
                updaters.add(new SpotDLUpdater(this));
            }

            if (config.isDebugMode()) {
                for (AbstractGitUpdater updater : updaters) {
                    updater.registerListener((status, progress) -> {
                        log.info("UPDATER {}: Status: {} Progress: {}",
                            updater.getClass().getName(), status, String.format("%.1f", progress));
                    });
                }
            }

            updateStartupStatus();

            AtomicBoolean failedOnce = new AtomicBoolean();
            mainTicker.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    loop(clipboardManager::tickClipboard);
                    loop(downloadManager::processQueue);
                }

                private void loop(Runnable action) {
                    try {
                        action.run();
                    } catch (Exception e) {
                        if (failedOnce.compareAndSet(false, true)) {
                            log.error("Looper task has failed at least once: {}", e.getMessage());

                            if (log.isDebugEnabled()) {
                                log.error("Exception: ", e);
                            }
                        }
                    }
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            // Java doesn't natively support detecting a click outside of the program window,
            // Which we would need for our custom context menus
            GlobalScreen.addNativeMouseListener(new NativeMouseListener() {
                @Override
                public void nativeMousePressed(NativeMouseEvent e) {
                    EventDispatcher.dispatch(new NativeMouseClickEvent(e.getPoint()));
                }
            });

            initialized = true;
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void clearCache() {
        clearCache(false);
    }

    public void clearCache(boolean notify) {
        downloadManager.stopDownloads();

        // A shorter cache dir name might benefit NTFS filesystems.
        File cachePath = new File(getDownloadsDirectory(), CACHE_DIRETORY_NAME);
        DirectoryUtils.deleteRecursively(cachePath.toPath());

        // We have to get rid of the old cache dir if it exists.
        File oldCachePath = new File(getDownloadsDirectory(), OLD_CACHE_DIRETORY_NAME);
        DirectoryUtils.deleteRecursively(oldCachePath.toPath());

        if (notify) {
            PopupMessenger.show(
                l10n("gui.clear_cache.notification_title"),
                l10n("gui.clear_cache.cleared"),
                2000,
                MessageTypeEnum.INFO,
                false, true);
        }
    }

    public void initUi() {
        if (initialized) {
            guiManager.createAndShowGUI();
        }
    }

    public boolean checkForUpdates() {
        return checkForUpdates(true);
    }

    public boolean checkForUpdates(boolean userInitiated) {
        if (userInitiated) {
            if (downloadManager.isBlocked()) {// This means we are already checking for updates
                return false;
            }

            downloadManager.block();
            downloadManager.stopDownloads();

            PopupMessenger.show(
                l10n("gui.update.notification_title"),
                l10n("gui.update.checking"),
                2000,
                MessageTypeEnum.INFO,
                false, true);
        }

        CountDownLatch latch = new CountDownLatch(updaters.size());

        for (AbstractGitUpdater updater : updaters) {
            if (updater.isSupported()) {
                GLOBAL_THREAD_POOL.submitWithPriority(() -> {
                    try {
                        log.error("Starting updater " + updater.getClass().getName());
                        updater.check(userInitiated);
                    } catch (NoFallbackAvailableException e) {
                        log.error("Updater for " + updater.getClass().getName()
                            + " failed and no fallback is available. Your OS might be unsupported.");
                    } catch (Exception e) {
                        handleException(e);
                    } finally {
                        latch.countDown();
                    }
                }, 5);
            } else {
                log.info("Updater " + updater.getClass().getName() + " is not supported on this platform or runtime method.");
                latch.countDown();
            }
        }

        GLOBAL_THREAD_POOL.submitWithPriority(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
            }

            log.info("Finished checking for updates");

            boolean updated = updaters.stream()
                .anyMatch(AbstractGitUpdater::isUpdated);

            if (userInitiated) {
                PopupMessenger.show(
                    l10n("gui.update.notification_title"),
                    l10n(updated
                        ? "gui.update.new_updates_installed"
                        : "gui.update.updated"),
                    2000,
                    MessageTypeEnum.INFO,
                    false, false);
            }

            for (AbstractGitUpdater updater : updaters) {
                if (updater instanceof SelfUpdater selfUpdater) {
                    if (selfUpdater.isUpdated()) {
                        log.info("Restarting to apply updates.");
                        restart();
                        break;
                    }
                }
            }

            if (!downloadManager.isMainDownloaderInitialized()) {
                log.error("Failed to initialize yt-dlp, the program cannot continue. Exiting...");

                if (!userInitiated) {
                    shutdown();
                }
            }

            downloadManager.unblock();
            clipboardManager.unblock();

            if (!userInitiated) {
                downloadManager.init();
            }

            ffmpegTranscoder.init();
        }, 5);

        return true;
    }

    /**
     * Builds the system tray menu.
     */
    private PopupMenu buildPopupMenu() {
        PopupMenu popup = new PopupMenu();

        popup.add(buildMenuItem(l10n("gui.toggle_downloads"), (ActionEvent e) -> {
            downloadManager.toggleDownloads();
        }));

        popup.add(buildMenuItem(l10n("gui.open_downloads_directory"), (ActionEvent e) -> {
            openDownloadsDirectory();
        }));

        popup.add(buildMenuItem(l10n("settings.sidebar_title"), (ActionEvent e) -> {
            guiManager.displaySettingsPanel();
        }));

        popup.add(buildMenuItem(l10n("gui.restart"), (ActionEvent e) -> {
            restart();
        }));

        popup.add(buildMenuItem(l10n("gui.exit"), (ActionEvent e) -> {
            log.info("Exiting....");

            shutdown();
        }));

        return popup;
    }

    public void setupAppServer() {
        appServer = new AppServer(this);
        appServer.init();
    }

    /**
     * Helper for building system tray menu entries.
     */
    private MenuItem buildMenuItem(String name, ActionListener actionListener) {
        MenuItem menuItem = new MenuItem(name);
        menuItem.addActionListener(actionListener);

        return menuItem;
    }

    public void openDownloadsDirectory() {
        open(getOrCreateDownloadsDirectory());
    }

    public void openLogFile() {
        open(LoggerUtils.getLogFile());
    }

    public void openConfigFile() {
        open(configFile);
    }

    public void open(File file) {
        try {
            Desktop desktop = Desktop.getDesktop();

            if (file.exists()) {
                desktop.open(file);
            } else {
                log.error("File not found: {}", file);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void openPlaylist(Collection<File> files) {
        if (files.isEmpty()) {
            log.error("No files provided for playlist.");
            return;
        }

        try {
            Desktop desktop = Desktop.getDesktop();

            Path tempPlaylist = Files.createTempFile("playlist_", ".m3u");
            File m3uFile = tempPlaylist.toFile();

            try (
                BufferedWriter writer = new BufferedWriter(new FileWriter(m3uFile))) {
                for (File file : files) {
                    if (file.exists()) {
                        writer.write(file.getAbsolutePath());
                        writer.newLine();
                    } else {
                        log.error("File not found, skipping: {}", file);
                    }
                }
            }

            if (m3uFile.exists()) {
                desktop.open(m3uFile);
            } else {
                log.error("File not found: {}", m3uFile);
            }

            m3uFile.deleteOnExit();
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void openUrlInBrowser(String urlIn) {
        try {
            URL url = new URI(urlIn).toURL();

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(url.toURI());
                    return;
                }
            }

            String os = System.getProperty("os.name").toLowerCase();
            Runtime runtime = Runtime.getRuntime();

            if (os.contains("mac")) {
                runtime.exec(new String[] {"open", url.toString()});
            } else if (os.contains("nix") || os.contains("nux")) {
                String[] browsers = {"xdg-open", "firefox", "google-chrome"};
                boolean success = false;

                for (String browser : browsers) {
                    try {
                        runtime.exec(new String[] {browser, url.toString()});
                        success = true;
                        break;
                    } catch (IOException e) {
                        // Continue to the next browser
                    }
                }

                if (!success) {
                    throw new RuntimeException("No suitable browser found to open the URL.");
                }
            } else if (os.contains("win")) {
                runtime.exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", url.toString()});
            } else {
                throw new UnsupportedOperationException("Unsupported operating system: " + os);
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private static BrowserEnum _cachedBrowser;

    public BrowserEnum getBrowserForCookies() {
        if (_cachedBrowser != null) {
            return _cachedBrowser;
        }

        if (config.getBrowser() == BrowserEnum.UNSET) {
            String os = System.getProperty("os.name").toLowerCase();
            String browserName = "";

            try {
                if (os.contains("win")) {
                    List<String> output = readOutput(
                        "reg",
                        "query",
                        "HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
                        "/v",
                        "Progid"
                    );

                    log.info("Default browser: {}", output);

                    for (String line : output) {
                        if (line.isEmpty() || !line.contains("REG_SZ")) {
                            continue;
                        }

                        BrowserEnum browserEnum = BrowserEnum.getBrowserForName(line);

                        if (browserEnum != BrowserEnum.UNSET) {
                            log.debug("Selected: {}", browserEnum);
                            browserName = browserEnum.getName();
                            break;
                        }
                    }
                } else if (os.contains("mac")) {
                    List<String> output = readOutput(
                        "bash",
                        "-c",
                        "defaults read ~/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure | awk -F '\"' '/http;/{print window[(NR)-1]}{window[NR]=$2}'");

                    log.info("Default browser: {}", output);

                    browserName = output.getFirst();
                } else if (os.contains("nix") || os.contains("nux")) {
                    List<String> output = readOutput("xdg-settings", "get", "default-web-browser");

                    log.info("Default browser: {}", output);

                    for (String line : output) {
                        if (!line.isEmpty()) {
                            browserName = line.trim();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error querying for browser", e);
            }

            BrowserEnum browser = BrowserEnum.getBrowserForName(browserName);

            if (browser == BrowserEnum.UNSET) {// Everything failed, let's try to take a guess and hope for the best.
                _cachedBrowser = GDownloader.isWindows() ? BrowserEnum.CHROME
                    : GDownloader.isMac() ? BrowserEnum.SAFARI
                    : BrowserEnum.FIREFOX;
            } else {
                _cachedBrowser = browser;
            }
        } else {
            _cachedBrowser = config.getBrowser();
        }

        return _cachedBrowser;
    }

    // TODO: this could be moved to the settings class itself.
    public void updateConfig() {
        updateConfig(config);
    }

    /**
     * Writes changes made to the Settings class to disk.
     */
    public void updateConfig(Settings configIn) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.valueToTree(configIn);

            OBJECT_MAPPER.readerForUpdating(config).readValue(jsonNode);

            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile, configIn);

            if (configIn.getBrowser() != _cachedBrowser && _cachedBrowser != null) {
                _cachedBrowser = null;

                if (config.isDebugMode()) {
                    log.debug("Cached browser changed to {}", configIn.getBrowser());
                }
            }

            LoggerUtils.setDebugLogLevel(configIn.isDebugMode());
        } catch (IOException e) {
            handleException(e);
        }
    }

    @Nullable
    private List<String> getLaunchCommand() {
        List<String> launchString = null;

        String jarLocation = getJarLocation();
        if (jarLocation != null) {
            String javaHome = System.getProperty("java.home");

            if (nullOrEmpty(javaHome)) {
                javaHome = System.getenv("JAVA_HOME");
            }

            if (notNullOrEmpty(javaHome)) {
                if (!javaHome.endsWith(File.separator)) {
                    javaHome = javaHome + File.separator;
                }

                String javaPath;
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    javaPath = javaHome + "bin" + File.separator + "javaw.exe";
                } else {
                    javaPath = javaHome + "bin" + File.separator + "java";
                }

                String jarString = new File(jarLocation).getAbsolutePath();

                launchString = List.of(javaPath, "-jar", jarString);
            } else {
                log.error("Runtime type is .jar but JAVA_HOME is not set. Cannot restart program if necessary.");
            }
        }

        if (launchString == null && launcher != null) {
            launchString = List.of(launcher);
        }

        String jpackageAppPath = System.getProperty("jpackage.app-path");

        if (launchString == null && jpackageAppPath != null) {
            launchString = List.of(jpackageAppPath);
        }

        if (launchString == null || launchString.isEmpty()) {
            return null;
        }

        log.debug("Launching from {}", launchString);

        return launchString;
    }

    public void restart() {
        restartRequested.set(true);

        shutdown();
    }

    public void shutdown() {
        shutdown(0);
    }

    public void shutdown(int code) {
        System.exit(code);
    }

    public boolean isRestartRequested() {
        return restartRequested.get();
    }

    public void launchNewInstance() {
        List<String> launchString = getLaunchCommand();

        if (launchString == null || launchString.isEmpty()) {
            log.error("Cannot restart, binary location is unknown.");
            return;
        }

        log.info("Next instance launch command: " + launchString);

        ProcessBuilder processBuilder = new ProcessBuilder(launchString);
        processBuilder.environment().remove("_JPACKAGE_LAUNCHER");

        try {
            processBuilder.start();
            log.info("New instance launched with command: {}", launchString);
        } catch (IOException e) {
            log.error("Cannot restart, IO error", e);
        }
    }

    /**
     * Toggles the status of automatic startup
     */
    public void updateStartupStatus() {
        try {
            boolean currentStatus = config.isAutoStart();

            List<String> launchString = getLaunchCommand();

            log.debug("Launch command is: {}", launchString);

            if (launchString == null || launchString.isEmpty()) {
                log.error("Cannot locate runtime binary.");
                return;
            }

            if (isWindows()) {
                ProcessBuilder checkBuilder = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", REGISTRY_APP_NAME);

                Process checkProcess = checkBuilder.start();
                checkProcess.waitFor();

                int checkExitValue = checkProcess.exitValue();

                log.debug("Check startup status: {}", checkExitValue);

                if (checkExitValue == 0 && !currentStatus) {
                    ProcessBuilder deleteBuilder = new ProcessBuilder("reg", "delete",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", REGISTRY_APP_NAME, "/f");

                    Process updateProcess = deleteBuilder.start();
                    updateProcess.waitFor();

                    if (config.isDebugMode()) {
                        int updateExitValue = updateProcess.exitValue();
                        if (updateExitValue == 0) {
                            log.info("Startup entry updated successfully.");
                        } else {
                            log.error("Failed to update startup entry.");
                        }
                    }
                } else if (checkExitValue != 0 && currentStatus) {
                    List<String> regArgs = new ArrayList<>(List.of("reg", "add",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", REGISTRY_APP_NAME,
                        "/t", "REG_SZ",
                        "/d"));

                    List<String> programArgs = new ArrayList<>(launchString);
                    programArgs.add("--no-gui");

                    StringBuilder builder = new StringBuilder();
                    builder.append("\"");

                    for (String arg : programArgs) {
                        if (arg.contains(File.separator)) {
                            builder.append("\\\"").append(arg).append("\\\"").append(" ");
                        } else {
                            builder.append(arg).append(" ");
                        }
                    }

                    if (builder.charAt(builder.length() - 1) == ' ') {
                        builder.deleteCharAt(builder.length() - 1);
                    }

                    builder.append("\"");

                    regArgs.add(builder.toString());
                    regArgs.add("/f");

                    ProcessBuilder createBuilder = new ProcessBuilder(regArgs);

                    Process process = createBuilder.start();
                    int exitCode = process.waitFor();

                    if (config.isDebugMode()) {
                        log.info("Program args: {}", programArgs);
                        log.info("Startup command args: {}", regArgs);

                        if (exitCode == 0) {
                            log.info("Registry entry added successfully.");
                        } else {
                            log.error("Failed to add registry entry. Exit code: {} Command list: {}",
                                exitCode, createBuilder.command());
                        }
                    }
                }
            } else if (isLinux()) {
                File autostartDirectory = DirectoryUtils.getOrCreate(System.getProperty("user.home"), "/.config/autostart");

                File desktopFile = new File(autostartDirectory, REGISTRY_APP_NAME + ".desktop");
                if (!desktopFile.exists() && currentStatus) {
                    Path iconPath = getWorkDirectory().toPath().resolve("icon.png");
                    if (!iconPath.toFile().exists()) {
                        try (
                            InputStream imageStream = getClass().getResourceAsStream(guiManager.getCurrentAppIconPath())) {
                            if (imageStream == null) {
                                throw new FileNotFoundException("Resource not found: " + guiManager.getCurrentAppIconPath());
                            }

                            Files.copy(imageStream, iconPath);
                        }
                    }

                    // We almost give no thoughts to whitespaces on linux, but they can happen.
                    List<String> programArgs = new ArrayList<>(launchString);
                    programArgs.add("--no-gui");

                    StringBuilder builder = new StringBuilder();

                    for (String arg : programArgs) {
                        if (arg.contains(" ")) {
                            builder.append("\"").append(arg).append("\"").append(" ");
                        } else {
                            builder.append(arg).append(" ");
                        }
                    }

                    if (builder.charAt(builder.length() - 1) == ' ') {
                        builder.deleteCharAt(builder.length() - 1);
                    }

                    try (FileWriter writer = new FileWriter(desktopFile)) {
                        writer.write("[Desktop Entry]\n");
                        writer.write("Categories=Network;\n");
                        writer.write("Comment=Start " + REGISTRY_APP_NAME + "\n");
                        writer.write("Exec=" + builder.toString() + "\n");
                        writer.write("Icon=" + iconPath + "\n");
                        writer.write("Terminal=false\n");
                        writer.write("MimeType=\n");
                        writer.write("X-GNOME-Autostart-enabled=true\n");
                        writer.write("Name=" + REGISTRY_APP_NAME + "\n");
                        writer.write("Type=Application\n");
                    }

                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(desktopFile.toPath());

                    permissions.add(PosixFilePermission.OWNER_EXECUTE);
                    permissions.add(PosixFilePermission.GROUP_EXECUTE);
                    permissions.add(PosixFilePermission.OTHERS_EXECUTE);

                    Files.setPosixFilePermissions(desktopFile.toPath(), permissions);

                    log.info("Registered as a startup program.");
                } else if (desktopFile.exists() && !currentStatus) {
                    Files.delete(desktopFile.toPath());

                    log.info("Startup entry removed.");
                }
            } else {
                log.error("Unsupported operation.");
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    public void deduplicateDownloadsDirectory() {
        PopupMessenger.show(
            l10n("gui.deduplication.notification_title"),
            l10n("gui.deduplication.deduplicating"),
            1500,
            MessageTypeEnum.INFO,
            false, false);

        GLOBAL_THREAD_POOL.submitWithPriority(() -> {
            File directory = getDownloadsDirectory();
            if (directory.exists()) {
                DirectoryDeduplicator.deduplicateDirectory(directory);
            }

            PopupMessenger.show(
                l10n("gui.deduplication.notification_title"),
                l10n("gui.deduplication.deduplicated"),
                2000,
                MessageTypeEnum.INFO,
                false, false);
        }, 0);
    }

    public File getOrCreateDownloadsDirectory() {
        return getDownloadsDirectory(true);
    }

    public File getDownloadsDirectory() {
        return getDownloadsDirectory(false);
    }

    public File getDownloadsDirectory(boolean create) {
        File file;
        if (!config.getDownloadsPath().isEmpty()) {
            file = new File(config.getDownloadsPath());
        } else {
            if (isPortable()) {
                file = new File(getDownloadsPath());
            } else {
                file = new File(getDownloadsPath(), REGISTRY_APP_NAME);
            }
        }

        if (create) {
            if (!file.exists()) {
                file.mkdirs();
            }
        }

        return file;
    }

    /**
     * Returns the Downloads path used as default save location.
     */
    private String getDownloadsPath() {
        if (isPortable()) {
            File portableDirectory = getPortableRuntimeDirectory();

            return portableDirectory.toPath() + File.separator + "Downloads";
        } else {
            return System.getProperty("user.home") + File.separator + "Downloads";
        }
    }

    public void setDownloadsPath(File newDir) {
        File oldDir = getDownloadsDirectory();
        if (!oldDir.equals(newDir)) {
            downloadManager.stopDownloads();

            clearCache();
        }

        config.setDownloadsPath(newDir.getAbsolutePath());

        updateConfig();
    }

    public void logUrl(String fileName, String text, Object... replacements) {
        FileUtils.logToFile(getOrCreateDownloadsDirectory(), fileName, text, replacements);
    }

    private void printDebugInformation() {
        log.info("System Properties:");
        Properties properties = System.getProperties();
        properties.forEach((key, value) -> log.info("{}: {}", key, value));

        log.info("Environment Variables:");
        Map<String, String> env = System.getenv();
        env.forEach((key, value) -> log.info("{}: {}", key, value));

        log.info("Code Source: {}", GDownloader.class.getProtectionDomain().getCodeSource().getLocation());

        try {
            Path codeSourcePath = Paths.get(GDownloader.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            log.info("Code source path: {}", codeSourcePath);
        } catch (URISyntaxException e) {
            log.warn("URI syntax error", e);
        }

        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsConfiguration config = device.getDefaultConfiguration();
        AffineTransform transform = config.getDefaultTransform();
        double scaleX = transform.getScaleX();
        double scaleY = transform.getScaleY();

        log.info("ScaleX: {}", scaleX);
        log.info("ScaleY: {}", scaleY);

        int cores = Runtime.getRuntime().availableProcessors();
        log.info("Number of available processor cores: {}", cores);
    }

    public static final void handleException(Throwable e) {
        handleException(e, true);
    }

    public static final void handleException(Throwable e, String message) {
        handleException(e, message, true);
    }

    public static final void handleException(Throwable e, boolean displayToUser) {
        handleException(e, "", true);
    }

    public static final void handleException(Throwable e, @NonNull String message, boolean displayToUser) {
        log.error(!message.isEmpty() ? message : "An exception has been caught", e);

        if (displayToUser && instance != null) {
            PopupMessenger.show(
                l10n("gui.error_popup_title"),
                l10n("gui.error_popup", e.getClass().getSimpleName(), e.getMessage()),
                4000,
                MessageTypeEnum.ERROR,
                true, false);
        }
    }

    public List<String> readOutput(String... command)
        throws IOException, InterruptedException {
        return readOutput(Arrays.asList(command));
    }

    public List<String> readOutput(List<String> command)
        throws IOException, InterruptedException {
        Process process = processMonitor.startProcess(command);

        List<String> list = new ArrayList<>();
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                list.add(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Failed command for {}",
                StringUtils.escapeAndBuildCommandLine(command));
        }

        return Collections.unmodifiableList(list);
    }

    public static boolean isFromJar() {
        return getJarLocation() != null;
    }

    public static boolean isFromJpackage() {
        String appPath = System.getProperty("jpackage.app-path");

        return appPath != null;
    }

    @Nullable
    private static String getJarLocation() {
        try {
            URI jarPath = GDownloader.class.getProtectionDomain().getCodeSource().getLocation().toURI();

            if (jarPath.toString().endsWith(".jar")) {
                return new File(jarPath).getAbsolutePath();
            }
        } catch (URISyntaxException e) {
            // Ignore
        }

        return null;
    }

    private static File getPortableRuntimeDirectory() {
        assert isPortable() : "Call not supported when not in portable mode";

        String appPath = launcher;

        // Give preference to the main executable's path; otherwise, fallback to its own.
        if (appPath == null) {
            appPath = System.getProperty("jpackage.app-path");
        }

        if (appPath != null) {
            File appFile = new File(appPath);
            File parentDir = appFile.getParentFile();

            if (parentDir != null) {
                log.info("Portable runtime directory: {}", parentDir.getAbsolutePath());
                return parentDir;
            }
        }

        throw new IllegalStateException("Program is in portable mode, but work directory could not be determined.");
    }

    private static File _cachedWorkDir;

    public static File getWorkDirectory() {
        if (_cachedWorkDir == null) {
            Path appDir;

            if (isPortable()) {
                File portableDirectory = getPortableRuntimeDirectory();

                appDir = Paths.get(portableDirectory.getAbsolutePath(), "Internal");
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                String userHome = System.getProperty("user.home");

                if (os.contains("win")) {
                    String appData = System.getenv("APPDATA");

                    if (appData != null) {
                        appDir = Paths.get(appData, REGISTRY_APP_NAME);
                    } else {
                        appDir = Paths.get(userHome, "AppData", "Roaming", REGISTRY_APP_NAME);
                    }
                } else if (os.contains("mac")) {
                    appDir = Paths.get(userHome, "Library", "Application Support", REGISTRY_APP_NAME);
                } else {
                    appDir = Paths.get(userHome, "." + REGISTRY_APP_NAME.toLowerCase());
                }
            }

            _cachedWorkDir = DirectoryUtils.getOrCreate(appDir.toFile());
        }

        return _cachedWorkDir;
    }

    public static boolean isMac() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("mac");
    }

    public static boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("windows");
    }

    public static boolean isLinux() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("nux");
    }

    public static File getPortableLockFilePath() {
        return new File(System.getProperty("java.home"), "portable.lock");
    }

    public static boolean isFlaggedAsPortable() {
        String appPath = System.getProperty("jpackage.app-path");

        // Updates are portable versions, but we don't want those to run in portable mode unless explicity determined by the --portable flag.
        if (appPath != null && appPath.contains(UpdaterBootstrap.PREFIX)) {
            return false;
        }

        return getPortableLockFilePath().exists();
    }

    public static void main(String[] args) {
        boolean noGui = false;
        int uiScale = 1;
        boolean fromOta = false;
        boolean disableHWAccel = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--debug")) {
                LoggerUtils.setForcedDebugMode();
            }

            if (args[i].equalsIgnoreCase("--run-ffmpeg-selftest")) {
                FFmpegSelfTester.runSelfTest();
                System.exit(0);
            }

            if (args[i].equalsIgnoreCase("--no-gui")) {
                noGui = true;
            }

            if (args[i].equalsIgnoreCase("--force-ui-scale")) {
                uiScale = Integer.parseInt(args[++i]);// Purposefully fail on bad arguments
            }

            if (args[i].equalsIgnoreCase("--from-ota")) {
                log.info("Sucessfully updated from ota");
                fromOta = true;
            }

            if (args[i].equalsIgnoreCase("--disable-hwaccel")) {
                log.info("Disabled hardware acceleration");
                disableHWAccel = true;
            }

            if (args[i].equalsIgnoreCase("--launcher")) {
                launcher = args[++i];
            }

            if (args[i].equalsIgnoreCase("--portable")) {
                portable = true;
                log.info("Running in portable mode. (Found --portable argument)");
            }
        }

        if (!portable) {
            portable = isFlaggedAsPortable();

            if (portable) {
                log.info("Running in portable mode. (Found lock file)");
            }
        }

        // Initialize AppClient earlier in the boot process to ensure a faster window restore time when another instance is already running.
        // This requires the base installed GDownloader version to be 1.3.4 or higher.
        // Older launchers will still need to jump through an extra hoop (e.g., GDownloader A v1.1 → GDownloader B v1.3 → "wake-up" → GDownloader C → "awakens").
        AppClient appClient = new AppClient();

        if (appClient.tryWakeSingleInstance()) {
            log.info("Starting...");
            // An instance is already running; wake it up and shut down.
            System.exit(0);
        }

        UpdaterBootstrap.tryOta(args, fromOta);

        System.setProperty("sun.java2d.uiScale", String.valueOf(uiScale));// Does not accept double
        System.setProperty("sun.java2d.opengl", !disableHWAccel && !isLinuxAndAmdGpu() ? "true" : "false");

        log.info("Starting...");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Default to Java's look and feel
        }

        try {
            Desktop.getDesktop().enableSuddenTermination();
            Desktop.getDesktop().setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
        } catch (Exception e) {
            // Not windows
        }

        try {
            GlobalScreen.registerNativeHook();

            // Get the logger for "com.github.kwhat.jnativehook" and set the level to off.
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);

            logger.setUseParentHandlers(false);
        } catch (NativeHookException e) {
            log.error("There was a problem registering the native hook.", e);

            System.exit(1);
        }

        GDownloader instance = new GDownloader();
        GDownloader.instance = instance;

        instance.setAppClient(appClient);

        if (!noGui) {
            instance.initUi();
        }

        instance.checkForUpdates(false);

        log.info("{} is initialized", REGISTRY_APP_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException e) {
                log.error("There was a problem unregistering the native hook.", e);
            }

            if (instance.isRestartRequested()) {
                instance.launchNewInstance();
            }

            try {
                instance.getDownloadManager().close();
            } catch (Exception e) {
                log.error("There was a problem closing the download manager", e);
            }

            try {
                instance.getFfmpegTranscoder().close();
            } catch (Exception e) {
                log.error("There was a problem closing the ffmpeg transcoder", e);
            }

            try {
                instance.getProcessMonitor().close();
            } catch (Exception e) {
                log.error("There was a problem closing the process monitor", e);
            }

            try {
                instance.getPersistenceManager().close();
            } catch (Exception e) {
                log.error("There was a problem closing the persistence manager", e);
            }

            try {
                instance.getAppServer().close();
            } catch (Exception e) {
                log.error("There was a problem closing the app server", e);
            }

            try {
                instance.getMainTicker().shutdownNow();
                GLOBAL_THREAD_POOL.shutdownNow();
            } catch (Exception e) {
                log.error("There was a problem closing thread pools", e);
            }
        }));

        Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
            GDownloader.handleException(e);
        });
    }

    public static void setUIFont(FontUIResource fontResource) {
        UIManager.getDefaults().keys().asIterator()
            .forEachRemaining(key -> {
                Object value = UIManager.get(key);

                if (key.toString().contains("FileChooser")) {
                    return;
                }

                if (value instanceof FontUIResource) {
                    UIManager.put(key, fontResource);
                }
            });
    }

    public static void setUIFontSize(int size) {
        UIManager.getDefaults().keys().asIterator()
            .forEachRemaining(key -> {
                Object value = UIManager.get(key);

                if (value instanceof FontUIResource resource) {
                    Font newFont = resource.deriveFont((float)size);

                    UIManager.put(key, new FontUIResource(newFont));
                }
            });
    }

    // [xcb] Unknown sequence number while processing queue
    // [xcb] You called XInitThreads, this is not your fault
    // [xcb] Aborting, sorry about that.
    // java: ../../src/xcb_io.c:278: poll_for_event: Assertion `!xcb_xlib_threads_sequence_lost' failed.
    // Aborted (core dumped)
    private static boolean isLinuxAndAmdGpu() {
        if (!isLinux()) {
            return false;
        }

        try {
            Process process = Runtime.getRuntime().exec(new String[] {"/bin/bash", "-c", "lspci | grep VGA"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            boolean isAMD = false;

            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("amd")
                    || lower.contains("radeon")
                    || lower.contains("advanced micro devices")) {
                    isAMD = true;
                    break;
                }
            }

            process.waitFor();

            if (isAMD) {
                log.error("Detected AMD Graphics, disabling HW acceleration due to a known issue.");
                return true;
            }
        } catch (Exception e) {
            log.error("Unable to query for GPU info.");

            if (log.isDebugEnabled()) {
                log.error("Exception: ", e);
            }
        }

        return false;
    }
}
