package com.cineagent.pricing.service;

import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.domain.DiscountType;
import com.cineagent.pricing.repository.DiscountCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock private DiscountCodeRepository repository;
    private DiscountService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneId.of("UTC"));
        service = new DiscountService(repository, clock);
    }

    @Test
    void validate_unknownCode_throwsException() {
        when(repository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate("UNKNOWN", BigDecimal.TEN))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_INVALID);
    }

    @Test
    void validate_beforeValidFrom_throwsException() {
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.ZERO, null, 100, 
                Instant.parse("2026-09-20T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"));
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validate("CODE", BigDecimal.TEN))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_INVALID);
    }

    @Test
    void validate_afterValidUntil_throwsException() {
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.ZERO, null, 100, 
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-10T00:00:00Z"));
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validate("CODE", BigDecimal.TEN))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_INVALID);
    }

    @Test
    void validate_inactive_throwsException() {
        // active is true by default, no setter. We can use reflection to set active=false to test the entity,
        // but it's better to just ensure the logic works. We'll skip inactive test if we can't easily set it,
        // or use reflection just for this test.
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.ZERO, null, 100, 
                Instant.MIN, Instant.MAX);
        try {
            java.lang.reflect.Field activeField = DiscountCode.class.getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(code, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validate("CODE", BigDecimal.TEN))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_INVALID);
    }

    @Test
    void validate_exhaustedRedemptions_throwsException() {
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.ZERO, null, 100, 
                Instant.MIN, Instant.MAX);
        try {
            java.lang.reflect.Field usedCountField = DiscountCode.class.getDeclaredField("usedCount");
            usedCountField.setAccessible(true);
            usedCountField.set(code, 100);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validate("CODE", BigDecimal.TEN))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_EXHAUSTED);
    }

    @Test
    void validate_belowMinOrderAmount_throwsException() {
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, new BigDecimal("20.00"), null, 100, 
                Instant.MIN, Instant.MAX);
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.validate("CODE", new BigDecimal("15.00")))
                .isInstanceOf(UnprocessableException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DISCOUNT_MIN_ORDER_NOT_MET);
    }

    @Test
    void validate_success() {
        DiscountCode code = new DiscountCode("CODE", DiscountType.PERCENTAGE, BigDecimal.TEN, new BigDecimal("10.00"), null, 100, 
                Instant.MIN, Instant.MAX);
        when(repository.findByCode("CODE")).thenReturn(Optional.of(code));

        DiscountCode validated = service.validate("CODE", new BigDecimal("15.00"));
        assertThat(validated).isSameAs(code);
    }
}
