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

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Window;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class TaskbarManager implements ITaskbarManager, AutoCloseable {

    private final ITaskbarManager delegate;

    public TaskbarManager(Window targetWindow) {
        delegate = createAppropriateImplementation(targetWindow);

        log.info("Using taskbar impl: {}", delegate.getClass().getSimpleName());
    }

    private ITaskbarManager createAppropriateImplementation(Window targetWindow) {
        JavaTaskbarManager javaImpl = new JavaTaskbarManager(targetWindow);
        if (javaImpl.isTaskbarSupported()) {
            return javaImpl;
        }

        if (GDownloader.isLinux()) {
            UnityTaskbarManager unityImpl = new UnityTaskbarManager();
            if (unityImpl.isTaskbarSupported()) {
                return unityImpl;
            }
        }

        return new NoOpTaskbarManager();
    }

    @Override
    public boolean isTaskbarSupported() {
        return delegate.isTaskbarSupported();
    }

    @Override
    public void setTaskbarState(TaskbarState state) {
        delegate.setTaskbarState(state);
    }

    @Override
    public void setProgressValue(int value) {
        delegate.setProgressValue(value);
    }

    @Override
    public void setProgressValue(double value) {
        delegate.setProgressValue(value);
    }

    @Override
    public void setBadgeValue(int value) {
        delegate.setBadgeValue(value);
    }

    @Override
    public void setShortcutMenu(PopupMenu shortcutMenu) {
        delegate.setShortcutMenu(shortcutMenu);
    }

    @Override
    public void addShortcutMenuItem(MenuItem item) {
        delegate.addShortcutMenuItem(item);
    }

    @Override
    public void removeShortcutMenuItem(MenuItem item) {
        delegate.removeShortcutMenuItem(item);
    }

    @Override
    public void clearShortcutMenu() {
        delegate.clearShortcutMenu();
    }

    @Override
    public void resetIndicators() {
        delegate.resetIndicators();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
