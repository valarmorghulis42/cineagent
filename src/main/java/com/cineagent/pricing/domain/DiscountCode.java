package com.cineagent.pricing.domain;

import com.cineagent.common.money.Money;
import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "discount_code")
public class DiscountCode extends BaseEntity {

  @Column(nullable = false, unique = true, length = 40)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20)
  private DiscountType discountType;

  @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
  private BigDecimal value;

  @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal minOrderAmount = BigDecimal.ZERO;

  @Column(name = "max_discount_amount", precision = 12, scale = 2)
  private BigDecimal maxDiscountAmount;

  @Column(name = "max_redemptions", nullable = false)
  private int maxRedemptions;

  @Column(name = "used_count", nullable = false)
  private int usedCount;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  @Column(nullable = false)
  private boolean active = true;

  protected DiscountCode() {}

  public DiscountCode(
      String code,
      DiscountType discountType,
      BigDecimal value,
      BigDecimal minOrderAmount,
      BigDecimal maxDiscountAmount,
      int maxRedemptions,
      Instant validFrom,
      Instant validUntil) {
    this.code = code.toUpperCase();
    this.discountType = discountType;
    this.value = value;
    this.minOrderAmount = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;
    this.maxDiscountAmount = maxDiscountAmount;
    this.maxRedemptions = maxRedemptions;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
  }

  public boolean isCurrentlyValid(Instant now) {
    return active && !now.isBefore(validFrom) && now.isBefore(validUntil);
  }

  /** Signed (negative) discount amount for the given order subtotal, clamped to the cap. */
  public BigDecimal computeDiscount(BigDecimal orderSubtotal) {
    BigDecimal raw =
        discountType == DiscountType.PERCENTAGE ? Money.percentOf(orderSubtotal, value) : Money.round(value);
    BigDecimal capped = maxDiscountAmount == null ? raw : raw.min(maxDiscountAmount);
    return capped.min(orderSubtotal).negate();
  }

  public String getCode() {
    return code;
  }

  public DiscountType getDiscountType() {
    return discountType;
  }

  public BigDecimal getValue() {
    return value;
  }

  public BigDecimal getMinOrderAmount() {
    return minOrderAmount;
  }

  public int getMaxRedemptions() {
    return maxRedemptions;
  }

  public int getUsedCount() {
    return usedCount;
  }

  public boolean isActive() {
    return active;
  }
}
