package com.q.q.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.Appointment;

// Adelantado de la Fase 6 con lo mínimo para la disponibilidad; el resto de consultas llega con las citas
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	// Citas activas (PENDING y CONFIRMED) del profesional que solapan [start, end): RN-02. Una cita que
	// termina justo en start, o empieza justo en end, no solapa. Usa el índice idx_appt_professional_time
	@Query("""
			select a from Appointment a
			where a.professional.id = :professionalId
			  and a.status in (com.q.q.entity.enums.AppointmentStatus.PENDING,
			                   com.q.q.entity.enums.AppointmentStatus.CONFIRMED)
			  and a.startTime < :end and a.endTime > :start
			""")
	List<Appointment> findActiveOverlapping(@Param("professionalId") Long professionalId,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
