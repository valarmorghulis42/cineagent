package com.cineagent.notification.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends BaseEntity {

  @Column(name = "dedupe_key", nullable = false, unique = true, length = 200)
  private String dedupeKey;

  @Column(name = "event_type", nullable = false, length = 60)
  private String eventType;

  @Column(nullable = false, length = 4000)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  protected OutboxEvent() {}

  public OutboxEvent(String dedupeKey, String eventType, String payload, Instant now) {
    this.dedupeKey = dedupeKey;
    this.eventType = eventType;
    this.payload = payload;
    this.nextAttemptAt = now;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void markSent() {
    this.status = OutboxStatus.SENT;
  }

  /** Exponential backoff: 1min, 2min, 4min, 8min... capped, dead-lettered after maxAttempts. */
  public void recordFailure(String error, Instant now, int maxAttempts) {
    this.attempts++;
    this.lastError = error.length() > 1000 ? error.substring(0, 1000) : error;
    if (this.attempts >= maxAttempts) {
      this.status = OutboxStatus.DEAD_LETTER;
    } else {
      long backoffMinutes = Math.min(1L << this.attempts, 60);
      this.nextAttemptAt = now.plusSeconds(backoffMinutes * 60);
    }
  }
}
