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
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class LoggerUtils {

    private static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n";

    private static final Level DEFAULT_LOG_LEVEL = Level.INFO;

    public static void setLogFile(File logFile) {
        LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger("ROOT");

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("LOG_FILE");
        fileAppender.setFile(logFile.getPath());

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
        } else if (!debug && logger.getLevel() != DEFAULT_LOG_LEVEL) {
            logger.setLevel(DEFAULT_LOG_LEVEL);
            jnativeLogger.setLevel(java.util.logging.Level.OFF);

            log.info("Log level changed to: {}", logger.getLevel());
        }
    }

    private static class LogFilter extends Filter<ILoggingEvent> {

        @Override
        public FilterReply decide(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            if (message != null && message.contains("[download]") && message.contains("ETA")) {
                return FilterReply.DENY;
            }

            return FilterReply.NEUTRAL;
        }
    }
}
