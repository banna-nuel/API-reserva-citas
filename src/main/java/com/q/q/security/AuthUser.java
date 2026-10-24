package com.q.q.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

public record AuthUser(Long id, String email, String passwordHash, Role role, boolean active) implements UserDetails {

	public static AuthUser from(User user) {
		return new AuthUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isActive());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));   // hasRole('ADMIN') busca "ROLE_ADMIN"
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

	@Override
	public String toString() {                  // el toString del record incluiría el hash
		return "AuthUser[id=" + id + ", role=" + role + ", active=" + active + "]";
	}

}
