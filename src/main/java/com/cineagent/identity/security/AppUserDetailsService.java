package com.cineagent.identity.security;

import com.cineagent.identity.domain.User;
import com.cineagent.identity.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Used only by the login flow's {@code AuthenticationManager} to verify a submitted password
 * against the stored BCrypt hash. Everyday request authentication is stateless (see {@link
 * JwtAuthenticationFilter}) and never touches this class or the database.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public AppUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmail(email.toLowerCase())
            .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    List<SimpleGrantedAuthority> authorities =
        user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    return org.springframework.security.core.userdetails.User.builder()
        .username(user.getEmail())
        .password(user.getPasswordHash())
        .authorities(authorities)
        .disabled(!user.isEnabled())
        .build();
  }
}
