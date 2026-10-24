package com.q.q.service;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ConflictException;
import com.q.q.mapper.UserMapper;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;
import com.q.q.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final UserMapper mapper;

	@Transactional
	public UserResponse register(RegisterRequest req) {
		String email = User.normalizeEmail(req.email());                     // RN-11
		if (users.existsByEmail(email)) {
			throw emailAlreadyUsed();
		}
		User user = new User();
		user.setFullName(req.fullName().strip());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		user.setPhone(req.phone());
		user.setRole(Role.CLIENT);                                           // el registro público solo crea clientes
		try {
			return mapper.toResponse(users.saveAndFlush(user));
		} catch (DataIntegrityViolationException ex) {                      // dos registros simultáneos con el mismo email
			throw emailAlreadyUsed();
		}
	}

	public AuthResponse login(LoginRequest req) {
		// BCrypt ignora lo que pase de 72 bytes: sin este corte, "clave + cualquier cosa" también entraría
		if (req.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new BadCredentialsException("Bad credentials");
		}
		Authentication auth = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(User.normalizeEmail(req.email()), req.password()));
		String token = jwtService.generateToken((AuthUser) auth.getPrincipal());
		return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds());
	}

	private static ConflictException emailAlreadyUsed() {
		return new ConflictException("EMAIL_ALREADY_USED", "El email ya está registrado");
	}

}
