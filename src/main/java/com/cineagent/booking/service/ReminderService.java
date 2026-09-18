package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.common.config.ReminderProperties;
import com.cineagent.common.event.NotificationRequested;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.ShowService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Show-starts-soon reminders — the "confirmation AND reminder notifications" half of the
 * notification requirement that the outbox alone doesn't cover. Idempotent by construction: the
 * dedupe key is per (booking, show-start-instant), so a job that fires twice in one window (a
 * restart, an admin-triggered run) produces at most one outbox row per booking, not a duplicate
 * blast — {@code SHOW_REMINDER:<bookingId>:<startsAtEpochMilli>} changes only when the show
 * itself changes, never per invocation.
 */
@Component
public class ReminderService {

  private final ShowService showService;
  private final BookingRepository bookingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReminderProperties reminderProperties;
  private final Clock clock;

  public ReminderService(
      ShowService showService,
      BookingRepository bookingRepository,
      ApplicationEventPublisher eventPublisher,
      ReminderProperties reminderProperties,
      Clock clock) {
    this.showService = showService;
    this.bookingRepository = bookingRepository;
    this.eventPublisher = eventPublisher;
    this.reminderProperties = reminderProperties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.reminder.check-interval}")
  public void checkReminders() {
    runOnce();
  }

  @Transactional
  public int runOnce() {
    Instant now = Instant.now(clock);
    Instant windowStart = now;
    Instant windowEnd = now.plus(reminderProperties.leadTimeHours(), ChronoUnit.HOURS);
    int enqueued = 0;
    for (Show show : showService.findStartingBetween(windowStart, windowEnd)) {
      for (Booking booking : bookingRepository.findByShowId(show.getId())) {
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PARTIALLY_CANCELLED) {
          continue;
        }
        eventPublisher.publishEvent(
            new NotificationRequested(
                "SHOW_REMINDER",
                "SHOW_REMINDER:" + booking.getId() + ":" + show.getStartsAt().toEpochMilli(),
                Map.of(
                    "bookingId", String.valueOf(booking.getId()),
                    "bookingReference", booking.getBookingReference(),
                    "showId", String.valueOf(show.getId()),
                    "startsAt", show.getStartsAt().toString())));
        enqueued++;
      }
    }
    return enqueued;
  }
}
