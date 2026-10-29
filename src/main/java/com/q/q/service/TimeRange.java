package com.q.q.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.q.q.entity.WorkingHour;

/** Tramo continuo de trabajo dentro de un día: de start (incluido) a end (excluido). */
public record TimeRange(LocalTime start, LocalTime end) {

	// RN-03: ordena los bloques de un mismo día y fusiona los contiguos (09:00-12:00 + 12:00-14:00 -> 09:00-14:00),
	// para que un servicio pueda empezar en uno y terminar en el otro. El PUT del horario no admite solapes,
	// pero si los hubiera también quedarían fusionados
	public static List<TimeRange> merge(Collection<WorkingHour> blocks) {
		List<WorkingHour> sorted = blocks.stream().sorted(Comparator.comparing(WorkingHour::getStartTime)).toList();
		List<TimeRange> merged = new ArrayList<>();
		for (WorkingHour block : sorted) {
			TimeRange last = merged.isEmpty() ? null : merged.getLast();
			if (last != null && !block.getStartTime().isAfter(last.end())) {
				LocalTime end = block.getEndTime().isAfter(last.end()) ? block.getEndTime() : last.end();
				merged.set(merged.size() - 1, new TimeRange(last.start(), end));
			} else {
				merged.add(new TimeRange(block.getStartTime(), block.getEndTime()));
			}
		}
		return merged;
	}

	// RN-03: [from, to) cabe completo en este tramo el día de from. Una cita que pasa de medianoche nunca cabe
	public boolean fits(LocalDateTime from, LocalDateTime to) {
		LocalDate day = from.toLocalDate();
		return !from.isBefore(day.atTime(start)) && !to.isAfter(day.atTime(end));
	}

	// Sección 10.5: los horarios que el tramo ofrece en date, anclados a su inicio cada stepMinutes y que caben
	// completos, antes de mirar citas y anticipación. Los comparten la disponibilidad y la creación de citas
	public List<TimeSlot> candidateSlots(LocalDate date, int durationMinutes, int stepMinutes) {
		List<TimeSlot> slots = new ArrayList<>();
		LocalDateTime rangeEnd = date.atTime(end);
		// LocalDateTime y no LocalTime: así 23:30 + 60 min no "da la vuelta" a las 00:30
		LocalDateTime slotStart = date.atTime(start);
		while (!slotStart.plusMinutes(durationMinutes).isAfter(rangeEnd)) {
			slots.add(new TimeSlot(slotStart, slotStart.plusMinutes(durationMinutes)));
			slotStart = slotStart.plusMinutes(stepMinutes);
		}
		return slots;
	}

}
