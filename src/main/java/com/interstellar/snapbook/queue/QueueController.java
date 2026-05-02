package com.interstellar.snapbook.queue;

import com.interstellar.snapbook.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/queue")
@RequiredArgsConstructor
public class QueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping
    public ResponseEntity<ApiResponse<QueueEntryResponse>> enterQueue(
            @PathVariable Long eventId,
            @RequestParam Long userId) {
        Long position = waitingQueueService.enterQueue(eventId, userId);
        return ResponseEntity.ok(ApiResponse.success(new QueueEntryResponse(position, false)));
    }

    @GetMapping("/position")
    public ResponseEntity<ApiResponse<QueuePositionResponse>> getPosition(
            @PathVariable Long eventId,
            @RequestParam Long userId) {
        Long position = waitingQueueService.getPosition(eventId, userId);
        boolean isAllowed = waitingQueueService.isAllowed(eventId, userId);

        return ResponseEntity.ok(ApiResponse.success(
                new QueuePositionResponse(position, isAllowed,
                        waitingQueueService.getWaitingCount(eventId))));
    }

    @PostMapping("/allow")
    public ResponseEntity<ApiResponse<Void>> allowEntry(@PathVariable Long eventId) {
        waitingQueueService.allowEntry(eventId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public record QueueEntryResponse(Long position, boolean isAllowed) {}

    public record QueuePositionResponse(Long position, boolean isAllowed, Long totalWaiting) {}
}
