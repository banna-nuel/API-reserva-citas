package com.q.q.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.response.UserResponse;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.UserMapper;
import com.q.q.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository users;
	private final UserMapper mapper;

	@Transactional(readOnly = true)
	public UserResponse getProfile(Long userId) {
		return users.findById(userId)
				.map(mapper::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
	}

}
