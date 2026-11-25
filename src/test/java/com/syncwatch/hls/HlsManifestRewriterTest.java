package com.syncwatch.hls;

import com.syncwatch.hls.HlsManifestRewriter.RewrittenManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HlsManifestRewriterTest {

    private static final String MANIFEST_URL = "https://cdn.example.com/video/index.m3u8";
    private static final String BASE = "https://cdn.example.com/video/";

    private final HlsManifestRewriter rewriter = new HlsManifestRewriter();

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String segmentProxy(String absolute) {
        return "/api/hls/segment?url=" + enc(absolute) + "&manifest=" + enc(MANIFEST_URL);
    }

    @Test
    @DisplayName("relative segment URLs are resolved and sent through the segment proxy")
    void rewritesRelativeSegments() {
        String manifest = "#EXTM3U\n#EXTINF:9.5,\nseg0.ts\n#EXTINF:8.0,\nseg1.ts\n#EXT-X-ENDLIST\n";

        RewrittenManifest result = rewriter.rewrite(manifest, MANIFEST_URL);

        assertThat(result.segmentUrls()).containsExactly(BASE + "seg0.ts", BASE + "seg1.ts");
        assertThat(result.content())
                .contains(segmentProxy(BASE + "seg0.ts"))
                .contains(segmentProxy(BASE + "seg1.ts"))
                .contains("#EXT-X-ENDLIST");
    }

    @Test
    @DisplayName("segment durations come from EXTINF")
    void capturesDurations() {
        String manifest = "#EXTINF:9.5,\nseg0.ts\n#EXTINF:4.25,\nseg1.ts\n";

        RewrittenManifest result = rewriter.rewrite(manifest, MANIFEST_URL);

        assertThat(result.durations()).containsExactly(9.5, 4.25);
    }

    @Test
    @DisplayName("a segment without EXTINF gets the default duration of 10 seconds")
    void defaultsDuration() {
        RewrittenManifest result = rewriter.rewrite("seg0.ts\n", MANIFEST_URL);

        assertThat(result.durations()).containsExactly(10.0);
    }

    @Test
    @DisplayName("absolute segment URLs are kept as they are")
    void keepsAbsoluteUrls() {
        String other = "https://other.example.com/a/seg.ts";

        RewrittenManifest result = rewriter.rewrite("#EXTINF:5,\n" + other + "\n", MANIFEST_URL);

        assertThat(result.segmentUrls()).containsExactly(other);
    }

    @Test
    @DisplayName("a nested playlist goes through the manifest proxy and is not counted as a segment")
    void nestedPlaylist() {
        String manifest = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n";

        RewrittenManifest result = rewriter.rewrite(manifest, MANIFEST_URL);

        assertThat(result.segmentUrls()).isEmpty();
        assertThat(result.content()).contains("/api/hls/manifest?url=" + enc(BASE + "low/index.m3u8"));
    }

    @Test
    @DisplayName("URI attributes are rewritten: keys through the segment proxy, media playlists through the manifest proxy")
    void rewritesTagUris() {
        String manifest = "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,URI=\"audio/index.m3u8\"\n";

        String content = rewriter.rewrite(manifest, MANIFEST_URL).content();

        assertThat(content).contains("URI=\"" + segmentProxy(BASE + "key.bin") + "\"");
        assertThat(content).contains("URI=\"/api/hls/manifest?url=" + enc(BASE + "audio/index.m3u8") + "\"");
    }

    @Test
    @DisplayName("tags without a URI and blank lines are preserved")
    void preservesOtherLines() {
        String manifest = "#EXTM3U\n#EXT-X-VERSION:3\n\n#EXT-X-TARGETDURATION:10\n";

        String content = rewriter.rewrite(manifest, MANIFEST_URL).content();

        assertThat(content).contains("#EXTM3U\n").contains("#EXT-X-VERSION:3\n").contains("#EXT-X-TARGETDURATION:10\n");
    }

    @Test
    @DisplayName("a playlist without segments produces an empty segment list")
    void emptyPlaylist() {
        RewrittenManifest result = rewriter.rewrite("#EXTM3U\n#EXT-X-ENDLIST\n", MANIFEST_URL);

        assertThat(result.segmentUrls()).isEmpty();
        assertThat(result.durations()).isEmpty();
    }
}
