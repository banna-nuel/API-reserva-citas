package com.q.q.service;

import java.time.LocalDateTime;

/** Intervalo [start, end) en la hora local del negocio. */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

	// RN-02: solape estricto. Dos intervalos que solo se tocan (uno termina cuando empieza el otro) no se solapan
	public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
		return start.isBefore(otherEnd) && end.isAfter(otherStart);
	}

}
