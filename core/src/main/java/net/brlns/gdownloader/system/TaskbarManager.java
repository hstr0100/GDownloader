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

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.awt.Window;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: With Unity basically dead, linux support is non-existant for this.
// We need to implement our own custom D-Bus interaction here to support KDE.
@Slf4j
public class TaskbarManager {

    private final Window targetWindow;
    private final Taskbar taskbar;
    private final boolean isSupported;

    private PopupMenu shortcutMenu;

    public TaskbarManager(Window targetWindowIn) {
        targetWindow = targetWindowIn;
        isSupported = Taskbar.isTaskbarSupported();
        taskbar = isSupported ? Taskbar.getTaskbar() : null;

        if (isSupported) {
            shortcutMenu = new PopupMenu();

            try {
                if (taskbar.isSupported(Taskbar.Feature.MENU)) {
                    taskbar.setMenu(shortcutMenu);
                }
            } catch (UnsupportedOperationException e) {
                log.error("Taskbar menu feature not fully supported: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred setting the Taskbar menu: {}", e.getMessage());
            }
        } else {
            log.error("Taskbar is not supported on this platform.");
        }
    }

    public boolean isTaskbarSupported() {
        return isSupported;
    }

    public void setProgressState(Taskbar.State state) {
        if (isSupported && taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
            try {
                taskbar.setWindowProgressState(targetWindow, state);
            } catch (UnsupportedOperationException e) {
                log.error("Setting Taskbar progress state not supported for window: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred setting Taskbar progress state: {}", e.getMessage());
            }
        }
    }

    public void setProgressValue(double value) {
        setProgressValue((int)Math.round(value));
    }

    public void setProgressValue(int value) {
        if (isSupported && taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
            int clampedValue = Math.clamp(value, 0, 100);

            try {
                taskbar.setWindowProgressValue(targetWindow, clampedValue);
            } catch (UnsupportedOperationException e) {
                log.error("Setting Taskbar progress value not supported for window: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred setting Taskbar progress value: {}", e.getMessage());
            }
        }
    }

    public void addShortcutMenuItem(MenuItem item) {
        if (isSupported && taskbar.isSupported(Taskbar.Feature.MENU) && shortcutMenu != null) {
            try {
                shortcutMenu.add(item);

                if (taskbar.isSupported(Taskbar.Feature.MENU)) {
                    taskbar.setMenu(shortcutMenu);
                }
            } catch (UnsupportedOperationException e) {
                log.error("Adding item to Taskbar menu not supported: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred adding item to Taskbar menu: {}", e.getMessage());
            }
        }
    }

    public void removeShortcutMenuItem(MenuItem item) {
        if (isSupported && taskbar.isSupported(Taskbar.Feature.MENU) && shortcutMenu != null) {
            try {
                shortcutMenu.remove(item);

                if (taskbar.isSupported(Taskbar.Feature.MENU)) {
                    taskbar.setMenu(shortcutMenu);
                }
            } catch (UnsupportedOperationException e) {
                log.error("Removing item from Taskbar menu not supported: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred removing item from Taskbar menu: {}", e.getMessage());
            }
        }
    }

    public void clearShortcutMenu() {
        if (isSupported && taskbar.isSupported(Taskbar.Feature.MENU) && shortcutMenu != null) {
            try {
                shortcutMenu.removeAll();

                if (taskbar.isSupported(Taskbar.Feature.MENU)) {
                    taskbar.setMenu(shortcutMenu);
                }
            } catch (UnsupportedOperationException e) {
                log.error("Clearing Taskbar menu not supported: {}", e.getMessage());
            } catch (Exception e) {
                log.error("An unexpected error occurred clearing Taskbar menu: {}", e.getMessage());
            }
        }
    }
}
