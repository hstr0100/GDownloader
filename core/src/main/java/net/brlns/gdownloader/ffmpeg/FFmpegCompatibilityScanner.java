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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ffmpeg.enums.EncoderEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderPresetEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderProfileEnum;
import net.brlns.gdownloader.ffmpeg.enums.EncoderTypeEnum;
import net.brlns.gdownloader.ffmpeg.enums.VideoCodecEnum;
import net.brlns.gdownloader.ffmpeg.structs.EncoderPreset;
import net.brlns.gdownloader.ffmpeg.structs.EncoderProfile;
import net.brlns.gdownloader.updater.SystemExecutableLocator;
import net.brlns.gdownloader.util.StringUtils;

import static net.brlns.gdownloader.ffmpeg.FFmpegCompatibilityScanner.EncoderPresetCommandEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@RequiredArgsConstructor
public class FFmpegCompatibilityScanner {

    private final Map<EncoderEnum, Boolean> testedEncoders = new ConcurrentHashMap<>();
    private final Map<EncoderEnum, String> vaapiDevices = new ConcurrentHashMap<>();

    private final Set<String> availableEncoders = new HashSet<>();
    private final Map<EncoderEnum, EncoderCapability> encoderCapabilities = new ConcurrentHashMap<>();

    private final AtomicBoolean capabilitiesScanned = new AtomicBoolean(false);
    private final AtomicBoolean encodersScanned = new AtomicBoolean(false);

    private final FFmpegTranscoder transcoder;

    private Optional<File> vainfoExecutable;

    public void scanEncoderCapabilities() {
        if (capabilitiesScanned.get()) {
            return;
        }

        try {
            for (EncoderEnum encoder : EncoderEnum.values()) {
                if (encoder == EncoderEnum.NO_ENCODER || encoder == EncoderEnum.AUTO
                    || encoder.getEncoderType() == EncoderTypeEnum.AUTO) {
                    continue;
                }

                if (encoder == EncoderEnum.H264_SOFTWARE || encoder == EncoderEnum.H265_SOFTWARE) {
                    // FFmpeg does not output the options available for these
                    encoderCapabilities.put(encoder, new EncoderCapability(
                        EncoderPresetEnum.getPresetsForCodec(encoder.getVideoCodec()),
                        EncoderProfileEnum.getProfilesForCodec(encoder.getVideoCodec())
                    ));

                    continue;
                }

                scanEncoder(encoder);
            }
        } finally {
            capabilitiesScanned.set(true);
        }
    }

    private void scanEncoder(EncoderEnum encoder) {
        try {
            List<String> lines = new ArrayList<>();

            FFmpegProcessRunner.runFFmpeg(
                transcoder,
                List.of("-h", "encoder=" + encoder.getFfmpegCodecName()),
                FFmpegProcessOptions.builder()
                    .listener((output, hasTaskStarted, progress) -> {
                        lines.add(output);
                    }).build());

            processEncoderOutput(encoder, lines);
        } catch (Exception e) {
            log.error("Error scanning encoder {}: {}", encoder.getFfmpegCodecName(), e.getMessage());
        }
    }

