package com.q.q.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.dto.request.WorkingHoursRequest;
import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.entity.Professional;
import com.q.q.entity.WorkingHour;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ApiError;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.WorkingHourMapper;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.WorkingHourRepository;
import com.q.q.security.AuthUser;
import com.q.q.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkingHourService {

	private final ProfessionalRepository professionals;
	private final WorkingHourRepository workingHours;
	private final WorkingHourMapper mapper;

	@Transactional(readOnly = true)
	public WorkingHoursResponse get(Long professionalId) {
		Professional professional = professionals.findWithUserById(professionalId).orElseThrow(WorkingHourService::notFound);
		if (!CurrentUser.isAdmin() && !professional.isPubliclyVisible()) {
			throw notFound();                        // misma visibilidad que el detalle del profesional
		}
		return mapper.toResponse(professionalId, workingHours.findByProfessionalId(professionalId));
	}

	// Reemplaza el horario completo en una transacción: si algo falla, el anterior queda intacto
	@Transactional
	public WorkingHoursResponse replace(Long professionalId, WorkingHoursRequest req) {
		AuthUser actor = CurrentUser.get().orElseThrow(WorkingHourService::notAllowed);
		if (actor.role() != Role.ADMIN && actor.role() != Role.PROFESSIONAL) {
			throw notAllowed();                      // defensa en profundidad: SecurityConfig y @PreAuthorize ya lo exigen
		}
		// SELECT ... FOR UPDATE: dos reemplazos simultáneos del mismo profesional se aplican uno detrás del otro
		Professional professional = professionals.findByIdForUpdate(professionalId)
				.orElseThrow(WorkingHourService::notFound);
		if (actor.role() == Role.PROFESSIONAL) {
			if (!professional.getUser().getId().equals(actor.id())) {
				throw new ForbiddenOperationException("Solo puedes editar tu propio horario laboral");
			}
			if (!professional.isActive()) {
				throw new ForbiddenOperationException("Tu perfil profesional está inactivo");
			}
		}
		List<ApiError.FieldIssue> overlaps = WorkingHourOverlaps.find(req.blocks());
		if (!overlaps.isEmpty()) {
			throw new BusinessRuleException("WORKING_HOURS_OVERLAP", "Hay bloques del mismo día que se solapan",
					overlaps);
		}
		workingHours.deleteByProfessionalId(professionalId);
		List<WorkingHour> saved = workingHours.saveAll(req.blocks().stream()
				.map(block -> toEntity(professional, block))
				.toList());
		return mapper.toResponse(professionalId, saved);
	}

	private static WorkingHour toEntity(Professional professional, WorkingHourBlockRequest block) {
		WorkingHour workingHour = new WorkingHour();
		workingHour.setProfessional(professional);
		workingHour.setDayOfWeek(block.day());
		workingHour.setStartTime(block.start());
		workingHour.setEndTime(block.end());
		return workingHour;
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Profesional no encontrado");
	}

	private static ForbiddenOperationException notAllowed() {
		return new ForbiddenOperationException("Solo un administrador o el propio profesional pueden editar el horario");
	}

}
