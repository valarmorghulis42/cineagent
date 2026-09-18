package com.cineagent.notification.service;

import com.cineagent.common.event.NotificationRequested;
import com.cineagent.notification.domain.OutboxEvent;
import com.cineagent.notification.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the outbox row INSIDE the publisher's own transaction, via BEFORE_COMMIT — never
 * AFTER_COMMIT/plain @Async, which would let a rolled-back booking still "confirm" (AGENTS.md:
 * async notifications). UNIQUE(dedupe_key) gives exactly-once production; a duplicate publish
 * (e.g. a retried request) is a harmless no-op here, not an error.
 */
@Component
public class OutboxEventListener {

  private final OutboxEventRepository outboxEventRepository;
  private final Clock clock;

  public OutboxEventListener(OutboxEventRepository outboxEventRepository, Clock clock) {
    this.outboxEventRepository = outboxEventRepository;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onNotificationRequested(NotificationRequested event) {
    if (outboxEventRepository.findByDedupeKey(event.dedupeKey()).isPresent()) {
      return;
    }
    String payload = formatPayload(event.fields());
    try {
      outboxEventRepository.save(
          new OutboxEvent(event.dedupeKey(), event.eventType(), payload, Instant.now(clock)));
    } catch (DataIntegrityViolationException raceOnDedupeKey) {
      // Two concurrent publishers for the same dedupeKey -- fine, exactly-once is what the
      // unique constraint is for.
    }
  }

  private String formatPayload(Map<String, String> fields) {
    return new TreeMap<>(fields).entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .reduce((a, b) -> a + ";" + b)
        .orElse("");
  }
}
