package com.q.q.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Perfil profesional de un usuario con rol PROFESSIONAL (1:1). Nombre, email y teléfono viven en User
@Entity
@Table(name = "professionals")
@Getter @Setter @NoArgsConstructor          // evita @Data en entidades JPA
public class Professional {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
	private User user;

	@Column(nullable = false, length = 100)
	private String specialty;

	@Column(length = 500)
	private String bio;

	@Column(nullable = false)
	private boolean active = true;

	// Al listar, los servicios de toda la página se cargan en una sola consulta (sin N+1 ni paginar en memoria)
	@ManyToMany
	@JoinTable(name = "professional_services",
			joinColumns = @JoinColumn(name = "professional_id"),
			inverseJoinColumns = @JoinColumn(name = "service_id"))
	@BatchSize(size = 100)
	private Set<ServiceOffering> services = new HashSet<>();

	@CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
	@UpdateTimestamp   @Column(name = "updated_at")                    private LocalDateTime updatedAt;

	// Para quien no es ADMIN, el profesional existe solo si su perfil y su usuario están activos
	public boolean isPubliclyVisible() {
		return active && user.isActive();
	}

	@Override
	public String toString() {                  // sin el usuario: no arrastra datos personales a un log
		return "Professional[id=" + id + ", active=" + active + "]";
	}

}
