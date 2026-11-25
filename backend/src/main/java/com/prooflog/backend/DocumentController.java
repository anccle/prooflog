package com.prooflog.backend;

import com.prooflog.backend.service.LogBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final VerificationService verificationService;
    private final DocumentRepository documentRepository;
    private final LogBufferService logBufferService;
    private final ObjectMapper objectMapper;

    public record CreateRequest(String title) {}
    public record SubmitRequest(String finalContent) {}

    // 1. 새 문서 작성 세션 발급 (UUID 생성)
    @PostMapping
    public ResponseEntity<Document> createDocument(@RequestBody(required = false) CreateRequest request) {
        String title = (request != null) ? request.title() : "새 작업 문서";
        return ResponseEntity.ok(verificationService.createDraft(title));
    }

    // 2. 작성 완료 및 검증서 발급
    @PostMapping("/{id}/submit")
    public ResponseEntity<Document> submitDocument(
            @PathVariable String id,
            @RequestBody SubmitRequest request) {
        Document verifiedDoc = verificationService.submitAndVerify(id, request.finalContent());
        // 발급 즉시 캐시 워밍 (첫 조회부터 Cache Hit 보장)
        logBufferService.cacheCertificate(id, verifiedDoc);
        return ResponseEntity.ok(verifiedDoc);
    }

    // 3. 발급된 검증서 조회 (Cache-Aside 패턴 적용)
    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable String id) {
        // 1) Redis 캐시 먼저 조회
        String cachedJson = logBufferService.getCachedCertificate(id);
        if (cachedJson != null) {
            try {
                log.info("Cache HIT for certificate id: {}", id);
                Document cachedDoc = objectMapper.readValue(cachedJson, Document.class);
                return ResponseEntity.ok(cachedDoc);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached certificate, falling back to DB", e);
            }
        }

        // 2) Cache MISS 시 DB 조회 후 Redis에 적재
        log.info("Cache MISS for certificate id: {}, querying DB", id);
        return documentRepository.findById(id)
                .map(doc -> {
                    logBufferService.cacheCertificate(id, doc);
                    return ResponseEntity.ok(doc);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}