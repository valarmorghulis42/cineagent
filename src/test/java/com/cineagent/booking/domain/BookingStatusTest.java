package com.cineagent.booking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusTest {

    @Test
    void canTransitionTo_exhaustiveMatrix() {
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                boolean expected = switch (from) {
                    case PENDING_PAYMENT -> to == BookingStatus.CONFIRMED || to == BookingStatus.PAYMENT_FAILED || to == BookingStatus.SEAT_LOST_AFTER_PAYMENT;
                    case CONFIRMED -> to == BookingStatus.PARTIALLY_CANCELLED || to == BookingStatus.CANCELLED;
                    case PARTIALLY_CANCELLED -> to == BookingStatus.PARTIALLY_CANCELLED || to == BookingStatus.CANCELLED;
                    case CANCELLED, PAYMENT_FAILED, SEAT_LOST_AFTER_PAYMENT -> false;
                };
                
                assertThat(from.canTransitionTo(to))
                        .as("Transition from %s to %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }
}
