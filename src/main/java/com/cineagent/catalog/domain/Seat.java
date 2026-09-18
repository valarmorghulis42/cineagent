package com.cineagent.catalog.domain;

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

/** A physical seat in a screen's layout. Bookable instances per show are {@code ShowSeat}. */
@Entity
@Table(
    name = "seat",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"screen_id", "row_label", "seat_number"}))
public class Seat extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "screen_id", nullable = false)
  private Screen screen;

  @Column(name = "row_label", nullable = false, length = 5)
  private String rowLabel;

  @Column(name = "seat_number", nullable = false)
  private int seatNumber;

  @Column(name = "display_label", nullable = false, length = 10)
  private String displayLabel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SeatCategory category;

  @Column(nullable = false)
  private boolean active = true;

  protected Seat() {}

  public Seat(Screen screen, String rowLabel, int seatNumber, SeatCategory category) {
    this.screen = screen;
    this.rowLabel = rowLabel;
    this.seatNumber = seatNumber;
    this.displayLabel = rowLabel + seatNumber;
    this.category = category;
  }

  public Screen getScreen() {
    return screen;
  }

  public String getRowLabel() {
    return rowLabel;
  }

  public int getSeatNumber() {
    return seatNumber;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  public SeatCategory getCategory() {
    return category;
  }

  public boolean isActive() {
    return active;
  }
}
