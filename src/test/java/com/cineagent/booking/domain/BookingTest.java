package com.cineagent.booking.domain;

import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    @Test
    void transitionTo_success() {
        Booking booking = new Booking("REF", 1L, 1L, 1L, 2, BigDecimal.TEN, "USD", null);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        booking.transitionTo(BookingStatus.CONFIRMED);
        
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void transitionTo_illegalState_throwsConflictException() {
        Booking booking = new Booking("REF", 1L, 1L, 1L, 2, BigDecimal.TEN, "USD", null);
        // PENDING_PAYMENT cannot transition directly to CANCELLED
        
        assertThatThrownBy(() -> booking.transitionTo(BookingStatus.CANCELLED))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_BOOKING_STATE);
    }
}
