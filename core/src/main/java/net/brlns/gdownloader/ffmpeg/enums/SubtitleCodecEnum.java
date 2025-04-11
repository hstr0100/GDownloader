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
package net.brlns.gdownloader.ffmpeg.enums;

import jakarta.annotation.Nullable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;

import static net.brlns.gdownloader.settings.enums.VideoContainerEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
public enum SubtitleCodecEnum {
    WEBVTT("webvtt", List.of(MKV, WEBM)),
    MOV_TEXT("mov_text", List.of(MP4, MOV, FLV)),
    ASS("ass", List.of(MP4, MKV, AVI, MOV)),
    SUBRIP("subrip", List.of(MKV, AVI)),
    DVBSUB("dvbsub", List.of()),
    COPY("copy", List.of());

    private final String codecName;
    private final List<VideoContainerEnum> supportedContainers;

    public boolean isSupportedByContainer(VideoContainerEnum container) {
        return supportedContainers.contains(container);
    }

    @Nullable
    public static SubtitleCodecEnum getFallbackCodec(@NonNull VideoContainerEnum container) {
        return switch (container) {
            case WEBM, MKV ->
                WEBVTT;
            case AVI ->
                SUBRIP;
            case MP4, MOV, FLV ->
                MOV_TEXT;
            default ->
                null;
        };
    }

    @Nullable
    public static SubtitleCodecEnum getTargetSubtitleCodec(
        @NonNull VideoContainerEnum container, @NonNull String currentCodec) {
        SubtitleCodecEnum codec = switch (currentCodec.toLowerCase()) {
            case "webvtt", "vtt" ->
                WEBVTT;
            case "subrip", "srt" ->
                SUBRIP;
            case "ass", "ssa" ->
                ASS;
            case "mov_text" ->
                MOV_TEXT;
            case "dvd_subtitle", "dvb_subtitle" ->
                DVBSUB;
            case "hdmv_pgs_subtitle", "pgssub", "dvb_teletext", "eia_608", "microdvd" ->
                COPY;
            default ->
                getFallbackCodec(container);
        };

        if (codec != null && codec.isSupportedByContainer(container)) {
            return codec;
        }

        return getFallbackCodec(container);
    }
}
