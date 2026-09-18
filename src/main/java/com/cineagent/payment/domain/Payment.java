package com.cineagent.payment.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

  @Column(name = "booking_id", nullable = false)
  private Long bookingId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(name = "gateway_reference", nullable = false, length = 100)
  private String gatewayReference;

  protected Payment() {}

  public Payment(Long bookingId, BigDecimal amount, String currency, PaymentStatus status, String gatewayReference) {
    this.bookingId = bookingId;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.gatewayReference = gatewayReference;
  }

  public Long getBookingId() {
    return bookingId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getGatewayReference() {
    return gatewayReference;
  }

  public void markReversed() {
    this.status = PaymentStatus.REVERSED;
  }
}
