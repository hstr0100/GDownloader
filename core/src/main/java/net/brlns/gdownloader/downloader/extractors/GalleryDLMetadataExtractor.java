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
 * along with this program.  See the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.downloader.extractors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.GalleryDlDownloader;
import net.brlns.gdownloader.downloader.structs.MediaInfo;
import net.brlns.gdownloader.downloader.structs.Thumbnail;
import net.brlns.gdownloader.process.ProcessArguments;

import static net.brlns.gdownloader.util.StringUtils.nullOrEmpty;

/**
 * Gallery-DL metadata extractor, utilizing heuristics to match whatever metadata it can find.
 * Designed to salvage what it can from Gallery-DL's thoroughly unpredictable, schemaless, unusable --dump-json output.
 *
 * Gallery-dl supports an impressive number of extractors. They also appear to have reached an
 * equally impressive lack of consensus regarding what metadata should look like. Depending on
 * which extractor answered the request, the same logical piece of information may live under
 * different field names, nesting levels, tuple layouts, or entirely different document shapes.
 * GDownloader, meanwhile, has the rather unreasonable expectation that metadata can be fetched
 * consistently regardless of which URL was supplied. This class exists to negotiate that peace
 * treaty.
 *
 * Hacky and ugly on purpose. This extractor treats gallery-dl's stdout/stderr as fundamentally
 * untrusted, unstructured text, because that is exactly what it is: gallery-dl frequently
 * interleaves human-readable status/progress/error lines (e.g. "[twitter][info] Requesting guest token",
 * "[gallery-dl][error] Unsupported URL") with the actual --dump-json payload, and does so
 * inconsistently between extractors, as if the flag were a polite suggestion.
 * Nothing here assumes the raw output is well-formed JSON from byte zero, because it often isn't.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
@RequiredArgsConstructor
public class GalleryDLMetadataExtractor implements IMetadataExtractor {

    /**
     * Matches gallery-dl's own log line format: "[extractor][level] message".
     * These are never valid JSON, no matter how badly we squint, and must never
     * be handed to the JSON parser, regardless of where in the output stream
     * they show up to ruin our day.
     */
    private static final Pattern GALLERY_DL_LOG_LINE
        = Pattern.compile("^\\s*\\[[^\\[\\]]{1,64}]\\[(?:debug|info|warning|error)]\\s?.*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAUSIBLE_JSON_FRAGMENT
        = Pattern.compile("^\\s*[\\[{\\]}\"\\-\\d,:.]|^\\s*(?:true|false|null)\\b.*", Pattern.CASE_INSENSITIVE);

    /**
     * Hard cap on how many candidate start positions we'll humor before giving up
     * and accepting that this particular dump is a lost cause.
     */
    private static final int MAX_JSON_START_CANDIDATES = 64;

    private static final Pattern RESOLUTION_KEY_PATTERN = Pattern.compile("^(\\d+)x");

    private final GalleryDlDownloader downloader;

    @Override
    public void init() {

    }

    @Override
    public boolean canConsume(String urlIn) {
        return !nullOrEmpty(urlIn);
    }

    @Override
    public Optional<MediaInfo> fetchMetadata(String urlIn) throws Exception {
        File executablePath = downloader.getExecutablePath().get();
        if (executablePath == null || !executablePath.exists()) {
            log.warn("gallery-dl binary is missing. Skipping parsing for: {}", urlIn);

            return Optional.empty();
        }

        ProcessArguments arguments = new ProcessArguments(
            executablePath.getAbsolutePath(),
            "--dump-json",// A mere suggestion
            "--range", "1",
            "--quiet",
            "--config-ignore",
            urlIn);

        String proxyUrl = downloader.getMain().getConfig().getProxySettings().createProxyUrl();
        if (proxyUrl != null) {
            arguments.add("--proxy", proxyUrl);
        }

        if (downloader.getMain().getConfig().isReadCookiesFromBrowser()) {
            arguments.add(
                "--cookies-from-browser",
                downloader.getMain().getBrowserForCookies().getName()
            );
        } else {
            File cookieJar = downloader.getCookieJarFile();
            if (cookieJar != null) {
                arguments.add(
                    "--cookies",
                    cookieJar.getAbsolutePath()
                );
            }
        }

        List<String> rawLines;
        try {
            rawLines = downloader.getMain().readOutput(arguments);
        } catch (Exception e) {
            log.warn("gallery-dl process failed to run for {}: {}", urlIn, e.toString());

            return Optional.empty();
        }

        if (rawLines == null || rawLines.isEmpty()) {
            log.debug("gallery-dl produced no output for: {}", urlIn);

            return Optional.empty();
        }

        String cleaned = stripNoise(rawLines);
        if (cleaned.isBlank()) {
            log.debug("gallery-dl produced no JSON-shaped output for: {} (likely unsupported URL)", urlIn);

            return Optional.empty();
        }

        //if (log.isDebugEnabled()) {
        //    log.debug("gallery-dl json: {}", cleaned);
        //}
        Optional<JsonNode> rootOpt = extractJsonRoot(cleaned);
        if (rootOpt.isEmpty()) {
            log.debug("Could not locate a parseable JSON payload in gallery-dl output for: {}", urlIn);

            return Optional.empty();
        }

        try {
            MediaInfo info = parseGalleryDlRoot(rootOpt.get());
            if (info != null) {
                info.setOriginalUrl(urlIn);
                downloader.getManager().getMetadataManager().augmentThumbnailIfMissing(info, urlIn);

                return Optional.of(info);
            }
        } catch (Exception e) {
            log.warn("Unexpected error while interpreting gallery-dl metadata for {}: {}", urlIn, e.toString());
            log.debug("Full stack trace:", e);
        }

        return Optional.empty();
    }

    /**
     * Drops known gallery-dl log lines and any chat that clearly isn't part of a JSON
     * document, then rejoins what's left.
     */
    private String stripNoise(List<String> lines) {
        StringBuilder sb = new StringBuilder();

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine.stripTrailing();
            if (line.isBlank()) {
                continue;
            }

            if (GALLERY_DL_LOG_LINE.matcher(line).matches()) {
                continue;
            }

            // Defends against a log line and a JSON fragment having been glued
            // together just to see if we're paying enough attention
            String salvaged = salvageEmbeddedJson(line);
            if (salvaged == null) {
                continue;
            }

            if (!PLAUSIBLE_JSON_FRAGMENT.matcher(salvaged).find()) {
                continue;
            }

            sb.append(salvaged).append('\n');
        }

        return sb.toString().trim();
    }

    /**
     * find the first structural JSON character that isn't part of a bracketed log prefix and
     * return the tail starting there. Returns the original line unchanged if no log
     * prefix is detected, or null if the line is pure log noise with nothing after it.
     */
    private String salvageEmbeddedJson(String line) {
        if (!line.startsWith("[")) {
            return line;
        }

        int searchFrom = 0;
        int firstClose = line.indexOf(']');
        if (firstClose > 0 && line.length() > firstClose + 1 && line.charAt(firstClose + 1) == '[') {
            int secondClose = line.indexOf(']', firstClose + 1);
            if (secondClose > 0) {
                searchFrom = secondClose + 1;
            }
        }

        if (searchFrom == 0) {
            // Starts with '[' but doesn't match the "[x][level]" log-prefix shape - might be valid json, leave it.
            return line;
        }

        int jsonStart = -1;
        for (int i = searchFrom; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '[' || c == '{') {
                jsonStart = i;
                break;
            }
        }

        return jsonStart >= 0 ? line.substring(jsonStart) : null;
    }

    /**
     * Attempts to parse a JSON root value out of arbitrary text that may still contain
     * leading/trailing garbage we failed to filter out above.
     */
    private Optional<JsonNode> extractJsonRoot(String text) {
        List<Integer> candidateStarts = new ArrayList<>();
        for (int i = 0; i < text.length() && candidateStarts.size() < MAX_JSON_START_CANDIDATES; i++) {
            char c = text.charAt(i);
            if (c == '[' || c == '{') {
                candidateStarts.add(i);
            }
        }

        for (int start : candidateStarts) {
            String candidate = text.substring(start);
            try {
                JsonNode node = GDownloader.OBJECT_MAPPER.readTree(candidate);
                if (node != null && !node.isMissingNode() && (node.isArray() || node.isObject())) {
                    return Optional.of(node);
                }
            } catch (JsonProcessingException e) {
                // noisy on purpose; most candidates are expected to fail - ignore.
            }
        }

        return Optional.empty();
    }

    /**
     * Accepts either gallery-dl's usual [[index, {...}], [index, "url", {...}], ...] shape,
     * a bare object, or anything in between. Falls back to a generic tree-wide heuristic
     * scan when the expected tuple shape doesn't yield anything useful.
     */
    private MediaInfo parseGalleryDlRoot(JsonNode root) {
        MediaInfo info = new MediaInfo();

        if (root.isArray()) {
            for (JsonNode eventNode : root) {
                harvestFromNode(eventNode, info);
            }
        } else if (root.isObject()) {
            harvestFromNode(root, info);
        }

        if (!info.isValid() && nullOrEmpty(info.getId())) {
            // Structured pass came up empty - the shape of this extractor's output didn't
            // match our expectations, shocking absolutely no one. Fall back to a
            // best-effort BFS over the whole tree and hope for the best.
            heuristicFallback(root, info);
        }

        return info.isValid() ? info : null;
    }

    /**
     * Walks one node of the [index, ...] tuple (or a bare object) and any objects nested
     * one level inside it, pulling out whatever recognizable fields exist. Defensive
     * against any individual field being absent, null, or the wrong type.
     */
    private void harvestFromNode(JsonNode eventNode, MediaInfo info) {
        if (eventNode == null) {
            return;
        }

        List<JsonNode> objectsToScan = new ArrayList<>();
        String tupleUrl = null;

        if (eventNode.isObject()) {
            objectsToScan.add(eventNode);
        } else if (eventNode.isArray()) {
            for (JsonNode child : eventNode) {
                if (child.isObject()) {
                    objectsToScan.add(child);
                } else if (child.isTextual() && isHttpUrl(child.asText())) {
                    // gallery-dl's [3, url, kwdict] "file" message: the url arg is the
                    // actual media location and is at least as trustworthy as anything
                    // we'd dig out of the kwdict.
                    tupleUrl = child.asText();
                }
            }
        }

        if (nullOrEmpty(info.getThumbnail()) && tupleUrl != null) {
            info.setThumbnail(tupleUrl);
        }

        for (JsonNode entry : objectsToScan) {
            try {
                if (nullOrEmpty(info.getId())) {
                    info.setId(findFirstString(entry,
                        "id",
                        "node_id",
                        "media_id",
                        "tweet_id",
                        "pin_id"
                    ));
                }

                if (nullOrEmpty(info.getTitle())) {
                    info.setTitle(findFirstString(entry,
                        "title",
                        "grid_title",
                        "pin_title",
                        "auto_alt_text",
                        "name",
                        "content",
                        "display_name"
                    ));
                }

                if (nullOrEmpty(info.getThumbnail())) {
                    info.setThumbnail(findThumbnailDeep(entry));
                }

                if (nullOrEmpty(info.getExtractor())) {
                    String category = findFirstString(entry,
                        "category",
                        "extractor",
                        "subcategory"
                    );

                    if (!nullOrEmpty(category)) {
                        info.setExtractor(category);
                        info.setExtractorKey(category);
                        info.setHostDisplayName(category);
                    }
                }

                if (nullOrEmpty(info.getDescription())) {
                    info.setDescription(findFirstString(entry,
                        "description",
                        "seo_alt_text",
                        "content"
                    ));
                }
            } catch (Exception e) {
                log.debug("Skipping unreadable field while parsing gallery-dl entry: {}", e.toString());
            }

            try {
                collectThumbnailAlternatives(entry, info);
            } catch (Exception e) {
                log.debug("Skipping thumbnail alternatives for gallery-dl entry: {}", e.toString());
            }

            // Let Jackson pick fields that already line up for free, rather than hand-writing a
            // heuristic for every numeric/date field yt-dlp already gets natively.
            mergeDirectFieldMapping(entry, info);
        }
    }

    private void mergeDirectFieldMapping(JsonNode entry, MediaInfo info) {
        MediaInfo direct;
        try {
            direct = GDownloader.OBJECT_MAPPER.treeToValue(entry, MediaInfo.class);
        } catch (Exception e) {
            return;
        }

        if (direct == null) {
            return;
        }

        if (nullOrEmpty(info.getId()) && !nullOrEmpty(direct.getId())) {
            info.setId(direct.getId());
        }
        if (nullOrEmpty(info.getTitle()) && !nullOrEmpty(direct.getTitle())) {
            info.setTitle(direct.getTitle());
        }
        if (nullOrEmpty(info.getThumbnail()) && !nullOrEmpty(direct.getThumbnail())) {
            info.setThumbnail(direct.getThumbnail());
        }
        if (nullOrEmpty(info.getDescription()) && !nullOrEmpty(direct.getDescription())) {
            info.setDescription(direct.getDescription());
        }
        if (nullOrEmpty(info.getPlaylistTitle()) && !nullOrEmpty(direct.getPlaylistTitle())) {
            info.setPlaylistTitle(direct.getPlaylistTitle());
        }
        if (nullOrEmpty(info.getChannelId()) && !nullOrEmpty(direct.getChannelId())) {
            info.setChannelId(direct.getChannelId());
        }
        if (nullOrEmpty(info.getUploadDate()) && !nullOrEmpty(direct.getUploadDate())) {
            info.setUploadDate(direct.getUploadDate());
        }
        if (nullOrEmpty(info.getResolution()) && !nullOrEmpty(direct.getResolution())) {
            info.setResolution(direct.getResolution());
        }
        if (info.getWidth() == 0 && direct.getWidth() != 0) {
            info.setWidth(direct.getWidth());
        }
        if (info.getHeight() == 0 && direct.getHeight() != 0) {
            info.setHeight(direct.getHeight());
        }
        if (info.getDuration() == 0 && direct.getDuration() != 0) {
            info.setDuration(direct.getDuration());
        }
        if (info.getViewCount() == 0 && direct.getViewCount() != 0) {
            info.setViewCount(direct.getViewCount());
        }
        if (info.getTimestamp() == 0 && direct.getTimestamp() != 0) {
            info.setTimestamp(direct.getTimestamp());
        }
        if (info.getFilesizeApprox() == 0 && direct.getFilesizeApprox() != 0) {
            info.setFilesizeApprox(direct.getFilesizeApprox());
        }
        if (info.getFps() == 0 && direct.getFps() != 0) {
            info.setFps(direct.getFps());
        }
    }

    /**
     * When the structured pass whiffs entirely, we resort to wandering the whole tree
     * asking every node if it happens to know its id, title, or thumbnail. Undignified,
     * but effective, which describes most of this class.
     */
    private void heuristicFallback(JsonNode root, MediaInfo info) {
        if (nullOrEmpty(info.getId())) {
            String id = HeuristicParser.findFirstMatch(root,
                List.of("id", "node_id", "media_id", "tweet_id"));

            if (id != null) {
                info.setId(id);
            }
        }

        if (nullOrEmpty(info.getTitle())) {
            String title = HeuristicParser.findFirstMatch(root,
                List.of("title", "grid_title", "pin_title", "auto_alt_text", "name"));

            if (title != null) {
                info.setTitle(title);
            }
        }

        if (nullOrEmpty(info.getThumbnail())) {
            String thumb = HeuristicParser.findFirstMatch(root,
                List.of("thumbnail", "image_cover_hd_url", "image_cover_url", "image_large_url", "preview"));

            if (thumb == null) {
                thumb = HeuristicParser.findFallbackImageUrl(root);
            }

            if (thumb != null) {
                info.setThumbnail(thumb);
            }
        }

        if (nullOrEmpty(info.getExtractor())) {
            String category = HeuristicParser.findFirstMatch(root,
                List.of("category", "extractor", "subcategory"));

            if (category != null) {
                info.setExtractor(category);
                info.setExtractorKey(category);
                info.setHostDisplayName(category);
            }
        }
    }

    private String findFirstString(JsonNode node, String... keys) {
        if (node == null) {
            return "";
        }

        for (String key : keys) {
            JsonNode match = node.findValue(key);
            if (match != null && match.isTextual() && !match.asText().isBlank()) {
                return match.asText();
            }
        }

        return "";
    }

    private String findThumbnailDeep(JsonNode node) {
        if (node == null) {
            return "";
        }

        String directThumbnail = findFirstString(node,
            "thumbnail",
            "image_cover_hd_url",
            "image_cover_url",
            "image_large_url",
            "preview",
            "poster",
            "cover",
            "avatar"
        );

        if (!directThumbnail.isBlank()) {
            return directThumbnail;
        }

        JsonNode urlsNode = node.findValue("urls");
        if (urlsNode != null) {
            String thumbnail = findFirstString(urlsNode,
                "thumbnail",
                "hd",
                "orig"
            );

            if (!thumbnail.isBlank()) {
                return thumbnail;
            }
        }

        JsonNode imagesNode = node.findValue("images");
        if (imagesNode != null) {
            String thumbnail = findFirstString(imagesNode,
                "orig",
                "736x",
                "474x",
                "236x",
                "170x",
                "url"
            );

            if (!thumbnail.isBlank()) {
                return thumbnail;
            }
        }

        JsonNode mediaNode = node.findValue("media");
        if (mediaNode != null) {
            String thumbnail = findFirstString(mediaNode,
                "url",
                "thumbnail",
                "preview_url"
            );

            if (!thumbnail.isBlank()) {
                return thumbnail;
            }
        }

        // Last resort: a bare "url"/"path" field on a directlink-style entry, but only
        // when it looks like an actual media file rather than a webpage.
        String direct = findFirstString(node, "url", "path");
        if (!direct.isBlank() && looksLikeMediaUrl(direct)) {
            return direct;
        }

        return "";
    }

    private void collectThumbnailAlternatives(JsonNode entry, MediaInfo info) {
        if (entry == null) {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (Thumbnail existing : info.getThumbnails()) {
            if (existing.getUrl() != null) {
                seen.add(existing.getUrl());
            }
        }

        if (!nullOrEmpty(info.getThumbnail())) {
            seen.add(info.getThumbnail());
        }

        List<Thumbnail> candidates = new ArrayList<>();

        // same list as findThumbnailDeep, minus "avatar".
        List<String> alternatives = List.of(
            "thumbnail",
            "image_cover_hd_url",
            "image_cover_url",
            "image_large_url",
            "preview",
            "poster",
            "cover"
        );

        for (String key : alternatives) {
            JsonNode match = entry.findValue(key);
            if (match != null && match.isTextual() && !match.asText().isBlank()) {
                addThumbnailCandidate(candidates, seen, match.asText(), 0, 0, 0);
            }
        }

        JsonNode imagesNode = entry.findValue("images");
        if (imagesNode != null && imagesNode.isObject()) {
            for (Map.Entry<String, JsonNode> field : imagesNode.properties()) {
                JsonNode value = field.getValue();
                if (value != null && value.isObject()) {
                    String url = findFirstString(value, "url");
                    if (!url.isBlank()) {
                        int width = value.path("width").asInt(0);
                        int height = value.path("height").asInt(0);

                        addThumbnailCandidate(candidates, seen, url,
                            derivePreference(field.getKey(), width), width, height);
                    }
                }
            }
        }

        JsonNode urlsNode = entry.findValue("urls");
        if (urlsNode != null) {
            for (String key : List.of("orig", "hd", "thumbnail")) {
                String url = findFirstString(urlsNode, key);
                if (!url.isBlank()) {
                    addThumbnailCandidate(candidates, seen, url, 0, 0, 0);
                }
            }
        }

        JsonNode mediaNode = entry.findValue("media");
        if (mediaNode != null) {
            for (String key : List.of("url", "thumbnail", "preview_url")) {
                String url = findFirstString(mediaNode, key);
                if (!url.isBlank()) {
                    addThumbnailCandidate(candidates, seen, url, 0, 0, 0);
                }
            }
        }

        info.getThumbnails().addAll(candidates);
    }

    private void addThumbnailCandidate(List<Thumbnail> candidates, Set<String> seen,
        String url, int preference, int width, int height) {
        if (nullOrEmpty(url) || !seen.add(url)) {
            // Either unusable or we've already got this exact URL from somewhere else.
            return;
        }

        Thumbnail thumb = new Thumbnail();
        thumb.setUrl(url);
        thumb.setPreference(preference);
        thumb.setWidth(width);
        thumb.setHeight(height);
        candidates.add(thumb);
    }

    /**
     * Best-effort ranking for a resolution-map entry when the object itself didn't
     * already tell us its width.
     */
    private int derivePreference(String key, int width) {
        if (width > 0) {
            return width;
        }

        Matcher matcher = RESOLUTION_KEY_PATTERN.matcher(key);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Key looked numeric enough to match the pattern but wasn't - shrug
            }
        }

        return 0;
    }

    private boolean looksLikeMediaUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!isHttpUrl(url)) {
            return false;
        }

        if (lower.endsWith(".html")
            || lower.endsWith(".htm")
            || lower.endsWith(".php")
            || lower.endsWith(".css")
            || lower.endsWith(".js")) {
            return false;
        }

        if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
            || lower.contains(".webp") || lower.contains(".gif")) {
            return true;
        }

        return lower.contains("format=") || lower.contains("img");
    }

    private boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);

        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * The "we've given up on structure, just find SOMETHING" department.
     * BFS over the entire tree because at this point we'll take whatever we can get.
     */
    private static final class HeuristicParser {

        public static String findFirstMatch(JsonNode root, List<String> targetKeys) {
            if (root == null) {
                return null;
            }

            Queue<JsonNode> depthQueue = new LinkedList<>();
            depthQueue.add(root);

            while (!depthQueue.isEmpty()) {
                JsonNode currentNode = depthQueue.poll();
                if (currentNode == null) {
                    continue;
                }

                if (currentNode.isObject()) {
                    for (String key : targetKeys) {
                        if (currentNode.hasNonNull(key)) {
                            JsonNode valueNode = currentNode.get(key);
                            if (valueNode.isTextual() && !valueNode.asText().trim().isEmpty()) {
                                return valueNode.asText();
                            } else if (valueNode.isNumber()) {
                                return String.valueOf(valueNode.asLong());
                            }
                        }
                    }

                    currentNode.elements().forEachRemaining(depthQueue::add);
                } else if (currentNode.isArray()) {
                    currentNode.elements().forEachRemaining(depthQueue::add);
                }
            }

            return null;
        }

        // The nuclear option: crawl every string field in the tree and guess whether it's
        // an image URL based on vibes (the filename extension and whether the key name
        // sounds image-y). Not proud of this one, but it works often enough.
        public static String findFallbackImageUrl(JsonNode root) {
            if (root == null) {
                return null;
            }

            Queue<JsonNode> depthQueue = new LinkedList<>();
            depthQueue.add(root);

            while (!depthQueue.isEmpty()) {
                JsonNode currentNode = depthQueue.poll();
                if (currentNode == null) {
                    continue;
                }

                if (currentNode.isObject()) {
                    for (Iterator<Map.Entry<String, JsonNode>> it = currentNode.properties().iterator(); it.hasNext();) {
                        Map.Entry<String, JsonNode> field = it.next();
                        JsonNode valueNode = field.getValue();

                        if (valueNode.isTextual()) {
                            String potentialUrl = valueNode.asText();
                            String evaluationString = potentialUrl.toLowerCase(Locale.ROOT);

                            if (evaluationString.startsWith("http")
                                && (evaluationString.contains(".jpg") || evaluationString.contains(".jpeg")
                                || evaluationString.contains(".png") || evaluationString.contains(".webp"))) {

                                String keyName = field.getKey().toLowerCase(Locale.ROOT);
                                if (keyName.contains("url") || keyName.contains("src") || keyName.contains("image")
                                    || keyName.contains("thumb") || keyName.contains("orig") || keyName.contains("full")) {
                                    return potentialUrl;
                                }
                            }
                        }

                        depthQueue.add(valueNode);
                    }
                } else if (currentNode.isArray()) {
                    currentNode.elements().forEachRemaining(depthQueue::add);
                }
            }

            return null;
        }
    }
}
