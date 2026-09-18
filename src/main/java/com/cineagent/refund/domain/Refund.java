package com.cineagent.refund.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "refund")
public class Refund extends BaseEntity {

  @Column(name = "booking_id", nullable = false)
  private Long bookingId;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(length = 500)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RefundStatus status;

  @Column(name = "gateway_reference", nullable = false, length = 100)
  private String gatewayReference;

  protected Refund() {}

  public Refund(
      Long bookingId, Long paymentId, BigDecimal amount, String reason, RefundStatus status, String gatewayReference) {
    this.bookingId = bookingId;
    this.paymentId = paymentId;
    this.amount = amount;
    this.reason = reason;
    this.status = status;
    this.gatewayReference = gatewayReference;
  }

  public Long getBookingId() {
    return bookingId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public RefundStatus getStatus() {
    return status;
  }

  public String getGatewayReference() {
    return gatewayReference;
  }
}
