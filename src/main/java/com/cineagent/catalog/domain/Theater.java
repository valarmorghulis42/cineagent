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
@Table(name = "theater", uniqueConstraints = @UniqueConstraint(columnNames = {"city_id", "name"}))
public class Theater extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 500)
  private String address;

  @Column(nullable = false)
  private boolean active = true;

  protected Theater() {}

  public Theater(City city, String name, String address) {
    this.city = city;
    this.name = name;
    this.address = address;
  }

  public City getCity() {
    return city;
  }

  public String getName() {
    return name;
  }

  public String getAddress() {
    return address;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void update(String name, String address) {
    this.name = name;
    this.address = address;
  }
}
