package com.q.q.service;

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

}
