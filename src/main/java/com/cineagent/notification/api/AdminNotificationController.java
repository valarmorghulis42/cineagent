package com.cineagent.notification.api;

import com.cineagent.notification.api.dto.OutboxEventDtos.OutboxEventResponse;
import com.cineagent.notification.repository.OutboxEventRepository;
import com.cineagent.notification.service.OutboxDispatcher;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operability endpoints (AGENTS.md/plan enhancement G1) — the demo video cannot wait out a
 * real @Scheduled interval, and "trust me, it's async" is not a substitute for showing the row. */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxDispatcher outboxDispatcher;

  public AdminNotificationController(OutboxEventRepository outboxEventRepository, OutboxDispatcher outboxDispatcher) {
    this.outboxEventRepository = outboxEventRepository;
    this.outboxDispatcher = outboxDispatcher;
  }

  @GetMapping
  public List<OutboxEventResponse> list(@RequestParam(required = false) String bookingRef) {
    List<com.cineagent.notification.domain.OutboxEvent> events =
        bookingRef == null
            ? outboxEventRepository.findTop50ByOrderByIdDesc()
            : outboxEventRepository.findByPayloadContaining(bookingRef);
    return events.stream().map(OutboxEventResponse::from).toList();
  }

  @PostMapping("/dispatch-now")
  public String dispatchNow() {
    int dispatched = outboxDispatcher.dispatchOnce();
    return "dispatched=" + dispatched;
  }
}
