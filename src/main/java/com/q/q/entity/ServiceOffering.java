package com.q.q.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Se llama ServiceOffering (y no Service) para no confundirla con la anotación @Service de Spring
@Entity
@Table(name = "service_offerings")
@Getter @Setter @NoArgsConstructor          // evita @Data en entidades JPA
public class ServiceOffering {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100, unique = true)
	private String name;

	@Column(length = 500)
	private String description;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Column(nullable = false, precision = 10, scale = 2)    // dinero: BigDecimal, nunca double
	private BigDecimal price;

	@Column(nullable = false)
	private boolean active = true;

	@CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
	@UpdateTimestamp   @Column(name = "updated_at")                    private LocalDateTime updatedAt;

}
