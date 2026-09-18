package com.cineagent.identity.domain;

/**
 * The two roles defined by the assignment: admin (manage cities, theaters, shows, seat layouts,
 * pricing tiers, refund policies) and customer (browse, book, cancel, view history). Advanced
 * auth (OAuth/SSO/MFA) is explicitly out of scope — this fixed two-role enum, stored as a
 * {@code @Enumerated(STRING)} element collection on {@link User}, is deliberately simple rather
 * than a full role/permission entity model.
 */
public enum Role {
  ADMIN,
  CUSTOMER
}
