package com.q.q.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Vista del ADMIN: incluye los datos de contacto del usuario y todos los servicios asignados, activos o no
public record ProfessionalAdminResponse(Long id, Long userId, String fullName, String email, String phone,
		String specialty, String bio, boolean active, List<OfferedService> services, LocalDateTime createdAt)
		implements ProfessionalView {

	public record OfferedService(Long id, String name, int durationMinutes, BigDecimal price, boolean active) {}

}
