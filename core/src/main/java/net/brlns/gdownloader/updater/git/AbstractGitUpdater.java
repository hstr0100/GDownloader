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
package net.brlns.gdownloader.updater.git;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.system.SystemExecutableLocator;
import net.brlns.gdownloader.updater.ArchVersionEnum;
import net.brlns.gdownloader.updater.IUpdater;
import net.brlns.gdownloader.updater.UpdateProgressListener;
import net.brlns.gdownloader.updater.UpdateStatusEnum;
import net.brlns.gdownloader.util.NoFallbackAvailableException;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.URLUtils;

import static net.brlns.gdownloader.updater.UpdateStatusEnum.*;
import static net.brlns.gdownloader.util.LockUtils.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractGitUpdater implements IUpdater {

    private static final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_2)
        .build();

    protected final GDownloader main;
    protected final File workDir;

    private final List<UpdateProgressListener> listeners = new CopyOnWriteArrayList<>();

    @Getter
    protected boolean updated = false;

    public AbstractGitUpdater(GDownloader mainIn) {
        main = mainIn;
        workDir = GDownloader.getWorkDirectory();
    }

    @Override
    public void registerListener(UpdateProgressListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterListener(UpdateProgressListener listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isRestartRequired() {
        return false;
    }

    protected abstract String getUser();

    protected abstract String getRepo();

    /**
     * Suffix of the binary name as provided by GitHub.
     * Binaries may be named in various formats, such as `yt-dlp`, `yt-dlp.exe`, or `yt-dlp-1.0.exe`.
     * Currently, comparing the suffix is sufficient to retrieve the required binaries.
     * In the future, this may need to be replaced with a more robust regex-based solution.
     */
    @Nullable
    protected abstract String getGitHubBinaryName();

    @Nullable
    protected abstract String getRuntimeBinaryName();

    @Nullable
    protected abstract String getSystemBinaryName();

    @Nullable
    protected abstract String getLockFileName();

    @Override
    public abstract boolean isEnabled();

    @Override
    public abstract String getName();

    protected abstract void setExecutablePath(File executablePath);

    protected abstract void init() throws Exception;

    protected void finishUpdate(File executablePath) {
        setExecutablePath(executablePath);

        notifyStatus(DONE);
    }

    protected void tryFallback(File workDir) throws NoFallbackAvailableException, Exception {
        File binaryFile = getBinaryIfInstalled();
        if (binaryFile != null) {
            finishUpdate(binaryFile);
            log.info("Selected previous installation as fallback {}", getRepo());
            return;
        }

        File systemFallback = SystemExecutableLocator.locateExecutable(getSystemBinaryName());
        if (systemFallback != null) {
            finishUpdate(systemFallback);
            log.info("Selected platform installation as fallback {}", getRepo());
            return;
        }

        notifyStatus(FAILED);

        throw new NoFallbackAvailableException();
    }

    public boolean isInstalled() {
        return getBinaryIfInstalled() != null;
    }

    public File getBinaryIfInstalled() {
        try {
            String fileName = getRuntimeBinaryName();

            if (fileName != null) {
                File lock = new File(workDir, getLockFileName());

                File binaryFile = new File(workDir, fileName);
                if (binaryFile.exists() && lockExists(lock)) {
                    return binaryFile;
                }
            }
        } catch (IOException e) {
            log.error("Failed to obtain binary file", e);
        }

        return null;
    }

    @Override
    public final void check(boolean installIfMissing) throws Exception {
        init();

        doUpdateCheck(installIfMissing);

        if (_internalLastStatus != DONE && _internalLastStatus != FAILED) {
            throw new IllegalStateException("Exitted without notifying either status DONE or FAILED, final status was: " + _internalLastStatus);
        }
    }

    protected void doUpdateCheck(boolean installIfMissing) throws Exception {
        updated = false;

        notifyProgress(CHECKING, 0);

        if (main.getConfig().isPreferSystemExecutables()) {
            File systemFallback = SystemExecutableLocator.locateExecutable(getSystemBinaryName());
            if (systemFallback != null) {
                finishUpdate(systemFallback);
                log.info("Selected preferred platform binary {} for {}", systemFallback, getRepo());
                return;
            } else {
                log.error("{} not installed natively, falling back to portable installation managed by GDownloader", getRepo());
            }
        } else {
            if (this instanceof YtDlpUpdater
                && main.getConfig().isImpersonateBrowser()
                && (ArchVersionEnum.getArchVersion() == ArchVersionEnum.LINUX_ARM
                || ArchVersionEnum.getArchVersion() == ArchVersionEnum.LINUX_ARM64)) {
                log.error("Browser Impersonation is not available on this platform. Please install yt-dlp"
                    + " with curl_cffi using: \'pip install yt-dlp[default,curl_cffi]\'"
                    + " and enable \'Prefer System yt-dlp Installation\' under \'Download Settings\'");
            }
        }

        if (getGitHubBinaryName() == null || getRuntimeBinaryName() == null) {
            log.error("Cannot update, no binary configured for {}", getRepo());

            tryFallback(workDir);
            return;
        }

        File binaryPath = new File(workDir, getRuntimeBinaryName());

        if (isEnabled() && isInstalled()
            && !main.getConfig().isAutomaticUpdates() && !installIfMissing) {
            log.info("Automatic updates are disabled {}", getRepo());

            tryFallback(workDir);
            return;
        }

        Pair<String, String> tag = null;
        try {
            tag = getLatestReleaseTag();
        } catch (Exception e) {
            log.error("HTTP error for {}", getRepo(), e);
        }

        notifyProgress(CHECKING, 50);

        if (tag == null) {
            log.error("Release tag was null {}", getRepo());

            tryFallback(workDir);
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

        if (binaryPath.exists()) {
            if (checkLock(lock, lockTag)) {
                finishUpdate(binaryPath);

                log.info("{} is up to date", getRepo());
                return;
            }
        }

        Path tmpDir = null;
        if (binaryPath.exists()) {
            tmpDir = Paths.get(workDir.getAbsolutePath(), "old");

            if (!Files.exists(tmpDir)) {
                Files.createDirectories(tmpDir);
            }

            Files.move(binaryPath.toPath(), tmpDir.resolve(binaryPath.getName()), StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            log.info("Starting download {}", getRepo());

            notifyProgress(CHECKING, 100);

            File path = doDownload(url, workDir);
            if (path.exists()) {
                makeExecutable(path.toPath());
            }

            createLock(lock, lockTag);

            updated = true;

            finishUpdate(path);
            log.info("Downloaded {}", path);
        } catch (Exception e) {
            log.error("Failed to update {}", getRepo(), e);

            if (tmpDir != null) {
                notifyStatus(FAILED);

                Files.move(tmpDir.resolve(binaryPath.getName()), binaryPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                tryFallback(workDir);
            }
        } finally {
            if (binaryPath.exists()) {
                makeExecutable(binaryPath.toPath());
            }
        }
    }

    protected File doDownload(String url, File workDir) throws Exception {
        String fileName = getFilenameFromUrl(url);

        File outputFile = new File(workDir, fileName);

        downloadFile(url, outputFile);

        return outputFile;
    }

    @Nullable
    protected File copyResource(String resource, File targetFile) throws IOException {
        try (
            InputStream initialStream = getClass().getResourceAsStream(resource)) {
            if (initialStream == null) {
                log.error("Resource not available: " + resource);
                return null;
            }

            byte[] buffer = new byte[initialStream.available()];
            initialStream.read(buffer);

            Files.write(targetFile.toPath(), buffer);

            makeExecutable(targetFile.toPath());

            return targetFile;
        }
    }

    @Nullable
    protected Pair<String, String> getLatestReleaseTag() throws IOException, InterruptedException {
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", getUser(), getRepo());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", URLUtils.GLOBAL_USER_AGENT)
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        //log.info(response.body());
        JsonNode jsonNode = GDownloader.OBJECT_MAPPER.readTree(response.body());

        JsonNode tagName = jsonNode.get("tag_name");
        if (tagName == null) {
            return null;
        }

        Iterator<JsonNode> assets = jsonNode.get("assets").elements();

        while (assets.hasNext()) {
            JsonNode asset = assets.next();

            String downloadUrl = asset.get("browser_download_url").asText();

            if (downloadUrl.endsWith(getGitHubBinaryName())) {
                return new Pair<>(tagName.asText(), downloadUrl);
            }
        }

        return null;
    }

    protected String getFilenameFromUrl(String url) {
        return Paths.get(URI.create(url).getPath()).getFileName().toString();
    }

    protected void downloadFile(String urlIn, File outputFile) throws IOException, InterruptedException {
        notifyProgress(DOWNLOADING, 0);

        log.info("Downloading {} -> {}", urlIn, outputFile);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlIn))
            .header("User-Agent", URLUtils.GLOBAL_USER_AGENT)
            .build();

        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            int bufferSize = 8192;
            long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long downloadedBytes = 0;

            try (InputStream inputStream = response.body();
                 BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outputFile.toPath()), bufferSize)) {

                byte[] buffer = new byte[bufferSize];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;

                    if (totalBytes > 0) {
                        double progress = (double)downloadedBytes * 100 / totalBytes;
                        notifyProgress(DOWNLOADING, progress);
                    }
                }

                notifyProgress(DOWNLOADING, 100);
            }
        } else {
            throw new IOException("Failed to download file: " + urlIn + ": " + response.statusCode());
        }
    }

    private UpdateStatusEnum _internalLastStatus;

    protected void notifyStatus(UpdateStatusEnum status) {
        notifyProgress(status, 0);
    }

    protected void notifyProgress(UpdateStatusEnum status, double progress) {
        _internalLastStatus = status;

        for (UpdateProgressListener listener : listeners) {
            listener.update(status, progress);
        }
    }

    public static void makeExecutable(Path path) throws IOException {
        if (!GDownloader.isWindows()) {
            if (Files.isDirectory(path)) {
                Set<PosixFilePermission> dirPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(path, dirPermissions);

                try (
                    DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry : stream) {
                        makeExecutable(entry);
                    }
                }
            } else if (Files.isRegularFile(path)) {
                Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(path, filePermissions);
            } else {
                throw new IOException("The path provided is neither a file nor a directory.");
            }
        }
    }
}
