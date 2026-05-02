package com.interstellar.snapbook.domain.event;

import com.interstellar.snapbook.stock.StockService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final StockService stockService;

    @Transactional
    public Event createEvent(EventCreateRequest request) {
        Event event = Event.builder()
                .name(request.name())
                .totalTickets(request.totalTickets())
                .availableTickets(request.totalTickets())
                .eventDate(request.eventDate())
                .status(EventStatus.SCHEDULED)
                .build();

        Event savedEvent = eventRepository.save(event);
        return savedEvent;
    }

    @Transactional
    public Event openEvent(Long eventId) {
        Event event = getEvent(eventId);
        event.open();
        stockService.initStock(eventId, event.getAvailableTickets());
        return eventRepository.save(event);
    }

    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }

    public List<Event> getOpenEvents() {
        return eventRepository.findByStatus(EventStatus.OPEN);
    }

    public Long getStock(Long eventId) {
        return stockService.getStock(eventId);
    }
}
