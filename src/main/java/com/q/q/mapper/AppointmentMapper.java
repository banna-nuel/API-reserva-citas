package com.q.q.mapper;

import org.springframework.stereotype.Component;

import com.q.q.dto.response.AppointmentResponse;
import com.q.q.entity.Appointment;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;

@Component
public class AppointmentMapper {

	// Espera cliente, profesional con su usuario y servicio ya cargados (AppointmentRepository los trae juntos)
	public AppointmentResponse toResponse(Appointment appointment) {
		User client = appointment.getClient();
		Professional professional = appointment.getProfessional();
		ServiceOffering service = appointment.getService();
		return new AppointmentResponse(appointment.getId(),
				new AppointmentResponse.ClientSummary(client.getId(), client.getFullName()),
				new AppointmentResponse.ProfessionalSummary(professional.getId(), professional.getUser().getFullName()),
				new AppointmentResponse.ServiceSummary(service.getId(), service.getName(), service.getDurationMinutes()),
				appointment.getStartTime(), appointment.getEndTime(), appointment.getStatus(), appointment.getPrice(),
				appointment.getNotes(), appointment.getCancelReason(), appointment.getCreatedAt());
	}

}
