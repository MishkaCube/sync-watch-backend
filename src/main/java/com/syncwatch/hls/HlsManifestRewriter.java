package com.syncwatch.hls;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites an HLS playlist so every playlist and segment URL goes through this
 * backend's proxy endpoints. Pure logic: it does no I/O and keeps no state.
 */
@Component
public class HlsManifestRewriter {

    /** The rewritten playlist plus the segments found in it, in order, with their durations in seconds. */
    public record RewrittenManifest(String content, List<String> segmentUrls, List<Double> durations) {}

    private static final Pattern EXTINF = Pattern.compile("#EXTINF:([\\.\\d]+)");
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");
    private static final double DEFAULT_SEGMENT_SECONDS = 10.0;   // used when EXTINF is missing

    public RewrittenManifest rewrite(String content, String manifestUrl) {
        String baseUrl = baseUrl(manifestUrl);
        List<String> segmentUrls = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        double nextDuration = DEFAULT_SEGMENT_SECONDS;

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#EXTINF")) {
                // remember the duration for the segment line that follows
                Matcher duration = EXTINF.matcher(trimmed);
                if (duration.find()) nextDuration = Double.parseDouble(duration.group(1));
                out.append(trimmed).append("\n");
            } else if (trimmed.startsWith("#")) {
                out.append(rewriteTagUris(trimmed, manifestUrl, baseUrl)).append("\n");
            } else if (!trimmed.isEmpty()) {
                String absolute = resolveUrl(trimmed, baseUrl);
                if (isPlaylist(absolute)) {
                    out.append(proxyManifestUrl(absolute)).append("\n");
                } else {
                    segmentUrls.add(absolute);
                    durations.add(nextDuration);
                    out.append(proxySegmentUrl(absolute, manifestUrl)).append("\n");
                }
            } else {
                out.append("\n");
            }
        }
        return new RewrittenManifest(out.toString(), List.copyOf(segmentUrls), List.copyOf(durations));
    }

    /**
     * Rewrites URI="..." attributes inside HLS tags. EXT-X-MEDIA (audio, subtitles) and
     * EXT-X-I-FRAME-STREAM-INF point to nested .m3u8 playlists and go through the manifest
     * endpoint; everything else (EXT-X-KEY, EXT-X-MAP init segments) goes through the segment endpoint.
     */
    private String rewriteTagUris(String tag, String manifestUrl, String baseUrl) {
        Matcher matcher = URI_ATTR.matcher(tag);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String absolute = resolveUrl(matcher.group(1), baseUrl);
            String replacement = isPlaylist(absolute)
                    ? proxyManifestUrl(absolute)
                    : proxySegmentUrl(absolute, manifestUrl);
            matcher.appendReplacement(out, Matcher.quoteReplacement("URI=\"" + replacement + "\""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isPlaylist(String url) {
        return url.contains(".m3u8");
    }

    private static String proxyManifestUrl(String absoluteUrl) {
        return "/api/hls/manifest?url=" + encode(absoluteUrl);
    }

    private static String proxySegmentUrl(String absoluteUrl, String manifestUrl) {
        return "/api/hls/segment?url=" + encode(absoluteUrl)
                + (manifestUrl != null ? "&manifest=" + encode(manifestUrl) : "");
    }

    private static String resolveUrl(String href, String base) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try {
            return URI.create(base).resolve(href).toString();
        } catch (Exception e) {
            return base + href;
        }
    }

    private static String baseUrl(String url) {
        int last = url.lastIndexOf('/');
        return last > 0 ? url.substring(0, last + 1) : url;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
