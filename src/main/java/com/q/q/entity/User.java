package com.q.q.entity;

import java.time.LocalDateTime;
import java.util.Locale;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.q.q.entity.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor          // evita @Data en entidades JPA
public class User {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Column(nullable = false, length = 150, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(length = 30)
	private String phone;

	@Enumerated(EnumType.STRING)                // nunca ORDINAL
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean active = true;

	@CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
	@UpdateTimestamp   @Column(name = "updated_at")                    private LocalDateTime updatedAt;

	// RN-11: el email se guarda en minúsculas; usa este mismo método para buscar por email
	public static String normalizeEmail(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	public void setEmail(String email) {
		this.email = normalizeEmail(email);
	}

	@Override
	public String toString() {                  // sin email ni hash: puede terminar en un log
		return "User[id=" + id + ", role=" + role + ", active=" + active + "]";
	}

}
