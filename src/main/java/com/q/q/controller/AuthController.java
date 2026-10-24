package com.q.q.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		UserResponse created = authService.register(request);
		// El perfil recién creado se consulta en /users/me (no hay GET /users/{id} en el contrato)
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/users/me").build().toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

}
