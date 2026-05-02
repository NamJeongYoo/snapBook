package com.interstellar.snapbook.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitingQueueService {

    private static final String WAITING_QUEUE_KEY_PREFIX = "queue:waiting:";
    private static final String ALLOWED_QUEUE_KEY_PREFIX = "queue:allowed:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.queue.batch-size:100}")
    private int batchSize;

    public Long enterQueue(Long eventId, Long userId) {
        String waitingKey = getWaitingQueueKey(eventId);
        double score = System.currentTimeMillis();

        redisTemplate.opsForZSet().add(waitingKey, String.valueOf(userId), score);
        Long position = redisTemplate.opsForZSet().rank(waitingKey, String.valueOf(userId));

        log.info("User {} entered queue for event {} at position {}", userId, eventId, position);
        return position != null ? position + 1 : 1L;
    }

    public Long getPosition(Long eventId, Long userId) {
        String waitingKey = getWaitingQueueKey(eventId);
        Long rank = redisTemplate.opsForZSet().rank(waitingKey, String.valueOf(userId));

        if (rank == null) {
            if (isAllowed(eventId, userId)) {
                return 0L;
            }
            return -1L;
        }

        return rank + 1;
    }

    public boolean isAllowed(Long eventId, Long userId) {
        String allowedKey = getAllowedQueueKey(eventId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(allowedKey, String.valueOf(userId)));
    }

    public void allowEntry(Long eventId) {
        String waitingKey = getWaitingQueueKey(eventId);
        String allowedKey = getAllowedQueueKey(eventId);

        Set<ZSetOperations.TypedTuple<String>> users = redisTemplate.opsForZSet()
                .popMin(waitingKey, batchSize);

        if (users != null && !users.isEmpty()) {
            String[] userIds = users.stream()
                    .map(ZSetOperations.TypedTuple::getValue)
                    .toArray(String[]::new);

            redisTemplate.opsForSet().add(allowedKey, userIds);
            log.info("Allowed {} users for event {}", userIds.length, eventId);
        }
    }

    public void removeFromAllowed(Long eventId, Long userId) {
        String allowedKey = getAllowedQueueKey(eventId);
        redisTemplate.opsForSet().remove(allowedKey, String.valueOf(userId));
        log.info("Removed user {} from allowed queue for event {}", userId, eventId);
    }

    public Long getWaitingCount(Long eventId) {
        String waitingKey = getWaitingQueueKey(eventId);
        Long count = redisTemplate.opsForZSet().size(waitingKey);
        return count != null ? count : 0L;
    }

    public Long getAllowedCount(Long eventId) {
        String allowedKey = getAllowedQueueKey(eventId);
        Long count = redisTemplate.opsForSet().size(allowedKey);
        return count != null ? count : 0L;
    }

    private String getWaitingQueueKey(Long eventId) {
        return WAITING_QUEUE_KEY_PREFIX + eventId;
    }

    private String getAllowedQueueKey(Long eventId) {
        return ALLOWED_QUEUE_KEY_PREFIX + eventId;
    }
}
