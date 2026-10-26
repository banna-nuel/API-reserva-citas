package com.q.q.controller;

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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthUser principal) {
		return userService.getProfile(principal.id());
	}

	@PutMapping("/me")
	public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.updateProfile(principal.id(), request);
	}

	@PatchMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		userService.changePassword(principal.id(), request);
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<UserResponse> list(@RequestParam(required = false) Role role, Pageable pageable,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.list(role, pageable, principal);
	}

	@PatchMapping("/{id}/active")
	@PreAuthorize("hasRole('ADMIN')")
	public UserResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request,
			@AuthenticationPrincipal AuthUser principal) {
		return userService.setActive(id, request.active(), principal);
	}

}
