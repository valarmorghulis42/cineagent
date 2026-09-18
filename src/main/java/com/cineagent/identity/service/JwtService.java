package com.cineagent.identity.service;

import com.cineagent.common.security.AuthenticatedUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless HS256 JWTs. Chosen over server-side sessions because advanced
 * auth (OAuth/SSO/MFA) is out of scope and this project has no need for revocable sessions — see
 * AGENTS.md §2. Deliberately no refresh token: out of scope for the same reason.
 */
@Service
public class JwtService {

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  private static final String ROLES_CLAIM = "roles";
  private static final String EMAIL_CLAIM = "email";

  private final JwtProperties properties;
  private final Clock clock;

  public JwtService(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public String issue(Long userId, String email, Set<String> roles) {
    try {
      Instant now = clock.instant();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(String.valueOf(userId))
              .claim(EMAIL_CLAIM, email)
              .claim(ROLES_CLAIM, List.copyOf(roles))
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(properties.expirationMinutes(), ChronoUnit.MINUTES)))
              .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(secretBytes()));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign JWT", e);
    }
  }

  public Optional<AuthenticatedUser> parse(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secretBytes());
      if (!jwt.verify(verifier)) {
        return Optional.empty();
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      Date expiration = claims.getExpirationTime();
      if (expiration == null || expiration.toInstant().isBefore(clock.instant())) {
        return Optional.empty();
      }
      Long userId = Long.valueOf(claims.getSubject());
      String email = claims.getStringClaim(EMAIL_CLAIM);
      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) claims.getClaim(ROLES_CLAIM);
      return Optional.of(new AuthenticatedUser(userId, email, Set.copyOf(roles)));
    } catch (JOSEException | ParseException | NumberFormatException e) {
      log.debug("Rejected invalid JWT: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private byte[] secretBytes() {
    return properties.secret().getBytes(StandardCharsets.UTF_8);
  }
}
