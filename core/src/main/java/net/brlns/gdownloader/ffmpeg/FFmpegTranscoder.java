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
import net.brlns.gdownloader.process.ProcessMonitor;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.updater.SystemExecutableLocator;
import net.brlns.gdownloader.util.LoggerUtils;
import net.brlns.gdownloader.util.StringUtils;

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
        if (encoder.getEncoderType() != EncoderTypeEnum.AUTO) {
            return encoder;
        }

        VideoCodecEnum targetCodec = encoder.getVideoCodec();

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
    public int startTranscode(FFmpegConfig config, File inputFile,
        File outputFile, AtomicBoolean cancelHook, FFmpegProgressListener listener) throws Exception {
        if (config.getVideoEncoder() == EncoderEnum.NO_ENCODER
            && config.getAudioCodec() == AudioCodecEnum.NO_CODEC) {
            if (log.isDebugEnabled()) {
                log.debug("Transcoding not configured, returning -3");
            }

            return -3;
        }

        if (!inputFile.exists()) {
            if (log.isDebugEnabled()) {
                log.debug("Input file does not exist, returning -4");
            }

            return -4;
        }

        if (log.isDebugEnabled()) {
            log.debug("Input file: {}", inputFile);
            log.debug("Output file: {}", outputFile);
        }

        List<String> command = new ArrayList<>();
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add(log.isDebugEnabled() ? "error" : "quiet");
        command.add("-stats");
        command.add("-y");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());

        MediaStreamData streamData = getMediaStreams(inputFile);
        if (streamData == null) {
            throw new RuntimeException("Unable to obtain stream data");
        }

        command.add("-map_metadata");
        command.add("0");

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

        int outputVideoIndex = 0;
        for (VideoStream stream : streamData.getVideoStreams()) {
            command.add("-map");
            command.add("0:" + stream.getIndex());

            EncoderEnum actualEncoder = resolveEncoder(config.getVideoEncoder());
            EncoderTypeEnum encoderType = actualEncoder.getEncoderType();
            VideoCodecEnum videoCodec = actualEncoder.getVideoCodec();

            boolean needsTranscoding = !videoCodec.getCodecName().equals(stream.getCodecName());

            command.add("-c:v:" + outputVideoIndex);
            if (videoCodec != VideoCodecEnum.NO_CODEC && needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    String name = encoderType == EncoderTypeEnum.SOFTWARE ? "SW" : encoderType.name();
                    logPrefix = name + ": ";
                }

                command.add(actualEncoder.getFfmpegCodecName());

                EncoderProfile profile = config.getProfile();
                if (profile != null && !profile.equals(EncoderProfile.NO_PROFILE)
                    && getCompatScanner().isCompatible(actualEncoder, profile)) {
                    command.add("-profile:v:" + outputVideoIndex);
                    command.add(profile.getFfmpegProfileName());
                }

                EncoderPreset speedPreset = config.getSpeedPreset();
                if (speedPreset != null
                    && !speedPreset.equals(EncoderPreset.NO_PRESET)
                    && !speedPreset.getFfmpegPresetCommand().isEmpty()
                    && getCompatScanner().isCompatible(actualEncoder, speedPreset)) {
                    // AMF uses -quality, AV1 software uses -usage.
                    command.add(speedPreset.getFfmpegPresetCommand() + ":v:" + outputVideoIndex);
                    command.add(speedPreset.getFfmpegPresetName());
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
                                command.add("-cpu-used");
                                command.add(cpuCount <= 2 ? "8" : cpuCount <= 4
                                    ? "7" : cpuCount <= 8 ? "5" : "1");
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
                                        command.add("-crf:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case VP9 -> {
                                        command.add("-crf:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                        command.add("-b:v:" + outputVideoIndex);
                                        command.add("0");
                                    }
                                    case AV1 -> {
                                        command.add("-crf:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                }
                            }
                            case NVENC -> {
                                // NVENC uses different parameters for rate control, Untested.
                                command.add("-rc:v:" + outputVideoIndex);
                                command.add("vbr");
                                command.add("-cq:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                            }
                            case QSV -> {
                                // QSV uses global_quality for quality-based encoding, Untested
                                command.add("-global_quality:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                                command.add("-look_ahead:v:" + outputVideoIndex);
                                command.add("1");
                            }
                            case AMF -> {
                                // AMF uses cqp
                                command.add("-rc:v:" + outputVideoIndex);
                                command.add("cqp");
                                command.add("-qp_i:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                                command.add("-qp_p:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                                command.add("-qp_b:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                            }
                            case VAAPI -> {
                                // VAAPI uses qp parameter differently
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        // For H.264/H.265, set quantization parameter for all frames
                                        command.add("-qp:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case VP9, AV1 -> {
                                        // For VP9/AV1, use global_quality
                                        command.add("-global_quality:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                }
                            }
                            //case V4L2M2M -> {}
                            default -> {
                                // Fallback to bitrate-based encoding if unsupported
                                command.add("-b:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
                            }
                        }
                    }
                    case CQP -> {
                        switch (encoderType) {
                            case SOFTWARE -> {
                                switch (videoCodec) {
                                    case H264 -> {
                                        command.add("-qp:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case H265 -> {
                                        command.add("-x265-params:v:" + outputVideoIndex);
                                        command.add("qp=" + rateControlValue);
                                    }
                                    case VP9 -> {
                                        command.add("-qmin:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                        command.add("-qmax:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case AV1 -> {
                                        command.add("-qp:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    default -> {
                                        // Fall back to CRF for unsupported codecs
                                        command.add("-crf:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                }
                            }
                            case NVENC -> {
                                command.add("-rc:v:" + outputVideoIndex);
                                command.add("constqp");
                                command.add("-qp:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                            }
                            case QSV -> {
                                command.add("-q:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                            }
                            case AMF -> {
                                command.add("-rc:v:" + outputVideoIndex);
                                command.add("cqp");
                                command.add("-qp_i:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                                command.add("-qp_p:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                                command.add("-qp_b:v:" + outputVideoIndex);
                                command.add(String.valueOf(rateControlValue));
                            }
                            case VAAPI -> {
                                // VAAPI uses qp parameter differently
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        // For H.264/H.265, set quantization parameter for all frames
                                        command.add("-qp:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case VP9, AV1 -> {
                                        // For VP9/AV1, use global_quality
                                        command.add("-global_quality:v:" + outputVideoIndex);
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                }
                            }
                            //case V4L2M2M -> {}
                            default -> {
                                // Fallback to bitrate-based encoding if unsupported
                                command.add("-b:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
                            }
                        }
                    }
                    case CBR -> {
                        command.add("-b:v:" + outputVideoIndex);
                        command.add(videoBitrate + "k");
                        // Force constant bitrate with buffer constraints
                        switch (encoderType) {
                            case NVENC, QSV -> {
                                command.add("-maxrate:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
                                command.add("-minrate:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
                                command.add("-bufsize:v:" + outputVideoIndex);
                                command.add((videoBitrate * 2) + "k");
                            }
                            case AMF -> {
                                command.add("-rc:v:" + outputVideoIndex);
                                command.add("cbr");
                            }
                        }
                    }
                    default -> {
                        // VBR settings vary by encoder
                        switch (encoderType) {
                            case SOFTWARE -> {
                                if (videoCodec == VideoCodecEnum.H264
                                    || videoCodec == VideoCodecEnum.H265) {
                                    command.add("-crf:v:" + outputVideoIndex);
                                    command.add(String.valueOf(rateControlValue));
                                } else {
                                    command.add("-b:v:" + outputVideoIndex);
                                    command.add(videoBitrate + "k");
                                }
                            }
                            case NVENC -> {
                                command.add("-b:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
                                command.add("-maxrate:v:" + outputVideoIndex);
                                command.add((videoBitrate * 2) + "k");
                            }
                            // TODO: test AMF -rc qvbr
                            default -> {
                                command.add("-b:v:" + outputVideoIndex);
                                command.add(videoBitrate + "k");
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
                        command.add("-vaapi_device");
                        command.add(vaapiDevice);
                        command.add("-filter:v:" + outputVideoIndex);
                        command.add("format=nv12,hwupload");
                    }
                    case V4L2M2M -> {
                        String device = getCompatScanner().detectV4l2m2mDevice();
                        if (device != null) {
                            command.add("-device");
                            command.add(device);
                        }
                    }
                }
            } else {
                command.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                command.add("-metadata:s:v:" + outputVideoIndex);
                command.add("language=" + stream.getLanguage());
            }

            outputVideoIndex++;
        }

        VideoContainerEnum videoContainer = config.getVideoContainer();
        for (VideoStream stream : streamData.getThumbnailStreams()) {
            if (Stream.of(MP4, MOV, MKV)
                .noneMatch(c -> videoContainer == c)) {
                continue;// WebM and most other containers do not support thumbnails
            }

            switch (config.getVideoContainer()) {
                case MP4, MOV -> {
                    // For MP4, the thumbnail is just a single frame video stream
                    command.add("-map");
                    command.add("0:" + stream.getIndex());
                    command.add("-c:v:" + outputVideoIndex);
                    command.add("copy");
                    command.add("-disposition:v:" + outputVideoIndex);
                    command.add("attached_pic");

                    outputVideoIndex++;
                }
                case MKV -> {
                    // Matroska has a particular quirk in FFmpeg, we cannot simply copy the stream over
                    File thumbnailFile = extractThumbnail(stream, inputFile);
                    if (thumbnailFile != null) {
                        command.add("-attach");
                        command.add(thumbnailFile.getAbsolutePath());
                        command.add("-metadata:s:t");
                        command.add("mimetype=image/png");
                        command.add("-metadata:s:t");
                        command.add("filename=cover.png");
                    }
                }
            }
        }

        int outputAudioIndex = 0;
        for (AudioStream stream : streamData.getAudioStreams()) {
            AudioCodecEnum audioCodec = config.getAudioCodec();
            // TODO: rename enum to passthrough
            if (audioCodec != AudioCodecEnum.NO_CODEC && !audioCodec.isSupportedByContainer(videoContainer)) {
                log.error("Target container {} does not support audio codec: {} in stream #{}",
                    videoContainer, audioCodec, stream.getIndex());
                audioCodec = AudioCodecEnum.getFallbackCodec(videoContainer);
                log.error("Falling back to: {}", audioCodec);
            }

            if (audioCodec == null) {
                log.error("Target container {} does not support audio, dropping audio stream #{}",
                    videoContainer, stream.getIndex());
                continue;
            }

            AudioBitrateEnum audioBitrate = config.getAudioBitrate();

            boolean needsTranscoding = !audioCodec.getCodecName().equals(stream.getCodecName());

            command.add("-map");
            command.add("0:" + stream.getIndex());

            command.add("-c:a:" + outputAudioIndex);
            if (audioCodec != AudioCodecEnum.NO_CODEC && needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    logPrefix = audioCodec.name() + ": ";
                }

                command.add(audioCodec.getFfmpegCodecName());
                command.add("-b:a:" + outputAudioIndex);

                if (audioBitrate != AudioBitrateEnum.NO_AUDIO) {
                    command.add(audioBitrate.getValue() + "k");
                } else {
                    int bitrate = stream.getBitrateKbps();
                    if (bitrate == -1) {
                        // Fallback bitrate is 256 kbps unless otherwise specified.
                        bitrate = 256;
                    }

                    command.add(bitrate + "k");
                }
            } else {
                command.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                command.add("-metadata:s:a:" + outputAudioIndex);
                command.add("language=" + stream.getLanguage());
            }

            outputAudioIndex++;
        }

        int subtitleStreamIndex = 0;
        for (SubtitleStream stream : streamData.getSubtitleStreams()) {
            SubtitleCodecEnum subtitleCodec = SubtitleCodecEnum.getTargetSubtitleCodec(videoContainer, stream.getCodecName());
            if (subtitleCodec == null) {
                continue;
            }

            command.add("-map");
            command.add("0:" + stream.getIndex());

            boolean needsTranscoding = !subtitleCodec.getCodecName().equals(stream.getCodecName())
                && subtitleCodec != SubtitleCodecEnum.COPY;

            command.add("-c:s:" + subtitleStreamIndex);
            if (needsTranscoding) {
                isTranscodeNeeded = true;
                if (logPrefix.isEmpty()) {
                    // ¯\_(ツ)_/¯
                    String name = subtitleCodec == SubtitleCodecEnum.ASS ? "SSA" : subtitleCodec.name();
                    logPrefix = name + ": ";
                }

                command.add(subtitleCodec.getCodecName());
            } else {
                command.add("copy");
            }

            if (notNullOrEmpty(stream.getLanguage())) {
                command.add("-metadata:s:s:" + subtitleStreamIndex);
                command.add("language=" + stream.getLanguage());
            }

            if (notNullOrEmpty(stream.getTitle())) {
                command.add("-metadata:s:s:" + subtitleStreamIndex);
                command.add("title=" + stream.getTitle());
            }

            subtitleStreamIndex++;
        }

        int outputDataIndex = 0;
        for (DataStream stream : streamData.getDataStreams()) {
            switch (config.getVideoContainer()) {
                case MP4 -> {
                    // TODO: MOV: bin_data transcoding
                    command.add("-map");
                    command.add("0:" + stream.getIndex());
                    command.add("-c:d:" + outputDataIndex);
                    command.add("copy");

                    outputDataIndex++;
                }
            }
        }

        for (UnknownStream stream : streamData.getUnknownStreams()) {
            log.error("Unknown stream type: {} with codec: {} at index: {}",
                stream.getCodecType(), stream.getCodecName(), stream.getIndex());
        }

        command.add("-update");
        command.add("1");

        command.add(outputFile.getAbsolutePath());

        if (!isTranscodeNeeded) {
            if (log.isDebugEnabled()) {
                log.debug("Transcoding not required, returning -2");
            }

            return -2;
        }

        if (log.isDebugEnabled()) {
            log.debug("FFmpeg transcode command: {}", StringUtils.escapeAndBuildCommandLine(command));
        }

        int exitCode = FFmpegProcessRunner.runFFmpeg(
            this,
            command,
            FFmpegProcessOptions.builder()
                .cancelHook(cancelHook)
                .calculator(progressCalculator)
                .logOutput(log.isDebugEnabled())
                .logPrefix(logPrefix)
                .lazyReader(true)
                .listener(listener)
                .build());

        if (exitCode != 0) {
            Files.deleteIfExists(outputFile.toPath());
        }

        return exitCode;
    }

    @Nullable
    private MediaStreamData getMediaStreams(File inputFile) {
        List<String> command = new ArrayList<>();
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("stream");
        command.add("-of");
        command.add("json");
        command.add(inputFile.getAbsolutePath());

        try {
            StringBuilder finalOutput = new StringBuilder();

            int exitCode = FFmpegProcessRunner.runFFprobe(
                this, command,
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
        List<String> command = new ArrayList<>();
        command.add("-hide_banner");
        command.add("-y");
        command.add("-v");
        command.add("error");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-map");
        command.add("0:" + stream.getIndex());
        command.add("-vframes");
        command.add("1");
        command.add("-update");
        command.add("1");

        try {
            String ext = getTargetThumbnailExtension(stream.getCodecName());
            File tmp = File.createTempFile("gdownloader", "ffmpeg_thumb" + ext);
            tmp.deleteOnExit();

            command.add(tmp.getAbsolutePath());

            int exitCode = FFmpegProcessRunner.runFFmpeg(this, command);
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
