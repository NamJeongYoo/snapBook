package com.interstellar.snapbook.reservation;

import com.interstellar.snapbook.common.dto.ApiResponse;
import com.interstellar.snapbook.domain.reservation.Reservation;
import com.interstellar.snapbook.domain.reservation.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationFacade reservationFacade;
    private final ReservationService reservationService;

    @PostMapping("/events/{eventId}/reservations")
    public ResponseEntity<ApiResponse<String>> createReservation(
            @PathVariable Long eventId,
            @Valid @RequestBody ReservationRequest request) {
        reservationFacade.reserve(eventId, request.userId(), request.quantity());
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Reservation request accepted. Processing..."));
    }

    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @PathVariable Long reservationId) {
        Reservation reservation = reservationService.getReservation(reservationId);
        return ResponseEntity.ok(ApiResponse.success(ReservationResponse.from(reservation)));
    }

    @GetMapping("/users/{userId}/reservations")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getUserReservations(
            @PathVariable Long userId) {
        List<ReservationResponse> reservations = reservationService
                .getReservationsByUserId(userId)
                .stream()
                .map(ReservationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(reservations));
    }
}
