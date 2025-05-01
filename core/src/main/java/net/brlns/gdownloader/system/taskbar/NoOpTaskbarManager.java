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

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class NoOpTaskbarManager implements ITaskbarManager {

    @Override
    public boolean isTaskbarSupported() {
        return false;
    }

    @Override
    public void setTaskbarState(ITaskbarManager.TaskbarState state) {
        // No-op
    }

    @Override
    public void setProgressValue(int value) {
        // No-op
    }

    @Override
    public void setBadgeValue(int value) {
        // No-op
    }

    @Override
    public void setShortcutMenu(PopupMenu shortcutMenu) {
        // No-op
    }

    @Override
    public void addShortcutMenuItem(MenuItem item) {
        // No-op
    }

    @Override
    public void removeShortcutMenuItem(MenuItem item) {
        // No-op
    }

    @Override
    public void clearShortcutMenu() {
        // No-op
    }

    @Override
    public void resetIndicators() {
        // No-op
    }

    @Override
    public void close() {
        // No-op
    }
}
