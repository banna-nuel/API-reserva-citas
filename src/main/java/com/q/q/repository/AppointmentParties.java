package com.q.q.repository;

// Profesional y cliente de una cita, leídos sin cargarla: reschedule los bloquea antes que a la propia cita
public record AppointmentParties(Long id, Long professionalId, Long clientId) {}
