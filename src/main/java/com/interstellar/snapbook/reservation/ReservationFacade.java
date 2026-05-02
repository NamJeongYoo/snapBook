package com.interstellar.snapbook.reservation;

import com.interstellar.snapbook.common.exception.NotInQueueException;
import com.interstellar.snapbook.common.exception.QueueNotReadyException;
import com.interstellar.snapbook.common.exception.SoldOutException;
import com.interstellar.snapbook.kafka.ReservationMessage;
import com.interstellar.snapbook.kafka.ReservationProducer;
import com.interstellar.snapbook.queue.WaitingQueueService;
import com.interstellar.snapbook.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationFacade {

    private final WaitingQueueService waitingQueueService;
    private final StockService stockService;
    private final ReservationProducer reservationProducer;

    public void reserve(Long eventId, Long userId, Integer quantity) {
        // 1. Check if user is in the allowed queue
        if (!waitingQueueService.isAllowed(eventId, userId)) {
            Long position = waitingQueueService.getPosition(eventId, userId);
            if (position == -1) {
                throw new NotInQueueException("User not in queue. Please enter the queue first.");
            } else {
                throw new QueueNotReadyException("Please wait for your turn. Current position: " + position);
            }
        }

        // 2. Decrease stock atomically using Redis Lua Script
        boolean success = stockService.decreaseStock(eventId, quantity);
        if (!success) {
            throw new SoldOutException("Tickets are sold out for this event.");
        }

        // 3. Remove user from allowed queue
        waitingQueueService.removeFromAllowed(eventId, userId);

        // 4. Send reservation message to Kafka for async processing
        ReservationMessage message = ReservationMessage.of(eventId, userId, quantity);
        reservationProducer.sendReservationMessage(message);

        log.info("Reservation request accepted: event={}, user={}, quantity={}",
                eventId, userId, quantity);
    }
}
