package com.cineagent.notification.api.dto;

import com.cineagent.notification.domain.OutboxEvent;
import java.time.Instant;

public final class OutboxEventDtos {
  private OutboxEventDtos() {}

  public record OutboxEventResponse(
      Long id, String eventType, String payload, String status, int attempts, Instant createdAt) {
    public static OutboxEventResponse from(OutboxEvent e) {
      return new OutboxEventResponse(
          e.getId(), e.getEventType(), e.getPayload(), e.getStatus().name(), e.getAttempts(), e.getCreatedAt());
    }
  }
}
