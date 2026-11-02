package com.q.q.mapper;

import org.springframework.stereotype.Component;

import com.q.q.dto.response.UserResponse;
import com.q.q.entity.User;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {                     // nunca expone passwordHash
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
				user.getRole(), user.isActive(), user.getCreatedAt());
	}

}
