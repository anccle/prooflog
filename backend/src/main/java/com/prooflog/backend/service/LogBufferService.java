package com.prooflog.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogBufferService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String LOG_LIST_PREFIX = "prooflog:logs:";
    private static final String BATCH_SET_PREFIX = "prooflog:batches:";
    private static final String CERT_CACHE_PREFIX = "prooflog:cert:";

    private static final Duration BUFFER_TTL = Duration.ofHours(6);
    private static final Duration CERT_CACHE_TTL = Duration.ofHours(24);

    /**
     * 1. 로그 배치 버퍼링 (멱등성 보장 + Write-Back 적재)
     * @return true: 신규 적재 성공, false: 이미 처리된 중복 배치
     */
    public boolean bufferLogBatch(String documentId, String batchId, Object eventsPayload) {
        String batchSetKey = BATCH_SET_PREFIX + documentId;
        String logListKey = LOG_LIST_PREFIX + documentId;

        // SADD 반환값이 1이면 처음 들어온 batchId, 0이면 이미 처리된 중복 요청
        Long added = redisTemplate.opsForSet().add(batchSetKey, batchId);
        if (added == null || added == 0L) {
            log.warn("Duplicate batch ignored - documentId: {}, batchId: {}", documentId, batchId);
            return false;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(eventsPayload);
            redisTemplate.opsForList().rightPush(logListKey, jsonPayload);

            // 작성 중 브라우저를 닫아 고아 데이터가 남지 않도록 TTL 갱신
            redisTemplate.expire(batchSetKey, BUFFER_TTL);
            redisTemplate.expire(logListKey, BUFFER_TTL);
            return true;
        } catch (JsonProcessingException e) {
            // 직렬화 실패 시 멱등성 키 롤백
            redisTemplate.opsForSet().remove(batchSetKey, batchId);
            throw new RuntimeException("Failed to serialize log batch", e);
        }
    }

    /**
     * 2. 최종 제출 시 버퍼링된 모든 로그 배치 꺼내기
     */
    public List<String> getAllBufferedBatches(String documentId) {
        String logListKey = LOG_LIST_PREFIX + documentId;
        List<String> batches = redisTemplate.opsForList().range(logListKey, 0, -1);
        return batches != null ? batches : Collections.emptyList();
    }

    /**
     * 3. DB 영속화 완료 후 임시 버퍼 정리
     */
    public void clearBuffer(String documentId) {
        redisTemplate.delete(LOG_LIST_PREFIX + documentId);
        redisTemplate.delete(BATCH_SET_PREFIX + documentId);
    }

    /**
     * 4. 인증서 캐시 조회 (Cache-Aside Read)
     */
    public String getCachedCertificate(String documentId) {
        return redisTemplate.opsForValue().get(CERT_CACHE_PREFIX + documentId);
    }

    /**
     * 5. 인증서 캐시 저장 (Cache-Aside Write)
     */
    public void cacheCertificate(String documentId, Object certificateDto) {
        try {
            String json = objectMapper.writeValueAsString(certificateDto);
            redisTemplate.opsForValue().set(CERT_CACHE_PREFIX + documentId, json, CERT_CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to cache certificate for documentId: {}", documentId, e);
        }
    }
}