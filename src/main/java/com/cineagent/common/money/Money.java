package com.cineagent.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place money rounding happens. {@code BigDecimal} is the only representation for
 * currency amounts anywhere in this codebase — {@code double}/{@code float} are banned in the
 * {@code pricing}, {@code refund}, {@code payment}, and {@code booking} packages (AGENTS.md
 * §4.2).
 *
 * <p>Rounding happens once per line item, as it is produced — never only at the end of a
 * calculation. This is what guarantees an itemized breakdown always sums exactly to its total;
 * see {@code PricingEngineTest}'s invariant test.
 */
public final class Money {

  public static final int SCALE = 2;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private Money() {}

  /** Rounds to the standard 2-decimal currency scale using HALF_UP. */
  public static BigDecimal round(BigDecimal amount) {
    return amount.setScale(SCALE, ROUNDING);
  }

  /** {@code base * (percent / 100)}, computed at higher internal precision then rounded once. */
  public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
    BigDecimal fraction = percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    return round(base.multiply(fraction));
  }

  /** Clamps a (possibly negative or over-large) amount to the range [0, max]. */
  public static BigDecimal clamp(BigDecimal amount, BigDecimal max) {
    BigDecimal clamped = amount.max(BigDecimal.ZERO);
    return max == null ? clamped : clamped.min(max);
  }

  public static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
  }
}
