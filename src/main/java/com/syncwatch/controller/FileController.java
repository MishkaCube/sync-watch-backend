package com.syncwatch.controller;

import com.syncwatch.http.ByteRange;
import com.syncwatch.http.RangeHeaderParser;
import com.syncwatch.messaging.RoomEventPublisher;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.service.FileStorageService;
import com.syncwatch.service.FileStorageService.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class FileController {

    private final FileStorageService fileStorageService;
    private final RoomEventPublisher publisher;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") String roomId
    ) throws IOException {
        StoredFile stored = fileStorageService.store(file);
        String fileUrl = "/api/files/" + stored.id();

        // broadcast source-change so all clients in the room load the video
        PlayerEvent event = new PlayerEvent();
        event.setType(EventType.SOURCE_CHANGE);
        event.setCurrentTime(0);
        event.setSenderId("server");
        event.setSourceType("url");
        event.setSourceValue(fileUrl);
        publisher.publish(roomId, event);

        return ResponseEntity.ok(Map.of("url", fileUrl, "name", stored.name()));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileSystemResource> stream(
            @PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws IOException {
        StoredFile stored = fileStorageService.get(fileId);
        if (stored == null) return ResponseEntity.notFound().build();

        long fileSize = Files.size(stored.path());
        FileSystemResource resource = new FileSystemResource(stored.path());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, stored.contentType());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        ByteRange range;
        try {
            range = RangeHeaderParser.parse(rangeHeader, fileSize).orElse(null);
        } catch (RangeHeaderParser.RangeNotSatisfiableException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                    .build();
        }

        if (range == null) {   // no (usable) Range header: send the whole file
            headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
            return ResponseEntity.ok().headers(headers).body(resource);
        }

        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + fileSize);
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(range.length()));

        // Spring's FileSystemResource doesn't support partial read directly,
        // so return a ResourceRegion via a sliced response
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(new PartialFileResource(stored.path(), range.start(), range.length()));
    }
}
