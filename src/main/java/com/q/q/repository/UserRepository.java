package com.q.q.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);              // recibe el email ya normalizado (User.normalizeEmail)

	boolean existsByEmail(String email);

	Page<User> findByRole(Role role, Pageable pageable);

	// SELECT ... FOR UPDATE: al reservar se bloquea al cliente después del profesional, para que RN-06 resista dos
	// reservas simultáneas del mismo cliente con profesionales distintos
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

}
