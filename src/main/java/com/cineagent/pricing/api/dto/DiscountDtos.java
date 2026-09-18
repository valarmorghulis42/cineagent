package com.cineagent.pricing.api.dto;

import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class DiscountDtos {
  private DiscountDtos() {}

  public record CreateDiscountRequest(
      @NotBlank String code,
      @NotNull DiscountType discountType,
      @DecimalMin("0.01") BigDecimal value,
      BigDecimal minOrderAmount,
      BigDecimal maxDiscountAmount,
      @Min(1) int maxRedemptions,
      @NotNull Instant validFrom,
      @NotNull Instant validUntil) {}

  public record DiscountResponse(
      Long id,
      String code,
      DiscountType discountType,
      BigDecimal value,
      BigDecimal minOrderAmount,
      int maxRedemptions,
      int usedCount,
      boolean active) {
    public static DiscountResponse from(DiscountCode d) {
      return new DiscountResponse(
          d.getId(),
          d.getCode(),
          d.getDiscountType(),
          d.getValue(),
          d.getMinOrderAmount(),
          d.getMaxRedemptions(),
          d.getUsedCount(),
          d.isActive());
    }
  }
}
