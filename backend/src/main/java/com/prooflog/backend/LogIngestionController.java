package com.prooflog.backend;

import com.prooflog.backend.service.LogBufferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class LogIngestionController {

    private final LogBufferService logBufferService;

    public record LogBatchRequest(
            String documentId,
            int sequenceNum,
            List<ActivityLog.EventItem> events
    ) {}

    @PostMapping("/batch")
    public ResponseEntity<Void> ingestBatch(@RequestBody LogBatchRequest request) {
        if (request.events() == null || request.events().isEmpty()) {
            return ResponseEntity.ok().build();
        }

        // sequenceNum을 멱등성 키로 사용하여 중복 배치 방어 및 Redis 버퍼 적재
        logBufferService.bufferLogBatch(
                request.documentId(),
                String.valueOf(request.sequenceNum()),
                request
        );

        return ResponseEntity.accepted().build();
    }
}