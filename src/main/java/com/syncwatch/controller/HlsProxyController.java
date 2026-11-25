package com.syncwatch.controller;

import com.syncwatch.hls.HlsManifestRewriter;
import com.syncwatch.hls.HlsManifestRewriter.RewrittenManifest;
import com.syncwatch.service.HlsSegmentCache;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
public class HlsProxyController {

    private static final Logger log = LoggerFactory.getLogger(HlsProxyController.class);

    private final HlsSegmentCache cache;
    private final HlsManifestRewriter rewriter;

    /**
     * Fetch and rewrite an m3u8 playlist.
     * All segment and nested playlist URLs are rewritten to go through this proxy.
     */
    @GetMapping("/manifest")
    public ResponseEntity<String> manifest(
            @RequestParam String url
    ) throws Exception {
        long start = System.currentTimeMillis();
        String content = cache.fetchText(url);
        RewrittenManifest rewritten = rewriter.rewrite(content, url);
        if (!rewritten.segmentUrls().isEmpty()) {
            cache.registerManifest(url, rewritten.segmentUrls(), rewritten.durations());
        }
        log.info("Manifest fetched and rewritten in {}ms: {}",
                System.currentTimeMillis() - start, shorten(url));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(rewritten.content());
    }

    /**
     * Serve a cached HLS segment, downloading and prefetching if needed.
     */
    @GetMapping("/segment")
    public ResponseEntity<InputStreamResource> segment(
            @RequestParam String url,
            @RequestParam(required = false) String manifest
    ) throws Exception {
        boolean wasCached = cache.isCached(url);
        long start = System.currentTimeMillis();
        Path path = cache.getSegment(url, manifest);
        long size = cache.segmentSize(path);
        InputStream is = cache.openSegment(path);

        log.info("Segment {} ({} KB){}: {}",
                wasCached ? "HIT" : "MISS",
                size / 1024,
                wasCached ? "" : " downloaded in " + (System.currentTimeMillis() - start) + "ms",
                shorten(url));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(size))
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(new InputStreamResource(is));
    }

    /**
     * Prefetch segments starting from the given playback time.
     * Called by the frontend when a remote play/seek event is applied.
     */
    @GetMapping("/warmup")
    public ResponseEntity<Void> warmup(
            @RequestParam String manifest,
            @RequestParam double time
    ) {
        log.info("Warmup requested at {}s for manifest: {}", (int) time, shorten(manifest));
        cache.warmup(manifest, time);
        return ResponseEntity.ok().build();
    }

    /**
     * HLS players routinely abort segment requests on seek / quality switch.
     * That surfaces as "Broken pipe" — harmless, so we swallow it quietly.
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleClientAbort() {
        log.debug("Client aborted segment request (seek/buffer) — ignored");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Trim long URLs for cleaner logs — keep last path segment. */
    private static String shorten(String url) {
        int q = url.indexOf('?');
        String noQuery = q > 0 ? url.substring(0, q) : url;
        int last = noQuery.lastIndexOf('/');
        return last > 0 ? "…/" + noQuery.substring(last + 1) : noQuery;
    }
}