    private void processEncoderOutput(EncoderEnum encoder, List<String> lines) {
        Map<EncoderPresetCommandEnum, List<String>> commandValues = new EnumMap<>(EncoderPresetCommandEnum.class);
        Arrays.stream(EncoderPresetCommandEnum.values())
            .filter(commandEnum -> commandEnum != NONE)
            .forEach(commandEnum -> commandValues.put(commandEnum, new ArrayList<>()));

        List<String> profileValues = new ArrayList<>();

        EncoderPresetCommandEnum currentCommand = null;
        boolean inProfileSection = false;

        mainLoop:
        for (String line : lines) {
            String trimmedLine = line.trim();

            for (EncoderPresetCommandEnum commandEnum : EncoderPresetCommandEnum.values()) {
                if (commandEnum != NONE && trimmedLine.contains(commandEnum.getCommandFlag() + " ")) {
                    currentCommand = commandEnum;
                    inProfileSection = false;
                    continue mainLoop;
                }
            }

            if (trimmedLine.contains("-profile ")) {
                currentCommand = null;
                inProfileSection = true;
                continue;
            }

            // If we hit another option, end collection for current section
            if (trimmedLine.startsWith("-") && !trimmedLine.startsWith("  ")) {
                currentCommand = null;
                inProfileSection = false;
                continue;
            }

            // Collect values for current section
            if (currentCommand != null && !trimmedLine.isEmpty() && line.startsWith(" ")) {
                String value = trimmedLine.split("\\s+")[0];
                if (!value.isEmpty()) {
                    commandValues.get(currentCommand).add(value);
                }
            }

            if (inProfileSection && !trimmedLine.isEmpty() && line.startsWith(" ")) {
                String value = trimmedLine.split("\\s+")[0];
                if (!value.isEmpty()) {
                    profileValues.add(value);
                }
            }
        }

        EncoderPresetCommandEnum presetCommand = NONE;
        List<String> presetValues = List.of();

        // Determine which preset command to use based on priority: -preset > -quality > -usage
        for (EncoderPresetCommandEnum commandEnum : EncoderPresetCommandEnum.values()) {
            if (commandEnum != NONE && !commandValues.get(commandEnum).isEmpty()) {
                presetCommand = commandEnum;
                presetValues = commandValues.get(commandEnum);
                break;
            }
        }

        log.debug("Detected capabilities for {}: command={}, presets={}, profiles={}",
            encoder, presetCommand, presetValues, profileValues);

        String identifiedPresetCommand = presetCommand.getCommandFlag();
        EncoderCapability capability = new EncoderCapability(
            presetValues.stream()
                .map(value -> new EncoderPreset(EncoderPresetEnum.SYSTEM_MAPPER, identifiedPresetCommand, value))
                .collect(Collectors.toList()),
            profileValues.stream()
                .map(value -> new EncoderProfile(EncoderProfileEnum.SYSTEM_MAPPER, value))
                .collect(Collectors.toList())
        );
        encoderCapabilities.put(encoder, capability);
    }

    protected EncoderCapability getEncoderCapability(EncoderEnum encoder) {
        scanEncoderCapabilities();
        return encoderCapabilities.getOrDefault(encoder,
            new EncoderCapability(List.of(), List.of()));
    }

    public List<EncoderPreset> getAvailablePresets(EncoderEnum encoder) {
        return getEncoderCapability(encoder).getPresets();
    }

    public List<EncoderProfile> getAvailableProfiles(EncoderEnum encoder) {
        return getEncoderCapability(encoder).getProfiles();
    }

    public boolean isCompatible(EncoderEnum encoder, EncoderPreset preset) {
        return getAvailablePresets(encoder).contains(preset);
    }

    public boolean isCompatible(EncoderEnum encoder, EncoderProfile profile) {
        return getAvailableProfiles(encoder).contains(profile);
    }

    public Set<String> getAvailableFFmpegEncoders() {
        if (encodersScanned.get()) {
            return availableEncoders;
        }

        Set<String> encoders = new HashSet<>();

        try {
            List<String> lines = new ArrayList<>();

            FFmpegProcessRunner.runFFmpeg(
                transcoder,
                List.of("-hide_banner", "-encoders"),
                FFmpegProcessOptions.builder()
                    .listener((output, hasTaskStarted, progress) -> {
                        lines.add(output);
                    }).build());

            boolean encoderSection = false;
            for (String line : lines) {
                if (line.contains("------")) {
                    encoderSection = true;
                    continue;
                }

                if (encoderSection && line.contains("V")) {
                    String[] parts = line.trim().split(" ");
                    if (parts.length >= 2) {
                        encoders.add(parts[1]);
                    }
                }
            }

            availableEncoders.addAll(encoders);
            encodersScanned.set(true);

            scanEncoderCapabilities();
        } catch (Exception e) {
            log.warn("Error getting FFmpeg encoders: {}", e.getMessage());
        }

        return encoders;
    }

    private boolean isFFmpegEncoderPresent(EncoderEnum encoder) {
        return getAvailableFFmpegEncoders().contains(encoder.getFfmpegCodecName());
    }

