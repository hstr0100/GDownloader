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

import java.io.File;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.ffmpeg.enums.AudioCodecEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderTypeEnum;
import net.brlns.gdownloader.ffmpeg.enums.RateControlModeEnum;
import net.brlns.gdownloader.ffmpeg.enums.VideoCodecEnum;
import net.brlns.gdownloader.ffmpeg.structs.EncoderPreset;
import net.brlns.gdownloader.ffmpeg.structs.EncoderProfile;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.updater.SystemExecutableLocator;
import net.brlns.gdownloader.util.StringUtils;

import static net.brlns.gdownloader.util.FileUtils.getBinaryName;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO transcoding pipeline:
// raw file ->
// ffprobe (check if conversion needed) ->
// switch to transcoding progress status ->
// launch ffmpeg with progress hook ->
// replace file in tmp directory ->
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

    // TODO implement
    public String buildYtDlpCommand(FFmpegConfig config) {
        return String.join(" ", buildFFmpegArguments(config));
    }

    // TODO implement
    public List<String> buildCommand(FFmpegConfig config, File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        command.add(getFFmpegExecutableOrThrow());
        command.add("-hide_banner");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());

        command.addAll(buildFFmpegArguments(config));

        command.add(outputFile.getAbsolutePath());

        if (log.isDebugEnabled()) {
            log.debug("FFmpeg transcode command: {}", StringUtils.escapeAndBuildCommandLine(command));
        }

        return command;
    }

    // TODO: progress callback, encoder threads
    public List<String> buildFFmpegArguments(FFmpegConfig config) {
        List<String> command = new ArrayList<>();

        EncoderEnum actualEncoder = resolveEncoder(config.getVideoEncoder());
        VideoCodecEnum videoCodec = actualEncoder.getVideoCodec();

        // Video encoding
        if (videoCodec != VideoCodecEnum.NO_CODEC) {
            command.add("-c:v");
            command.add(actualEncoder.getFfmpegCodecName());

            EncoderProfile profile = config.getProfile();
            if (profile != null && !profile.equals(EncoderProfile.NO_PROFILE)
                && getCompatScanner().isCompatible(actualEncoder, profile)) {
                command.add("-profile:v");
                command.add(profile.getFfmpegProfileName());
            }

            EncoderPreset speedPreset = config.getSpeedPreset();
            if (speedPreset != null
                && !speedPreset.equals(EncoderPreset.NO_PRESET)
                && !speedPreset.getFfmpegPresetCommand().isEmpty()
                && getCompatScanner().isCompatible(actualEncoder, speedPreset)) {
                // AMF uses -quality, AV1 software uses -usage.
                command.add(speedPreset.getFfmpegPresetCommand() + ":v");
                command.add(speedPreset.getFfmpegPresetName());
            }

            // 0 = lossless, 63 = bathroom tiles, roof shingles
            int rateControlValue = Math.clamp(config.getRateControlValue(), 0, 63);
            // 0 = a black void, MAX_VALUE = a melted GPU
            int videoBitrate = Math.clamp(config.getVideoBitrate(), 0, Integer.MAX_VALUE);

            RateControlModeEnum rateControlMode = config.getRateControlMode();
            if (rateControlMode == RateControlModeEnum.DEFAULT) {
                // Default to VBR if not specified
                rateControlMode = RateControlModeEnum.VBR;
            }
            switch (rateControlMode) {
                case CRF -> {
                    switch (actualEncoder.getEncoderType()) {
                        case SOFTWARE -> {
                            if (videoCodec != null) {
                                switch (videoCodec) {
                                    case H264, H265 -> {
                                        command.add("-crf");
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    case VP9 -> {
                                        command.add("-crf");
                                        command.add(String.valueOf(rateControlValue));
                                        command.add("-b:v");
                                        command.add("0");
                                    }
                                    case AV1 -> {
                                        command.add("-crf");
                                        command.add(String.valueOf(rateControlValue));
                                    }
                                    default -> {
                                    }
                                }
                            }
                        }
                        case NVENC -> {
                            // NVENC uses different parameters for rate control, Untested.
                            command.add("-rc");
                            command.add("vbr");
                            command.add("-cq");
                            command.add(String.valueOf(rateControlValue));
                        }
                        case QSV -> {
                            // QSV uses global_quality for quality-based encoding, Untested
                            command.add("-global_quality");
                            command.add(String.valueOf(rateControlValue));
                            command.add("-look_ahead");
                            command.add("1");
                        }
                        case AMF -> {
                            // AMF uses cqp
                            command.add("-rc");
                            command.add("cqp");
                            command.add("-qp_i");
                            command.add(String.valueOf(rateControlValue));
                            command.add("-qp_p");
                            command.add(String.valueOf(rateControlValue));
                            command.add("-qp_b");
                            command.add(String.valueOf(rateControlValue));
                        }
                        case VAAPI -> {
                            // VAAPI uses qp parameter differently
                            if (videoCodec == VideoCodecEnum.H264
                                || videoCodec == VideoCodecEnum.H265) {
                                // For H.264/H.265, set quantization parameter for all frames
                                command.add("-qp");
                                command.add(String.valueOf(rateControlValue));
                            } else if (videoCodec == VideoCodecEnum.VP9
                                || videoCodec == VideoCodecEnum.AV1) {
                                // For VP9/AV1, use global_quality
                                command.add("-global_quality");
                                command.add(String.valueOf(rateControlValue));
                            }
                        }
                        //case V4L2M2M -> {}
                        default -> {
                            // Fallback to bitrate-based encoding if unsupported
                            command.add("-b:v");
                            command.add(videoBitrate + "k");
                        }
                    }
                }
                case CQP -> {
                    switch (actualEncoder.getEncoderType()) {
                        case SOFTWARE -> {
                            switch (videoCodec) {
                                case H264 -> {
                                    command.add("-qp");
                                    command.add(String.valueOf(rateControlValue));
                                }
                                case H265 -> {
                                    command.add("-x265-params");
                                    command.add("qp=" + rateControlValue);
                                }
                                case VP9 -> {
                                    command.add("-qmin");
                                    command.add(String.valueOf(rateControlValue));
                                    command.add("-qmax");
                                    command.add(String.valueOf(rateControlValue));
                                }
                                case AV1 -> {
                                    command.add("-qp");
                                    command.add(String.valueOf(rateControlValue));
                                }
                                default -> {
                                    // Fall back to CRF for unsupported codecs
                                    command.add("-crf");
                                    command.add(String.valueOf(rateControlValue));
                                }
                            }
                        }
                        case NVENC -> {
                            command.add("-rc");
                            command.add("constqp");
                            command.add("-qp");
                            command.add(String.valueOf(rateControlValue));
                        }
                        case QSV -> {
                            command.add("-q");
                            command.add(String.valueOf(rateControlValue));
                        }
                        case AMF -> {
                            command.add("-rc");
                            command.add("cqp");
                            command.add("-qp_i");
                            command.add(String.valueOf(rateControlValue));
                            command.add("-qp_p");
                            command.add(String.valueOf(rateControlValue));
                            command.add("-qp_b");
                            command.add(String.valueOf(rateControlValue));
                        }
                        case VAAPI -> {
                            // VAAPI uses qp parameter differently
                            if (videoCodec == VideoCodecEnum.H264
                                || videoCodec == VideoCodecEnum.H265) {
                                // For H.264/H.265, set quantization parameter for all frames
                                command.add("-qp");
                                command.add(String.valueOf(rateControlValue));
                            } else if (videoCodec == VideoCodecEnum.VP9
                                || videoCodec == VideoCodecEnum.AV1) {
                                // For VP9/AV1, use global_quality
                                command.add("-global_quality");
                                command.add(String.valueOf(rateControlValue));
                            }
                        }
                        //case V4L2M2M -> {}
                        default -> {
                            // Fallback to bitrate-based encoding if unsupported
                            command.add("-b:v");
                            command.add(videoBitrate + "k");
                        }
                    }
                }
                case CBR -> {
                    command.add("-b:v");
                    command.add(videoBitrate + "k");
                    // Force constant bitrate with buffer constraints
                    switch (actualEncoder.getEncoderType()) {
                        case NVENC, QSV -> {
                            command.add("-maxrate");
                            command.add(videoBitrate + "k");
                            command.add("-minrate");
                            command.add(videoBitrate + "k");
                            command.add("-bufsize");
                            command.add((videoBitrate * 2) + "k");
                        }
                        case AMF -> {
                            command.add("-rc");
                            command.add("cbr");
                        }
                        default -> {
                        }
                    }
                }
                default -> {
                    // VBR settings vary by encoder
                    switch (actualEncoder.getEncoderType()) {
                        case SOFTWARE -> {
                            if (videoCodec == VideoCodecEnum.H264
                                || videoCodec == VideoCodecEnum.H265) {
                                command.add("-crf");
                                command.add(String.valueOf(rateControlValue));
                            }
                        }
                        case NVENC -> {
                            command.add("-b:v");
                            command.add(videoBitrate + "k");
                            command.add("-maxrate");
                            command.add((videoBitrate * 2) + "k");
                        }
                        // TODO: test AMF -rc qvbr
                        default -> {
                            command.add("-b:v");
                            command.add(videoBitrate + "k");
                        }
                    }
                }
            }

            // HW encoding parameters
            switch (actualEncoder.getEncoderType()) {
                case VAAPI -> {
                    String vaapiDevice = getCompatScanner().detectVaapiDevice(actualEncoder);
                    command.add("-vaapi_device");
                    command.add(vaapiDevice);
                    command.add("-vf");
                    command.add("format=nv12,hwupload");
                }
                case QSV -> {
                    command.add("-qsv_device");
                    command.add("auto");
                }
                case V4L2M2M -> {
                    String device = getCompatScanner().detectV4l2m2mDevice();
                    if (device != null) {
                        command.add("-device");
                        command.add(device);
                    } else {
                        log.debug("Unable to locate a valid v4l2m2m device");
                    }
                }
                default -> {
                }
            }
        } else {
            command.add("-c:v");
            command.add("copy");
        }

        // Audio encoding
        AudioCodecEnum audioCodec = config.getAudioCodec();
        AudioBitrateEnum audioBitrate = config.getAudioBitrate();
        if (audioCodec != AudioCodecEnum.NO_CODEC) {
            command.add("-c:a");
            command.add(audioCodec.getFfmpegCodecName());
            command.add("-b:a");
            command.add(audioBitrate.getValue() + "k");
        } else {
            command.add("-c:a");
            command.add("copy");
        }

        return command;
    }

    // TODO
}
