package com.cineagent.identity.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false)
  private boolean enabled = true;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Set<Role> roles = EnumSet.noneOf(Role.class);

  protected User() {}

  public User(String email, String passwordHash, String fullName, String phone, Set<Role> roles) {
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.phone = phone;
    this.roles = EnumSet.copyOf(roles);
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPhone() {
    return phone;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Set<Role> getRoles() {
    return roles;
  }
}
