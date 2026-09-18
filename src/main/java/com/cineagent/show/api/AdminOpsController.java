package com.cineagent.show.api;

import com.cineagent.booking.service.ReminderService;
import com.cineagent.show.service.HoldExpirySweeper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operability endpoints (plan G1) — a demo video cannot wait out a real @Scheduled interval. */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class AdminOpsController {

  private final HoldExpirySweeper holdExpirySweeper;
  private final ReminderService reminderService;

  public AdminOpsController(HoldExpirySweeper holdExpirySweeper, ReminderService reminderService) {
    this.holdExpirySweeper = holdExpirySweeper;
    this.reminderService = reminderService;
  }

  @PostMapping("/sweep-now")
  public String sweepNow() {
    return "swept=" + holdExpirySweeper.sweepOnce();
  }

  @PostMapping("/remind-now")
  public String remindNow() {
    return "enqueued=" + reminderService.runOnce();
  }
}
