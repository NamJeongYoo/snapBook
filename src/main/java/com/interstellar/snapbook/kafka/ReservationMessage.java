package com.interstellar.snapbook.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationMessage {

    private Long eventId;
    private Long userId;
    private Integer quantity;
    private LocalDateTime requestedAt;

    public static ReservationMessage of(Long eventId, Long userId, Integer quantity) {
        return ReservationMessage.builder()
                .eventId(eventId)
                .userId(userId)
                .quantity(quantity)
                .requestedAt(LocalDateTime.now())
                .build();
    }
}
