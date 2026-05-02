package com.interstellar.snapbook.domain.reservation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;

    @Transactional
    public Reservation createReservation(Long eventId, Long userId, Integer quantity) {
        Reservation reservation = Reservation.builder()
                .eventId(eventId)
                .userId(userId)
                .quantity(quantity)
                .status(ReservationStatus.PENDING)
                .build();

        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = getReservation(reservationId);
        reservation.confirm();
        return reservationRepository.save(reservation);
    }

    public Reservation getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found: " + reservationId));
    }

    public List<Reservation> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId);
    }

    public List<Reservation> getReservationsByEventId(Long eventId) {
        return reservationRepository.findByEventId(eventId);
    }
}
