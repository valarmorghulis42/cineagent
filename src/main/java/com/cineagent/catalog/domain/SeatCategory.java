package com.cineagent.catalog.domain;

/**
 * A property of the physical seat, independent of any temporal surcharge (e.g. "weekend"). See
 * the requirement-interpretation note in the architecture plan: the assignment's "regular,
 * premium, weekend" pricing tiers conflate seat category with a show-level temporal surcharge —
 * modeling weekend as a category would make "a premium seat on a Saturday" unrepresentable. The
 * weekend/prime-time surcharge lives in the {@code pricing} module as a {@code PricingRule}
 * instead.
 */
public enum SeatCategory {
  REGULAR,
  PREMIUM,
  RECLINER
}
