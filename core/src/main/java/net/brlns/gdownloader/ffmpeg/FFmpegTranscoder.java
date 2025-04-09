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
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
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
// TODO transcoding pipeline:
// raw file ->
// ffprobe (check if conversion needed) ->
// switch to transcoding progress status ->
// extract metadata information
// launch ffmpeg transcoding with progress hook, -1 if impossible to calculate ->
// replace file in tmp directory ->
// remerge metadata information (metadata, subtitles, thumbnail, chapters)
// move final file to destination
@Slf4j
public final class FFmpegTranscoder {

    @Setter
    private Optional<File> ffmpegPath = Optional.empty();

    private boolean queriedForSystemBinary = false;

    @Getter
    private final FFmpegCompatibilityScanner compatScanner;

    public FFmpegTranscoder() {
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

    // TODO: implement codec detection
    public Optional<String> getFFprobeExecutable() {
        return getFfmpegPath()
            .map(path -> path + File.separator + getBinaryName("ffprobe"));
    }

    public String getFFmpegExecutableOrThrow() {
        return getFFmpegExecutable()
            .orElseThrow(() -> new RuntimeException("Please install FFmpeg to use this command"));
    }

    public String getFFprobeExecutableOrThrow() {
        return getFFprobeExecutable()
            .orElseThrow(() -> new RuntimeException("Please install FFmpeg to use this command"));
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
    public List<String> buildCommand(FFmpegConfig config, File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add(getFFmpegExecutableOrThrow());
        command.add("-hide_banner");
        command.add("-y");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());

        MediaStreamData streamData = getMediaStreams(inputFile);
        // TODO: If for some reason stream data is not available, we can still fallback to a very basic transcoding.
        if (streamData == null) {
            throw new RuntimeException("Unable to obtain stream data");
        }

        if (log.isDebugEnabled()) {
            log.debug("Streams: {}", streamData.getStreams());
        }

        command.add("-map_metadata");
        command.add("0");

        int outputVideoIndex = 0;
        for (VideoStream stream : streamData.getVideoStreams()) {
            command.add("-map");
            command.add("0:" + stream.getIndex());

            EncoderEnum actualEncoder = resolveEncoder(config.getVideoEncoder());
            VideoCodecEnum videoCodec = actualEncoder.getVideoCodec();

            boolean needsTranscoding = !videoCodec.getCodecName().equals(stream.getCodecName());

            command.add("-c:v:" + outputVideoIndex);
            if (videoCodec != VideoCodecEnum.NO_CODEC && needsTranscoding) {
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

                EncoderTypeEnum encoderType = actualEncoder.getEncoderType();

                // 0 = lossless, 63 = bathroom tiles, roof shingles
                int rateControlValue = Math.clamp(config.getRateControlValue(), 0, 63);
                // 0 = a black void, MAX_VALUE = a melted GPU
                int videoBitrate = Math.clamp(config.getVideoBitrate(), 0, Integer.MAX_VALUE);

                RateControlModeEnum rateControlMode = config.getRateControlMode();
                if (rateControlMode == RateControlModeEnum.DEFAULT) {
                    // Default to VBR if not specified
                    rateControlMode = RateControlModeEnum.VBR;
                }

                // AV1 quirk, select an appropriate speed/quality ratio based on available CPU horsepower
                switch (encoderType) {
                    case SOFTWARE -> {
                        switch (videoCodec) {
                            case AV1 -> {
                                int cpuCount = Runtime.getRuntime().availableProcessors();
                                command.add("-cpu-used");
                                command.add(cpuCount <= 2 ? "8" : cpuCount <= 4
                                    ? "7" : cpuCount <= 8 ? "4" : "1");
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
                    // Intel QSV is finicky and never works out of the box on Linux,
                    // so if device detection fails, we will simply let it fall back to VAAPI.
                    // The same applies to AMD AMF; additionally, on Linux, only custom FFmpeg builds include the AMF codecs.
                    //
                    // TODO: Need feedback from an Nvidia user that can test NVENC encoding
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
            if (!audioCodec.isSupportedByContainer(videoContainer)) {
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
                command.add(audioCodec.getFfmpegCodecName());
                command.add("-b:a:" + outputAudioIndex);
                command.add(audioBitrate.getValue() + "k");
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
            command.add("-map");
            command.add("0:" + stream.getIndex());
            command.add("-c:d:" + outputDataIndex);
            command.add("copy");

            outputDataIndex++;
        }

        for (UnknownStream stream : streamData.getUnknownStreams()) {
            log.error("Unknown stream type: {} with codec: {} at index: {}",
                stream.getCodecType(), stream.getCodecName(), stream.getIndex());
        }

        command.add("-update");
        command.add("1");

        command.add(outputFile.getAbsolutePath());

        if (log.isDebugEnabled()) {
            log.debug("FFmpeg transcode command: {}", StringUtils.escapeAndBuildCommandLine(command));
        }

        return command;
    }

    private String getTargetThumbnailExtension(String currentCodec) {
        return switch (currentCodec.toLowerCase()) {
            case "mjpeg", "jpg", "jpeg" ->
                ".jpg";
            default ->// WebP is a mistake
                ".png";
        };
    }

    @Nullable
    private MediaStreamData getMediaStreams(File inputFile) {
        List<String> command = new ArrayList<>();
        command.add(getFFprobeExecutableOrThrow());
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("stream");
        command.add("-of");
        command.add("json");
        command.add(inputFile.getAbsolutePath());

        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }

                String jsonOutput = output.toString();
                if (jsonOutput.startsWith("{")) {
                    MediaStreamData mediaStreamData = GDownloader.OBJECT_MAPPER
                        .readValue(jsonOutput, MediaStreamData.class);

                    return mediaStreamData;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("ffprobe process exited with code {}", exitCode);
            }
        } catch (Exception e) {
            log.error("Error running ffprobe: {}", e.getMessage(), e);
        }

        return null;
    }

    @Nullable
    private File extractThumbnail(VideoStream stream, File inputFile) {
        List<String> command = new ArrayList<>();
        command.add(getFFmpegExecutableOrThrow());
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

            // TODO shared process runner
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("P OUT: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("ffmpeg process exited with code {}", exitCode);
                return null;
            }

            return tmp;
        } catch (Exception e) {
            log.error("Error running ffmpeg: {}", e.getMessage(), e);
        }

        return null;
    }

    // TODO: progress callback, encoder threads
}
