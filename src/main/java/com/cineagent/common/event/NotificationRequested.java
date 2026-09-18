package com.cineagent.common.event;

import java.util.Map;

/**
 * The one generic domain event every feature publishes through, instead of a per-event-type class
 * per feature — keeps {@code notification} a true sink (AGENTS.md: "notification → nothing"):
 * publishers depend only on this record in {@code common}, never on anything inside
 * {@code notification} itself. {@code notification}'s outbox listener picks these up via
 * {@code @TransactionalEventListener(BEFORE_COMMIT)}.
 *
 * @param dedupeKey unique per logical notification (e.g. "BOOKING_CONFIRMED:42") — the outbox's
 *     UNIQUE constraint on this gives exactly-once production even under retry/replay.
 */
public record NotificationRequested(String eventType, String dedupeKey, Map<String, String> fields) {}
