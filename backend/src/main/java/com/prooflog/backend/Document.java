package com.prooflog.backend;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    private String id; // 외부 검증 링크용 UUID

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String finalContent;

    private String contentHash; // 위변조 검증용 SHA-256 해시

    private Integer humanScore; // 0~100점 환산 점수
    private int totalKeyStrokes;
    private int backspaceCount;
    private int pasteCount;
    private int pastedCharCount;
    private int focusOutCount;
    private long durationSeconds;

    @Column(nullable = false)
    private String status; // "DRAFT" -> "VERIFIED"

    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;

    public Document(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
    }

    public void verify(String finalContent, String contentHash, int humanScore,
                       int totalKeyStrokes, int backspaceCount, int pasteCount,
                       int pastedCharCount, int focusOutCount, long durationSeconds) {
        this.finalContent = finalContent;
        this.contentHash = contentHash;
        this.humanScore = humanScore;
        this.totalKeyStrokes = totalKeyStrokes;
        this.backspaceCount = backspaceCount;
        this.pasteCount = pasteCount;
        this.pastedCharCount = pastedCharCount;
        this.focusOutCount = focusOutCount;
        this.durationSeconds = durationSeconds;
        this.status = "VERIFIED";
        this.verifiedAt = LocalDateTime.now();
    }
}