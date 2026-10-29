package com.q.q.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.Professional;

import jakarta.persistence.LockModeType;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

	// Perfil, usuario y servicios en una sola consulta
	@EntityGraph(attributePaths = { "user", "services" })
	Optional<Professional> findWithDetailsById(Long id);

	@EntityGraph(attributePaths = "user")
	Optional<Professional> findWithUserById(Long id);

	// SELECT ... FOR UPDATE: serializa los cambios sobre un mismo profesional (p. ej. reemplazar su horario)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Professional p where p.id = :id")
	Optional<Professional> findByIdForUpdate(@Param("id") Long id);

	// Un parámetro en null desactiva ese filtro (active es el perfil; userActive, su usuario). El usuario llega con
	// un join fetch (es *-a-uno: la paginación sigue en SQL) y los servicios de toda la página, en una consulta
	// más gracias a @BatchSize
	@Query(value = """
			select p from Professional p join fetch p.user u
			where (:active is null or p.active = :active)
			  and (:userActive is null or u.active = :userActive)
			  and (:serviceId is null or exists (
			        select 1 from Professional p2 join p2.services s
			        where p2.id = p.id and s.id = :serviceId
			          and (:serviceActive is null or s.active = :serviceActive)))
			""",
			countQuery = """
			select count(p) from Professional p join p.user u
			where (:active is null or p.active = :active)
			  and (:userActive is null or u.active = :userActive)
			  and (:serviceId is null or exists (
			        select 1 from Professional p2 join p2.services s
			        where p2.id = p.id and s.id = :serviceId
			          and (:serviceActive is null or s.active = :serviceActive)))
			""")
	Page<Professional> search(@Param("active") Boolean active, @Param("userActive") Boolean userActive,
			@Param("serviceId") Long serviceId, @Param("serviceActive") Boolean serviceActive, Pageable pageable);

}
