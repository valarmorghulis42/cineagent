package com.cineagent.show.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShowSeatTest {

    @Test
    void effectiveStatus_beforeExpiry_remainsHeld() {
        ShowSeat seat = new ShowSeat(null, null);
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:01Z"); // 1 sec later
        seat.assignHold(1L, expiresAt);

        assertThat(seat.effectiveStatus(now)).isEqualTo(ShowSeatStatus.HELD);
    }

    @Test
    void effectiveStatus_atExpiryBoundary_becomesAvailable() {
        ShowSeat seat = new ShowSeat(null, null);
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:00Z"); // exact same time
        seat.assignHold(1L, expiresAt);

        // !expiresAt.isAfter(now) returns true, meaning it expired
        assertThat(seat.effectiveStatus(now)).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void effectiveStatus_afterExpiry_becomesAvailable() {
        ShowSeat seat = new ShowSeat(null, null);
        Instant now = Instant.parse("2026-09-19T10:00:01Z"); // 1 sec later
        Instant expiresAt = Instant.parse("2026-09-19T10:00:00Z");
        seat.assignHold(1L, expiresAt);

        assertThat(seat.effectiveStatus(now)).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void effectiveStatus_notHeld_returnsStoredStatus() {
        ShowSeat seat = new ShowSeat(null, null);
        seat.confirmBooking(100L); // sets status to BOOKED
        Instant now = Instant.now();

        assertThat(seat.effectiveStatus(now)).isEqualTo(ShowSeatStatus.BOOKED);
    }
}
