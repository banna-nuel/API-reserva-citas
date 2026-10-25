package com.q.q.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);              // recibe el email ya normalizado (User.normalizeEmail)

	boolean existsByEmail(String email);

	Page<User> findByRole(Role role, Pageable pageable);

}
