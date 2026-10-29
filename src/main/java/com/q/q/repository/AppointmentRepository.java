package com.q.q.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

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

	// RN-02 al reservar: cuántas citas activas del profesional solapan [start, end). excludeId deja fuera la propia
	// cita al reprogramar (Fase 6b); al crear va en null
	@Query("""
			select count(a) from Appointment a
			where a.professional.id = :professionalId
			  and a.status in (com.q.q.entity.enums.AppointmentStatus.PENDING,
			                   com.q.q.entity.enums.AppointmentStatus.CONFIRMED)
			  and a.startTime < :end and a.endTime > :start
			  and (:excludeId is null or a.id <> :excludeId)
			""")
	long countOverlapping(@Param("professionalId") Long professionalId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

	// RN-06: lo mismo para las citas activas del cliente, con cualquier profesional. Usa idx_appt_client_time
	@Query("""
			select count(a) from Appointment a
			where a.client.id = :clientId
			  and a.status in (com.q.q.entity.enums.AppointmentStatus.PENDING,
			                   com.q.q.entity.enums.AppointmentStatus.CONFIRMED)
			  and a.startTime < :end and a.endTime > :start
			  and (:excludeId is null or a.id <> :excludeId)
			""")
	long countOverlappingForClient(@Param("clientId") Long clientId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

	// Agenda (GET /appointments): cliente, profesional con su usuario y servicio llegan en la misma consulta. Todas
	// las asociaciones son *-a-uno, así que la paginación sigue en SQL; el count no carga el grafo
	@Override
	@EntityGraph(attributePaths = { "client", "professional", "professional.user", "service" })
	Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "client", "professional", "professional.user", "service" })
	Optional<Appointment> findWithDetailsById(Long id);

}
