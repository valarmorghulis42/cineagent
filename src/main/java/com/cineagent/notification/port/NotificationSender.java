package com.cineagent.notification.port;

/** The second of the two real ports in this codebase (with {@code PaymentGateway}). */
public interface NotificationSender {
  void send(String eventType, String payload);
}
