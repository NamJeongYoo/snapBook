package com.interstellar.snapbook.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 4, message = "Maximum 4 tickets per reservation")
        Integer quantity
) {}
