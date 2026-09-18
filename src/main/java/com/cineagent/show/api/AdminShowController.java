package com.cineagent.show.api;

import com.cineagent.show.api.dto.ShowDtos.CreateShowRequest;
import com.cineagent.show.api.dto.ShowDtos.ShowResponse;
import com.cineagent.show.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shows")
public class AdminShowController {

  private final ShowService showService;

  public AdminShowController(ShowService showService) {
    this.showService = showService;
  }

  @PostMapping
  public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ShowResponse.from(showService.create(req)));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable Long id) {
    showService.cancel(id);
    return ResponseEntity.noContent().build();
  }
}
