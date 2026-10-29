package com.q.q.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

// Body de PUT /professionals/{id}/services: el conjunto completo ([] lo deja sin servicios)
public record AssignServicesRequest(@NotNull List<@NotNull Long> serviceIds) {}
