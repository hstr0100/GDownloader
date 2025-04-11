/*
 * Copyright (C) 2025 hstr0100
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
package net.brlns.gdownloader.ffmpeg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.process.ProcessArguments;

import static net.brlns.gdownloader.ffmpeg.FFmpegProgressCalculator.isProgressOutput;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class FFmpegProcessRunner {

    public static int runFFmpeg(FFmpegTranscoder transcoder, ProcessArguments arguments) {
        return runFFmpeg(transcoder, arguments, new FFmpegProcessOptions());
    }

    public static int runFFmpeg(FFmpegTranscoder transcoder,
        ProcessArguments arguments, FFmpegProcessOptions options) {
        return runProcess(transcoder, transcoder.getFFmpegExecutable(), arguments, options);
    }

    public static int runFFprobe(FFmpegTranscoder transcoder, ProcessArguments arguments) {
        return runFFmpeg(transcoder, arguments, new FFmpegProcessOptions());
    }

    public static int runFFprobe(FFmpegTranscoder transcoder,
        ProcessArguments arguments, FFmpegProcessOptions options) {
        return runProcess(transcoder, transcoder.getFFprobeExecutable(), arguments, options);
    }

    private static int runProcess(FFmpegTranscoder transcoder, Optional<String> executable,
        ProcessArguments arguments, FFmpegProcessOptions options) {
        try {
            String ffmpegBinary = executable.orElseThrow(()
                -> new IOException("Please install FFmpeg to use the transcoding option"));

            ProcessArguments command = new ProcessArguments(ffmpegBinary, arguments);

            Process process;
            if (options.isDiscardOutput()) {
                process = transcoder.getProcessMonitor()
                    .startSilentProcess(command, options.getCancelHook());
            } else {
                process = transcoder.getProcessMonitor()
                    .startProcess(command, options.getCancelHook());
            }

            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                if (options.isLazyReader()) {
                    // This reader is better suited for status polling and long-running tasks, as it avoids busy-waiting constantly.
                    while (!options.getCancelHook().get() && process.isAlive()) {
                        if (Thread.currentThread().isInterrupted()) {
                            process.destroyForcibly();
                            throw new InterruptedException("Download interrupted");
                        }

                        if (reader.ready()) {
                            if ((line = reader.readLine()) != null) {
                                processLine(line, options);
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
                } else {
                    // This reader is for tasks that need an immediate output.
                    while (!options.getCancelHook().get() && process.isAlive() && (line = reader.readLine()) != null) {
                        processLine(line, options);
                    }
                }
            }

            TimeUnit timeUnit = options.getTimeoutUnit();
            if (timeUnit != null) {
                boolean completed = process.waitFor(options.getTimeoutValue(), timeUnit);

                if (!completed) {
                    log.error("ffmpeg process timed out");
                    process.destroyForcibly();
                    return -1;
                }

                return process.exitValue();
            }

            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            FFmpegProgressListener listener = options.getListener();
            if (listener != null) {
                if (log.isDebugEnabled()) {
                    log.debug("FFmpeg error: {}", e.getMessage(), e);
                }

                listener.updateProgress(options.getLogPrefix() + "error: " + e.getMessage(), options.getTaskStarted().get(), -1);
            } else {
                log.error("FFmpeg error: {}", e.getMessage(), e);
            }

            return 254;
        }
    }

    private static void processLine(String line, FFmpegProcessOptions options) {
        FFmpegProgressCalculator calculator = options.getCalculator();

        FFmpegProgressListener listener = options.getListener();
        if (listener != null) {
            double progress = -1;
            if (calculator != null) {
                progress = calculator.getCurrentProgress(line);
            }

            if (isProgressOutput(line)) {
                options.getTaskStarted().set(true);
            }

            if (options.isLogOutput()) {
                if (progress != -1) {
                    log.info("{} p-out: {} progress: {} ", options.getLogPrefix(), line, progress);
                } else {
                    log.info("{} p-out: {}", options.getLogPrefix(), line);
                }
            }

            listener.updateProgress(options.getLogPrefix() + line, options.getTaskStarted().get(), progress);
        } else if (options.isLogOutput()) {
            log.info("{} p-out: {}", options.getLogPrefix(), line);
        }
    }
}
