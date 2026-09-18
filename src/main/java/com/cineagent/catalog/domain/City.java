package com.cineagent.catalog.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "city", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "state"}))
public class City extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 100)
  private String state;

  @Column(nullable = false, length = 100)
  private String country;

  /** IANA timezone id (e.g. "Asia/Kolkata") — used to display local showtimes; storage is UTC. */
  @Column(nullable = false, length = 50)
  private String timezone;

  @Column(nullable = false)
  private boolean active = true;

  protected City() {}

  public City(String name, String state, String country, String timezone) {
    this.name = name;
    this.state = state;
    this.country = country;
    this.timezone = timezone;
  }

  public String getName() {
    return name;
  }

  public String getState() {
    return state;
  }

  public String getCountry() {
    return country;
  }

  public String getTimezone() {
    return timezone;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void update(String name, String state, String country, String timezone) {
    this.name = name;
    this.state = state;
    this.country = country;
    this.timezone = timezone;
  }
}
