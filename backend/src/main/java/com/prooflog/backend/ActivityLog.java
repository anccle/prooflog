package com.prooflog.backend;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "activity_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String documentId;

    @Column(nullable = false)
    private int sequenceNum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<EventItem> events;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ActivityLog(String documentId, int sequenceNum, List<EventItem> events) {
        this.documentId = documentId;
        this.sequenceNum = sequenceNum;
        this.events = events;
    }

    public record EventItem(
            long timestamp,
            String type,
            int lengthDelta,
            String keyCategory
    ) {}
}