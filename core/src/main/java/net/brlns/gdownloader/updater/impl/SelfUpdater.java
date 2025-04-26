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
package net.brlns.gdownloader.updater.impl;

import jakarta.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.util.LockUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.Version;

import static net.brlns.gdownloader.util.LockUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SelfUpdater extends AbstractGitUpdater {

    private static final String USER = "hstr0100";
    private static final String REPO = "GDownloader";

    public SelfUpdater(GDownloader mainIn) {
        super(mainIn);
    }

    @Override
    public boolean isRestartRequired() {
        return true;
    }

    @Override
    protected String getUser() {
        return USER;
    }

    @Override
    protected String getRepo() {
        return REPO;
    }

    @Override
    @Nullable
    public String getGitHubBinaryName() {
        return ArchVersionEnum.getDefinitions().getSelfBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return "gdownloader_ota.zip";
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return null;
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "ota.lock";
    }

    @Override
    public boolean isEnabled() {
        return GDownloader.isFromJpackage();
    }

    @Override
    public boolean isInstalled() {
        return true;
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        // Not used
    }

    @Override
    public String getName() {
        return "GDownloader";
    }

    @Override
    protected void init() throws Exception {
        LockUtils.renameLockIfExists("ota_lock.txt", getLockFileName());
    }

    @Override
    protected void doUpdateCheck(boolean force) throws Exception {
        updated = false;

        notifyProgress(UpdateStatus.CHECKING, 0);

        File binaryPath = new File(workDir, getRuntimeBinaryName());

        if (isEnabled() && isInstalled()
            && !main.getConfig().isAutomaticUpdates() && !force) {
            log.info("Automatic updates are disabled {}", getRepo());

            finishUpdate(binaryPath);
            return;
        }

        Pair<String, String> tag = null;
        try {
            tag = getLatestReleaseTag();
        } catch (Exception e) {
            log.error("HTTP error for {}", getRepo(), e);
        }

        notifyProgress(UpdateStatus.CHECKING, 50);

        if (tag == null) {
            log.error("Release tag was null {}", getRepo());

            tryFallback(workDir);
            return;
        }

        String version = Version.VERSION;
        if (version != null && !isVersionNewer(version, tag.getKey())) {
            finishUpdate(binaryPath);

            log.info("{} is up to date, update skipped", getRepo());
            return;
        }

        String url = tag.getValue();
        if (url == null) {
            log.error("No {} binary for platform", getRepo());

            tryFallback(workDir);
            return;
        }

        String lockTag = getLockTag(tag.getKey());
        log.info("current {} tag is {}", getRepo(), lockTag);

        File lock = new File(workDir, getLockFileName());

        if (!lock.exists() && version != null) {
            createLock(lock, getLockTag("v" + version));
        }

        if (checkLock(lock, lockTag)) {
            finishUpdate(binaryPath);

            log.info("{} is up to date", getRepo());
            return;
        }

        try {
            log.info("Starting download {}", getRepo());

            notifyProgress(UpdateStatus.CHECKING, 100);

            File path = doDownload(url, workDir);
            if (path.exists() && !path.getName().endsWith("zip")) {
                makeExecutable(path.toPath());
            }

            createLock(lock, lockTag);

            updated = true;

            finishUpdate(path);
            log.info("Downloaded {}", path);
        } catch (Exception e) {
            log.error("Failed to update {}", getRepo(), e);
            tryFallback(workDir);
        }
    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File zipPath = new File(workDir, fileName);
        log.info("Zip path {}", zipPath);

        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Out path {}", outputFile);

        downloadFile(url, zipPath);

        Files.move(zipPath.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return outputFile;
    }
}
