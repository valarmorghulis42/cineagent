package com.cineagent.show.domain;

import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * Per-show, per-seat-category base price. A show's base price is genuinely per-show (a Tuesday
 * matinee and a Friday premiere of the same film in the same screen price differently) — never
 * stored on {@link ShowSeat}, which would duplicate the price hundreds of times per show and
 * make a price change an UPDATE over every seat row. Missing a category here for a show that has
 * seats of that category is a hard error at booking/quote time, never a silent zero — see
 * {@code PricingEngine}.
 */
@Entity
@Table(name = "show_price", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_category"}))
public class ShowPrice extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Enumerated(EnumType.STRING)
  @Column(name = "seat_category", nullable = false, length = 20)
  private SeatCategory seatCategory;

  @Column(name = "base_amount", nullable = false)
  private BigDecimal baseAmount;

  protected ShowPrice() {}

  public ShowPrice(Show show, SeatCategory seatCategory, BigDecimal baseAmount) {
    this.show = show;
    this.seatCategory = seatCategory;
    this.baseAmount = baseAmount;
  }

  public Show getShow() {
    return show;
  }

  public SeatCategory getSeatCategory() {
    return seatCategory;
  }

  public BigDecimal getBaseAmount() {
    return baseAmount;
  }
}
