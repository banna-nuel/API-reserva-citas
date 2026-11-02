package com.q.q.mapper;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.entity.WorkingHour;

@Component
public class WorkingHourMapper {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
	// DayOfWeek se compara por su orden en el enum: de MONDAY a SUNDAY
	private static final Comparator<WorkingHour> BY_DAY_AND_START =
			Comparator.comparing(WorkingHour::getDayOfWeek).thenComparing(WorkingHour::getStartTime);

	public WorkingHoursResponse toResponse(Long professionalId, List<WorkingHour> workingHours) {
		List<WorkingHoursResponse.Block> blocks = workingHours.stream()
				.sorted(BY_DAY_AND_START)
				.map(w -> new WorkingHoursResponse.Block(w.getDayOfWeek(), HH_MM.format(w.getStartTime()),
						HH_MM.format(w.getEndTime())))
				.toList();
		return new WorkingHoursResponse(professionalId, blocks);
	}

}
