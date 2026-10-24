package com.q.q.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.q.q.dto.response.UserResponse;
import com.q.q.security.AuthUser;
import com.q.q.service.UserService;

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

}
