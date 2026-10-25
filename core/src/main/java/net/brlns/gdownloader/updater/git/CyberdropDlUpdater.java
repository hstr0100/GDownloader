/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.updater.git;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.updater.UvRuntime;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CyberdropDlUpdater extends AbstractPythonToolUpdater {

    public CyberdropDlUpdater(GDownloader mainIn, UvRuntime uvRuntimeIn) {
        super(mainIn, uvRuntimeIn);
    }

    @Override
    protected String getPackageName() {
        return "cyberdrop-dl-patched";
    }

    @Override
    protected String getEntryPointName() {
        return "cyberdrop-dl";
    }

    @Override
    protected String getEnvironmentName() {
        return "cyberdrop-dl";
    }

    @Override
    protected String getLockFileName() {
        return "cyberdrop-dl.lock";
    }

    @Override
    public boolean isEnabled() {
        return main.getConfig().getCyberdropDLSettings().isEnabled();
    }

    @Override
    public boolean isPreferSystemExecutable() {
        return main.getConfig().getCyberdropDLSettings().isPreferSystemExecutable()
            || main.getConfig().isPreferSystemExecutables();
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        main.getDownloadManager().setExecutablePath(
            DownloaderIdEnum.CYBERDROP_DL, executablePath);
    }

    @Override
    public String getName() {
        return "Cyberdrop-DL";
    }
}
