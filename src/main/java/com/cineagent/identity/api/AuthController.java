package com.cineagent.identity.api;

import com.cineagent.common.security.AuthenticatedUser;
import com.cineagent.common.security.CurrentUser;
import com.cineagent.identity.api.dto.AuthResponse;
import com.cineagent.identity.api.dto.LoginRequest;
import com.cineagent.identity.api.dto.RegisterRequest;
import com.cineagent.identity.api.dto.UserResponse;
import com.cineagent.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controllers delegate to exactly one service method that owns the transaction — see the
 * spring-boot-conventions skill. GET /me previously injected UserRepository directly here; moved
 * to AuthService.getCurrentUser. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @GetMapping("/me")
  public UserResponse me(@CurrentUser AuthenticatedUser currentUser) {
    return authService.getCurrentUser(currentUser.userId());
  }
}
