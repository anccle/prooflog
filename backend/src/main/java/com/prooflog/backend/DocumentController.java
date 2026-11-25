package com.prooflog.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class DocumentController {

    private final VerificationService verificationService;
    private final DocumentRepository documentRepository;

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
        return ResponseEntity.ok(verificationService.submitAndVerify(id, request.finalContent()));
    }

    // 3. 발급된 검증서 조회 (채용담당자/교수자 열람용)
    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable String id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}