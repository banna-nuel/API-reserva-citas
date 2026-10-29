package com.q.q.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.Professional;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

	// Perfil, usuario y servicios en una sola consulta
	@EntityGraph(attributePaths = { "user", "services" })
	Optional<Professional> findWithDetailsById(Long id);

	// Un parámetro en null desactiva ese filtro. El usuario llega con un join fetch (es *-a-uno: la paginación
	// sigue en SQL) y los servicios de toda la página, en una consulta más gracias a @BatchSize
	@Query(value = """
			select p from Professional p join fetch p.user
			where (:active is null or p.active = :active)
			  and (:serviceId is null or exists (
			        select 1 from Professional p2 join p2.services s
			        where p2.id = p.id and s.id = :serviceId
			          and (:serviceActive is null or s.active = :serviceActive)))
			""",
			countQuery = """
			select count(p) from Professional p
			where (:active is null or p.active = :active)
			  and (:serviceId is null or exists (
			        select 1 from Professional p2 join p2.services s
			        where p2.id = p.id and s.id = :serviceId
			          and (:serviceActive is null or s.active = :serviceActive)))
			""")
	Page<Professional> search(@Param("active") Boolean active, @Param("serviceId") Long serviceId,
			@Param("serviceActive") Boolean serviceActive, Pageable pageable);

}
