package com.q.q.dto.request;

import jakarta.validation.constraints.NotNull;

// Body de los PATCH .../{id}/active (usuarios y servicios)
public record UpdateActiveRequest(@NotNull Boolean active) {}
