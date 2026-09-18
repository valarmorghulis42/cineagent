package com.cineagent.catalog.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "movie")
public class Movie extends BaseEntity {

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false, length = 50)
  private String language;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(length = 20)
  private String certification;

  @Column(length = 2000)
  private String synopsis;

  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Column(nullable = false)
  private boolean active = true;

  protected Movie() {}

  public Movie(
      String title,
      String language,
      int durationMinutes,
      String certification,
      String synopsis,
      LocalDate releaseDate) {
    this.title = title;
    this.language = language;
    this.durationMinutes = durationMinutes;
    this.certification = certification;
    this.synopsis = synopsis;
    this.releaseDate = releaseDate;
  }

  public String getTitle() {
    return title;
  }

  public String getLanguage() {
    return language;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public String getCertification() {
    return certification;
  }

  public String getSynopsis() {
    return synopsis;
  }

  public LocalDate getReleaseDate() {
    return releaseDate;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
      String title,
      String language,
      int durationMinutes,
      String certification,
      String synopsis,
      LocalDate releaseDate) {
    this.title = title;
    this.language = language;
    this.durationMinutes = durationMinutes;
    this.certification = certification;
    this.synopsis = synopsis;
    this.releaseDate = releaseDate;
  }
}