    public boolean isEncoderAvailable(EncoderEnum encoder) {
        return testedEncoders.computeIfAbsent(encoder, key -> {
            log.debug("Checking encoder: {}", key);

            if (isFFmpegEncoderPresent(key)) {
                log.debug("Encoder {} is available, testing...", key);

                if (testEncoder(key)) {
                    log.debug("Found working encoder: {}", key);
                    return true;
                } else {
                    log.debug("Encoder {} is available but failed testing", key);
                }
            } else {
                log.debug("Encoder {} is not available in FFmpeg", key);
            }

            return false;
        });
    }

    public List<String> getTestVideoCommand(EncoderEnum encoder) {
        return getTestVideoCommand(encoder, encoder.getEncoderType() == EncoderTypeEnum.VAAPI
            ? detectVaapiDevice(encoder) : null);
    }

    public List<String> getTestVideoCommand(EncoderEnum encoder, String vaapiDevice) {
        List<String> command = new ArrayList<>();

        command.add("-hide_banner");
        command.add("-loglevel");
        command.add(log.isDebugEnabled() ? "error" : "panic");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("testsrc=size=640x360:rate=5");
        command.add("-frames:v");
        command.add("5");
        command.add("-c:v");
        command.add(encoder.getFfmpegCodecName());

        EncoderTypeEnum encoderType = encoder.getEncoderType();
        switch (encoderType) {
            case VAAPI -> {
                command.add("-vaapi_device");
                command.add(vaapiDevice);
                command.add("-vf");
                command.add("format=nv12,hwupload");
            }
            case V4L2M2M -> {
                String device = detectV4l2m2mDevice();
                if (device != null) {
                    command.add("-device");
                    command.add(device);
                }
            }
        }

        switch (encoderType) {
            case SOFTWARE -> {
                if (encoder.getVideoCodec() == VideoCodecEnum.H264
                    || encoder.getVideoCodec() == VideoCodecEnum.H265) {
                    command.add("-crf");
                    command.add("33");
                } else {
                    command.add("-b:v");
                    command.add("500k");
                }

                if (encoder.getVideoCodec() == VideoCodecEnum.AV1) {
                    command.add("-cpu-used");
                    command.add("8");
                }
            }
            case NVENC, QSV, AMF -> {
                command.add("-b:v");
                command.add("500k");
            }
            case VAAPI -> {
                command.add("-qp");
                command.add("33");
            }
        }

        command.add("-f");
        command.add("null");
        command.add("-");

        return command;
    }

