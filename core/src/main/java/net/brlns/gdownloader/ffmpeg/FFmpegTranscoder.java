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

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ffmpeg.enums.*;
import net.brlns.gdownloader.ffmpeg.streams.*;
import net.brlns.gdownloader.ffmpeg.structs.EncoderPreset;
import net.brlns.gdownloader.ffmpeg.structs.EncoderProfile;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.process.ProcessMonitor;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.updater.SystemExecutableLocator;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.NoFallbackAvailableException;

import static net.brlns.gdownloader.settings.enums.VideoContainerEnum.*;
import static net.brlns.gdownloader.util.FileUtils.getBinaryName;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class FFmpegTranscoder {

    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

    private boolean queriedForSystemBinary = false;

    @Getter
    private final ProcessMonitor processMonitor;

    @Getter
    private final FFmpegCompatibilityScanner compatScanner;

    public FFmpegTranscoder(@Nullable ProcessMonitor processMonitorIn) {
        processMonitor = processMonitorIn == null ? new ProcessMonitor() : processMonitorIn;
        compatScanner = new FFmpegCompatibilityScanner(this);
    }

    public void init() {
        compatScanner.init();
    }

    public boolean hasFFmpeg() {
        return getFfmpegPath().isPresent();
    }

    public Optional<File> getFfmpegPath() {
        if (ffmpegPath.isEmpty() && !queriedForSystemBinary) {
            ffmpegPath = Optional.ofNullable(
                SystemExecutableLocator.locateExecutable("ffmpeg")
            ).map(File::getParentFile);

            queriedForSystemBinary = true;
        }

        return ffmpegPath;
    }

    public Optional<String> getFFmpegExecutable() {
        return getFfmpegPath()
            .map(path -> path + File.separator + getBinaryName("ffmpeg"));
    }

    public Optional<String> getFFprobeExecutable() {
        return getFfmpegPath()
            .map(path -> path + File.separator + getBinaryName("ffprobe"));
    }

    protected EncoderEnum resolveEncoder(EncoderEnum encoder) {
        if (!encoder.isAutomatic()) {
            return encoder;
        }

        return resolveEncoder(encoder.getVideoCodec());
    }

    protected EncoderEnum resolveEncoder(VideoCodecEnum targetCodec) {
        // Try each encoder type in the fallback order for the target codec
        for (EncoderTypeEnum type : EncoderTypeEnum.values()) {
            if (type == EncoderTypeEnum.AUTO) {
                continue;
            }

            EncoderEnum candidate = findEncoderByTypeAndCodec(type, targetCodec);
            if (candidate != null) {
                if (getCompatScanner().isEncoderAvailable(candidate)) {
                    return candidate;
                }
            }
        }

        // If all hardware encoders fail, fallback to software
        EncoderEnum softwareEncoder = findEncoderByTypeAndCodec(EncoderTypeEnum.SOFTWARE, targetCodec);
        if (softwareEncoder != null) {
            log.info("Falling back to software encoder: {}", softwareEncoder);
            return softwareEncoder;
        }

        // Last resort: use the default H264 software encoder
        // If this computer was manufactured in this century, it should always be available.
        log.warn("No suitable encoders found for {}, using H.264 software", targetCodec);
        return EncoderEnum.H264_SOFTWARE;
    }

    private EncoderEnum findEncoderByTypeAndCodec(EncoderTypeEnum type, VideoCodecEnum codec) {
        for (EncoderEnum encoder : EncoderEnum.values()) {
            if (encoder.getEncoderType() == type && encoder.getVideoCodec() == codec) {
                return encoder;
            }
        }

        return null;
    }

    // TODO abstraction
    // TODO separate bitrate for audio-only mode
    public int startTranscode(FFmpegConfig config, File inputFile,
        File outputFile, AtomicBoolean cancelHook, FFmpegProgressListener listener) throws Exception {
        if (config.getVideoEncoder() == EncoderEnum.NO_ENCODER
            && config.getAudioCodec() == AudioCodecEnum.NO_CODEC || !config.isEnabled()) {
            if (log.isDebugEnabled()) {
                log.debug("Transcoding not configured, returning -3");
            }

            return -3;
        }

        if (!inputFile.exists()) {
            log.error("Input file does not exist, returning -4");
            return -4;
        }

        if (log.isDebugEnabled()) {
            log.debug("Input file: {}", inputFile);
            log.debug("Output file: {}", outputFile);
        }

        ProcessArguments args = new ProcessArguments(
            "-hide_banner",
            "-loglevel",
            (log.isDebugEnabled() ? "error" : "quiet"),
            "-stats",
            "-y",
            "-i", inputFile.getAbsolutePath(),
            "-map_metadata", "0");

        MediaStreamData streamData = getMediaStreams(inputFile);
        if (streamData == null) {
            throw new IOException("Unable to obtain stream data from " + inputFile);
        }

        FFmpegProgressCalculator progressCalculator = null;
        for (AbstractStream stream : streamData.getStreams()) {
            if (progressCalculator == null) {
                progressCalculator = FFmpegProgressCalculator.fromStream(stream);
            }

            if (progressCalculator != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Progress Data: {}", progressCalculator);
                }

                break;
            }
        }

        boolean isTranscodeNeeded = false;
        String logPrefix = "";

        VideoContainerEnum videoContainer = config.getVideoContainer();

        int outputVideoIndex = 0;
        for (VideoStream stream : streamData.getVideoStreams()) {
            EncoderEnum actualEncoder = resolveEncoder(config.getVideoEncoder());
            VideoCodecEnum videoCodec = actualEncoder.getVideoCodec();

            if (videoCodec != VideoCodecEnum.NO_CODEC && !videoCodec.isSupportedByContainer(videoContainer)) {
                log.error("Target container {} does not support video codec: {} in stream #{}",
                    videoContainer, videoCodec, stream.getIndex());
                videoCodec = VideoCodecEnum.getFallbackCodec(videoContainer);
                if (videoCodec == null) {
                    throw new NoFallbackAvailableException(
                        "Impossible to transcode video to container " + videoContainer);
                }

                actualEncoder = resolveEncoder(videoCodec);
                log.error("Falling back to: {}", actualEncoder);
            }

            EncoderTypeEnum encoderType = actualEncoder.getEncoderType();

            boolean needsTranscoding = !videoCodec.getCodecName().equals(stream.getCodecName());

            args.add("-map", "0:" + stream.getIndex());
            args.add("-c:v:" + outputVideoIndex);
            if (videoCodec != VideoCodecEnum.NO_CODEC && needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    String name = encoderType == EncoderTypeEnum.SOFTWARE ? "SW" : encoderType.name();
                    logPrefix = name + ": ";
                }

                args.add(actualEncoder.getFfmpegCodecName());

                EncoderProfile profile = config.getProfile();
                if (!profile.equals(EncoderProfile.NO_PROFILE)
                    && getCompatScanner().isCompatible(actualEncoder, profile)) {
                    args.add(
                        "-profile:v:" + outputVideoIndex,
                        profile.getFfmpegProfileName());
                }

                EncoderPreset speedPreset = config.getSpeedPreset();
                if (!speedPreset.equals(EncoderPreset.NO_PRESET)
                    && !speedPreset.getFfmpegPresetCommand().isEmpty()
                    && getCompatScanner().isCompatible(actualEncoder, speedPreset)) {
                    // AMF uses -quality, AV1 software uses -usage.
                    args.add(
                        speedPreset.getFfmpegPresetCommand() + ":v:" + outputVideoIndex,
                        speedPreset.getFfmpegPresetName());
                }

                // 0 = lossless, 63 = bathroom tiles, roof shingles
                int rateControlValue = Math.clamp(config.getRateControlValue(), 0, 63);
                // 0 = a black void, MAX_VALUE = a melted GPU
                int videoBitrate = Math.clamp(config.getVideoBitrate(), 0, Integer.MAX_VALUE);

                RateControlModeEnum rateControlMode = config.getRateControlMode();
                if (rateControlMode == RateControlModeEnum.DEFAULT) {
                    // Default to CRF if not specified
                    rateControlMode = RateControlModeEnum.CRF;
                }

                // AV1 quirk, select an appropriate speed/quality ratio based on available CPU horsepower
                switch (encoderType) {
                    case SOFTWARE -> {
                        switch (videoCodec) {
                            case AV1 -> {
                                int cpuCount = Runtime.getRuntime().availableProcessors();
                                String cpuUsed = cpuCount <= 2 ? "8" : cpuCount <= 4
                                    ? "7" : cpuCount <= 8 ? "5" : "1";
                                args.add("-cpu-used", cpuUsed);
                            }
                        }
                    }
                }

                switch (rateControlMode) {
                    case CRF -> {
                        switch (encoderType) {
                            case SOFTWARE -> {
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        args.add("-crf:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    case VP9 -> {
                                        args.add(
                                            "-crf:v:" + outputVideoIndex, rateControlValue,
                                            "-b:v:" + outputVideoIndex, "0");
                                    }
                                    case AV1 -> {
                                        args.add("-crf:v:" + outputVideoIndex, rateControlValue);
                                    }
                                }
                            }
                            case NVENC -> {
                                // NVENC uses different parameters for rate control, Untested.
                                args.add(
                                    "-rc:v:" + outputVideoIndex, "vbr",
                                    "-cq:v:" + outputVideoIndex, rateControlValue);
                            }
                            case QSV -> {
                                // QSV uses global_quality for quality-based encoding, Untested
                                args.add(
                                    "-global_quality:v:" + outputVideoIndex, rateControlValue,
                                    "-look_ahead:v:" + outputVideoIndex, "1");
                            }
                            case AMF -> {
                                // AMF uses cqp
                                args.add(
                                    "-rc:v:" + outputVideoIndex, "cqp",
                                    "-qp_i:v:" + outputVideoIndex, rateControlValue,
                                    "-qp_p:v:" + outputVideoIndex, rateControlValue,
                                    "-qp_b:v:" + outputVideoIndex, rateControlValue);
                            }
                            case VAAPI -> {
                                // VAAPI uses qp parameter differently
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        // For H.264/H.265, set quantization parameter for all frames
                                        args.add("-qp:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    case VP9, AV1 -> {
                                        // For VP9/AV1, use global_quality
                                        args.add(
                                            "-global_quality:v:" + outputVideoIndex, rateControlValue);
                                    }
                                }
                            }
                            //case V4L2M2M -> {}
                            default -> {
                                // Fallback to bitrate-based encoding if unsupported
                                args.add("-b:v:" + outputVideoIndex, videoBitrate + "k");
                            }
                        }
                    }
                    case CQP -> {
                        switch (encoderType) {
                            case SOFTWARE -> {
                                switch (videoCodec) {
                                    case H264 -> {
                                        args.add("-qp:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    case H265 -> {
                                        args.add(
                                            "-x265-params:v:" + outputVideoIndex, "qp=" + rateControlValue);
                                    }
                                    case VP9 -> {
                                        args.add(
                                            "-qmin:v:" + outputVideoIndex, rateControlValue,
                                            "-qmax:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    case AV1 -> {
                                        args.add("-qp:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    default -> {
                                        // Fall back to CRF for unsupported codecs
                                        args.add("-crf:v:" + outputVideoIndex, rateControlValue);
                                    }
                                }
                            }
                            case NVENC -> {
                                args.add(
                                    "-rc:v:" + outputVideoIndex, "constqp",
                                    "-qp:v:" + outputVideoIndex, rateControlValue);
                            }
                            case QSV -> {
                                args.add("-q:v:" + outputVideoIndex, rateControlValue);
                            }
                            case AMF -> {
                                args.add(
                                    "-rc:v:" + outputVideoIndex, "cqp",
                                    "-qp_i:v:" + outputVideoIndex, rateControlValue,
                                    "-qp_p:v:" + outputVideoIndex, rateControlValue,
                                    "-qp_b:v:" + outputVideoIndex, rateControlValue);
                            }
                            case VAAPI -> {
                                // VAAPI uses qp parameter differently
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        // For H.264/H.265, set quantization parameter for all frames
                                        args.add("-qp:v:" + outputVideoIndex, rateControlValue);
                                    }
                                    case VP9, AV1 -> {
                                        // For VP9/AV1, use global_quality
                                        args.add(
                                            "-global_quality:v:" + outputVideoIndex, rateControlValue);
                                    }
                                }
                            }
                            //case V4L2M2M -> {}
                            default -> {
                                // Fallback to bitrate-based encoding if unsupported
                                args.add("-b:v:" + outputVideoIndex, videoBitrate + "k");
                            }
                        }
                    }
                    case CBR -> {
                        args.add("-b:v:" + outputVideoIndex, videoBitrate + "k");
                        // Force constant bitrate with buffer constraints
                        switch (encoderType) {
                            case NVENC, QSV -> {
                                args.add(
                                    "-maxrate:v:" + outputVideoIndex, videoBitrate + "k",
                                    "-minrate:v:" + outputVideoIndex, videoBitrate + "k",
                                    "-bufsize:v:" + outputVideoIndex, (videoBitrate * 2) + "k");
                            }
                            case AMF -> {
                                args.add("-rc:v:" + outputVideoIndex, "cbr");
                            }
                        }
                    }
                    default -> {
                        // VBR settings vary by encoder
                        switch (encoderType) {
                            case SOFTWARE -> {
                                if (videoCodec == VideoCodecEnum.H264
                                    || videoCodec == VideoCodecEnum.H265) {
                                    args.add("-crf:v:" + outputVideoIndex, rateControlValue);
                                } else {
                                    args.add("-b:v:" + outputVideoIndex, videoBitrate + "k");
                                }
                            }
                            case NVENC -> {
                                args.add(
                                    "-b:v:" + outputVideoIndex, videoBitrate + "k",
                                    "-maxrate:v:" + outputVideoIndex, (videoBitrate * 2) + "k");
                            }
                            // TODO: test AMF -rc qvbr
                            default -> {
                                args.add("-b:v:" + outputVideoIndex, videoBitrate + "k");
                            }
                        }
                    }
                }

                // HW encoding parameters
                switch (encoderType) {
                    // Intel QSV is finicky and never works out of the box on Linux,
                    // so if device detection fails, we will simply let it fall back to VAAPI.
                    // The same applies to AMD AMF; additionally, on Linux, only custom FFmpeg builds include the AMF codecs.
                    //
                    // TODO: Need feedback from an Nvidia user that can test NVENC encoding
                    case VAAPI -> {
                        String vaapiDevice = getCompatScanner().detectVaapiDevice(actualEncoder);
                        args.add(
                            "-vaapi_device", vaapiDevice,
                            "-filter:v:" + outputVideoIndex, "format=nv12,hwupload");
                    }
                    case V4L2M2M -> {
                        String device = getCompatScanner().detectV4l2m2mDevice();
                        if (device != null) {
                            args.add("-device", device);
                        }
                    }
                }
            } else {
                args.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                args.add(
                    "-metadata:s:v:" + outputVideoIndex,
                    "language=" + stream.getLanguage());
            }

            outputVideoIndex++;
        }

        for (VideoStream stream : streamData.getThumbnailStreams()) {
            if (Stream.of(MP4, MOV, MKV)
                .noneMatch(c -> videoContainer == c)) {
                continue;// WebM and most other containers do not support thumbnails
            }

            switch (config.getVideoContainer()) {
                case MP4, MOV -> {
                    // For MP4, the thumbnail is just a single frame video stream
                    args.add(
                        "-map", "0:" + stream.getIndex(),
                        "-c:v:" + outputVideoIndex, "copy",
                        "-disposition:v:" + outputVideoIndex, "attached_pic");

                    outputVideoIndex++;
                }
                case MKV -> {
                    // Matroska has a particular quirk in FFmpeg, we cannot simply copy the stream over
                    File thumbnailFile = extractThumbnail(stream, inputFile);
                    if (thumbnailFile != null) {
                        args.add(
                            "-attach", thumbnailFile.getAbsolutePath(),
                            "-metadata:s:t", "mimetype=image/png",
                            "-metadata:s:t", "filename=cover.png");
                    }
                }
            }
        }

        int outputAudioIndex = 0;
        for (AudioStream stream : streamData.getAudioStreams()) {
            AudioCodecEnum audioCodec = config.getAudioCodec();
            if (audioCodec != AudioCodecEnum.NO_CODEC && !audioCodec.isSupportedByContainer(videoContainer)) {
                log.error("Target container {} does not support audio codec: {} in stream #{}",
                    videoContainer, audioCodec, stream.getIndex());
                audioCodec = AudioCodecEnum.getFallbackCodec(videoContainer);
                if (audioCodec == null) {
                    throw new NoFallbackAvailableException(
                        "Impossible to transcode audio to container " + videoContainer);
                }

                log.error("Falling back to: {}", audioCodec);
            }

            boolean needsTranscoding = !audioCodec.getCodecName().equals(stream.getCodecName());

            args.add("-map", "0:" + stream.getIndex());
            args.add("-c:a:" + outputAudioIndex);
            if (audioCodec != AudioCodecEnum.NO_CODEC && needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    logPrefix = audioCodec.name() + ": ";
                }

                args.add(audioCodec.getFfmpegCodecName());

                args.add("-b:a:" + outputAudioIndex);
                AudioBitrateEnum audioBitrate = config.getAudioBitrate();
                if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                    args.add(audioBitrate.getValue() + "k");
                } else {
                    int bitrate = stream.getBitrateKbps();
                    if (bitrate == -1) {
                        // Fallback bitrate is 256 kbps unless otherwise specified.
                        bitrate = 256;
                    }

                    args.add(bitrate + "k");
                }
            } else {
                args.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                args.add(
                    "-metadata:s:a:" + outputAudioIndex,
                    "language=" + stream.getLanguage());
            }

            outputAudioIndex++;
        }

        int outputSubtitleIndex = 0;
        for (SubtitleStream stream : streamData.getSubtitleStreams()) {
            SubtitleCodecEnum subtitleCodec = SubtitleCodecEnum.getTargetSubtitleCodec(
                videoContainer, stream.getCodecName());
            if (subtitleCodec == null) {
                continue;
            }

            boolean needsTranscoding = !subtitleCodec.getCodecName().equals(stream.getCodecName())
                && subtitleCodec != SubtitleCodecEnum.COPY;

            args.add("-map", "0:" + stream.getIndex());
            args.add("-c:s:" + outputSubtitleIndex);
            if (needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    // ¯\_(ツ)_/¯
                    String name = subtitleCodec == SubtitleCodecEnum.ASS ? "SSA" : subtitleCodec.name();
                    logPrefix = name + ": ";
                }

                args.add(subtitleCodec.getCodecName());
            } else {
                args.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                args.add(
                    "-metadata:s:s:" + outputSubtitleIndex,
                    "language=" + stream.getLanguage());
            }

            if (notNullOrEmpty(stream.getTitle())) {
                args.add(
                    "-metadata:s:s:" + outputSubtitleIndex,
                    "title=" + stream.getTitle());
            }

            outputSubtitleIndex++;
        }

        int outputDataIndex = 0;
        for (DataStream stream : streamData.getDataStreams()) {
            switch (config.getVideoContainer()) {
                case MP4 -> {
                    // TODO: MOV: bin_data transcoding
                    args.add(
                        "-map", "0:" + stream.getIndex(),
                        "-c:d:" + outputDataIndex, "copy");

                    outputDataIndex++;
                }
            }
        }

        for (UnknownStream stream : streamData.getUnknownStreams()) {
            log.error("Unknown stream type: {} with codec: {} at index: {}",
                stream.getCodecType(), stream.getCodecName(), stream.getIndex());
        }

        args.add("-update", "1");
        args.add(outputFile.getAbsolutePath());

        if (!isTranscodeNeeded) {
            if (log.isDebugEnabled()) {
                log.debug("Transcoding not required, returning -2");
            }

            return -2;
        }

        log.info("FFmpeg transcode command: {}", args);

        int exitCode = 234;
        try {
            exitCode = FFmpegProcessRunner.runFFmpeg(
                this, args,
                FFmpegProcessOptions.builder()
                    .cancelHook(cancelHook)
                    .calculator(progressCalculator)
                    .logOutput(log.isDebugEnabled())
                    .logPrefix(logPrefix)
                    .lazyReader(true)
                    .listener(listener)
                    .build());
        } finally {
            if (exitCode != 0) {
                Files.deleteIfExists(outputFile.toPath());
            }
        }

        return exitCode;
    }

    @Nullable
    private MediaStreamData getMediaStreams(File inputFile) {
        ProcessArguments args = new ProcessArguments(
            "-v", "error",
            "-show_entries", "stream",
            "-of", "json",
            inputFile.getAbsolutePath());

        try {
            StringBuilder finalOutput = new StringBuilder();

            int exitCode = FFmpegProcessRunner.runFFprobe(
                this, args,
                FFmpegProcessOptions.builder()
                    .listener((output, hasTaskStarted, progress) -> {
                        finalOutput.append(output);
                    }).build());

            if (exitCode != 0) {
                log.error("ffprobe process exited with code {}", exitCode);
                return null;
            }

            String jsonOutput = finalOutput.toString();
            if (jsonOutput.startsWith("{")) {
                MediaStreamData mediaStreamData = GDownloader.OBJECT_MAPPER
                    .readValue(jsonOutput, MediaStreamData.class);
                if (log.isDebugEnabled()) {
                    log.debug("{}", mediaStreamData);
                }

                return mediaStreamData;
            }
        } catch (Exception e) {
            log.error("Error running ffprobe: {}", e.getMessage(), e);
        }

        return null;
    }

    @Nullable
    private File extractThumbnail(VideoStream stream, File inputFile) {
        ProcessArguments args = new ProcessArguments(
            "-hide_banner",
            "-y",
            "-v", "error",
            "-i", inputFile.getAbsolutePath(),
            "-map", "0:" + stream.getIndex(),
            "-vframes", "1",
            "-update", "1");

        try {
            String ext = getTargetThumbnailExtension(stream.getCodecName());
            File tmp = File.createTempFile(FileUtils.TMP_FILE_IDENTIFIER, "ffthumb" + ext);
            tmp.deleteOnExit();

            args.add(tmp.getAbsolutePath());

            int exitCode = FFmpegProcessRunner.runFFmpeg(this, args);
            if (exitCode != 0) {
                log.error("FFmpeg process exited with code: {}", exitCode);
                return null;
            }

            return tmp;
        } catch (Exception e) {
            log.error("Error running ffmpeg: {}", e.getMessage(), e);
        }

        return null;
    }

    private String getTargetThumbnailExtension(String currentCodec) {
        return switch (currentCodec.toLowerCase()) {
            case "mjpeg", "jpg", "jpeg" ->
                ".jpg";
            default ->// WebP is usually a mistake
                ".png";
        };
    }

    @PreDestroy
    public void close() {
        processMonitor.close();
    }
}
