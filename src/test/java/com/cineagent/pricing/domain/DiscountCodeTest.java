package com.cineagent.pricing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCodeTest {

    @Test
    void computeDiscount_percentageWithoutCap() {
        DiscountCode code = new DiscountCode("P10", DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null, 100, Instant.MIN, Instant.MAX);
        BigDecimal subtotal = new BigDecimal("50.00");
        
        BigDecimal discount = code.computeDiscount(subtotal);
        
        assertThat(discount).isEqualByComparingTo("-5.00");
    }

    @Test
    void computeDiscount_percentageWithCap() {
        DiscountCode code = new DiscountCode("P10", DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("4.00"), 100, Instant.MIN, Instant.MAX);
        BigDecimal subtotal = new BigDecimal("50.00");
        
        BigDecimal discount = code.computeDiscount(subtotal);
        
        assertThat(discount).isEqualByComparingTo("-4.00");
    }

    @Test
    void computeDiscount_flat() {
        DiscountCode code = new DiscountCode("F15", DiscountType.FLAT, new BigDecimal("15.00"), BigDecimal.ZERO, null, 100, Instant.MIN, Instant.MAX);
        BigDecimal subtotal = new BigDecimal("50.00");
        
        BigDecimal discount = code.computeDiscount(subtotal);
        
        assertThat(discount).isEqualByComparingTo("-15.00");
    }

    @Test
    void computeDiscount_cappedToSubtotal() {
        DiscountCode code = new DiscountCode("F50", DiscountType.FLAT, new BigDecimal("50.00"), BigDecimal.ZERO, null, 100, Instant.MIN, Instant.MAX);
        BigDecimal subtotal = new BigDecimal("30.00");
        
        BigDecimal discount = code.computeDiscount(subtotal);
        
        // Discount should not exceed subtotal
        assertThat(discount).isEqualByComparingTo("-30.00");
    }

    @Test
    void computeDiscount_roundingHalfUp() {
        DiscountCode code = new DiscountCode("P15", DiscountType.PERCENTAGE, new BigDecimal("15.00"), BigDecimal.ZERO, null, 100, Instant.MIN, Instant.MAX);
        // 15% of 33.33 = 4.9995 -> rounds to 5.00
        BigDecimal subtotal = new BigDecimal("33.33");
        
        BigDecimal discount = code.computeDiscount(subtotal);
        
        assertThat(discount).isEqualByComparingTo("-5.00");
    }
}
