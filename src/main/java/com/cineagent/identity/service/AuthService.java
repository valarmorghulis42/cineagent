package com.cineagent.identity.service;

import com.cineagent.common.error.ConflictException;
import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.ResourceNotFoundException;
import com.cineagent.common.error.UnauthorizedException;
import com.cineagent.identity.api.dto.AuthResponse;
import com.cineagent.identity.api.dto.LoginRequest;
import com.cineagent.identity.api.dto.RegisterRequest;
import com.cineagent.identity.api.dto.UserResponse;
import com.cineagent.identity.domain.Role;
import com.cineagent.identity.domain.User;
import com.cineagent.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      JwtService jwtService,
      JwtProperties jwtProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String email = request.email().toLowerCase();
    if (userRepository.existsByEmail(email)) {
      throw new ConflictException(
          ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with email " + email + " already exists");
    }
    // Public registration is deliberately CUSTOMER-only — admins are seeded (V100 migration),
    // never self-registered, to avoid a privilege-escalation path through this endpoint.
    User user =
        new User(
            email,
            passwordEncoder.encode(request.password()),
            request.fullName(),
            request.phone(),
            EnumSet.of(Role.CUSTOMER));
    user = userRepository.save(user);
    return issueFor(user);
  }

  public AuthResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(
              request.email().toLowerCase(), request.password()));
    } catch (BadCredentialsException e) {
      throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
    // Reaching here means AuthenticationManager already verified this email+password pair
    // against a real user, so this lookup cannot fail in practice — but if it somehow did
    // (e.g. a deletion race), the response must stay generic, never echo the submitted email
    // back into an auth-failure body.
    User user =
        userRepository
            .findByEmail(request.email().toLowerCase())
            .orElseThrow(
                () -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
    return issueFor(user);
  }

  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.RESOURCE_NOT_FOUND, userId));
    return UserResponse.from(user);
  }

  private AuthResponse issueFor(User user) {
    Set<String> roleNames = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    String token = jwtService.issue(user.getId(), user.getEmail(), roleNames);
    Instant expiresAt = Instant.now(clock).plus(jwtProperties.expirationMinutes(), ChronoUnit.MINUTES);
    return new AuthResponse(
        token, expiresAt, user.getId(), user.getEmail(), user.getFullName(), roleNames);
  }
}
