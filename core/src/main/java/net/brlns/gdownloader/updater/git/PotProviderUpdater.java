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

import jakarta.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.YtDlpDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.updater.ArchVersionEnum;

import static net.brlns.gdownloader.GDownloader.isWindows;

/**
 * Uses bgutil-ytdlp-pot-provider-rs by jim60105, a Rust rewrite of
 * Brainicism's bgutil-ytdlp-pot-provider:
 * https://github.com/jim60105/bgutil-ytdlp-pot-provider-rs
 *
 * The original is TypeScript and ships no standalone binary: it needs
 * either a server running on the client machine, or Node.js/Deno plus
 * an npm install of its sources, which does not align with our philosophy.
 * This one ships a single native binary that yt-dlp runs on demand through
 * its plugin, so nothing persistent runs on the client.
 *
 * This will remain experimental and turned off by default for the time being,
 * since we cannot be certain how this project is maintained.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class PotProviderUpdater extends AbstractGitUpdater {

    private static final String USER = "jim60105";
    private static final String REPO = "bgutil-ytdlp-pot-provider-rs";
    private static final String PLUGIN_DIRECTORY_NAME = "pot-plugins";
    private static final String PLUGIN_FILE_NAME = "bgutil-ytdlp-pot-provider-rs.zip";

    public PotProviderUpdater(GDownloader mainIn) {
        super(mainIn);
    }

    public static File getProviderFile() {
        return new File(GDownloader.getWorkDirectory(), isWindows() ? "bgutil-pot.exe" : "bgutil-pot");
    }

    public static File getPluginDirectory() {
        return new File(GDownloader.getWorkDirectory(), PLUGIN_DIRECTORY_NAME);
    }

    public static File getPluginFile() {
        return new File(getPluginDirectory(), PLUGIN_FILE_NAME);
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
    public String getReleaseBinaryName() {
        return ArchVersionEnum.getDefinitions().getPotProviderBinary();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return isWindows() ? "bgutil-pot.exe" : "bgutil-pot";
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return getRuntimeBinaryName();
    }

    @Nullable
    @Override
    protected String getLockFileName() {
        return "bgutil-pot.lock";
    }

    @Override
    public boolean isEnabled() {
        return getReleaseBinaryName() != null
            && main.getConfig().getYtDlpSettings().isUsePoToken();
    }

    @Override
    public boolean isPreferSystemExecutable() {
        return false;
    }

    @Override
    protected void setExecutablePath(File executablePath) {
        YtDlpDownloader ytDlpDownloader = (YtDlpDownloader)main.getDownloadManager().getDownloader(DownloaderIdEnum.YT_DLP);
        if (ytDlpDownloader != null) {
            ytDlpDownloader.setPotProviderPath(Optional.of(executablePath));
        }
    }

    @Override
    public String getName() {
        return "PO Token Provider";
    }

    @Override
    protected void init() throws Exception {

    }

    @Override
    protected File doDownload(String url, File workDir) throws Exception {
        File outputFile = new File(workDir, getRuntimeBinaryName());
        log.info("Final path {}", outputFile);

        String pluginUrl = url.substring(0, url.lastIndexOf('/') + 1) + PLUGIN_FILE_NAME;

        Files.createDirectories(getPluginDirectory().toPath());

        File pluginFile = getPluginFile();
        File tmpPluginFile = new File(workDir, PLUGIN_FILE_NAME + ".tmp");

        try {
            downloadFile(pluginUrl, tmpPluginFile);
            downloadFile(url, outputFile);

            Files.move(tmpPluginFile.toPath(), pluginFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (tmpPluginFile.exists()) {
                tmpPluginFile.delete();
            }
        }

        return outputFile;
    }

}
