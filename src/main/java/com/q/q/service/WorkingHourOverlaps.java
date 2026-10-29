package com.q.q.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.exception.ApiError;

/**
 * Solapes entre bloques del mismo día de un horario laboral ya validado. A y B se solapan si
 * inicioA < finB y finA > inicioB: los contiguos (09:00-12:00 y 12:00-14:00) no cuentan y un bloque
 * repetido sí. Devuelve un elemento de details por cada par en conflicto, de lunes a domingo.
 */
final class WorkingHourOverlaps {

	private static final Comparator<WorkingHourBlockRequest> BY_START =
			Comparator.comparing(WorkingHourBlockRequest::start).thenComparing(WorkingHourBlockRequest::end);

	private WorkingHourOverlaps() {
	}

	static List<ApiError.FieldIssue> find(List<WorkingHourBlockRequest> blocks) {
		Map<DayOfWeek, List<WorkingHourBlockRequest>> byDay = blocks.stream()
				.collect(Collectors.groupingBy(WorkingHourBlockRequest::day, () -> new EnumMap<>(DayOfWeek.class),
						Collectors.toList()));
		List<ApiError.FieldIssue> conflicts = new ArrayList<>();
		byDay.forEach((day, sameDay) -> {
			List<WorkingHourBlockRequest> sorted = sameDay.stream().sorted(BY_START).toList();
			for (int i = 0; i < sorted.size(); i++) {
				for (int j = i + 1; j < sorted.size(); j++) {           // como mucho 5 por día: basta comparar pares
					WorkingHourBlockRequest a = sorted.get(i);
					WorkingHourBlockRequest b = sorted.get(j);
					if (a.start().isBefore(b.end()) && a.end().isAfter(b.start())) {
						conflicts.add(new ApiError.FieldIssue("blocks", day + ": " + a.startTime() + "-" + a.endTime()
								+ " se solapa con " + b.startTime() + "-" + b.endTime()));
					}
				}
			}
		});
		return conflicts;
	}

}
