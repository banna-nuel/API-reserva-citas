package com.q.q.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.q.q.entity.WorkingHour;

public interface WorkingHourRepository extends JpaRepository<WorkingHour, Long> {

	List<WorkingHour> findByProfessionalId(Long professionalId);

	// Un solo DELETE (la versión derivada de Spring Data cargaría y borraría las filas una a una)
	@Modifying
	@Query("delete from WorkingHour w where w.professional.id = :professionalId")
	void deleteByProfessionalId(@Param("professionalId") Long professionalId);

}
