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
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
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
import net.brlns.gdownloader.event.impl.SettingsChangeEvent;
import net.brlns.gdownloader.ffmpeg.FFmpegSelfTester;
import net.brlns.gdownloader.ffmpeg.FFmpegTranscoder;
import net.brlns.gdownloader.persistence.PersistenceManager;
import net.brlns.gdownloader.persistence.entity.CounterTypeEnum;
import net.brlns.gdownloader.process.ProcessMonitor;
import net.brlns.gdownloader.server.AppClient;
import net.brlns.gdownloader.server.AppServer;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.BrowserEnum;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import net.brlns.gdownloader.updater.*;
import net.brlns.gdownloader.util.*;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.updater.UpdaterBootstrap.*;
import static net.brlns.gdownloader.util.LoggerUtils.initLogFile;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

// TODO media converter
// TODO implement CD Ripper
// TODO d&d files for conversion to different formats, we already have ffmpeg anyway
//
// TODO max simultaneous downloads should be independent per website
// TODO scale on resolution DPI
// TODO verify checksums during updates, add bouncycastle, check signatures
// TODO write a component factory for GUIManager
// FEEDBACK Should choose to download video and audio independently on each card
// DROPPED check updates on a timer, but do not ever restart when anything is in the queue.
// TODO --no-playlist when single video option is active
// TODO Artifacting seems to be happening on the scroll pane with any video card
// TODO open a window asking which videos in a playlist to download or not
// TODO Better visual eye candy for when dragging cards
// TODO Javadoc, a whole lot of it.
// TODO Twitch settings purposefully default to suboptimal quality due to huge file sizes. Maybe consider adding a warning about this in the GUI.
// TODO Split GUI into a different subproject from core logic.
// TODO Investigate screen reader support (https:// www.nvaccess.org/download/)
// TODO Send notifications when a NO_METHOD is triggered, explaining why it was triggered.
// TODO Test downloading sections of a livestream (currently it gets stuck on status PREPARING). Note: it also leaves a zombie ffmpeg process behind dealing with the hls stream.
// TODO The issue above is a yt-dlp bug https:// github.com/yt-dlp/yt-dlp/issues/7927
// TODO Notify the user whenever a setting that requires restart was changed.
// TODO Add an url ignore list / Allow filters to be disabled
// TODO Add option to clear all installed updates and start fresh. (Tackling certain issues where failed updates could break downloads)
// TODO Wget integration
// TODO NTFS File path length workaround for gallery-dl
// TODO Split main window from GUIManager
// TODO gallery-dl does not accept an argument specifying yt-dlp/ffmpeg location, figure out a workaround to pass the correct path to it
// TODO Fastutil collections
// TODO Proxy settings should be add to the UI, fields should be validated on the fly
// TODO Tabs in settings for the different downloaders
// TODO Crawl for valid links that can be consumed by direct-http
// TODO Two column layout when in full screen
// TODO Fix notification line wrapping
// TODO Downloader priority settings
// TODO Notifications are appearing below the main window when in fullscreen mode.
// TODO About page
// TODO Confirm dialog before clearing DL queue
// TODO Move config files, downloaders and their respective data to subfolders
// TODO ctrl+z to undo removals
// TODO implement remuxing to mkv
// TODO System/provided binary selection should be individually configurable per downloader
// TODO display number in download queue
// TODO automatic ui sorting
// TODO Manually mark downloads as complete, correctly move files to final directory
// TODO Make download paths for the different downloaders customizable
// TODO Fetch favicons for url filters
// TODO Direct-HTTP: user-agent
// TODO Tags/Filtering
// TODO Remove ffmpeg requirement by omitting transcoding arguments
// TODO Keep track of file hashes to avoid duplicates if such setting is turned on
// TODO Investigate issue where video files get downloaded with audio name pattern applied
// TODO Implement plugin API, create example plugin, create more events.
// prio
// TODO save last window size in config
// TODO Right Click > Skip Download
// TODO when changing download path, move the cache directory to the new location. Need to take available space into consideration
// TODO Taskbar progress bar
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

    // Virtual threads remove the limitations of a small thread pool.
    // Task prioritization is no longer necessary.
    public final static ExecutorService GLOBAL_THREAD_POOL
        = Executors.newVirtualThreadPerTaskExecutor();

    @Getter
    private static GDownloader instance;

    private static String launcher;

    @Getter
    private static boolean portable;

    @Getter
    private boolean systemTrayInitialized;

    private SystemTray tray;
    @Getter
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
    private UpdateManager updateManager;

    @Getter
    private GUIManager guiManager;

    @Getter
    private FFmpegTranscoder ffmpegTranscoder;

    @Getter
    private ProcessMonitor processMonitor;

    @Getter
    private NetworkConnectivityListener connectivityListener;

    @Getter
    private boolean initialized = false;

    private final AtomicBoolean uiInitialized = new AtomicBoolean();

    @Getter(AccessLevel.PRIVATE)
    private final ScheduledExecutorService mainTicker
        = Executors.newScheduledThreadPool(1);

    private final AtomicBoolean restartRequested = new AtomicBoolean(false);

    @Getter
    @Setter
    private AppClient appClient;

    @Getter
    private AppServer appServer;

    public GDownloader() {
        // Initialize the config file
        File workDir = getWorkDirectory();

        initLogFile(workDir);

        initConfig(workDir);

        initLanguage(config);

        updateConfig();

        log.info(l10n("_startup"));

        ThemeProvider.setTheme(config.getTheme());

        if (!config.isUseSystemFont()) {
            GUIManager.setUIFont(new FontUIResource("Dialog", Font.BOLD, config.getFontSize()));
        } else {
            GUIManager.setUIFontSize(config.getFontSize());
        }

        try {
            setupAppServer();

            processMonitor = new ProcessMonitor();
            persistenceManager = new PersistenceManager(this);
            persistenceManager.init();

            GLOBAL_THREAD_POOL.execute(() -> {
                // Prime the db by preloading some random item before the updaters even fire up
                persistenceManager.getCounters()
                    .getCurrentValue(CounterTypeEnum.DOWNLOAD_ID);
            });

            updateManager = new UpdateManager(this);

            ffmpegTranscoder = new FFmpegTranscoder(processMonitor);
            clipboardManager = new ClipboardManager(this);
            downloadManager = new DownloadManager(this);
            guiManager = new GUIManager(this);

            connectivityListener = new NetworkConnectivityListener();

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

    public void initMainWindow(boolean silently) {
        if (!silently) {
            guiManager.createAndShowGUI();
        } else {
            guiManager.createGUISilently();
        }

        runStartupTasks();
    }

    public void runStartupTasks() {
        if (!uiInitialized.compareAndSet(false, true)) {
            return;
        }

        try {
            registerToSystemTray();

            updateManager.checkForUpdates(true);

            StartupManager.updateAutoStartupState(this);

            startLooperTasks();

            connectivityListener.startBackgroundConnectivityCheck();
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void registerToSystemTray() {
        if (SystemTray.isSupported()) {
            try {
                tray = SystemTray.getSystemTray();

                Image image = Toolkit.getDefaultToolkit().createImage(
                    getClass().getResource(guiManager.getCurrentTrayIconPath()));

                trayIcon = new TrayIcon(image, REGISTRY_APP_NAME, buildPopupMenu());
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener((ActionEvent e) -> {
                    initUi(false);
                });

                tray.add(trayIcon);

                systemTrayInitialized = true;
            } catch (AWTException e) {
                log.error("Error initializing the system tray");
                handleException(e);
            }
        } else {
            log.error("System tray is not supported, some features may not work.");
        }
    }

    private void startLooperTasks() {
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
    }

    public void runPostUpdateInitTasks() {
        GLOBAL_THREAD_POOL.execute(() -> {
            downloadManager.init();
            ffmpegTranscoder.init();
        });
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
            PopupMessenger.show(Message.builder()
                .title("gui.clear_cache.notification_title")
                .message("gui.clear_cache.cleared")
                .durationMillis(2000)
                .messageType(MessageTypeEnum.INFO)
                .discardDuplicates(true)
                .build());
        }
    }

    public void initUi(boolean silently) {
        if (initialized) {
            if (!silently && config.isShowWelcomeScreen()) {
                guiManager.displayWelcomeScreen();
            } else {
                initMainWindow(silently);
            }
        }
    }

    /**
     * Builds the system tray menu.
     */
    private PopupMenu buildPopupMenu() {
        PopupMenu popup = new PopupMenu();

        popup.add(buildMenuItem(l10n("gui.toggle_downloads"),
            e -> downloadManager.toggleDownloads()));

        popup.add(buildMenuItem(l10n("gui.open_downloads_directory"),
            e -> openDownloadsDirectory()));

        popup.add(buildMenuItem(l10n("settings.sidebar_title"),
            e -> guiManager.displaySettingsPanel()));

        popup.add(buildMenuItem(l10n("gui.restart"),
            e -> restart()));

        popup.add(buildMenuItem(l10n("gui.exit"),
            e -> shutdown()));

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

    public void openWorkDirectory() {
        open(getWorkDirectory());
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

    private void initConfig(File workDir) {
        configFile = new File(workDir, "config.json");

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

            log.error("I/O Error initializing the config file, settings have been reset.", e);
        }

        log.info("Loaded config file");
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

            EventDispatcher.dispatch(SettingsChangeEvent.builder()
                .settings(configIn)
                .build());
        } catch (IOException e) {
            handleException(e);
        }
    }

    @Nullable
    public List<String> getLaunchCommand() {
        List<String> launchString = null;

        if (launcher != null) {
            if (launcher.endsWith(".jar") && !launcher.contains(" -jar ")) {
                launchString = getJarLaunchCommand(new File(launcher));
            } else {
                launchString = List.of(launcher);
            }
        }

        File jarLocation = getJarLocation();
        if (launchString == null && jarLocation != null) {
            launchString = getJarLaunchCommand(jarLocation);
        }

        String appImage = getAppImageLauncher();
        if (launchString == null && notNullOrEmpty(appImage)) {
            launchString = List.of(appImage);
        }

        String jpackageAppPath = getJpackageLauncher();
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
        log.info("Exiting....");
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

    public void deduplicateDownloadsDirectory() {
        PopupMessenger.show(Message.builder()
            .title("gui.deduplication.notification_title")
            .message("gui.deduplication.deduplicating")
            .durationMillis(1500)
            .messageType(MessageTypeEnum.INFO)
            .build());

        GLOBAL_THREAD_POOL.execute(() -> {
            File directory = getDownloadsDirectory();
            if (directory.exists()) {
                DirectoryDeduplicator.deduplicateDirectory(directory);
            }

            PopupMessenger.show(Message.builder()
                .title("gui.deduplication.notification_title")
                .message("gui.deduplication.deduplicated")
                .durationMillis(2000)
                .messageType(MessageTypeEnum.INFO)
                .build());
        });
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
            log.warn("Command failed, exit code: {} args: {}", exitCode,
                StringUtils.escapeAndBuildCommandLine(command));
        }

        return Collections.unmodifiableList(list);
    }

    public static boolean isFromJar() {
        return getJarLocation() != null;
    }

    public static boolean isFromJpackage() {
        return !isFromAppImage() && notNullOrEmpty(System.getProperty("jpackage.app-path"));
    }

    public static boolean isFromAppImage() {
        return notNullOrEmpty(System.getenv("APPIMAGE"));
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
        // Updates are portable versions, but we don't want those to run in portable
        // mode unless explicity determined by the --portable flag.
        if (appPath != null && appPath.contains(UpdaterBootstrap.PREFIX)) {
            return false;
        }

        return getPortableLockFilePath().exists();
    }

    private static void printDebugInformation() {
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
            PopupMessenger.show(Message.builder()
                .title("gui.error_popup_title")
                .message("gui.error_popup", e.getClass().getSimpleName(), e.getMessage())
                .durationMillis(4000)
                .messageType(MessageTypeEnum.ERROR)
                .playTone(true)
                .build());
        }
    }

    public static void main(String[] args) {
        boolean noGui = false;
        int uiScale = 1;
        boolean fromOta = false;
        //boolean disableHWAccel = false;

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

            //if (args[i].equalsIgnoreCase("--disable-hwaccel")) {
            //    log.info("Disabled hardware acceleration");
            //    disableHWAccel = true;
            //}
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
        // TODO: there seems to be artifacting issues on fluxbox and sometimes Windows 11
        //System.setProperty("sun.java2d.opengl", !disableHWAccel && !isLinuxAndAmdGpu() ? "true" : "false");
        //System.setProperty("sun.java2d.d3d", "true");

        log.info("Starting...");

        if (log.isDebugEnabled()) {
            printDebugInformation();
        }

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

        instance.initUi(noGui);

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
                instance.getConnectivityListener().close();
            } catch (Exception e) {
                log.error("There was a problem closing the connectivity listener", e);
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
}
