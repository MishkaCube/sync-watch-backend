package com.syncwatch.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Uploads a small file and streams it back the way a video player does, with Range requests. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"rezka.service.enabled=false", "rezka.feature.enabled=false"})
class FileStreamingIntegrationTest {

    private static final String CONTENT = "0123456789abcdefghij";   // 20 bytes

    @Autowired
    private TestRestTemplate rest;

    private String fileUrl;

    @BeforeEach
    void upload() {
        String roomId = (String) rest.postForEntity("/api/rooms", null, Map.class).getBody().get("id");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(CONTENT.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "clip.mp4";
            }
        });
        body.add("roomId", roomId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = rest.postForEntity("/api/files/upload",
                new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        fileUrl = (String) response.getBody().get("url");
    }

    private ResponseEntity<String> get(String rangeHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (rangeHeader != null) headers.set(HttpHeaders.RANGE, rangeHeader);
        return rest.exchange(fileUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("without a Range header the whole file is returned")
    void wholeFile() {
        ResponseEntity<String> response = get(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }

    @Test
    @DisplayName("a closed range returns 206 with exactly those bytes")
    void closedRange() {
        ResponseEntity<String> response = get("bytes=5-9");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBody()).isEqualTo("56789");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 5-9/20");
    }

    @Test
    @DisplayName("an open-ended range runs to the end of the file")
    void openEnded() {
        ResponseEntity<String> response = get("bytes=15-");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBody()).isEqualTo("fghij");
    }

    @Test
    @DisplayName("a suffix range returns the last bytes")
    void suffixRange() {
        ResponseEntity<String> response = get("bytes=-4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getBody()).isEqualTo("ghij");
    }

    @Test
    @DisplayName("a range outside the file answers 416 with the file size")
    void unsatisfiable() {
        ResponseEntity<String> response = get("bytes=100-200");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */20");
    }

    @Test
    @DisplayName("a malformed Range header is rejected with 416 instead of a server error")
    void malformedRangeIsRejected() {
        ResponseEntity<String> response = get("bytes=abc-def");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    @Test
    @DisplayName("an unknown file id returns 404")
    void unknownFile() {
        ResponseEntity<String> response = rest.getForEntity("/api/files/does-not-exist", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
