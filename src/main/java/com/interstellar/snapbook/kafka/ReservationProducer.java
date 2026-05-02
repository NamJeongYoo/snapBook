package com.interstellar.snapbook.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationProducer {

    private final KafkaTemplate<String, ReservationMessage> kafkaTemplate;

    @Value("${app.kafka.topic.reservation}")
    private String reservationTopic;

    public void sendReservationMessage(ReservationMessage message) {
        String key = String.valueOf(message.getEventId());

        CompletableFuture<SendResult<String, ReservationMessage>> future =
                kafkaTemplate.send(reservationTopic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Sent reservation message for event {} user {} to partition {} offset {}",
                        message.getEventId(),
                        message.getUserId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send reservation message for event {} user {}",
                        message.getEventId(), message.getUserId(), ex);
            }
        });
    }
}
