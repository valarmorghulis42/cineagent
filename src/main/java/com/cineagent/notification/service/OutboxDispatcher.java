package com.cineagent.notification.service;

import com.cineagent.notification.domain.OutboxEvent;
import com.cineagent.notification.port.NotificationSender;
import com.cineagent.notification.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Bounded-batch drain, exponential backoff, DEAD_LETTER after too many failures — see
 * OutboxEvent.recordFailure. Each dispatched event gets its own short transaction so one slow/
 * failing send never holds up the batch or a booking transaction. */
@Component
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 6;

  private final OutboxEventRepository outboxEventRepository;
  private final NotificationSender notificationSender;
  private final Clock clock;

  public OutboxDispatcher(
      OutboxEventRepository outboxEventRepository, NotificationSender notificationSender, Clock clock) {
    this.outboxEventRepository = outboxEventRepository;
    this.notificationSender = notificationSender;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.notification.dispatch-interval:PT10S}")
  public void dispatch() {
    dispatchOnce();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int dispatchOnce() {
    Instant now = Instant.now(clock);
    List<Long> dueIds = outboxEventRepository.findDuePendingIds(now, PageRequest.of(0, BATCH_SIZE));
    if (dueIds.isEmpty()) {
      return 0;
    }
    List<OutboxEvent> locked = outboxEventRepository.lockAllByIdInOrder(dueIds);
    for (OutboxEvent event : locked) {
      try {
        notificationSender.send(event.getEventType(), event.getPayload());
        event.markSent();
      } catch (Exception e) {
        log.warn("Notification send failed for outbox event {}: {}", event.getId(), e.getMessage());
        event.recordFailure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), now, MAX_ATTEMPTS);
      }
    }
    return locked.size();
  }
}
