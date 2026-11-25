package com.prooflog.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final DocumentRepository documentRepository;
    private final ActivityLogRepository logRepository;

    @Transactional
    public Document createDraft(String title) {
        Document doc = new Document(title == null || title.isBlank() ? "제목 없는 문서" : title);
        return documentRepository.save(doc);
    }

    @Transactional
    public Document submitAndVerify(String documentId, String finalContent) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문서입니다: " + documentId));

        List<ActivityLog> logs = logRepository.findByDocumentIdOrderBySequenceNumAsc(documentId);

        int keyStrokes = 0;
        int backspaces = 0;
        int pastes = 0;
        int pastedChars = 0;
        int focusOuts = 0;
        long maxTimestamp = 0;

        for (ActivityLog log : logs) {
            for (ActivityLog.EventItem event : log.getEvents()) {
                if (event.timestamp() > maxTimestamp) {
                    maxTimestamp = event.timestamp();
                }
                switch (event.type()) {
                    case "KEY_DOWN" -> keyStrokes++;
                    case "BACKSPACE" -> backspaces++;
                    case "PASTE" -> {
                        pastes++;
                        pastedChars += Math.max(0, event.lengthDelta());
                    }
                    case "FOCUS_OUT" -> focusOuts++;
                }
            }
        }

        int totalLen = Math.max(1, finalContent.length());
        int score = calculateHumanScore(totalLen, keyStrokes, backspaces, pastes, pastedChars, focusOuts);
        String hash = generateSha256(documentId + ":" + finalContent + ":" + score);

        doc.verify(finalContent, hash, score, keyStrokes, backspaces, pastes, pastedChars, focusOuts, maxTimestamp / 1000);
        return doc;
    }

    // 휴리스틱 기반 인간 작성 신뢰도 산출 (0~100점)
    private int calculateHumanScore(int totalLen, int keyStrokes, int backspaces,
                                    int pastes, int pastedChars, int focusOuts) {
        double score = 100.0;

        // 1. 전체 글자 수 대비 붙여넣기 비율 감점 (최대 60점 감점)
        double pasteRatio = Math.min(1.0, (double) pastedChars / totalLen);
        score -= pasteRatio * 60.0;

        // 2. 붙여넣기 횟수 자체에 대한 패널티 (1회당 3점 감점)
        score -= pastes * 3.0;

        // 3. 다른 창(AI 툴 등)으로 이탈한 빈도 감점 (3회 초과부터 1회당 2점 감점)
        if (focusOuts > 3) {
            score -= (focusOuts - 3) * 2.0;
        }

        // 4. 인간 특유의 퇴고(백스페이스 수정) 흔적 보너스 (최대 +5점)
        if (keyStrokes > 20 && backspaces > 0) {
            double editRatio = (double) backspaces / keyStrokes;
            if (editRatio >= 0.05 && editRatio <= 0.4) {
                score += 5.0;
            }
        }

        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private String generateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 해시 생성 실패", e);
        }
    }
}