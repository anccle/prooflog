package com.prooflog.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class LogIngestionController {

    private final ActivityLogRepository logRepository;

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
        ActivityLog log = new ActivityLog(
                request.documentId(),
                request.sequenceNum(),
                request.events()
        );
        logRepository.save(log);
        return ResponseEntity.accepted().build();
    }
}