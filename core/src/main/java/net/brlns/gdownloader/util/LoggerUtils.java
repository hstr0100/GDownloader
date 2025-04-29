/*
 * Copyright (C) 2024 @hstr0100
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
package net.brlns.gdownloader.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.github.kwhat.jnativehook.GlobalScreen;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import org.slf4j.LoggerFactory;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class LoggerUtils {

    private static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n";

    private static final int MAX_LOG_FILES = 10;

    private static final Level DEFAULT_LOG_LEVEL = Level.INFO;
    private static boolean DEBUG_MODE_FORCED = false;

    @Getter
    private static File logFile;

    public static void setLogFile(File logFileIn) {
        LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("LOG_FILE");
        fileAppender.setFile(logFileIn.getPath());

        LogFilter filter = new LogFilter();
        filter.setContext(loggerContext);
        filter.start();
        fileAppender.addFilter(filter);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        logger.addAppender(fileAppender);

        logFile = logFileIn;
    }

    public static void initLogFile(@NonNull File workDir) {
        deleteLegacyLogFiles(workDir);

        Path logDirPath = Paths.get(workDir.getAbsolutePath(), "logs");
        try {
            Files.createDirectories(logDirPath);
            String previousPrefix = "previous_";

            Files.deleteIfExists(logDirPath.resolve(previousPrefix + MAX_LOG_FILES + ".log"));
            Files.deleteIfExists(logDirPath.resolve(previousPrefix + MAX_LOG_FILES + ".zip"));

            for (int i = MAX_LOG_FILES - 1; i >= 1; i--) {
                Path sourcePath = logDirPath.resolve(previousPrefix + i + ".log");
                Path targetPath = logDirPath.resolve(previousPrefix + (i + 1) + ".log");
                FileUtils.moveFileIfExists(sourcePath, targetPath);

                Path sourceZipPath = logDirPath.resolve(previousPrefix + i + ".zip");
                Path targetZipPath = logDirPath.resolve(previousPrefix + (i + 1) + ".zip");
                FileUtils.moveFileIfExists(sourceZipPath, targetZipPath);
            }

            // Compress older log files
            for (int i = 3; i <= MAX_LOG_FILES; i++) {
                Path logPath = logDirPath.resolve(previousPrefix + i + ".log");
                Path zipPath = logDirPath.resolve(previousPrefix + i + ".zip");

                if (Files.exists(logPath) && !Files.exists(zipPath)) {
                    ArchiveUtils.deflateToZip(logPath.toFile(), zipPath);
                    Files.delete(logPath);
                }
            }

            Path currentLogPath = logDirPath.resolve("current.log");
            Path previous1LogPath = logDirPath.resolve(previousPrefix + 1 + ".log");
            FileUtils.moveFileIfExists(currentLogPath, previous1LogPath);

            Files.createFile(currentLogPath);

            setLogFile(currentLogPath.toFile());
        } catch (IOException e) {
            GDownloader.handleException(e, "I/O Error during log rotation.");
        }
    }

    public static void setForcedDebugMode() {
        DEBUG_MODE_FORCED = true;
        setDebugLogLevel(true);
    }

    public static void setDebugLogLevel(boolean debug) {
        LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");

        java.util.logging.Logger jnativeLogger
            = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());

        if (debug && logger.getLevel() != Level.DEBUG) {
            logger.setLevel(Level.DEBUG);
            jnativeLogger.setLevel(java.util.logging.Level.ALL);

            log.info("Log level changed to: {}", logger.getLevel());
        } else if (!debug && logger.getLevel() != DEFAULT_LOG_LEVEL && !DEBUG_MODE_FORCED) {
            logger.setLevel(DEFAULT_LOG_LEVEL);
            jnativeLogger.setLevel(java.util.logging.Level.OFF);

            log.info("Log level changed to: {}", logger.getLevel());
        }
    }

    private static void deleteLegacyLogFiles(File workDir) {
        try {
            Files.deleteIfExists(workDir.toPath().resolve("gdownloader_log.txt"));
            Files.deleteIfExists(workDir.toPath().resolve("gdownloader_log_previous.txt"));
        } catch (IOException e) {
            // Irrelevant
        }
    }

    private static class LogFilter extends Filter<ILoggingEvent> {

        @Override
        public FilterReply decide(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            if (message != null) {
                if (message.contains("[download]") && message.contains("ETA")) {
                    return FilterReply.DENY;
                }

                if (message.contains("bitrate=") && message.contains("time=")) {
                    return FilterReply.DENY;
                }
            }

            return FilterReply.NEUTRAL;
        }
    }
}
