package com.interstellar.snapbook.reservation;

import com.interstellar.snapbook.domain.reservation.Reservation;
import com.interstellar.snapbook.domain.reservation.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        Integer quantity,
        ReservationStatus status,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
