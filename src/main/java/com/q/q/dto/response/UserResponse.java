package com.q.q.dto.response;

import java.time.LocalDateTime;

import com.q.q.entity.enums.Role;

public record UserResponse(Long id, String fullName, String email, String phone, Role role,
		boolean active, LocalDateTime createdAt) {}
