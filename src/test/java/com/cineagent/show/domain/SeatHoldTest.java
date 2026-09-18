package com.cineagent.show.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SeatHoldTest {

    @Test
    void isLogicallyExpired_beforeExpiry_isFalse() {
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:01Z"); // 1 sec later
        SeatHold hold = new SeatHold(null, 1L, 2, expiresAt);

        assertThat(hold.isLogicallyExpired(now)).isFalse();
    }

    @Test
    void isLogicallyExpired_atExpiryBoundary_isTrue() {
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:00Z"); // exact boundary
        SeatHold hold = new SeatHold(null, 1L, 2, expiresAt);

        assertThat(hold.isLogicallyExpired(now)).isTrue();
    }

    @Test
    void isLogicallyExpired_afterExpiry_isTrue() {
        Instant now = Instant.parse("2026-09-19T10:00:01Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:00Z");
        SeatHold hold = new SeatHold(null, 1L, 2, expiresAt);

        assertThat(hold.isLogicallyExpired(now)).isTrue();
    }

    @Test
    void isLogicallyExpired_statusNotActive_isFalse() {
        Instant now = Instant.parse("2026-09-19T10:00:01Z");
        Instant expiresAt = Instant.parse("2026-09-19T10:00:00Z");
        SeatHold hold = new SeatHold(null, 1L, 2, expiresAt);
        hold.markConsumed(); // Sets status to CONSUMED

        assertThat(hold.isLogicallyExpired(now)).isFalse();
    }
}
