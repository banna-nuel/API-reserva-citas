package com.q.q.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.entity.enums.Role;

/** Quién hace la petición actual, según el SecurityContext que llena JwtAuthenticationFilter. */
public final class CurrentUser {

	private CurrentUser() {
	}

	// Vacío si la petición es anónima: sin token, o con uno inválido en un endpoint público
	public static Optional<AuthUser> get() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getPrincipal() instanceof AuthUser user ? Optional.of(user) : Optional.empty();
	}

	public static boolean isAdmin() {
		return get().map(user -> user.role() == Role.ADMIN).orElse(false);
	}

}
