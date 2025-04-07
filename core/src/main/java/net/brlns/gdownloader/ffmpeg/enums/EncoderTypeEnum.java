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

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public enum EncoderTypeEnum {
    /*
     * Supported hardware video encoder types in order of fallback preference.
     * We will attempt to use encoders in this order when available.
     */
    NVENC, // NVIDIA - HW encoder
    AMF, // AMD AMF - HW encoder
    QSV, // Intel Quick Sync - HW encoder
    V4L2M2M, // Video4Linux2 memory-to-memory - Generic - RPI/Linux only - HW encoder
    VAAPI, // Video Acceleration API - Generic - Linux only - HW encoder
    SOFTWARE, // CPU - SW encoder (fallback when no supported HW acceleration is available)
    AUTO; // Automatic selection
}
