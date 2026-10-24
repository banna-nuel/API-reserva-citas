package com.q.q.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.entity.User;
import com.q.q.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	@Override
	@Transactional(readOnly = true)
	public AuthUser loadUserByUsername(String email) {
		return users.findByEmail(User.normalizeEmail(email))
				.map(AuthUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));   // sin el email
	}

}
