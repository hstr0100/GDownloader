/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.downloader.webscanner;

import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: regex support
public class WebScannerExtensions {

    private static final Set<String> DEFAULT_IMAGE_EXTENSIONS = Set.of(
        // let gallery-dl handle these as they are almost always page assets
        /*"jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "avif", "apng",*/
        "bmp", "tiff", "tif", "heic", "heif", "jfif", "jp2", "jxl", "psd");

    private static final Set<String> DEFAULT_AUDIO_EXTENSIONS = Set.of(
        "mp3", "wav", "flac", "aac", "ogg", "oga", "wma", "m4a", "opus",
        "aiff", "aif", "ape", "mid", "midi", "amr", "alac", "dsd", "dsf");

    private static final Set<String> DEFAULT_VIDEO_EXTENSIONS = Set.of(
        "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "m4v", "mpg",
        "mpeg", "mpe", "3gp", "3g2", "ts", "ogv", "vob", "asf", "rm",
        "rmvb", "f4v", "divx", "mts", "m2ts");

    private static final Set<String> DEFAULT_ARCHIVE_EXTENSIONS = Set.of(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2",
        "iso", "cab", "lz", "lzma", "z", "zst");

    private static final Set<String> DEFAULT_ROM_EXTENSIONS = Set.of(
        "nes", "fds", "smc", "sfc", "fig", "swc", "n64", "v64", "z64",
        "gcm", "wbfs", "rvz", "wud", "wux", "wua", "xci", "nsp", "nsz", "xcz",
        "gb", "gbc", "gba", "sgb", "nds", "3ds", "cia", "cci",
        "chd", "cso", "pbp", "pkg",
        "smd", "gen", "md", "sms", "gg", "gdi", "cdi", "xex", "xbe",
        "bin", "cue", "img", "mdf", "nrg",
        "a26", "a52", "a78", "lnx", "adf", "d64", "pvs");

    private static final Pattern SEVEN_ZIP_VOLUME_PATTERN = Pattern.compile("(?i)\\.7z\\.\\d{3}$");
    private static final Pattern ZIP_VOLUME_PATTERN = Pattern.compile("(?i)\\.z\\d{2}$");

    private final Set<String> imageExtensions;
    private final Set<String> audioExtensions;
    private final Set<String> videoExtensions;
    private final Set<String> archiveExtensions;
    private final Set<String> romExtensions;

    private final Set<String> customExtensions;

    private final Set<String> blacklistedExtensions;

    private final Set<String> allExtensions;

    public WebScannerExtensions(
        Set<String> imageExtensionsIn,
        Set<String> audioExtensionsIn,
        Set<String> videoExtensionsIn,
        Set<String> archiveExtensionsIn,
        Set<String> romExtensionsIn,
        Set<String> customExtensionsIn,
        Set<String> blacklistedExtensionsIn) {

        imageExtensions = normalize(imageExtensionsIn);
        audioExtensions = normalize(audioExtensionsIn);
        videoExtensions = normalize(videoExtensionsIn);
        archiveExtensions = normalize(archiveExtensionsIn);
        romExtensions = normalize(romExtensionsIn);
        customExtensions = normalize(customExtensionsIn);
        blacklistedExtensions = normalize(blacklistedExtensionsIn);

        Set<String> all = new HashSet<>();
        all.addAll(imageExtensions);
        all.addAll(audioExtensions);
        all.addAll(videoExtensions);
        all.addAll(archiveExtensions);
        all.addAll(romExtensions);
        all.addAll(customExtensions);
        allExtensions = Set.copyOf(all);
    }

    private static Set<String> normalize(@Nullable Set<String> input) {
        if (input == null || input.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String extension : input) {
            String cleaned = cleanExtension(extension);
            if (cleaned != null) {
                result.add(cleaned);
            }
        }

        return Set.copyOf(result);
    }

    @Nullable
    private static String cleanExtension(@Nullable String extension) {
        if (extension == null) {
            return null;
        }

        String trimmed = extension.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }

        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isDownloadableExtension(@Nullable String extension) {
        return checkExtension(extension, allExtensions);
    }

    public boolean isImage(@Nullable String extension) {
        return checkExtension(extension, imageExtensions);
    }

    public boolean isAudio(@Nullable String extension) {
        return checkExtension(extension, audioExtensions);
    }

    public boolean isVideo(@Nullable String extension) {
        return checkExtension(extension, videoExtensions);
    }

    public boolean isArchive(@Nullable String extension) {
        return checkExtension(extension, archiveExtensions);
    }

    public boolean isRom(@Nullable String extension) {
        return checkExtension(extension, romExtensions);
    }

    public boolean isCustom(@Nullable String extension) {
        return checkExtension(extension, customExtensions);
    }

    private boolean checkExtension(@Nullable String extension, Set<String> targetSet) {
        String cleaned = cleanExtension(extension);

        return cleaned != null
            && !blacklistedExtensions.contains(cleaned)
            && targetSet.contains(cleaned);
    }

    public boolean isMultiPartArchiveUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        String withoutQuery = url;
        int q = withoutQuery.indexOf('?');
        if (q >= 0) {
            withoutQuery = withoutQuery.substring(0, q);
        }

        return SEVEN_ZIP_VOLUME_PATTERN.matcher(withoutQuery).find()
            || ZIP_VOLUME_PATTERN.matcher(withoutQuery).find();
    }

    public static WebScannerExtensions createDefault() {
        return createDefault(Set.of(), Set.of());
    }

    public static WebScannerExtensions createDefault(
        Set<String> extraAllowedExtensions,
        Set<String> extraBlacklistedExtensions) {

        return new WebScannerExtensions(
            DEFAULT_IMAGE_EXTENSIONS,
            DEFAULT_AUDIO_EXTENSIONS,
            DEFAULT_VIDEO_EXTENSIONS,
            DEFAULT_ARCHIVE_EXTENSIONS,
            DEFAULT_ROM_EXTENSIONS,
            extraAllowedExtensions,
            extraBlacklistedExtensions);
    }

    public static Set<String> parseExtensionList(@Nullable String commaSeparatedExtensions) {
        if (commaSeparatedExtensions == null || commaSeparatedExtensions.isBlank()) {
            return Set.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (String part : commaSeparatedExtensions.split(",")) {
            String cleaned = cleanExtension(part);
            if (cleaned != null) {
                result.add(cleaned);
            }
        }

        return Set.copyOf(result);
    }

    public static String formatExtensionList(@Nullable Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return "";
        }

        return String.join(", ", new LinkedHashSet<>(extensions));
    }
}
