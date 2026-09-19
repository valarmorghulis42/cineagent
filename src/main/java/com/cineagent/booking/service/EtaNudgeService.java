package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.common.config.EtaNudgeProperties;
import com.cineagent.common.event.NotificationRequested;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.ShowService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Leave now" nudges, Swiggy/Zomato-meme-notification style — a lighter-weight, more urgent
 * sibling of {@link ReminderService}'s day-before reminder. Travel time is a flat configured
 * estimate ({@code app.eta.travel-minutes}), not a real maps/traffic call — see {@link
 * EtaNudgeProperties}'s javadoc for why that's a deliberate simplification, not an oversight.
 *
 * <p>Fires once a show is within the ETA window and hasn't started yet. Idempotent by
 * construction exactly like {@code ReminderService}: the dedupe key includes the show's
 * {@code startsAt} instant, so a job firing on every scan of a sliding window still produces at
 * most one outbox row per booking.
 */
@Component
public class EtaNudgeService {

  private static final String[] MESSAGE_TEMPLATES = {
    "🍿 Showtime is in %d minutes. Traffic won't wait, and neither will the trailers you paid to skip.",
    "🚗 %d minutes to showtime. Grab your keys before the villain grabs the plot twist without you.",
    "⏰ T-minus %d min to curtain. Netflix and chill can wait — this popcorn can't.",
    "🎬 %d minutes left. Leave now, or explain to your friends why you missed the first 10 minutes... again."
  };

  private final ShowService showService;
  private final BookingRepository bookingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final EtaNudgeProperties etaNudgeProperties;
  private final Clock clock;

  public EtaNudgeService(
      ShowService showService,
      BookingRepository bookingRepository,
      ApplicationEventPublisher eventPublisher,
      EtaNudgeProperties etaNudgeProperties,
      Clock clock) {
    this.showService = showService;
    this.bookingRepository = bookingRepository;
    this.eventPublisher = eventPublisher;
    this.etaNudgeProperties = etaNudgeProperties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.eta.check-interval:PT2M}")
  public void checkNudges() {
    runOnce();
  }

  @Transactional
  public int runOnce() {
    Instant now = Instant.now(clock);
    Instant windowEnd = now.plus(etaNudgeProperties.travelMinutes(), ChronoUnit.MINUTES);
    int enqueued = 0;
    for (Show show : showService.findStartingBetween(now, windowEnd)) {
      long minutesToShow = Duration.between(now, show.getStartsAt()).toMinutes();
      for (Booking booking : bookingRepository.findByShowId(show.getId())) {
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PARTIALLY_CANCELLED) {
          continue;
        }
        String message = MESSAGE_TEMPLATES[(int) (booking.getId() % MESSAGE_TEMPLATES.length)].formatted(minutesToShow);
        eventPublisher.publishEvent(
            new NotificationRequested(
                "START_TIME_NUDGE",
                "START_TIME_NUDGE:" + booking.getId() + ":" + show.getStartsAt().toEpochMilli(),
                Map.of(
                    "bookingId", String.valueOf(booking.getId()),
                    "bookingReference", booking.getBookingReference(),
                    "message", message,
                    "etaMinutes", String.valueOf(minutesToShow))));
        enqueued++;
      }
    }
    return enqueued;
  }
}
