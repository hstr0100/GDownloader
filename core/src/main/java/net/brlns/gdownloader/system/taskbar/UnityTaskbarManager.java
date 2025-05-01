/*
 * Copyright (C) 2025 @hstr0100
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
package net.brlns.gdownloader.system.taskbar;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.awt.MenuItem;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.FileUtils;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.LoggerFactory;

import static net.brlns.gdownloader.updater.UpdaterBootstrap.getAppImageLauncher;
import static net.brlns.gdownloader.util.StringUtils.calculateMD5;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * Unity/D-Bus implementation of ITaskbarManager.
 * Provides taskbar functionality for Linux desktop environments
 * that support the Unity LauncherEntry interface, such as KDE.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: test behavior under a DE without D-Bus support.
@Slf4j
public class UnityTaskbarManager implements ITaskbarManager {

    static {
        Logger noisyLogger = (Logger)LoggerFactory.getLogger("org.freedesktop.dbus");
        noisyLogger.setLevel(Level.OFF);
    }

    private DBusConnection connection;
    private boolean isDBusAvailable = false;

    private TaskbarState currentTaskbarState = null;
    private int progressValue = -1;
    private int badgeValue = -1;

    private TaskbarState lastTaskbarState = currentTaskbarState;
    private int lastProgressValue = progressValue;
    private int lastBadgeValue = badgeValue;

    private final String applicationId;
    private final String objectPath = "/gdownloader";

    public UnityTaskbarManager() {
        this(getDesktopFileName());
    }

    public UnityTaskbarManager(String desktopFileName) {
        applicationId = "application://" + desktopFileName;

        initDBus();
    }

    private void initDBus() {
        try {
            connection = DBusConnectionBuilder.forSessionBus().build();

            isDBusAvailable = connection != null
                && connection.isConnected()
                && isUsable(connection);

            if (!isDBusAvailable) {
                log.warn("DBus connection could not be established.");

                if (connection != null) {
                    try {
                        connection.close();
                    } catch (IOException e) {
                        log.error("Error closing partially established DBus connection: {}", e.getMessage());
                    } finally {
                        connection = null;
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("Error during DBus connection setup: {}", t.getMessage());
            connection = null;
        }
    }

    private boolean isUsable(DBusConnection connection) {
        try {
            String[] services = connection.getRemoteObject(
                "org.freedesktop.DBus",
                "/org/freedesktop/DBus",
                DBus.class).ListNames();

            for (String service : services) {
                if (service.equals("com.canonical.Unity")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Error checking for Unity support", e);
        }

        return false;
    }

    @Override
    public boolean isTaskbarSupported() {
        return isDBusAvailable;
    }

    @Override
    public void setProgressState(TaskbarState state) {
        currentTaskbarState = state;

        if (currentTaskbarState != lastTaskbarState) {
            lastTaskbarState = currentTaskbarState;

            updateProgress();
        }
    }

    @Override
    public void setProgressValue(int value) {
        progressValue = Math.clamp(value, 0, 100);

        if (progressValue != lastProgressValue) {
            lastProgressValue = progressValue;

            updateProgress();
        }
    }

    @Override
    public void setBadgeValue(int value) {
        badgeValue = Math.clamp(value, 0, 999);

        if (badgeValue != lastBadgeValue) {
            lastBadgeValue = badgeValue;

            updateProgress();
        }
    }

    @Override
    public void addShortcutMenuItem(MenuItem item) {
        // TODO quicklists, handle actions via AppServer.
        log.warn("Shortcut menu items are not supported in Unity implementation");
    }

    @Override
    public void removeShortcutMenuItem(MenuItem item) {
        // TODO quicklists, handle actions via AppServer.
        log.warn("Shortcut menu items are not supported in Unity implementation");
    }

    @Override
    public void clearShortcutMenu() {
        // TODO quicklists, handle actions via AppServer.
        log.warn("Shortcut menu items are not supported in Unity implementation");
    }

    @Override
    public void resetIndicators() {
        progressValue = 0;
        badgeValue = 0;
        currentTaskbarState = TaskbarState.OFF;

        lastTaskbarState = currentTaskbarState;
        lastProgressValue = progressValue;
        lastBadgeValue = badgeValue;

        updateProgress();
    }

    private void updateProgress() {
        if (!isDBusAvailable || connection == null) {
            return;
        }

        try {
            Map<String, Variant<?>> properties = new HashMap<>();

            double progressFraction = (double)progressValue / 100d;
            // Progress percentage between 0.0 and 1.0
            properties.put("progress", new Variant<>(progressFraction));
            properties.put("progress-visible", new Variant<>(progressValue > 0 && currentTaskbarState != TaskbarState.OFF));
            // Number Badge
            properties.put("count", new Variant<>(badgeValue));
            properties.put("count-visible", new Variant<>(badgeValue > 0));
            // Errored state
            properties.put("urgent", new Variant<>(currentTaskbarState == TaskbarState.ERROR));

            LauncherEntry.Update updateSignal = new LauncherEntry.Update(
                objectPath,
                applicationId,
                properties
            );

            connection.sendMessage(updateSignal);
        } catch (DBusException e) {
            log.error("Failed to update progress bar via DBus: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (connection != null && connection.isConnected()) {
            resetIndicators();

            // Unless I'm looking at the wrong code due to an inheritance nightmare
            // dbus-java-core/org/freedesktop/dbus/connections/base/AbstractConnectionBase.java#L236
            // shows that pending signals should get flushed by calling disconnect(), as long as no exceptions are thrown
            // however, in practice they do not, and dbus-java does not expose a flush() call, so our only option is to delay
            // our disconnect call by a few cycles to ensure the taskbar icon is properly reset before our D-Bus pipe is closed.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            try {
                connection.close();// Redirects to disconnect()
            } catch (IOException e) {
                log.error("Error closing DBus connection: {}", e.getMessage());
            }
        }
    }

    @DBusInterfaceName("com.canonical.Unity.LauncherEntry")
    private static interface LauncherEntry extends DBusInterface {

        @Getter
        class Update extends DBusSignal {

            @Position(0)
            private final String appUri;

            @Position(1)
            private final Map<String, Variant<?>> properties;

            public Update(String pathIn, String appUriIn,
                Map<String, Variant<?>> propertiesIn) throws DBusException {
                super(pathIn, appUriIn, propertiesIn);

                appUri = appUriIn;
                properties = propertiesIn;
            }
        }
    }

    private static String getDesktopFileName() {
        List<String> possibleBasenames = new ArrayList<>();

        String appImagePath = getAppImageLauncher();
        if (notNullOrEmpty(appImagePath)) {
            String pathUri = "file://" + appImagePath;
            String md5Hash = calculateMD5(pathUri.getBytes());

            possibleBasenames.add(String.format("appimagekit_%s-%s",
                md5Hash, GDownloader.REGISTRY_APP_NAME));
        }

        possibleBasenames.addAll(List.of(
            "gdownloader-linux-GDownloader",
            "net.brlns.gdownloader",
            "GDownloader",
            "gdownloader"
        ));

        for (String basename : possibleBasenames) {
            File desktopFile = FileUtils.locateDesktopFile(basename + ".desktop");

            log.info("Testing {} {}", basename, desktopFile);

            if (desktopFile != null) {
                log.info("Resolved .desktop file to: {}", basename);
                return basename;
            }
        }

        // Take a guess
        return possibleBasenames.get(0);
    }
}
