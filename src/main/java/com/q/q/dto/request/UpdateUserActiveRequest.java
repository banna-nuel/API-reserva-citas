package com.q.q.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateUserActiveRequest(@NotNull Boolean active) {}
