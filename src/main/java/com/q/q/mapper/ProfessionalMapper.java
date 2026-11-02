package com.q.q.mapper;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.q.q.dto.response.ProfessionalAdminResponse;
import com.q.q.dto.response.ProfessionalResponse;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;

@Component
public class ProfessionalMapper {

	private static final Comparator<ServiceOffering> BY_ID = Comparator.comparing(ServiceOffering::getId);

	public ProfessionalResponse toPublicResponse(Professional professional) {
		List<ProfessionalResponse.OfferedService> services = professional.getServices().stream()
				.filter(ServiceOffering::isActive)                    // para el público, un servicio inactivo no existe
				.sorted(BY_ID)
				.map(s -> new ProfessionalResponse.OfferedService(s.getId(), s.getName(), s.getDurationMinutes(),
						s.getPrice()))
				.toList();
		return new ProfessionalResponse(professional.getId(), professional.getUser().getFullName(),
				professional.getSpecialty(), professional.getBio(), services);
	}

	public ProfessionalAdminResponse toAdminResponse(Professional professional) {
		User user = professional.getUser();
		List<ProfessionalAdminResponse.OfferedService> services = professional.getServices().stream()
				.sorted(BY_ID)
				.map(s -> new ProfessionalAdminResponse.OfferedService(s.getId(), s.getName(), s.getDurationMinutes(),
						s.getPrice(), s.isActive()))
				.toList();
		return new ProfessionalAdminResponse(professional.getId(), user.getId(), user.getFullName(), user.getEmail(),
				user.getPhone(), professional.getSpecialty(), professional.getBio(), professional.isActive(),
				user.isActive(), services, professional.getCreatedAt());
	}

}
