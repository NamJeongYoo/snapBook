package com.interstellar.snapbook.kafka;

import com.interstellar.snapbook.domain.reservation.Reservation;
import com.interstellar.snapbook.domain.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationConsumer {

    private final ReservationService reservationService;

    @KafkaListener(
            topics = "${app.kafka.topic.reservation}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeReservation(ReservationMessage message) {
        log.info("Received reservation message: event={}, user={}, quantity={}",
                message.getEventId(), message.getUserId(), message.getQuantity());

        try {
            Reservation reservation = reservationService.createReservation(
                    message.getEventId(),
                    message.getUserId(),
                    message.getQuantity()
            );

            Reservation confirmed = reservationService.confirmReservation(reservation.getId());

            log.info("Reservation confirmed: id={}, event={}, user={}",
                    confirmed.getId(), confirmed.getEventId(), confirmed.getUserId());

        } catch (Exception e) {
            log.error("Failed to process reservation: event={}, user={}",
                    message.getEventId(), message.getUserId(), e);
        }
    }
}
