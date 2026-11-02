package com.q.q.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Público: sin @SecurityRequirement
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registro de clientes e inicio de sesión")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)                   // solo para la documentación: el ResponseEntity ya es 201
	@Operation(summary = "Registrar un cliente",
			description = "Crea un usuario con rol CLIENT. El email se guarda en minúsculas; role y active se ignoran.")
	@ApiResponse(responseCode = "201", description = "Cliente creado; Location apunta a /api/v1/users/me")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_USED")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		UserResponse created = authService.register(request);
		// El perfil recién creado se consulta en /users/me (no hay GET /users/{id} en el contrato)
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/users/me").build().toUri();
		return ResponseEntity.created(location).body(created);              // 201 + Location
	}

	@PostMapping("/login")
	@Operation(summary = "Iniciar sesión", description = "Devuelve el JWT para la cabecera Authorization: Bearer.")
	@ApiResponse(responseCode = "200", description = "Token emitido")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS (email, contraseña o usuario inactivo)")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

}
