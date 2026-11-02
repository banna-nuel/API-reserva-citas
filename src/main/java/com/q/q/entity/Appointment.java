package com.q.q.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.q.q.entity.enums.AppointmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Adelantada de la Fase 6 para la disponibilidad: solo el mapeo de la tabla de la V1
@Entity
@Table(name = "appointments")
@Getter @Setter @NoArgsConstructor          // evita @Data en entidades JPA
public class Appointment {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id")
	private User client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "professional_id")
	private Professional professional;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_id")
	private ServiceOffering service;

	// Hora local del negocio (app.timezone), sin zona: así se guarda en las columnas DATETIME
	@Column(name = "start_time", nullable = false) private LocalDateTime startTime;
	@Column(name = "end_time", nullable = false)   private LocalDateTime endTime;

	@Enumerated(EnumType.STRING)                // nunca ORDINAL
	@Column(nullable = false, length = 20)
	private AppointmentStatus status = AppointmentStatus.PENDING;

	@Column(nullable = false, precision = 10, scale = 2)    // precio congelado al reservar (RN-09)
	private BigDecimal price;

	@Column(length = 500)
	private String notes;

	@Column(name = "cancel_reason")
	private String cancelReason;

	@CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
	@UpdateTimestamp   @Column(name = "updated_at")                    private LocalDateTime updatedAt;

	@Override
	public String toString() {                  // sin cliente ni notas: no arrastra datos personales a un log
		return "Appointment[id=" + id + ", status=" + status + ", " + startTime + "-" + endTime + "]";
	}

}
