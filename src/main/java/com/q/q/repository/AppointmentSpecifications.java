package com.q.q.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

import com.q.q.entity.Appointment;
import com.q.q.entity.enums.AppointmentStatus;

/** Filtros de la agenda (GET /appointments). Se combinan con Specification.allOf. */
public final class AppointmentSpecifications {

	private AppointmentSpecifications() {
	}

	public static Specification<Appointment> clientIs(Long userId) {
		return (root, query, cb) -> cb.equal(root.get("client").get("id"), userId);
	}

	// La agenda de un profesional se busca por su usuario: es lo que trae el token
	public static Specification<Appointment> professionalUserIs(Long userId) {
		return (root, query, cb) -> cb.equal(root.get("professional").get("user").get("id"), userId);
	}

	public static Specification<Appointment> professionalIs(Long professionalId) {
		return (root, query, cb) -> cb.equal(root.get("professional").get("id"), professionalId);
	}

	public static Specification<Appointment> statusIs(AppointmentStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<Appointment> startsAtOrAfter(LocalDateTime from) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
	}

	public static Specification<Appointment> startsBefore(LocalDateTime until) {
		return (root, query, cb) -> cb.lessThan(root.get("startTime"), until);
	}

}
