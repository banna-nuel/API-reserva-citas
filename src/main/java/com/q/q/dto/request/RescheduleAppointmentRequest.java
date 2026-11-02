package com.q.q.dto.request;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

import jakarta.validation.constraints.NotNull;

// Body de PATCH /appointments/{id}/reschedule. startTime sigue las reglas de la creación: hora local, sin zona
public record RescheduleAppointmentRequest(@NotNull @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime startTime) {}
