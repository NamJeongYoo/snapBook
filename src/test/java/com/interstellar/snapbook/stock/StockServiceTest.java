package com.interstellar.snapbook.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class StockServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private StockService stockService;

    private static final Long EVENT_ID = 1L;
    private static final Integer INITIAL_STOCK = 100;

    @BeforeEach
    void setUp() {
        stockService.initStock(EVENT_ID, INITIAL_STOCK);
    }

    @Test
    @DisplayName("Should initialize stock correctly")
    void testInitStock() {
        Long stock = stockService.getStock(EVENT_ID);
        assertThat(stock).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("Should decrease stock successfully")
    void testDecreaseStock() {
        boolean result = stockService.decreaseStock(EVENT_ID, 10);

        assertThat(result).isTrue();
        assertThat(stockService.getStock(EVENT_ID)).isEqualTo(90);
    }

    @Test
    @DisplayName("Should fail to decrease when not enough stock")
    void testDecreaseStockFail() {
        boolean result = stockService.decreaseStock(EVENT_ID, 150);

        assertThat(result).isFalse();
        assertThat(stockService.getStock(EVENT_ID)).isEqualTo(INITIAL_STOCK);
    }

    @Test
    @DisplayName("Should handle concurrent requests correctly")
    void testConcurrentDecrease() throws InterruptedException {
        int threadCount = 100;
        int quantityPerRequest = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean result = stockService.decreaseStock(EVENT_ID, quantityPerRequest);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(stockService.getStock(EVENT_ID)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should prevent overselling in concurrent environment")
    void testPreventOverselling() throws InterruptedException {
        stockService.initStock(EVENT_ID, 50);

        int threadCount = 100;
        int quantityPerRequest = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean result = stockService.decreaseStock(EVENT_ID, quantityPerRequest);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
        assertThat(stockService.getStock(EVENT_ID)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should restore stock correctly")
    void testRestoreStock() {
        stockService.decreaseStock(EVENT_ID, 30);
        stockService.restoreStock(EVENT_ID, 10);

        assertThat(stockService.getStock(EVENT_ID)).isEqualTo(80);
    }
}
