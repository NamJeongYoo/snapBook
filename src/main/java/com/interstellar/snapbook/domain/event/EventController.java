package com.interstellar.snapbook.domain.event;

import com.interstellar.snapbook.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody EventCreateRequest request) {
        Event event = eventService.createEvent(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(EventResponse.from(event)));
    }

    @PostMapping("/{eventId}/open")
    public ResponseEntity<ApiResponse<EventResponse>> openEvent(@PathVariable Long eventId) {
        Event event = eventService.openEvent(eventId);
        return ResponseEntity.ok(ApiResponse.success(EventResponse.from(event)));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable Long eventId) {
        Event event = eventService.getEvent(eventId);
        return ResponseEntity.ok(ApiResponse.success(EventResponse.from(event)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getOpenEvents() {
        List<EventResponse> events = eventService.getOpenEvents().stream()
                .map(EventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/{eventId}/stock")
    public ResponseEntity<ApiResponse<Long>> getStock(@PathVariable Long eventId) {
        Long stock = eventService.getStock(eventId);
        return ResponseEntity.ok(ApiResponse.success(stock));
    }
}
