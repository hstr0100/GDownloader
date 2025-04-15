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

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ffmpeg.enums.EncoderEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class FFmpegSelfTester {

    private static final FFmpegTranscoder TRANSCODER = new FFmpegTranscoder(null);

    public static void main(String[] args) {
        runSelfTest();

        TRANSCODER.close();
    }

    public static void runSelfTest() {
        Set<String> ffmpegEncoders = TRANSCODER.getCompatScanner().getAvailableFFmpegEncoders();

        log.info("System Report:");
        log.info("Available FFmpeg encoders: {}", ffmpegEncoders);

        log.info("Starting FFmpeg encoder tests");

        Map<EncoderEnum, TestResult> results = new EnumMap<>(EncoderEnum.class);

        for (EncoderEnum encoder : EncoderEnum.values()) {
            if (encoder != EncoderEnum.NO_ENCODER && !encoder.isAutomatic()) {
                testPreset(encoder, results);
            }
        }

        for (EncoderEnum encoder : EncoderEnum.values()) {
            if (encoder.isAutomatic()) {
                testAutoPreset(encoder, results);
            }
        }

        printSummary(results);
    }

    private static void testPreset(EncoderEnum encoder, Map<EncoderEnum, TestResult> results) {
        log.info("Testing encoder: {}", encoder);

        try {
            long startTime = System.currentTimeMillis();
            boolean available = TRANSCODER.getCompatScanner().testEncoder(encoder, true);
            long endTime = System.currentTimeMillis();

            TestResult result = new TestResult(
                encoder,
                available,
                available ? "PASS" : "FAIL",
                endTime - startTime
            );

            results.put(encoder, result);

            log.info("Encoder {} test result: {}", encoder,
                available ? "AVAILABLE" : "UNAVAILABLE");
        } catch (Exception e) {
            log.error("Error testing encoder {}: {}", encoder, e.getMessage());

            results.put(encoder, new TestResult(encoder, false, "Error: " + e.getMessage(), 0));
        }
    }

    private static void testAutoPreset(EncoderEnum autoPreset, Map<EncoderEnum, TestResult> results) {
        log.info("Testing AUTO encoder: {}", autoPreset);

        try {
            long startTime = System.currentTimeMillis();

            EncoderEnum resolvedEncoder = TRANSCODER.resolveEncoder(autoPreset);

            boolean available = TRANSCODER.getCompatScanner().testEncoder(resolvedEncoder, true);
            long endTime = System.currentTimeMillis();

            TestResult result = new TestResult(
                autoPreset,
                available,
                available
                    ? "Resolved to " + resolvedEncoder
                    : "Failed to resolve or test",
                endTime - startTime,
                resolvedEncoder
            );

            results.put(autoPreset, result);

            log.info("AUTO encoder {} resolved to {} ({})",
                autoPreset,
                resolvedEncoder,
                available ? "AVAILABLE" : "UNAVAILABLE");
        } catch (Exception e) {
            log.error("Error testing AUTO encoder {}: {}", autoPreset, e.getMessage());

            results.put(autoPreset, new TestResult(autoPreset, false, "Error: " + e.getMessage(), 0));
        }
    }

    private static void printSummary(Map<EncoderEnum, TestResult> results) {
        log.info("==== FFmpeg Encoder Test Summary ====");

        log.info("-- Hardware Encoders --");
        for (EncoderTypeEnum type : EncoderTypeEnum.values()) {
            if (type != EncoderTypeEnum.AUTO && type != EncoderTypeEnum.SOFTWARE) {
                printEncoderTypeResults(results, type);
            }
        }

        log.info("-- Software Encoders --");
        printEncoderTypeResults(results, EncoderTypeEnum.SOFTWARE);

        log.info("-- Auto Encoders --");
        printEncoderTypeResults(results, EncoderTypeEnum.AUTO);

        int totalAvailable = (int)results.values().stream().filter(TestResult::isAvailable).count();
        int total = results.size();
        log.info("Total available encoders: {}/{} ({}%)",
            totalAvailable,
            total,
            total > 0 ? (totalAvailable * 100 / total) : 0);
    }

    private static void printEncoderTypeResults(Map<EncoderEnum, TestResult> results, EncoderTypeEnum type) {
        log.info("{} Encoders:", type);

        boolean hasEncodersOfType = false;

        for (EncoderEnum encoder : EncoderEnum.values()) {
            if (encoder.getEncoderType() == type) {
                TestResult result = results.get(encoder);
                if (result != null) {
                    hasEncodersOfType = true;

                    // The encode time in milliseconds also includes the system call overhead
                    log.info("  {} ({}): {} {} ({}ms)",
                        encoder,
                        encoder.getFfmpegCodecName(),
                        result.isAvailable() ? "✓" : "✗",
                        result.getDetails(),
                        result.getTestDurationMs());
                }
            }
        }

        if (!hasEncodersOfType) {
            log.info("  None tested");
        }
    }

    @Data
    @AllArgsConstructor
    private static class TestResult {

        private final EncoderEnum encoder;
        private final boolean available;
        private final String details;
        private final long testDurationMs;
        private final EncoderEnum resolvedTo;

        public TestResult(EncoderEnum encoder, boolean available, String details, long testDurationMs) {
            this(encoder, available, details, testDurationMs, null);
        }
    }
}
