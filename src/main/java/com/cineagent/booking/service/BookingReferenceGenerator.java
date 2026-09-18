package com.cineagent.booking.service;

import com.cineagent.booking.repository.BookingRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Collision retry on the UNIQUE constraint (plan B9) — a 8-char alphanumeric space is large
 * enough that a collision is rare, but the retry makes it structurally impossible to surface as
 * a 500 to the customer. */
@Component
public class BookingReferenceGenerator {

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
  private static final int LENGTH = 8;
  private static final int MAX_ATTEMPTS = 5;

  private final BookingRepository bookingRepository;
  private final SecureRandom random = new SecureRandom();

  public BookingReferenceGenerator(BookingRepository bookingRepository) {
    this.bookingRepository = bookingRepository;
  }

  public String generate() {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String candidate = "CINE-" + randomSuffix();
      if (!bookingRepository.existsByBookingReference(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Could not generate a unique booking reference after " + MAX_ATTEMPTS + " attempts");
  }

  private String randomSuffix() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}
