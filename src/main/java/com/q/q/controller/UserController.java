package com.q.q.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.q.q.dto.request.ChangePasswordRequest;
import com.q.q.dto.request.UpdateProfileRequest;
import com.q.q.dto.request.UpdateActiveRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.enums.Role;
import com.q.q.security.AuthUser;
import com.q.q.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Todo /users exige token: sin @SecurityRequirement, Swagger UI no enviaría el JWT de Authorize
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Perfil propio y gestión de usuarios (ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	@Operation(summary = "Mi perfil", description = "Los datos del usuario del token.")
	@ApiResponse(responseCode = "200", description = "Perfil")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	public UserResponse me(@AuthenticationPrincipal AuthUser principal) {
		return userService.getProfile(principal.id());
	}

	@PutMapping("/me")
	@Operation(summary = "Actualizar mi perfil", description = "Reemplaza fullName y phone; email, role y active se ignoran.")
	@ApiResponse(responseCode = "200", description = "Perfil actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.updateProfile(principal.id(), request);
	}

	@PatchMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cambiar mi contraseña", description = "Pide la contraseña actual y la nueva (8 a 72 caracteres).")
	@ApiResponse(responseCode = "204", description = "Contraseña cambiada")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR o MALFORMED_REQUEST")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "422", description = "INVALID_CURRENT_PASSWORD o PASSWORD_UNCHANGED")
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		userService.changePassword(principal.id(), request);
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Listar usuarios (ADMIN)", description = "Paginado; filtro opcional role; sort con lista blanca.")
	@ApiResponse(responseCode = "200", description = "Página de usuarios")
	@ApiResponse(responseCode = "400", description = "INVALID_PARAMETER o INVALID_SORT")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	public PageResponse<UserResponse> list(@RequestParam(required = false) Role role, @ParameterObject Pageable pageable,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.list(role, pageable, principal);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Activar o desactivar un usuario (ADMIN)", description = "El usuario desactivado pierde el acceso.")
	@ApiResponse(responseCode = "200", description = "Usuario actualizado")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, MALFORMED_REQUEST o INVALID_PARAMETER")
	@ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED")
	@ApiResponse(responseCode = "403", description = "ACCESS_DENIED: no es ADMIN")
	@ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND")
	@ApiResponse(responseCode = "422", description = "CANNOT_DEACTIVATE_SELF")
	public UserResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.setActive(id, request.active(), principal);
	}

}
