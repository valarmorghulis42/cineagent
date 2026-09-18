package com.cineagent.catalog.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "screen", uniqueConstraints = @UniqueConstraint(columnNames = {"theater_id", "name"}))
public class Screen extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "theater_id", nullable = false)
  private Theater theater;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "total_seats", nullable = false)
  private int totalSeats;

  @Column(nullable = false)
  private boolean active = true;

  protected Screen() {}

  public Screen(Theater theater, String name) {
    this.theater = theater;
    this.name = name;
    this.totalSeats = 0;
  }

  public Theater getTheater() {
    return theater;
  }

  public String getName() {
    return name;
  }

  public int getTotalSeats() {
    return totalSeats;
  }

  public void setTotalSeats(int totalSeats) {
    this.totalSeats = totalSeats;
  }

  public boolean isActive() {
    return active;
  }
}
