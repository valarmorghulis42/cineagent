package com.cineagent.notification.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** No real email/SMS provider for this assignment — logs, and the row stays queryable via
 * GET /admin/notifications, which is how "trust me, it's async" becomes "here it is" on camera. */
@Component
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(String eventType, String payload) {
    log.info("NOTIFY [{}] {}", eventType, payload);
  }
}
