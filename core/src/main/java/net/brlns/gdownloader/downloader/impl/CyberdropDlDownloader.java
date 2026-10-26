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
package net.brlns.gdownloader.downloader.impl;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.DownloadManager;
import net.brlns.gdownloader.downloader.QueueEntry;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloadTypeEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.settings.downloader.CyberdropDLSettings;
import net.brlns.gdownloader.updater.UvRuntime;
import net.brlns.gdownloader.util.CancelHook;
import net.brlns.gdownloader.util.DirectoryDeduplicator;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import net.brlns.gdownloader.util.StringUtils;
import net.brlns.gdownloader.util.URLUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import static net.brlns.gdownloader.GDownloader.spawn;
import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.downloader.enums.DownloadTypeEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.util.FileUtils.relativize;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class CyberdropDlDownloader extends AbstractDownloader {

    private static final String SNAPSHOT_PREFIX = "{\"files\":";
    private static final String LOGS_DIRECTORY_NAME = ".cyberdrop-dl-logs";
    private static final Pattern CSV_ERROR = Pattern.compile("^\"[^\"]*\",\"((?:[^\"]|\"\")*)\"");

    @Getter
    private Optional<File> executablePath = Optional.empty();

    private final Set<String> historyBypassUrls = ConcurrentHashMap.newKeySet();

    private volatile Set<String> supportedDomains = Set.of();

    public CyberdropDlDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public void setExecutablePath(Optional<File> file) {
        boolean changed = !file.equals(executablePath);

        executablePath = file;

        if (file.isPresent() && (changed || supportedDomains.isEmpty())) {
            spawn(this::loadSupportedDomains);
        }
    }

    @Override
    public CyberdropDLSettings settings() {
        return main.getConfig().getCyberdropDLSettings();
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent() && settings().isEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.CYBERDROP_DL;
    }

    @Override
    public int getPreferenceScore(String inputUrl) {
        String host = URLUtils.getHost(inputUrl);
        if (host == null) {
            return -1;
        }

        String lowered = host.toLowerCase(Locale.ROOT);

        for (String domain : supportedDomains) {
            if (domain.endsWith(".*")) {
                String name = domain.substring(0, domain.length() - 2);

                if (lowered.startsWith(name + ".") || lowered.contains("." + name + ".")) {
                    return 5;
                }
            } else if (lowered.equals(domain) || lowered.endsWith("." + domain)) {
                return 5;
            }
        }

        return -1;
    }

    @Override
    public boolean isMainDownloader() {
        return false;
    }

    @Override
    public String getDefaultOutputSubdirectory() {
        return "CyberdropDL";
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.emptyList();
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        historyBypassUrls.add(queueEntry.getUrl());
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return isEnabled() && URLUtils.isHttpUrl(inputUrl);
    }

    @Override
    protected boolean tryQueryMetadata(QueueEntry queueEntry) {
        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        if (!settings().isEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        AbstractUrlFilter filter = entry.getFilter();

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        File logsPath = new File(tmpPath, LOGS_DIRECTORY_NAME);

        ProcessArguments genericArguments = new ProcessArguments(
            getExecutablePath().get().getAbsolutePath(),
            "download",
            entry.getUrl(),
            "--ui", "activity",
            "--logs.folder", logsPath.getAbsolutePath(),
            "--no-print-traffic",
            "--no-dedupe");

        if (!settings().isRespectConfigFile()) {
            File isolatedConfig = FileUtils.getOrCreate(GDownloader.getWorkDirectory(),
                getDownloaderId().getDisplayName() + "_config.yaml");

            genericArguments.add("--config-file", isolatedConfig.getAbsolutePath());
        }

        if (!main.getConfig().isRecordToDownloadArchive() || historyBypassUrls.contains(entry.getUrl())) {
            genericArguments.add("--ignore-history");
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        entry.setCurrentDownloadType(CYBERDROP);

        ProcessArguments arguments = new ProcessArguments(
            genericArguments,
            filter.getArguments(this,
                CYBERDROP,
                manager,
                tmpPath,
                entry.getUrl()
            )
        );

        Pair<Integer, JsonNode> result = processDownload(entry, arguments);

        if (result == null || entry.getCancelHook().get()) {
            return new DownloadResult(FLAG_STOPPED);
        }

        if (result.getKey() != 0) {
            return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, "Cyberdrop-DL exited with code " + result.getKey());
        }

        List<String> errors = Stream.of("download_errors.csv", "scrape_errors.csv")
            .map(name -> new File(logsPath, name))
            .filter(File::exists)
            .flatMap(file -> {
                try {
                    return Files.readAllLines(file.toPath()).stream().skip(1);
                } catch (IOException | UncheckedIOException e) {
                    log.debug("Unable to read {}: {}", file, e.getMessage());

                    return Stream.empty();
                }
            })
            .map(CSV_ERROR::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1).replace("\"\"", "\""))
            .collect(Collectors.toList());

        boolean hasFiles;
        try (Stream<Path> paths = Files.walk(tmpPath.toPath())) {
            hasFiles = paths.anyMatch(path
                -> Files.isRegularFile(path) && !isTemporary(path, logsPath.toPath()));
        }

        JsonNode counters = result.getValue().path("files");

        boolean hasProgress = hasFiles || counters.path("skipped").asInt(0)
            + counters.path("prev_completed").asInt(0)
            + counters.path("completed").asInt(0) > 0;

        boolean fatalErrors = errors.stream().anyMatch(error
            -> !error.startsWith("FFmpeg Fixup Error")) || (!errors.isEmpty() && !hasFiles);

        if (fatalErrors) {
            return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, errors.stream()
                .collect(Collectors.groupingBy(error -> error,
                    LinkedHashMap::new, Collectors.counting()
                ))
                .entrySet().stream()
                .map(error
                    -> error.getValue() > 1 ? error.getKey() + " (x" + error.getValue() + ")" : error.getKey())
                .collect(Collectors.joining(" | ")));
        }

        if (!hasProgress) {
            return new DownloadResult(FLAG_UNSUPPORTED, "No downloadable files found");
        }

        historyBypassUrls.remove(entry.getUrl());

        if (settings().isMediaTranscoding()) {
            DownloadResult transcodeResult = transcodeMediaFiles(entry);

            if (main.getConfig().isFailDownloadsOnTranscodingFailures()
                && !FLAG_SUCCESS.isSet(transcodeResult.getFlags())) {
                return transcodeResult;
            }
        }

        return new DownloadResult(FLAG_SUCCESS);
    }

    @Override
    public void cleanupFailedEntry(QueueEntry entry) {
        File tmpPath = entry.getTmpDirectory();

        if (tmpPath != null && new File(tmpPath, LOGS_DIRECTORY_NAME).exists()) {
            entry.cleanDirectories();
        }
    }

    @Override
    protected void processMediaFiles(QueueEntry entry) {
        File finalPath = resolveOutputDirectory(entry);

        File tmpPath = entry.getTmpDirectory();
        if (!tmpPath.exists()) {
            return;
        }

        Path logsPath = new File(tmpPath, LOGS_DIRECTORY_NAME).toPath();
        boolean flatten = settings().isFlattenOutput();
        Path deepestDir = null;
        int maxDepth = -1;

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .filter(path -> !path.equals(tmpPath.toPath()) && !isTemporary(path, logsPath))
                .collect(Collectors.toList());

            for (Path path : paths) {
                if (!flatten && Files.isDirectory(path)) {
                    Path targetPath = relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath);
                        int depth = targetPath.getNameCount();
                        if (depth > maxDepth) {
                            maxDepth = depth;
                            deepestDir = targetPath;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to create directory: {}", targetPath, e);
                    }
                }
            }

            for (Path path : paths) {
                if (!Files.isDirectory(path)) {
                    Path targetPath = flatten
                        ? finalPath.toPath().resolve(path.getFileName().toString())
                        : relativize(tmpPath, finalPath, path);

                    try {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);

                        Optional<LocalDateTime> uploadTime = FileUtils.readLastModifiedTime(path);

                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        updateFileTimes(entry, targetPath, uploadTime.orElseGet(entry::getUploadTime));

                        entry.getFinalMediaFiles().add(targetPath.toFile());
                    } catch (IOException e) {
                        log.error("Failed to move file: {}", path, e);
                    }
                }
            }

            if (deepestDir != null) {
                File deepestFile = deepestDir.toFile();
                entry.getFinalMediaFiles().add(deepestFile);

                if (settings().isFileDeduplication()) {
                    entry.updateStatus(DownloadStatusEnum.DEDUPLICATING, l10n("gui.deduplication.deduplicating"));

                    DirectoryDeduplicator.deduplicateDirectory(deepestFile);
                    entry.getFinalMediaFiles().removeIf(file -> !file.exists());
                }
            }
        } catch (IOException e) {
            log.error("Failed to process media files", e);
        }
    }

    @Nullable
    private Pair<Integer, JsonNode> processDownload(QueueEntry entry, ProcessArguments arguments) throws Exception {
        long start = System.currentTimeMillis();

        entry.setLastCommandLine(arguments, true);

        Map<String, String> environment = new HashMap<>(UvRuntime.getPythonEnvironment());
        environment.put("COLUMNS", "2000");
        environment.put("TTY_COMPATIBLE", "1");
        environment.put("CDL_WRITE_JSON_UI", "1");

        main.getFfmpegTranscoder().getFfmpegPath().ifPresent(ffmpeg -> {
            File directory = ffmpeg.isDirectory() ? ffmpeg : ffmpeg.getParentFile();

            if (directory != null) {
                environment.merge("PATH", directory.getAbsolutePath(),
                    (current, added) -> added + File.pathSeparator + current);
            }
        });

        CancelHook cancelHook = entry.getCancelHook().derive(manager::isRunning, true);
        Process process = main.getProcessMonitor().startProcess(arguments, environment, cancelHook);
        entry.setProcess(process);

        JsonNode snapshot = GDownloader.OBJECT_MAPPER.createObjectNode();

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;
            while (!cancelHook.get() && (process.isAlive() || reader.ready())) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();

                    throw new InterruptedException("Download interrupted");
                }

                if (reader.ready()) {
                    if ((line = reader.readLine()) != null) {
                        int index = line.indexOf(SNAPSHOT_PREFIX);

                        if (index >= 0) {
                            try {
                                snapshot = GDownloader.OBJECT_MAPPER.readTree(line.substring(index));

                                processProgress(entry, snapshot);
                            } catch (JacksonException e) {
                                log.debug("Unable to parse progress snapshot: {}", e.getMessage());
                            }
                        } else if (main.getConfig().isDebugMode()) {
                            log.debug("[{}] - {}", entry.getDownloadId(), truncateLine(line));
                        }
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.debug("Sleep interrupted, closing process");

                        process.destroyForcibly();
                    }
                }
            }

            long stopped = System.currentTimeMillis() - start;

            if (cancelHook.get()) {
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process halted after {}ms.", stopped);
                }

                return null;
            } else {
                int exitCode = process.waitFor();
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process took {}ms, exit code: {}", stopped, exitCode);
                }

                return new Pair<>(exitCode, snapshot);
            }
        } catch (IOException e) {
            log.info("IO error: {}", e.getMessage());

            return null;
        } finally {
            entry.getDownloadStarted().set(false);
        }
    }

    private void processProgress(QueueEntry entry, JsonNode snapshot) {
        JsonNode downloads = snapshot.path("downloads");
        JsonNode messages = snapshot.path("status").path("messages");

        String message = messages.isEmpty()
            ? snapshot.path("scraping").path(0).path("url").asString("")
            : messages.path(messages.size() - 1).asString("");

        if (downloads.isEmpty()) {
            if (message.toLowerCase(Locale.ROOT).contains("rate limit")) {
                entry.markRateLimited();

                entry.updateStatus(DownloadStatusEnum.WAITING, message, false);
            } else if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, message, false);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, message, false);
            }

            return;
        }

        long total = 0;
        long done = 0;
        double speed = 0;

        for (JsonNode download : downloads.values()) {
            total += download.path("size").asLong(0);
            done += download.path("bytes_downloaded").asLong(0);
            speed += download.path("speed").asDouble(0);
        }

        String name = downloads.path(0).path("description").asString("");

        StringBuilder text = new StringBuilder();

        if (downloads.size() > 1) {
            text.append("(+").append(downloads.size() - 1).append(") ");
        }

        text.append(StringUtils.getHumanReadableFileSize(done));

        if (total > 0) {
            text.append(" / ").append(StringUtils.getHumanReadableFileSize(total));
        }

        if (speed > 0) {
            text.append(" (").append(StringUtils.getHumanReadableFileSize((long)speed)).append("/s)");
        }

        if (!name.isEmpty()) {
            text.append(" - ").append(name);
        }

        long eta = downloads.path(0).path("eta").asLong(0);
        if (eta > 0 && downloads.size() == 1) {
            text.append(" - ").append(StringUtils.formatETATime(eta * 1000));
        }

        boolean validTotal = total > 0 && done <= total;

        double roundedPercent = validTotal
            ? Math.round((done * 100d / total) * 10) / 10.0
            : -1;

        entry.getMediaCard().setPercentage(roundedPercent);
        entry.updateStatus(DownloadStatusEnum.DOWNLOADING, text.toString(), false);
    }

    private static boolean isTemporary(Path path, Path logsPath) {
        String name = path.toString();

        return path.startsWith(logsPath)
            || name.contains(FileUtils.TMP_FILE_IDENTIFIER)
            || name.contains(".cdl_hls")
            || name.contains(".cdl_hsl")
            || name.endsWith(".lock")
            || name.endsWith(".part");
    }

    private void loadSupportedDomains() {
        try {
            List<String> output = main.readOutput(
                List.of(getExecutablePath().get().getAbsolutePath(), "show", "--json"),
                Duration.ofSeconds(90));

            JsonNode sites = GDownloader.OBJECT_MAPPER.readTree(String.join("\n", output));

            Set<String> domains = new HashSet<>();
            for (JsonNode site : sites.values()) {
                for (JsonNode domain : site.path("supported_domains").values()) {
                    String name = domain.asString().toLowerCase(Locale.ROOT);

                    domains.add(name.startsWith(".") ? name.substring(1) : name);
                }
            }

            supportedDomains = Set.copyOf(domains);

            log.info("{} reports {} supported domains", getDownloaderId(), supportedDomains.size());
        } catch (Exception e) {
            log.error("{} failed to load supported domains: {}", getDownloaderId(), e.getMessage());

            if (log.isDebugEnabled()) {
                log.error("Exception:", e);
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {

    }
}
