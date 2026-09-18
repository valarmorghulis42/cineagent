package com.cineagent.show.api;

import com.cineagent.common.security.AuthenticatedUser;
import com.cineagent.common.security.CurrentUser;
import com.cineagent.show.api.dto.HoldDtos.CreateHoldRequest;
import com.cineagent.show.api.dto.HoldDtos.HoldResponse;
import com.cineagent.show.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints require authentication (anyRequest().authenticated() in SecurityConfig). */
@RestController
public class SeatHoldController {

  private final SeatHoldService seatHoldService;

  public SeatHoldController(SeatHoldService seatHoldService) {
    this.seatHoldService = seatHoldService;
  }

  @PostMapping("/api/v1/shows/{showId}/holds")
  public ResponseEntity<HoldResponse> acquire(
      @PathVariable Long showId,
      @Valid @RequestBody CreateHoldRequest req,
      @CurrentUser AuthenticatedUser user) {
    var hold = seatHoldService.acquire(showId, req.seatIds(), user.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(HoldResponse.from(hold));
  }

  @PostMapping("/api/v1/holds/{holdId}/extend")
  public HoldResponse extend(@PathVariable Long holdId, @CurrentUser AuthenticatedUser user) {
    return HoldResponse.from(seatHoldService.extend(holdId, user.userId()));
  }

  @DeleteMapping("/api/v1/holds/{holdId}")
  public ResponseEntity<Void> release(@PathVariable Long holdId, @CurrentUser AuthenticatedUser user) {
    seatHoldService.release(holdId, user.userId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/admin/show-seats/{showSeatId}/block")
  public ResponseEntity<Void> blockSeat(@PathVariable Long showSeatId) {
    seatHoldService.blockSeat(showSeatId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/admin/show-seats/{showSeatId}/unblock")
  public ResponseEntity<Void> unblockSeat(@PathVariable Long showSeatId) {
    seatHoldService.unblockSeat(showSeatId);
    return ResponseEntity.noContent().build();
  }
}
