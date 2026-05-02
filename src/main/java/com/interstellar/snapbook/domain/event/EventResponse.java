package com.interstellar.snapbook.domain.event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        Integer totalTickets,
        Integer availableTickets,
        LocalDateTime eventDate,
        EventStatus status,
        LocalDateTime createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getTotalTickets(),
                event.getAvailableTickets(),
                event.getEventDate(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}
