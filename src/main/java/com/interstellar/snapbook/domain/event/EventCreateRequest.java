package com.interstellar.snapbook.domain.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank(message = "Event name is required")
        String name,

        @Positive(message = "Total tickets must be positive")
        Integer totalTickets,

        @Future(message = "Event date must be in the future")
        LocalDateTime eventDate
) {}
