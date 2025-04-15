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

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.brlns.gdownloader.ffmpeg.streams.AbstractStream;
import net.brlns.gdownloader.ffmpeg.streams.VideoStream;

import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Setter
@ToString
@AllArgsConstructor
public class FFmpegProgressCalculator {

    private long duration = -1;
    private long durationTimestamp = -1;
    private long frameCount = -1;

    public static boolean isProgressOutput(String output) {
        return output.contains("frame=") || output.contains("time=");
    }

    public double getCurrentProgress(String output) {
        if (!isProgressOutput(output)) {
            return -1;
        }

        Pattern framePattern = Pattern.compile("frame=\\s*(\\d+)");
        Pattern timePattern = Pattern.compile("time=\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

        Matcher frameMatcher = framePattern.matcher(output);
        Matcher timeMatcher = timePattern.matcher(output);

        int frameNumber = -1;
        if (frameMatcher.find()) {
            try {
                frameNumber = Integer.parseInt(frameMatcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        long timeMs = -1;
        if (timeMatcher.find()) {
            try {
                int hours = Integer.parseInt(timeMatcher.group(1));
                int minutes = Integer.parseInt(timeMatcher.group(2));
                int seconds = Integer.parseInt(timeMatcher.group(3));
                int centiseconds = Integer.parseInt(timeMatcher.group(4));

                timeMs = hours * 3600000
                    + minutes * 60000
                    + seconds * 1000
                    + centiseconds * 10;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        double intermediate = -1;

        if (frameNumber != -1 && frameCount != -1) {
            intermediate = ((double)frameNumber / frameCount) * 100;
        } else if (duration != -1 && timeMs != -1) {
            intermediate = ((double)timeMs / duration) * 100;
        } else if (durationTimestamp != -1 && timeMs != -1) {
            intermediate = ((double)timeMs / durationTimestamp) * 100;
        }

        if (intermediate == -1) {
            return intermediate;
        }

        return Math.round(intermediate * 10) / 10.0;// Strip out unecessary precision
    }

    @Nullable
    public static FFmpegProgressCalculator fromStream(AbstractStream stream) {
        long duration = parseDuration(stream.getDuration());
        long durationTs = parseDurationTimestamp(stream.getTimeBase(), stream.getDurationTs());
        long frameCount = -1;

        if (stream instanceof VideoStream videoStream) {
            frameCount = parseFrameCount(videoStream.getNumberOfFrames());
        }

        if (duration == -1 && durationTs == -1 && frameCount == -1) {
            return null;
        }

        return new FFmpegProgressCalculator(duration, durationTs, frameCount);
    }

    @JsonIgnore
    private static long parseDuration(String duration) {
        if (notNullOrEmpty(duration)) {
            try {
                double timestamp = Double.parseDouble(duration);
                return Math.round(timestamp * 1000);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }

    @JsonIgnore
    private static long parseDurationTimestamp(String timeBase, Long durationTs) {
        if (notNullOrEmpty(timeBase) && durationTs != null) {
            try {
                String[] parts = timeBase.split("/");
                int numerator = Integer.parseInt(parts[0]);
                int denominator = Integer.parseInt(parts[1]);

                double seconds = (double)durationTs * numerator / denominator;
                long milliseconds = Math.round(seconds * 1000);

                return milliseconds;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }

    @JsonIgnore
    private static long parseFrameCount(String numberOfFrames) {
        if (notNullOrEmpty(numberOfFrames)) {
            try {
                return Long.parseLong(numberOfFrames);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return -1;
    }

}
