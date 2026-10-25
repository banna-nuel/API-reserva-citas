package com.q.q.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.ServiceOffering;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

	// Un parámetro en null desactiva ese filtro. namePattern llega en minúsculas y escapado con '!'
	@Query("""
			select s from ServiceOffering s
			where (:active is null or s.active = :active)
			  and (:namePattern is null or lower(s.name) like :namePattern escape '!')
			""")
	Page<ServiceOffering> search(@Param("active") Boolean active, @Param("namePattern") String namePattern,
			Pageable pageable);

}
