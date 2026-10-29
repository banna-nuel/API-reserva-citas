package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.dto.request.WorkingHourBlockRequest;
import com.q.q.dto.request.WorkingHoursRequest;
import com.q.q.dto.response.WorkingHoursResponse;
import com.q.q.entity.Professional;
import com.q.q.entity.User;
import com.q.q.entity.WorkingHour;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.WorkingHourMapper;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.WorkingHourRepository;
import com.q.q.security.AuthUser;

@ExtendWith(MockitoExtension.class)
class WorkingHourServiceTest {

	private static final Long OWNER_USER_ID = 50L;
	private static final WorkingHoursRequest MONDAY_MORNING =
			new WorkingHoursRequest(List.of(new WorkingHourBlockRequest("MONDAY", "09:00", "12:00")));

	@Mock
	private ProfessionalRepository professionals;

	@Mock
	private WorkingHourRepository workingHours;

	private WorkingHourService service;

	@BeforeEach
	void setUp() {
		service = new WorkingHourService(professionals, workingHours, new WorkingHourMapper());
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// ---------- quién puede reemplazar ----------

	@Test
	void onlyAdminsAndProfessionalsCanReplaceASchedule() {
		for (Role role : new Role[] { null, Role.CLIENT }) {                  // null = anónimo
			authenticateAs(role, 1L);
			assertThatThrownBy(() -> service.replace(5L, MONDAY_MORNING)).isInstanceOf(ForbiddenOperationException.class);
		}
		verifyNoInteractions(professionals, workingHours);
	}

	@Test
	void anotherProfessionalCannotReplaceTheSchedule() {
		authenticateAs(Role.PROFESSIONAL, 99L);
		when(professionals.findByIdForUpdate(5L)).thenReturn(Optional.of(professional(5L, true, true)));

		assertThatThrownBy(() -> service.replace(5L, MONDAY_MORNING))
				.isInstanceOf(ForbiddenOperationException.class)
				.extracting("code").isEqualTo("FORBIDDEN_OPERATION");
		verifyNoInteractions(workingHours);
	}

	@Test
	void anOwnerWithAnInactiveProfileCannotReplaceIt() {
		authenticateAs(Role.PROFESSIONAL, OWNER_USER_ID);
		when(professionals.findByIdForUpdate(5L)).thenReturn(Optional.of(professional(5L, false, true)));

		assertThatThrownBy(() -> service.replace(5L, MONDAY_MORNING)).isInstanceOf(ForbiddenOperationException.class);
		verifyNoInteractions(workingHours);
	}

	@Test
	void theOwnerReplacesTheWholeScheduleAfterLockingTheProfessional() {
		authenticateAs(Role.PROFESSIONAL, OWNER_USER_ID);
		Professional professional = professional(5L, true, true);
		when(professionals.findByIdForUpdate(5L)).thenReturn(Optional.of(professional));
		when(workingHours.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		WorkingHoursResponse response = service.replace(5L, new WorkingHoursRequest(List.of(
				new WorkingHourBlockRequest("TUESDAY", "09:00", "12:00"),
				new WorkingHourBlockRequest("MONDAY", "14:00", "18:00"),
				new WorkingHourBlockRequest("MONDAY", "09:00", "12:00"))));

		// Primero el bloqueo, luego borrar el horario anterior y guardar el nuevo
		InOrder order = inOrder(professionals, workingHours);
		order.verify(professionals).findByIdForUpdate(5L);
		order.verify(workingHours).deleteByProfessionalId(5L);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WorkingHour>> saved = ArgumentCaptor.forClass(List.class);
		order.verify(workingHours).saveAll(saved.capture());
		assertThat(saved.getValue()).allSatisfy(w -> assertThat(w.getProfessional()).isSameAs(professional));
		assertThat(saved.getValue()).extracting(WorkingHour::getDayOfWeek, WorkingHour::getStartTime, WorkingHour::getEndTime)
				.containsExactlyInAnyOrder(tuple(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
						tuple(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0)),
						tuple(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

		assertThat(response.professionalId()).isEqualTo(5L);
		assertThat(response.blocks()).extracting(b -> b.dayOfWeek() + " " + b.startTime() + "-" + b.endTime())
				.containsExactly("MONDAY 09:00-12:00", "MONDAY 14:00-18:00", "TUESDAY 09:00-12:00");
	}

	@Test
	void anAdminCanReplaceTheScheduleOfAnInactiveProfile() {
		authenticateAs(Role.ADMIN, 1L);
		when(professionals.findByIdForUpdate(5L)).thenReturn(Optional.of(professional(5L, false, false)));
		when(workingHours.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.replace(5L, new WorkingHoursRequest(List.of())).blocks()).isEmpty();
		verify(workingHours).deleteByProfessionalId(5L);
	}

	@Test
	void overlappingBlocksAreRejectedWithoutTouchingTheCurrentSchedule() {
		authenticateAs(Role.ADMIN, 1L);
		when(professionals.findByIdForUpdate(5L)).thenReturn(Optional.of(professional(5L, true, true)));

		assertThatThrownBy(() -> service.replace(5L, new WorkingHoursRequest(List.of(
				new WorkingHourBlockRequest("MONDAY", "09:00", "12:00"),
				new WorkingHourBlockRequest("MONDAY", "11:00", "13:00")))))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
					assertThat(ex.getCode()).isEqualTo("WORKING_HOURS_OVERLAP");
					assertThat(ex.getDetails()).hasSize(1);
				});
		verify(workingHours, never()).deleteByProfessionalId(any());
		verify(workingHours, never()).saveAll(anyList());
	}

	@Test
	void unknownProfessionalIsNotFound() {
		authenticateAs(Role.ADMIN, 1L);
		when(professionals.findByIdForUpdate(99L)).thenReturn(Optional.empty());
		when(professionals.findWithUserById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.replace(99L, MONDAY_MORNING)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(workingHours);
	}

	// ---------- lectura ----------

	@Test
	void theScheduleIsHiddenLikeTheProfessionalForNonAdmins() {
		when(professionals.findWithUserById(5L)).thenReturn(Optional.of(professional(5L, false, true)));
		when(professionals.findWithUserById(6L)).thenReturn(Optional.of(professional(6L, true, false)));
		when(workingHours.findByProfessionalId(any())).thenReturn(List.of());

		for (Role role : new Role[] { null, Role.CLIENT, Role.PROFESSIONAL }) {
			authenticateAs(role, OWNER_USER_ID);                       // ni siquiera el dueño
			assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ResourceNotFoundException.class);   // perfil inactivo
			assertThatThrownBy(() -> service.get(6L)).isInstanceOf(ResourceNotFoundException.class);   // usuario inactivo
		}
		authenticateAs(Role.ADMIN, 1L);
		assertThat(service.get(5L).blocks()).isEmpty();
		assertThat(service.get(6L).professionalId()).isEqualTo(6L);
	}

	// ---------- utilidades ----------

	private static void authenticateAs(Role role, Long userId) {
		SecurityContextHolder.clearContext();
		if (role != null) {
			AuthUser user = new AuthUser(userId, "usuario@mail.test", "hash-sintetico", role, true);
			SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		}
	}

	// Perfil cuyo usuario es OWNER_USER_ID
	private static Professional professional(Long id, boolean active, boolean userActive) {
		User user = new User();
		user.setId(OWNER_USER_ID);
		user.setRole(Role.PROFESSIONAL);
		user.setActive(userActive);
		Professional professional = new Professional();
		professional.setId(id);
		professional.setUser(user);
		professional.setSpecialty("Fisioterapia");
		professional.setActive(active);
		return professional;
	}

}
