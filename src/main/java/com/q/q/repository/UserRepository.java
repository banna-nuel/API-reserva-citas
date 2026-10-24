package com.q.q.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.q.q.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);              // recibe el email ya normalizado (User.normalizeEmail)

	boolean existsByEmail(String email);

}
