package net.brlns.gdownloader.downloader;

import jakarta.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.enums.DownloadStatusEnum;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.downloader.structs.DownloadResult;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.enums.AudioContainerEnum;
import net.brlns.gdownloader.settings.enums.DownloadTypeEnum;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.menu.IMenuEntry;
import net.brlns.gdownloader.ui.menu.RunnableMenuEntry;
import net.brlns.gdownloader.util.DirectoryUtils;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;

import static net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum.*;
import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.DownloadTypeEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SpotDLDownloader extends AbstractDownloader {

    @Getter
    @Setter
    private Optional<File> executablePath = Optional.empty();

    @Getter
    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

    public SpotDLDownloader(DownloadManager managerIn) {
        super(managerIn);
    }

    @Override
    public boolean isEnabled() {
        return getExecutablePath().isPresent()
            && main.getConfig().isSpotDLEnabled();
    }

    @Override
    public DownloaderIdEnum getDownloaderId() {
        return DownloaderIdEnum.SPOTDL;
    }

    @Override
    public boolean isMainDownloader() {
        return false;
    }

    @Override
    public List<DownloadTypeEnum> getArchivableTypes() {
        return Collections.singletonList(SPOTIFY);
    }

    @Override
    public void removeArchiveEntry(QueueEntry queueEntry) {
        try {
            for (DownloadTypeEnum downloadType : getArchivableTypes()) {
                FileUtils.removeLineIfExists(
                    manager.getArchiveFile(this, downloadType),
                    queueEntry.getUrl());
            }
        } catch (Exception e) {
            log.error("Failed to remove archive entry for video: {}", queueEntry.getUrl(), e);
        }
    }

    @Override
    protected boolean canConsumeUrl(String inputUrl) {
        return isEnabled() && inputUrl.contains("spotify.com");
    }

    @Override
    protected boolean tryQueryVideo(QueueEntry queueEntry) {
        // TODO
        return false;
    }

    @Override
    protected DownloadResult tryDownload(QueueEntry entry) throws Exception {
        AbstractUrlFilter filter = entry.getFilter();

        if (!main.getConfig().isSpotDLEnabled()) {
            return new DownloadResult(FLAG_DOWNLOADER_DISABLED);
        }

        File finalPath = main.getOrCreateDownloadsDirectory();

        File tmpPath = DirectoryUtils.getOrCreate(finalPath, GDownloader.CACHE_DIRETORY_NAME, String.valueOf(entry.getDownloadId()));
        entry.setTmpDirectory(tmpPath);

        List<String> genericArguments = new ArrayList<>();

        genericArguments.addAll(List.of(
            executablePath.get().getAbsolutePath(),
            "--simple-tui",//As far as I can tell, these change nothing. The way it's displayed now Java cannot read spotDL's progress bar.
            "--headless"
        ));

        if (main.getConfig().isRespectSpotDLConfigFile()) {
            // We can't specify config location for spotDL, our only choice is to copy or symlink it.
            genericArguments.add("--config");
        }

        if (ffmpegPath.isPresent()) {
            genericArguments.addAll(List.of(
                "--ffmpeg",
                ffmpegPath.get().getAbsolutePath()
            ));
        }

        genericArguments.addAll(filter.getArguments(this, ALL, manager, tmpPath, entry.getUrl()));

        boolean success = false;
        String lastOutput = "";

        for (DownloadTypeEnum type : DownloadTypeEnum.values()) {
            boolean supported = getDownloadTypes().contains(type);

            if (!supported || type != SPOTIFY
                || !main.getConfig().isSpotDLEnabled()) {
                continue;
            }

            entry.getMediaCard().setPlaceholderIcon(type);

            List<String> arguments = new ArrayList<>(genericArguments);

            List<String> downloadArguments = filter.getArguments(this, type, manager, tmpPath, entry.getUrl());
            arguments.addAll(downloadArguments);

            if (main.getConfig().isDebugMode()) {
                log.debug("ALL {}: Type {} ({}): {}",
                    genericArguments,
                    type,
                    filter.getDisplayName(),
                    downloadArguments);
            }

            Pair<Integer, String> result = processDownload(entry, arguments);

            if (result == null || entry.getCancelHook().get()) {
                return new DownloadResult(FLAG_STOPPED);
            }

            lastOutput = result.getValue();

            if (result.getKey() != 0) {
                if (lastOutput.contains("Unsupported URL")) {
                    return new DownloadResult(FLAG_UNSUPPORTED, lastOutput);
                }

                return new DownloadResult(FLAG_MAIN_CATEGORY_FAILED, lastOutput);
            } else {
                success = true;
            }
        }

        return new DownloadResult(success ? FLAG_SUCCESS : FLAG_UNSUPPORTED, lastOutput);
    }

    @Override
    protected Map<String, IMenuEntry> processMediaFiles(QueueEntry entry) {
        File finalPath = new File(main.getOrCreateDownloadsDirectory(), "SpotDL");
        if (!finalPath.exists()) {
            finalPath.mkdirs();
        }

        File tmpPath = entry.getTmpDirectory();

        QualitySettings quality = entry.getFilter().getQualitySettings();

        Map<String, IMenuEntry> rightClickOptions = new TreeMap<>();

        try {
            List<Path> paths = Files.walk(tmpPath.toPath())
                .sorted(Comparator.reverseOrder()) // Process files before directories
                .toList();

            for (Path path : paths) {
                if (path.equals(tmpPath.toPath())) {
                    continue;
                }

                Path relativePath = tmpPath.toPath().relativize(path);
                Path targetPath = finalPath.toPath().resolve(relativePath);

                try {
                    if (Files.isDirectory(targetPath)) {
                        Files.createDirectories(targetPath);

                        log.info("Created directory: {}", targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        targetPath = FileUtils.ensureUniqueFileName(targetPath);
                        Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        entry.getFinalMediaFiles().add(targetPath.toFile());
                        updateRightClickOptions(path, quality, rightClickOptions, entry);

                        log.info("Moved file: {}", targetPath);
                    }
                } catch (FileAlreadyExistsException e) {
                    log.warn("File or directory already exists: {}", targetPath, e);
                } catch (IOException e) {
                    log.error("Failed to move file: {}", path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list files", e);
        }

        return rightClickOptions;
    }

    private void updateRightClickOptions(Path path, QualitySettings quality, Map<String, IMenuEntry> rightClickOptions, QueueEntry entry) {
        if (isFileType(path, quality.getAudioContainer().getValue())) {
            rightClickOptions.putIfAbsent(l10n("gui.play_audio"),
                new RunnableMenuEntry(() -> entry.play(AudioContainerEnum.class)));
        }
    }

    private boolean isFileType(Path path, String extension) {
        return path.getFileName().toString().toLowerCase().endsWith("." + extension);
    }

    @Nullable
    private Pair<Integer, String> processDownload(QueueEntry entry, List<String> arguments) throws Exception {
        long start = System.currentTimeMillis();

        List<String> finalArgs = new ArrayList<>(arguments);
        finalArgs.add("download");
        finalArgs.add(entry.getUrl());

        ProcessBuilder processBuilder = new ProcessBuilder(finalArgs);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        entry.setProcess(process);

        String lastOutput = "";

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while (manager.isRunning() && !entry.getCancelHook().get() && process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedException("Download interrupted");
                }

                if (reader.ready()) {
                    if ((line = reader.readLine()) != null) {
                        lastOutput = line;

                        processProgress(entry, lastOutput);
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

            if (!manager.isRunning() || entry.getCancelHook().get()) {
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process halted after {}ms.", stopped);
                }

                return null;
            } else {
                int exitCode = process.waitFor();
                if (main.getConfig().isDebugMode()) {
                    log.debug("Download process took {}ms, exit code: {}", stopped, exitCode);
                }

                return new Pair<>(exitCode, lastOutput);
            }
        } catch (IOException e) {
            log.info("IO error: {}", e.getMessage());

            return null;
        } finally {
            entry.getDownloadStarted().set(false);

            // Our ProcessMonitor will take care of closing the underlying process.
        }
    }

    private void processProgress(QueueEntry entry, String lastOutput) {
        if (main.getConfig().isDebugMode()) {
            log.debug("[{}] - {}", entry.getDownloadId(), lastOutput);
        }

        if (lastOutput.contains("Replacing with empty") || lastOutput.endsWith("string.")) {
            return;
        }

        if (lastOutput.contains("Downloading")) {
            entry.getMediaCard().setPercentage(-1);

            entry.updateStatus(DownloadStatusEnum.DOWNLOADING, lastOutput);
        } else {
            if (entry.getDownloadStarted().get()) {
                entry.updateStatus(DownloadStatusEnum.PROCESSING, lastOutput);
            } else {
                entry.updateStatus(DownloadStatusEnum.PREPARING, lastOutput);
            }
        }
    }

    @Override
    public void close() {

    }

    /**
     * Converts yt-dlp naming templates to spotDL
     */
    public static String convertTemplateForSpotDL(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Mapping from yt-dlp template variables to spotdl variables
        Map<String, String> templateMap = new HashMap<>();

        templateMap.put("title", "title");
        templateMap.put("uploader", "artists");
        templateMap.put("creator", "artists");
        templateMap.put("artist", "artist");
        templateMap.put("album", "album");
        templateMap.put("channel", "album-artist");
        templateMap.put("track", "title");
        templateMap.put("genre", "genre");
        templateMap.put("duration", "duration");
        templateMap.put("duration_string", "duration");
        templateMap.put("release_year", "year");
        templateMap.put("release_date", "original-date");
        templateMap.put("upload_date", "original-date");
        templateMap.put("track_number", "track-number");
        templateMap.put("n_entries", "tracks-count");
        templateMap.put("playlist", "list-name");
        templateMap.put("playlist_index", "list-position");
        templateMap.put("playlist_title", "list-name");
        templateMap.put("playlist_count", "list-length");
        templateMap.put("ext", "output-ext");
        templateMap.put("disc_number", "disc-number");
        templateMap.put("id", "track-id");
        templateMap.put("publisher", "publisher");
        templateMap.put("isrc", "isrc");

        // Use a regex pattern to match yt-dlp template variables with all their formatting options.
        // You've earned yourself a royal cookie if you can understand this entire regex.
        Pattern pattern = Pattern.compile("%\\(([^>,:&|\\)]*)(?:[>,:&|][^\\)]*)?\\)([-#0+ ]*\\d*\\.?\\d*[diouxXheEfFgGcrsBlqDSUj])?");

        try {
            Matcher matcher = pattern.matcher(input);

            // Check if there are unbalanced parentheses which indicate malformed templates
            int openCount = 0;
            int closeCount = 0;
            for (char c : input.toCharArray()) {
                if (c == '(') {
                    openCount++;
                }
                if (c == ')') {
                    closeCount++;
                }
            }

            // If unbalanced, return original string
            if (openCount != closeCount) {
                return input;
            }

            StringBuffer result = new StringBuffer();

            // Process each match
            while (matcher.find()) {
                String baseVar = matcher.group(1);

                // Check if this is an empty field name
                if (baseVar.isEmpty()) {
                    matcher.appendReplacement(result, "{}");
                    continue;
                }

                // Handle object traversal and arithmetic
                String fieldName = baseVar;
                if (baseVar.contains(".")) {
                    fieldName = baseVar.split("\\.")[0]; // Get the part before first dot
                } else if (baseVar.contains("+") || baseVar.contains("-") || baseVar.contains("*")) {
                    // For arithmetic expressions, extract the variable name
                    fieldName = baseVar.split("[+\\-*]")[0];
                }

                // Look up the corresponding spotdl variable
                String spotdlVar = templateMap.getOrDefault(fieldName, fieldName);

                // For complex formatting that spotdl doesn't support, just map the base variable
                String replacement = "{" + spotdlVar + "}";

                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }

            // Append the remainder of the input string
            matcher.appendTail(result);

            return result.toString();
        } catch (Exception e) {
            // Something went wrong, return the original string
            log.error("Failed to parse name template: {}", input);
            return input;
        }
    }
}
