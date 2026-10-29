package com.q.q.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Vista del ADMIN: incluye los datos de contacto del usuario, el estado del perfil (active) y el de su usuario
// (userActive), y todos los servicios asignados, activos o no
public record ProfessionalAdminResponse(Long id, Long userId, String fullName, String email, String phone,
		String specialty, String bio, boolean active, boolean userActive, List<OfferedService> services,
		LocalDateTime createdAt) implements ProfessionalView {

	public record OfferedService(Long id, String name, int durationMinutes, BigDecimal price, boolean active) {}

}