    // TODO: GPU selector
    public String detectVaapiDevice(EncoderEnum encoder) {
        if (encoder.getEncoderType() != EncoderTypeEnum.VAAPI) {
            throw new IllegalArgumentException("Preset encoder type was not vaapi");
        }

        // Return cached device if available
        if (vaapiDevices.containsKey(encoder)) {
            return vaapiDevices.get(encoder);
        }

        // Default fallback device
        String fastestDevice = "/dev/dri/renderD128";
        long fastestTime = Long.MAX_VALUE;

        try {
            File driDir = new File("/dev/dri");
            if (driDir.exists() && driDir.isDirectory()) {
                File[] renderDevices = driDir.listFiles((dir, name) -> name.startsWith("renderD"));

                if (renderDevices != null && renderDevices.length > 0) {
                    for (File device : renderDevices) {
                        String devicePath = device.getAbsolutePath();

                        if (checkVaapiDeviceSupport(encoder, devicePath)) {
                            if (renderDevices.length == 1) {
                                // Only one device found, we can skip the benchmark.
                                return devicePath;
                            }

                            // Test encoding speed, pick the fastest encoder.
                            long encodeTime = testVaapiEncodeSpeed(encoder, devicePath);
                            if (encodeTime > 0 && encodeTime < fastestTime) {
                                fastestTime = encodeTime;
                                fastestDevice = devicePath;

                                log.debug("Device: {} encode time: {}ms", devicePath, encodeTime);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error detecting VAAPI device: {}", e.getMessage());
        }

        vaapiDevices.put(encoder, fastestDevice);
        log.debug("Selected VAAPI device {} for codec {} with encode time {}ms",
            fastestDevice, encoder, fastestTime);

        return fastestDevice;
    }

    private long testVaapiEncodeSpeed(EncoderEnum encoder, String devicePath) {
        try {
            List<String> command = getTestVideoCommand(encoder, devicePath);

            FFmpegProcessOptions options = FFmpegProcessOptions.builder()
                .timeoutUnit(TimeUnit.SECONDS)
                .timeoutValue(5l)
                .discardOutput(true)
                .build();

            long startTime = System.currentTimeMillis();
            int exitCode = FFmpegProcessRunner.runFFmpeg(transcoder, command, options);
            long endTime = System.currentTimeMillis();

            return exitCode == 0 ? endTime - startTime : -1;
        } catch (Exception e) {
            log.debug("Error testing VAAPI encode speed for device {}: {}", devicePath, e.getMessage());

            return -1;
        }
    }

    @Nullable
    public String detectV4l2m2mDevice() {
        // TODO: test auto detect for RPI / arm based SBCs
        String device = "/dev/video11";// RPI MJPEG/H264 M2M encoder
        if (Files.exists(Paths.get(device))) {
            return device;
        }

        log.debug("Unable to locate a v4l2m2m device, handing off detection to ffmpeg");
        return null;
    }

    public boolean checkVaapiDeviceSupport(EncoderEnum encoder, String devicePath) {
        try {
            if (vainfoExecutable == null) {
                vainfoExecutable = Optional.ofNullable(SystemExecutableLocator.locateExecutable("vainfo"));
            }

            if (!vainfoExecutable.isPresent()) {
                // Without vainfo, let's just assume the device is valid and let FFmpeg test against it.
                log.warn("vainfo not found, unable to query for supported vaapi profiles");
                return true;
            }

            List<String> command = new ArrayList<>();
            command.add(vainfoExecutable.get().getAbsolutePath());
            command.add("--display");
            command.add("drm");
            command.add("--device");
            command.add(devicePath);

            Process process = transcoder.getProcessMonitor().startProcess(command);

            boolean hasVaapiCodec = false;
            String vaapiName = encoder.getVideoCodec().getVaapiName();

            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("VAEntrypointEncSlice") && line.contains(vaapiName)) {
                        hasVaapiCodec = true;
                    }
                }
            }

            int exitCode = process.waitFor();

            boolean result = exitCode == 0 && hasVaapiCodec;
            log.debug("vaapi entrypoint for encoder {} in {}: {}", encoder, devicePath, result ? "AVAILABLE" : "MISSING");

            return result;
        } catch (Exception e) {
            log.debug("vainfo error: {}", e.getMessage());
            return false;
        }
    }

    public boolean testEncoder(EncoderEnum encoder) {
        return testEncoder(encoder, false);
    }

    public boolean testEncoder(EncoderEnum encoder, boolean verbose) {
        try {
            // Resolve AUTO encoders first
            if (encoder.getEncoderType() == EncoderTypeEnum.AUTO) {
                EncoderEnum resolved = transcoder.resolveEncoder(encoder);
                log.info("Auto encoder resolved to: {}", resolved);

                return testEncoder(resolved);
            }

            List<String> command = getTestVideoCommand(encoder);

            log.debug("Test command: " + StringUtils.escapeAndBuildCommandLine(command));

            FFmpegProcessOptions options = FFmpegProcessOptions.builder()
                .timeoutUnit(TimeUnit.SECONDS)
                .timeoutValue(verbose ? 10l : 5l)
                .discardOutput(!verbose)
                .logOutput(!verbose)
                .build();

            int exitCode = FFmpegProcessRunner.runFFmpeg(transcoder, command, options);
            if (exitCode != 0) {
                if (verbose) {
                    log.warn("Encoder test failed for: {}", encoder);
                }

                return false;
            }

            return true;
        } catch (Exception e) {
            if (verbose) {
                log.warn("Error testing encoder: {}", e.getMessage());
            }

            return false;
        }
    }

    @Getter
    @AllArgsConstructor
    protected static enum EncoderPresetCommandEnum {
        // Ordered by fallback priority
        PRESET("-preset"),
        QUALITY("-quality"),
        USAGE("-usage"),
        NONE("");

        private final String commandFlag;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    protected static class EncoderCapability {

        private List<EncoderPreset> presets;
        private List<EncoderProfile> profiles;
    }

}
