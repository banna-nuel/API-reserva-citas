package com.q.q.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.config.AppProperties;
import com.q.q.dto.response.AvailabilityResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.repository.AppointmentRepository;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

	private final ProfessionalRepository professionals;
	private final ServiceOfferingRepository services;
	private final WorkingHourService workingHours;
	private final AppointmentRepository appointments;
	private final AppProperties props;
	private final Clock clock;

	@Transactional(readOnly = true)
	public AvailabilityResponse find(Long professionalId, LocalDate date, Long serviceId) {
		// 404 para todos, también para el ADMIN: con un profesional inactivo (perfil o usuario) no se puede reservar
		Professional professional = professionals.findWithDetailsById(professionalId)
				.filter(Professional::isPubliclyVisible)
				.orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado"));
		// Si el profesional lo ofrece, el servicio ya llegó con él y no hace falta otra consulta
		ServiceOffering service = services.findById(serviceId)
				.filter(ServiceOffering::isActive)
				.orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
		if (!professional.offers(service)) {
			throw new BusinessRuleException("SERVICE_NOT_OFFERED", "El profesional no ofrece este servicio");   // RN-05
		}
		int duration = service.getDurationMinutes();
		return new AvailabilityResponse(professionalId, serviceId, date, duration,
				freeSlots(professionalId, date, duration));
	}

	// Algoritmo de la sección 10.5
	private List<AvailabilityResponse.Slot> freeSlots(Long professionalId, LocalDate date, int durationMinutes) {
		AppProperties.Booking booking = props.booking();
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime earliest = now.plusMinutes(booking.minNoticeMinutes());       // RN-04: anticipación mínima
		LocalDateTime latest = now.plusDays(booking.maxDaysAhead());               // RN-04: tope hacia adelante
		if (date.isBefore(earliest.toLocalDate()) || date.isAfter(latest.toLocalDate())) {
			return List.of();                        // fecha pasada o tras el tope: ningún slot cumpliría RN-04
		}
		List<TimeRange> ranges = workingHours.workingRanges(professionalId, date.getDayOfWeek());
		if (ranges.isEmpty()) {
			return List.of();
		}
		// El día completo, para incluir también las citas que empiezan la víspera y cruzan la medianoche
		LocalDateTime dayStart = date.atStartOfDay();
		List<Appointment> busy = appointments.findActiveOverlapping(professionalId, dayStart, dayStart.plusDays(1));

		List<AvailabilityResponse.Slot> slots = new ArrayList<>();
		for (TimeRange range : ranges) {                // ordenados y sin solapes: los slots salen en orden
			LocalDateTime rangeEnd = date.atTime(range.end());
			// LocalDateTime y no LocalTime: así 23:30 + 60 min no "da la vuelta" a las 00:30
			LocalDateTime start = date.atTime(range.start());
			LocalDateTime end = start.plusMinutes(durationMinutes);
			while (!end.isAfter(rangeEnd)) {
				if (!start.isBefore(earliest) && !start.isAfter(latest) && isFree(busy, start, end)) {
					slots.add(new AvailabilityResponse.Slot(start, end));
				}
				start = start.plusMinutes(booking.slotStepMinutes());
				end = start.plusMinutes(durationMinutes);
			}
		}
		return slots;
	}

	// RN-02: hay solape si inicio < finCita y fin > inicioCita; una cita que termina a las 10:00 deja libre el slot
	// de las 10:00
	private static boolean isFree(List<Appointment> busy, LocalDateTime start, LocalDateTime end) {
		return busy.stream().noneMatch(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));
	}

}
