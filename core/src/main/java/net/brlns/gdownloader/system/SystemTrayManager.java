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
package net.brlns.gdownloader.system;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;

import static net.brlns.gdownloader.GDownloader.handleException;
import static net.brlns.gdownloader.lang.Language.l10n;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@RequiredArgsConstructor
public class SystemTrayManager {

    private final GDownloader main;

    @Getter
    private boolean initialized;

    private SystemTray tray;

    @Getter
    private TrayIcon trayIcon = null;

    public void register() {
        if (SystemTray.isSupported()) {
            try {
                tray = SystemTray.getSystemTray();

                Image image = Toolkit.getDefaultToolkit().createImage(
                    getClass().getResource(main.getGuiManager().getCurrentTrayIconPath()));

                trayIcon = new TrayIcon(image,
                    GDownloader.REGISTRY_APP_NAME,
                    getDefaultSystemTrayMenu());

                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener((ActionEvent e) -> {
                    main.initUi(false);
                });

                tray.add(trayIcon);

                initialized = true;
            } catch (AWTException e) {
                log.error("Error initializing the system tray");
                handleException(e);
            }
        } else {
            log.error("System tray is not supported, some features may not work.");
        }
    }

    /**
     * Builds the system tray menu.
     */
    private PopupMenu getDefaultSystemTrayMenu() {
        // TODO: Java's SystemTray menu looks horrifying.
        // Evaluate over time whether this project warrants the effort required to implement cross-platform custom menus.
        // Dorkbox's existing library does not seem reliable enough across all the environments we support.
        PopupMenu popup = new PopupMenu();

        popup.add(buildMenuItem("gui.toggle_downloads",
            e -> main.getDownloadManager().toggleDownloads()));
        popup.add(buildMenuItem("gui.open_downloads_directory",
            e -> main.openDownloadsDirectory()));

        popup.addSeparator();

        popup.add(buildMenuItem("settings.sidebar_title",
            e -> main.getGuiManager().displaySettingsPanel()));
        popup.add(buildMenuItem("gui.restart",
            e -> main.restart()));
        popup.add(buildMenuItem("gui.exit",
            e -> main.shutdown()));

        return popup;
    }

    public static PopupMenu getDefaultShortcutMenu() {
        GDownloader main = GDownloader.getInstance();

        PopupMenu popup = new PopupMenu();

        popup.add(buildMenuItem("gui.toggle_downloads",
            e -> main.getDownloadManager().toggleDownloads()));
        popup.add(buildMenuItem("gui.open_downloads_directory",
            e -> main.openDownloadsDirectory()));
        popup.add(buildMenuItem("settings.sidebar_title",
            e -> main.getGuiManager().displaySettingsPanel()));

        return popup;
    }

    /**
     * Helper for building system tray menu entries.
     */
    public static MenuItem buildMenuItem(String translationKey, ActionListener actionListener) {
        MenuItem menuItem = new MenuItem(l10n(translationKey));
        menuItem.addActionListener(actionListener);

        return menuItem;
    }
}
