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

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.awt.Window;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.util.ImageUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class JavaTaskbarManager implements ITaskbarManager {

    private final Window targetWindow;
    private final Taskbar taskbar;

    private final boolean isSupported;
    private final boolean isNumberBadgeSupported;
    private final boolean isTextBadgeSupported;
    private final boolean isImageBadgeSupported;

    private PopupMenu shortcutMenu;

    private int lastBadgeValue = -1;
    private int lastProgressValue = -1;
    private TaskbarState lastProgressState = null;

    public JavaTaskbarManager(Window targetWindowIn) {
        targetWindow = targetWindowIn;
        isSupported = Taskbar.isTaskbarSupported();
        taskbar = isSupported ? Taskbar.getTaskbar() : null;

        if (isSupported) {
            isNumberBadgeSupported = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER);
            isTextBadgeSupported = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT);
            isImageBadgeSupported = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW);

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
            isNumberBadgeSupported = false;
            isTextBadgeSupported = false;
            isImageBadgeSupported = false;

            log.error("Java taskbar is not supported on this platform.");
        }
    }

    @Override
    public boolean isTaskbarSupported() {
        return isSupported;
    }

    @Override
    public void setProgressState(TaskbarState state) {
        if (!isSupported || !taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
            return;
        }

        if (state == lastProgressState) {
            return;
        }

        lastProgressState = state;

        try {
            Taskbar.State nativeState = switch (state) {
                case NORMAL ->
                    Taskbar.State.NORMAL;
                case ERROR ->
                    Taskbar.State.ERROR;
                case PAUSED ->
                    Taskbar.State.PAUSED;
                case INDETERMINATE ->
                    Taskbar.State.INDETERMINATE;
                case OFF ->
                    Taskbar.State.OFF;
                default ->
                    Taskbar.State.NORMAL;
            };

            taskbar.setWindowProgressState(targetWindow, nativeState);
        } catch (UnsupportedOperationException e) {
            log.error("Setting Taskbar progress state not supported for window: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred setting Taskbar progress state: {}", e.getMessage());
        }
    }

    @Override
    public void setProgressValue(int value) {
        if (!isSupported || !taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
            return;
        }

        int clampedValue = Math.clamp(value, 0, 100);
        if (clampedValue == lastProgressValue) {
            return;
        }

        lastProgressValue = clampedValue;

        try {
            taskbar.setWindowProgressValue(targetWindow, clampedValue);
        } catch (UnsupportedOperationException e) {
            log.error("Setting Taskbar progress value not supported for window: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred setting Taskbar progress value: {}", e.getMessage());
        }
    }

    @Override
    public void setBadgeValue(int value) {
        if (!isSupported) {
            return;
        }

        int effectiveValue = Math.clamp(value, 0, 999);
        if (effectiveValue == lastBadgeValue) {
            return;
        }

        lastBadgeValue = effectiveValue;

        try {
            if (effectiveValue == 0) {
                if (isNumberBadgeSupported || isTextBadgeSupported) {
                    taskbar.setIconBadge(null);
                } else if (isImageBadgeSupported) {
                    taskbar.setWindowIconBadge(targetWindow, null);
                }
            } else {
                if (isNumberBadgeSupported || isTextBadgeSupported) {
                    taskbar.setIconBadge(String.valueOf(effectiveValue));
                } else if (isImageBadgeSupported) {
                    Image badgeImage = ImageUtils.generateBadgeIcon(effectiveValue);

                    taskbar.setWindowIconBadge(targetWindow, badgeImage);
                }
            }
        } catch (UnsupportedOperationException e) {
            log.error("Taskbar badge feature unexpectedly not supported during set operation: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred setting Taskbar badge: {}", e.getMessage());
        }
    }

    @Override
    public void addShortcutMenuItem(MenuItem item) {
        if (!isSupported || !taskbar.isSupported(Taskbar.Feature.MENU) || shortcutMenu == null) {
            return;
        }

        try {
            shortcutMenu.add(item);

            taskbar.setMenu(shortcutMenu);
        } catch (UnsupportedOperationException e) {
            log.error("Adding item to Taskbar menu not supported: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred adding item to Taskbar menu: {}", e.getMessage());
        }
    }

    @Override
    public void removeShortcutMenuItem(MenuItem item) {
        if (!isSupported || !taskbar.isSupported(Taskbar.Feature.MENU) || shortcutMenu == null) {
            return;
        }

        try {
            shortcutMenu.remove(item);

            taskbar.setMenu(shortcutMenu);
        } catch (UnsupportedOperationException e) {
            log.error("Removing item from Taskbar menu not supported: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred removing item from Taskbar menu: {}", e.getMessage());
        }
    }

    @Override
    public void clearShortcutMenu() {
        if (!isSupported || !taskbar.isSupported(Taskbar.Feature.MENU) || shortcutMenu == null) {
            return;
        }

        try {
            shortcutMenu.removeAll();

            taskbar.setMenu(shortcutMenu);
        } catch (UnsupportedOperationException e) {
            log.error("Clearing Taskbar menu not supported: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred clearing Taskbar menu: {}", e.getMessage());
        }
    }

    @Override
    public void resetIndicators() {
        lastProgressValue = -1;
        lastBadgeValue = -1;
        lastProgressState = null;

        setProgressValue(0);
        setBadgeValue(0);

        if (isSupported) {
            setProgressState(TaskbarState.OFF);
        }
    }

    @Override
    public void close() {
        resetIndicators();
    }
}
