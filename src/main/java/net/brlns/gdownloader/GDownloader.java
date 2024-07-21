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
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.UIManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.ui.GUIManager;
import net.brlns.gdownloader.ui.GUIManager.MessageType;
import net.brlns.gdownloader.ui.themes.ThemeProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import static net.brlns.gdownloader.Language.*;

/**
 * GDownloader - GUI wrapper for yt-dlp
 *
 * As of 2024-07-17 only tested in Kubuntu
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class GDownloader{

    /**
     * Constants and application states
     */
    public static final String REGISTRY_APP_NAME = "GDownloader";//Don't change

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemTray tray;
    private TrayIcon trayIcon = null;

    private File configFile;

    @Getter
    private Settings config;

    @Getter
    @Setter
    private boolean watchClipboard = true;

    @Getter
    private YtDlpDownloader downloadManager;

    @Getter
    private YtDlpUpdater ytDlpUpdater;

    @Getter
    private GUIManager guiManager;

    @Getter
    private boolean initialized = false;

    private final Clipboard clipboard;
    private final Map<FlavorType, String> lastClipboardState = new HashMap<>();

    private ExecutorService threadPool = new ThreadPoolExecutor(0, 5,
        1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    public GDownloader(){
        //Initialize the config file
        configFile = new File(getWorkDirectory(), "config.json");

        if(!configFile.exists()){
            config = new Settings();
            updateConfig();
        }

        try{
            config = OBJECT_MAPPER.readValue(configFile, Settings.class);
        }catch(IOException e){
            config = new Settings();
            updateConfig();

            handleException(e);
        }

        if(config.isDebugMode()){
            //Slf4j in particular does not allow switching log level during runtime
            //Here we'd like to have the option to toggle it on the fly
            //Hence why this is not using not log.debug
            log.info("Loaded config file");

            printDebugInformation();
        }

        Language.initLanguage(config.getLanguage());
        log.info(Language.get("startup"));

        ThemeProvider.setTheme(config.getTheme());

        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        tray = SystemTray.getSystemTray();

        try{
            downloadManager = new YtDlpDownloader(this);

            guiManager = new GUIManager(this);

            //Register to the system tray
            Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource(guiManager.getCurrentTrayIconPath()));
            trayIcon = new TrayIcon(image, REGISTRY_APP_NAME, buildPopupMenu());
            trayIcon.setImageAutoSize(true);

            trayIcon.addActionListener((ActionEvent e) -> {
                if(initialized){
                    guiManager.wakeUp();
                }
            });

            tray.add(trayIcon);

            ytDlpUpdater = new YtDlpUpdater(this);

            threadPool.execute(() -> {
                try{
                    ytDlpUpdater.init();

                    downloadManager.unblock();
                }catch(Exception e){
                    handleException(e);

                    if(ytDlpUpdater.getYtDlpExecutablePath() == null || !ytDlpUpdater.getYtDlpExecutablePath().exists()){
                        log.error("Failed to initialize YT-DLP");
                        System.exit(0);
                    }
                }
            });

            updateStartupStatus();

            GlobalScreen.addNativeKeyListener(new NativeKeyListener(){
                @Override
                public void nativeKeyPressed(NativeKeyEvent e){
                    try{
                        if((e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0
                            && e.getKeyCode() == NativeKeyEvent.VC_C){
                            resetClipboard();

                            //TODO check how the clipboard deals with concurrency
                            updateClipboard();
                        }
                    }catch(Exception ex){
                        handleException(ex);
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e){
                    //Not implemented
                }

                @Override
                public void nativeKeyTyped(NativeKeyEvent e){
                    //Not implemented
                }
            });

            AtomicInteger spinCounter = new AtomicInteger();

            Runnable processClipboard = () -> {
                updateClipboard();

                downloadManager.processQueue();

                if(spinCounter.incrementAndGet() % 1728000 == 0){
                    //try{
                    //    tray.remove(trayIcon);
                    //    tray.add(trayIcon);
                    //}catch(AWTException e){
                    //    throw new RuntimeException(e);
                    //}
                }
            };

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            scheduler.scheduleAtFixedRate(processClipboard, 0, 50, TimeUnit.MILLISECONDS);

            initialized = true;
        }catch(Exception e){
            handleException(e);
        }
    }

    public void initUi(){
        guiManager.wakeUp();
    }

    /**
     * Builds the system tray menu.
     */
    private PopupMenu buildPopupMenu(){
        PopupMenu popup = new PopupMenu();

        popup.add(buildMenuItem(get("settings.sidebar_title"), (ActionEvent e) -> {
            guiManager.displaySettingsPanel();
        }));

        popup.add(buildMenuItem(get("gui.exit"), (ActionEvent e) -> {
            log.info("Exiting....");

            System.exit(0);
        }));

        return popup;
    }

    public void open(File file){
        try{
            Desktop desktop = Desktop.getDesktop();

            if(file.exists()){
                desktop.open(file);
            }else{
                log.error("File not found: {}", file);
            }
        }catch(Exception e){
            handleException(e);
        }
    }

    public void openUrlInBrowser(String urlIn){
        try{
            URL url = new URI(urlIn).toURL();

            if(Desktop.isDesktopSupported()){
                Desktop desktop = Desktop.getDesktop();

                if(desktop.isSupported(Desktop.Action.BROWSE)){
                    desktop.browse(url.toURI());
                    return;
                }
            }

            String os = System.getProperty("os.name").toLowerCase();
            Runtime runtime = Runtime.getRuntime();

            if(os.contains("mac")){
                runtime.exec(new String[]{"open", url.toString()});
            }else if(os.contains("nix") || os.contains("nux")){
                String[] browsers = {"xdg-open", "firefox", "google-chrome"};
                boolean success = false;

                for(String browser : browsers){
                    try{
                        runtime.exec(new String[]{browser, url.toString()});
                        success = true;
                        break;
                    }catch(IOException e){
                        //Continue to the next browser
                    }
                }

                if(!success){
                    throw new RuntimeException("No suitable browser found to open the URL.");
                }
            }else if(os.contains("win")){
                runtime.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url.toString()});
            }else{
                throw new UnsupportedOperationException("Unsupported operating system: " + os);
            }
        }catch(Exception e){
            handleException(e);
        }
    }

    /**
     * Helper for building system tray menu entries.
     */
    private MenuItem buildMenuItem(String name, ActionListener actionListener){
        MenuItem menuItem = new MenuItem(name);
        menuItem.addActionListener(actionListener);

        return menuItem;
    }

    public void updateConfig(){
        updateConfig(config);
    }

    /**
     * Writes changes made to the Settings class to disk.
     */
    public void updateConfig(Settings configIn){
        try{
            JsonNode jsonNode = OBJECT_MAPPER.valueToTree(configIn);

            OBJECT_MAPPER.readerForUpdating(config).readValue(jsonNode);

            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile, configIn);
        }catch(IOException e){
            handleException(e);
        }
    }

    /**
     * Toggles the status of automatic startup
     */
    public void updateStartupStatus(){
        try{
            boolean currentStatus = config.isAutoStart();

            URI jarPath = GDownloader.class.getProtectionDomain().getCodeSource().getLocation().toURI();

            String launchString;
            if(jarPath.toString().endsWith(".jar")){
                launchString = new File(jarPath).getAbsolutePath();

                String extension = isWindows() ? ".bat" : ".sh";

                launchString = launchString.replaceAll(Pattern.quote("build" + File.separator + "libs" + File.separator) + "GDownloader-.*\\.jar", "start" + extension);
                launchString = launchString.replace("target" + File.separator + "GDownloader.jar", "start" + extension);
                launchString = launchString.replace("GDownloader.jar", "start" + extension);
                launchString = launchString.replace("classes", "start" + extension);
            }else{
                launchString = System.getProperty("jpackage.app-path");
            }

            if(launchString.isEmpty()){
                log.error("Cannot locate runtime binary {}", jarPath);
                return;
            }

            if(config.isDebugMode()){
                log.info("Jar path {}", jarPath);
                log.info("Launching from {}", launchString);
            }

            if(isWindows()){
                ProcessBuilder checkBuilder = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                    "/v", REGISTRY_APP_NAME);

                Process checkProcess = checkBuilder.start();
                checkProcess.waitFor();

                int checkExitValue = checkProcess.exitValue();

                if(checkExitValue == 0 && !currentStatus){
                    ProcessBuilder deleteBuilder = new ProcessBuilder("reg", "delete",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", REGISTRY_APP_NAME, "/f");

                    Process updateProcess = deleteBuilder.start();
                    updateProcess.waitFor();

                    if(config.isDebugMode()){
                        int updateExitValue = updateProcess.exitValue();
                        if(updateExitValue == 0){
                            log.info("Startup entry updated successfully.");
                        }else{
                            log.error("Failed to update startup entry.");
                        }
                    }
                }else if(checkExitValue != 0 && currentStatus){

                    ProcessBuilder createBuilder = new ProcessBuilder(
                        "reg", "add",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", REGISTRY_APP_NAME,
                        "/t", "REG_SZ",
                        "/d", "\\\"" + launchString + " --no-gui\\\"",
                        "/f"
                    );

                    Process process = createBuilder.start();
                    int exitCode = process.waitFor();

                    if(config.isDebugMode()){
                        if(exitCode == 0){
                            log.info("Registry entry added successfully.");
                        }else{
                            log.error("Failed to add registry entry. Exit code: " + exitCode);
                        }
                    }
                }
            }else{
                File autostartDirectory = getOrCreate(System.getProperty("user.home"), "/.config/autostart");

                File desktopFile = new File(autostartDirectory, REGISTRY_APP_NAME + ".desktop");
                if(!desktopFile.exists() && currentStatus){
                    Path iconPath = getWorkDirectory().toPath().resolve("icon.png");
                    if(!iconPath.toFile().exists()){
                        try(InputStream imageStream = getClass().getResourceAsStream(guiManager.getCurrentAppIconPath())){
                            if(imageStream == null){
                                throw new FileNotFoundException("Resource not found: " + guiManager.getCurrentAppIconPath());
                            }

                            Files.copy(imageStream, iconPath);
                        }
                    }

                    try(FileWriter writer = new FileWriter(desktopFile)){
                        writer.write("[Desktop Entry]\n");
                        writer.write("Categories=Qt;KDE;Internet;\n");
                        writer.write("Comment=Start " + REGISTRY_APP_NAME + "\n");
                        writer.write("Exec=" + launchString + " --no-gui\n");
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
                }else if(desktopFile.exists() && !currentStatus){
                    Files.delete(desktopFile.toPath());

                    log.info("Startup entry removed.");
                }
            }
        }catch(Exception e){
            handleException(e);
        }
    }

    public File getOrCreateDownloadsDirectory(){
        File file;
        if(!config.getDownloadsPath().isEmpty()){
            file = new File(config.getDownloadsPath());
        }else{
            file = new File(getDownloadsPath(), REGISTRY_APP_NAME);
        }

        if(!file.exists()){
            file.mkdirs();
        }

        return file;
    }

    /**
     * Returns the Downloads path used as default save location.
     */
    private String getDownloadsPath(){
        return System.getProperty("user.home") + File.separator + "Downloads";
    }

    public void setDownloadsPath(File newDir){
        File oldDir = getOrCreateDownloadsDirectory();
        if(!oldDir.equals(newDir)){
            downloadManager.stopDownloads();

            deleteRecursively(oldDir.toPath());
        }

        config.setDownloadsPath(newDir.getAbsolutePath());

        updateConfig();
    }

    private File _cachedWorkDir;

    public File getWorkDirectory(){
        if(_cachedWorkDir == null){
            String os = System.getProperty("os.name").toLowerCase();
            String userHome = System.getProperty("user.home");

            Path appDir;

            if(os.contains("win")){
                String appData = System.getenv("APPDATA");

                if(appData != null){
                    appDir = Paths.get(appData, REGISTRY_APP_NAME);
                }else{
                    appDir = Paths.get(userHome, "AppData", "Roaming", REGISTRY_APP_NAME);
                }
            }else if(os.contains("mac")){
                appDir = Paths.get(userHome, "Library", "Application Support", REGISTRY_APP_NAME);
            }else{
                appDir = Paths.get(userHome, "." + REGISTRY_APP_NAME.toLowerCase());
            }

            _cachedWorkDir = getOrCreate(appDir.toFile());
        }

        return _cachedWorkDir;
    }

    private boolean resetOnce = false;

    //TODO refactor clipboard
    private void resetClipboard(){
        if(resetOnce){
            return;
        }

        resetOnce = true;

        for(FlavorType type : new HashSet<>(lastClipboardState.keySet())){
            lastClipboardState.put(type, "reset");
        }
    }

    public boolean updateClipboard(){
        return updateClipboard(null, false);
    }

    public boolean updateClipboard(Transferable transferable, boolean force){
        boolean success = false;

        if(watchClipboard || force){
            if(transferable == null && !force){
                transferable = clipboard.getContents(null);
            }

            if(transferable == null){
                return false;
            }

            if(transferable.isDataFlavorSupported(FlavorType.STRING.getFlavor())){
                try{
                    String data = (String)transferable.getTransferData(FlavorType.STRING.getFlavor());

                    if(!force){
                        processClipboardData(FlavorType.STRING, data);
                    }else{
                        handleClipboardInput(data);
                    }

                    success = true;
                }catch(UnsupportedFlavorException | IOException e){
                    log.warn(e.getLocalizedMessage());
                }
            }

            if(transferable.isDataFlavorSupported(FlavorType.HTML.getFlavor())){
                try{
                    String data = (String)transferable.getTransferData(FlavorType.HTML.getFlavor());

                    if(!force){
                        processClipboardData(FlavorType.HTML, data);
                    }else{
                        handleClipboardInput(data);
                    }

                    success = true;
                }catch(UnsupportedFlavorException | IOException e){
                    log.warn(e.getLocalizedMessage());
                }
            }
        }

        return success;
    }

    private void handleClipboardInput(String data){
        threadPool.execute(() -> {
            List<CompletableFuture<Boolean>> list = new ArrayList<>();

            for(String url : extractUrlsFromString(data)){
                if(url.startsWith("http")){
                    list.add(downloadManager.captureUrl(url));
                }

                if(url.startsWith("magnet")){
                    logUrl(url, "magnets");
                }
            }

            CompletableFuture<Void> futures = CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));

            futures.thenRun(() -> {
                int captured = 0;

                List<Boolean> results = list.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

                for(boolean result : results){
                    if(result){
                        captured++;
                    }
                }

                if(captured > 0){
                    guiManager.showMessage(
                        get("gui.clipboard_monitor.captured_title"),
                        get("gui.clipboard_monitor.captured", captured),
                        2500,
                        MessageType.INFO,
                        false
                    );

                    guiManager.requestFocus();
                }
            });

            try{
                futures.get(1l, TimeUnit.MINUTES);
            }catch(InterruptedException | ExecutionException e){
                handleException(e);
            }catch(TimeoutException e){
                log.warn("Timed out waiting for futures");
            }
        });
    }

    private void processClipboardData(FlavorType flavorType, String data){
        if(!lastClipboardState.containsKey(flavorType)){ // Ignore state during startup
            lastClipboardState.put(flavorType, data);
        }else{
            String last = lastClipboardState.get(flavorType);

            if(!last.equals(data)){
                lastClipboardState.put(flavorType, data);

                handleClipboardInput(data);
            }
        }
    }

    private static final Object _logSync = new Object();

    private void logUrl(String format, String file, Object... params){
        FormattingTuple ft = MessageFormatter.arrayFormat(format, params);
        String message = ft.getMessage();

        synchronized(_logSync){
            try(FileWriter fw = new FileWriter(getOrCreateDownloadsDirectory().toPath().resolve(file + ".txt").toFile(), true);
                PrintWriter pw = new PrintWriter(fw)){
                for(String str : message.split("\n")){
                    pw.println(str);
                }
            }catch(IOException e){
                //Ignore
            }
        }
    }

    private ArrayList<String> extractUrlsFromString(String content){
        ArrayList<String> result = new ArrayList<>();

        Document doc = Jsoup.parse(content);

        Elements links = doc.select("a[href]");
        Elements media = doc.select("[src]");

        if(config.isDebugMode()){
            log.info("Found {} Links and {} Media", links.size(), media.size());
        }

        if(links.isEmpty() && media.isEmpty()){
            String regex = "(http[^\\s]*|magnet:[^\\s]*)(?=\\s|$|http|magnet:)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content);

            while(matcher.find()){
                String url = matcher.group(1);

                if(isValidURL(url)){
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

    private void printDebugInformation(){
        log.info("System Properties:");
        Properties properties = System.getProperties();
        properties.forEach((key, value) -> log.info("{}: {}", key, value));

        log.info("Environment Variables:");
        Map<String, String> env = System.getenv();
        env.forEach((key, value) -> log.info("{}: {}", key, value));

        log.info("Code Source: {}", GDownloader.class.getProtectionDomain().getCodeSource().getLocation());

        try{
            Path codeSourcePath = Paths.get(GDownloader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            log.info("Code source path: {}", codeSourcePath);
        }catch(URISyntaxException e){
            e.printStackTrace();
        }

        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        GraphicsConfiguration config = device.getDefaultConfiguration();
        AffineTransform transform = config.getDefaultTransform();
        double scaleX = transform.getScaleX();
        double scaleY = transform.getScaleY();

        log.info("ScaleX: {}", scaleX);
        log.info("ScaleY: {}", scaleY);
    }

    public final void handleException(Throwable e){
        handleException(e, true);
    }

    public final void handleException(Throwable e, boolean displayToUser){
        e.printStackTrace();

        if(displayToUser){
            guiManager.showMessage(
                get("gui.error_popup_title"),
                get("gui.error_popup", e.getLocalizedMessage()),
                4000,
                MessageType.ERROR,
                true);
        }
    }

    private static boolean isValidURL(String urlString){
        try{
            new URI(urlString).toURL();
            return true;
        }catch(Exception e){
            return false;
        }
    }

    public static void deleteRecursively(Path directory){
        if(!Files.exists(directory)){
            return;
        }

        try(Stream<Path> dirStream = Files.walk(directory)){
            dirStream
                .sorted(Comparator.reverseOrder()) //Ensure deeper directories are deleted first
                .forEach(file -> {
                    try{
                        Files.deleteIfExists(file);
                    }catch(IOException e){
                        log.error("Failed to delete: {} {}", file, e.getLocalizedMessage());
                    }
                });

        }catch(IOException e){
            log.error("Failed to delete: {} {}", directory, e.getLocalizedMessage());
        }
    }

    public static File getOrCreate(String dir, String... path){
        return getOrCreate(new File(dir), path);
    }

    public static File getOrCreate(File dir, String... path){
        File file = new File(dir, String.join(File.separator, path));
        file.mkdirs();

        return file;
    }

    public static boolean isWindows(){
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("windows");
    }

    public static void main(String[] args){
        System.setProperty("sun.java2d.uiScale", "1");
        System.setProperty("sun.java2d.opengl", "true");

        if(SystemTray.isSupported()){
            log.info("Starting...");

            try{
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }catch(Exception e){
                //Default to Java's look and feel
            }

            //setUIFont(new FontUIResource("Dialog", Font.BOLD, 12));
            try{
                GlobalScreen.registerNativeHook();

                //Get the logger for "com.github.kwhat.jnativehook" and set the level to off.
                Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
                logger.setLevel(Level.OFF);

                logger.setUseParentHandlers(false);
            }catch(NativeHookException e){
                System.err.println("There was a problem registering the native hook.");
                e.printStackTrace();

                System.exit(1);
            }

            GDownloader instance = new GDownloader();

            boolean noGui = false;

            for(String arg : args){
                if(arg.equalsIgnoreCase("--no-gui")){
                    noGui = true;
                }
            }

            if(!noGui){
                instance.initUi();
            }

            log.info("Started");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                File cachePath = new File(instance.getOrCreateDownloadsDirectory(), "cache");

                deleteRecursively(cachePath.toPath());

                try{
                    GlobalScreen.unregisterNativeHook();
                }catch(NativeHookException ex){
                    ex.printStackTrace();
                }
            }));

            Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
                instance.handleException(e);
            });
        }else{
            System.err.println("System tray not supported???? did you run this on a calculator?");
        }
    }

//    public static void setUIFont(FontUIResource fontResource){
//        UIManager.getDefaults().keys().asIterator()
//            .forEachRemaining(key -> {
//                Object value = UIManager.get(key);
//
//                if(value instanceof FontUIResource){
//                    UIManager.put(key, fontResource);
//                }
//            });
//    }
    @Getter
    public static enum FlavorType{
        STRING(DataFlavor.stringFlavor),
        HTML(DataFlavor.selectionHtmlFlavor);

        private final DataFlavor flavor;

        private FlavorType(DataFlavor flavorIn){
            flavor = flavorIn;
        }
    }
}
