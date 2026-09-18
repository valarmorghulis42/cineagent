package com.cineagent.common.error;

import org.springframework.http.HttpStatus;

/**
 * The single registry of every error condition the API can return. Adding a new error is one
 * new constant here plus a throw site — never a {@code switch} over exception type in {@link
 * GlobalExceptionHandler}. See the {@code api-contract} skill for the full status-code policy.
 */
public enum ErrorCode {
  // --- Booking / seat holds ---
  SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "One or more selected seats are no longer available"),
  HOLD_EXPIRED(HttpStatus.CONFLICT, "Your seat hold has expired"),
  HOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "Hold not found"),
  HOLD_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Too many active seat holds"),
  SEAT_CONTENTION_TIMEOUT(HttpStatus.CONFLICT, "Could not acquire seats, please retry"),
  SEAT_LOST_AFTER_PAYMENT(
      HttpStatus.CONFLICT, "Seats were lost after payment; a refund was issued"),
  INVALID_BOOKING_STATE(
      HttpStatus.CONFLICT, "Booking is not in a valid state for this operation"),
  BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
  SEAT_NOT_IN_BOOKING(HttpStatus.UNPROCESSABLE_ENTITY, "Seat is not part of this booking"),

  // --- Shows / catalog ---
  SHOW_NOT_BOOKABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Show is not open for booking"),
  SHOW_ALREADY_STARTED(HttpStatus.UNPROCESSABLE_ENTITY, "Show has already started"),
  SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Show not found"),
  SCREEN_OVERLAP(HttpStatus.CONFLICT, "This screen already has an overlapping show"),
  SEAT_LAYOUT_ALREADY_EXISTS(HttpStatus.CONFLICT, "This screen already has a seat layout"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

  // --- Pricing / discounts ---
  DISCOUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Discount code is not valid"),
  DISCOUNT_EXHAUSTED(HttpStatus.UNPROCESSABLE_ENTITY, "Discount code usage limit reached"),
  DISCOUNT_NOT_STACKABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Discount codes cannot be combined"),
  DISCOUNT_MIN_ORDER_NOT_MET(
      HttpStatus.UNPROCESSABLE_ENTITY, "Order does not meet the minimum amount for this code"),

  // --- Payment / refund ---
  PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "Payment was declined"),
  PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A payment already exists for this booking"),
  REFUND_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "This booking is not eligible for a refund"),
  REFUND_ALREADY_EXISTS(HttpStatus.CONFLICT, "A refund already exists for this booking"),

  // --- Idempotency ---
  IDEMPOTENCY_KEY_REUSE(
      HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused with a different request body"),
  REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "An identical request is already in progress"),
  IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),

  // --- Identity / auth ---
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),

  // --- Generic ---
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

  private final HttpStatus defaultStatus;
  private final String title;

  ErrorCode(HttpStatus defaultStatus, String title) {
    this.defaultStatus = defaultStatus;
    this.title = title;
  }

  public HttpStatus getDefaultStatus() {
    return defaultStatus;
  }

  public String getTitle() {
    return title;
  }
}
