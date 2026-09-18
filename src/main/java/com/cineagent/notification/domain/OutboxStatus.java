package com.cineagent.notification.domain;

public enum OutboxStatus {
  PENDING,
  SENT,
  DEAD_LETTER
}
