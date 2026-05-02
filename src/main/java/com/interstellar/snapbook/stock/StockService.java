package com.interstellar.snapbook.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private static final String STOCK_KEY_PREFIX = "stock:event:";

    private final StringRedisTemplate redisTemplate;

    private static final String DECREASE_STOCK_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then
                return -1
            end
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                redis.call('DECRBY', KEYS[1], quantity)
                return 1
            else
                return 0
            end
            """;

    public void initStock(Long eventId, Integer quantity) {
        String key = getStockKey(eventId);
        redisTemplate.opsForValue().set(key, String.valueOf(quantity));
        log.info("Initialized stock for event {}: {} tickets", eventId, quantity);
    }

    public boolean decreaseStock(Long eventId, Integer quantity) {
        String key = getStockKey(eventId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DECREASE_STOCK_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, Collections.singletonList(key), String.valueOf(quantity));

        if (result == null || result == -1) {
            log.warn("Stock not found for event {}", eventId);
            return false;
        }

        if (result == 1) {
            log.info("Decreased stock for event {} by {}", eventId, quantity);
            return true;
        } else {
            log.info("Not enough stock for event {}, requested: {}", eventId, quantity);
            return false;
        }
    }

    public Long getStock(Long eventId) {
        String key = getStockKey(eventId);
        String stock = redisTemplate.opsForValue().get(key);
        return stock != null ? Long.parseLong(stock) : 0L;
    }

    public void restoreStock(Long eventId, Integer quantity) {
        String key = getStockKey(eventId);
        redisTemplate.opsForValue().increment(key, quantity);
        log.info("Restored stock for event {} by {}", eventId, quantity);
    }

    private String getStockKey(Long eventId) {
        return STOCK_KEY_PREFIX + eventId;
    }
}
