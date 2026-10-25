package com.q.q.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceOfferingResponse(Long id, String name, String description, int durationMinutes,
		BigDecimal price, boolean active, LocalDateTime createdAt) {}
