package com.q.q.dto.response;

import java.math.BigDecimal;
import java.util.List;

// Vista pública: sin userId, email, phone, active ni createdAt, y solo con los servicios activos
public record ProfessionalResponse(Long id, String fullName, String specialty, String bio,
		List<OfferedService> services) implements ProfessionalView {

	public record OfferedService(Long id, String name, int durationMinutes, BigDecimal price) {}

}
