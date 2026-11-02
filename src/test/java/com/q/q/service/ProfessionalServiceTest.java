package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.q.q.dto.request.AssignServicesRequest;
import com.q.q.dto.request.CreateProfessionalRequest;
import com.q.q.dto.request.UpdateProfessionalRequest;
import com.q.q.dto.response.ProfessionalAdminResponse;
import com.q.q.dto.response.ProfessionalResponse;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ApiError;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.ProfessionalMapper;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;

@ExtendWith(MockitoExtension.class)
class ProfessionalServiceTest {

	private static final Role[] NON_ADMINS = { null, Role.CLIENT, Role.PROFESSIONAL };   // null = anónimo
	private static final String PASSWORD = "clave-secreta-1";

	@Mock
	private ProfessionalRepository professionals;

	@Mock
	private UserRepository users;

	@Mock
	private ServiceOfferingRepository services;

	@Mock
	private PasswordEncoder passwordEncoder;

	private ProfessionalService service;

	@BeforeEach
	void setUp() {
		service = new ProfessionalService(professionals, users, services, passwordEncoder, new ProfessionalMapper());
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// El servicio vuelve a comprobar el rol aunque SecurityConfig y @PreAuthorize ya lo hagan

	@Test
	void writesRejectAnonymousAndNonAdminUsers() {
		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThatThrownBy(() -> service.create(createRequest("ana@mail.test", List.of())))
					.isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.update(1L, new UpdateProfessionalRequest("Pediatría", null)))
					.isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.setActive(1L, false)).isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.replaceServices(1L, new AssignServicesRequest(List.of(1L))))
					.isInstanceOf(ForbiddenOperationException.class);
		}
		verifyNoInteractions(professionals, users, services, passwordEncoder);
	}

	// ---------- alta ----------

	@Test
	void createBuildsAnActiveProfessionalUserWithNormalizedEmailAndHashedPassword() {
		authenticateAs(Role.ADMIN);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-sintetico");
		when(services.findAllById(Set.of(1L, 2L))).thenReturn(List.of(offering(2L, true), offering(1L, true)));
		when(professionals.save(any(Professional.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProfessionalAdminResponse created = service.create(new CreateProfessionalRequest("  Carlos Ruiz  ",
				"  Carlos@Mail.TEST ", PASSWORD, "3001112233", "  Fisioterapia  ", "   ", List.of(2L, 1L, 2L)));

		ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
		verify(users).existsByEmail("carlos@mail.test");
		verify(users).saveAndFlush(savedUser.capture());
		User user = savedUser.getValue();
		assertThat(user.getEmail()).isEqualTo("carlos@mail.test");                // RN-11
		assertThat(user.getFullName()).isEqualTo("Carlos Ruiz");
		assertThat(user.getPasswordHash()).isEqualTo("hash-sintetico");
		assertThat(user.getRole()).isEqualTo(Role.PROFESSIONAL);
		assertThat(user.isActive()).isTrue();

		assertThat(created.email()).isEqualTo("carlos@mail.test");
		assertThat(created.specialty()).isEqualTo("Fisioterapia");
		assertThat(created.bio()).isNull();
		assertThat(created.active()).isTrue();
		assertThat(created.services()).extracting(ProfessionalAdminResponse.OfferedService::id).containsExactly(1L, 2L);
		assertThat(created.toString()).doesNotContain(PASSWORD, "hash-sintetico");
	}

	@Test
	void createRejectsAUsedEmailWithoutWriting() {
		authenticateAs(Role.ADMIN);
		when(users.existsByEmail("ana@mail.test")).thenReturn(true);

		assertThatThrownBy(() -> service.create(createRequest("ANA@mail.test", List.of(1L))))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("EMAIL_ALREADY_USED");
		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(professionals, services, passwordEncoder);
	}

	@Test
	void uniqueIndexViolationFromARaceIsReportedAs409AndNoProfileIsSaved() {
		authenticateAs(Role.ADMIN);
		when(users.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate entry for key uq_users_email"));

		assertThatThrownBy(() -> service.create(createRequest("ana@mail.test", List.of())))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("EMAIL_ALREADY_USED");
		verify(professionals, never()).save(any());
	}

	@Test
	void unknownOrInactiveServiceIdsAreRejectedBeforeWritingAnything() {
		authenticateAs(Role.ADMIN);
		when(services.findAllById(Set.of(1L, 3L, 99L))).thenReturn(List.of(offering(1L, true), offering(3L, false)));

		assertThatThrownBy(() -> service.create(createRequest("ana@mail.test", List.of(99L, 3L, 1L, 99L))))
				.isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
					assertThat(ex.getCode()).isEqualTo("INVALID_SERVICE_IDS");
					assertThat(ex.getDetails()).extracting(ApiError.FieldIssue::field).containsOnly("serviceIds");
					assertThat(ex.getDetails()).extracting(ApiError.FieldIssue::message).containsExactly(
							"El servicio 3 no existe o está inactivo", "El servicio 99 no existe o está inactivo");
				});
		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(professionals, passwordEncoder);
	}

	// ---------- edición ----------

	@Test
	void updateChangesOnlySpecialtyAndBio() {
		authenticateAs(Role.ADMIN);
		when(professionals.findWithDetailsById(5L)).thenReturn(Optional.of(professional(5L, false)));

		ProfessionalAdminResponse updated = service.update(5L, new UpdateProfessionalRequest("  Pediatría  ", null));

		assertThat(updated.specialty()).isEqualTo("Pediatría");
		assertThat(updated.bio()).isNull();
		assertThat(updated.active()).isFalse();
		assertThat(updated.email()).isEqualTo("pro@mail.test");
		verifyNoInteractions(users, services);
	}

	@Test
	void setActiveTogglesTheProfileButNotItsUser() {
		authenticateAs(Role.ADMIN);
		Professional existing = professional(5L, true);
		when(professionals.findWithDetailsById(5L)).thenReturn(Optional.of(existing));

		assertThat(service.setActive(5L, false).active()).isFalse();
		assertThat(existing.getUser().isActive()).isTrue();
		verifyNoInteractions(users);
	}

	@Test
	void replaceServicesSwapsTheWholeSetIgnoringDuplicatesAndAllowsAnEmptyList() {
		authenticateAs(Role.ADMIN);
		Professional existing = professional(5L, true, offering(1L, true));
		when(professionals.findWithDetailsById(5L)).thenReturn(Optional.of(existing));
		when(services.findAllById(Set.of(2L, 3L))).thenReturn(List.of(offering(3L, true), offering(2L, true)));

		assertThat(service.replaceServices(5L, new AssignServicesRequest(List.of(3L, 2L, 3L))).services())
				.extracting(ProfessionalAdminResponse.OfferedService::id).containsExactly(2L, 3L);
		assertThat(service.replaceServices(5L, new AssignServicesRequest(List.of())).services()).isEmpty();
		assertThat(existing.getServices()).isEmpty();
		verify(services, times(1)).findAllById(any());                   // la lista vacía no consulta
	}

	@Test
	void replaceServicesWithAnInvalidIdChangesNothing() {
		authenticateAs(Role.ADMIN);
		Professional existing = professional(5L, true, offering(1L, true));
		when(professionals.findWithDetailsById(5L)).thenReturn(Optional.of(existing));
		when(services.findAllById(Set.of(2L, 7L))).thenReturn(List.of(offering(2L, true)));

		assertThatThrownBy(() -> service.replaceServices(5L, new AssignServicesRequest(List.of(2L, 7L))))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("INVALID_SERVICE_IDS");
		assertThat(existing.getServices()).extracting(ServiceOffering::getId).containsExactly(1L);
	}

	@Test
	void unknownProfessionalIsNotFoundBeforeLookingAtTheServices() {
		authenticateAs(Role.ADMIN);
		when(professionals.findWithDetailsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.update(99L, new UpdateProfessionalRequest("Pediatría", null)))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.setActive(99L, true)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.replaceServices(99L, new AssignServicesRequest(List.of(1L))))
				.isInstanceOf(ResourceNotFoundException.class);
		verifyNoInteractions(services);
	}

	// ---------- lecturas ----------

	@Test
	void nonAdminsOnlyListActiveProfessionalsWhateverTheyAskFor() {
		when(professionals.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			service.list(false, 7L, PageRequest.of(0, 20));
			service.list(null, null, PageRequest.of(0, 20));
		}

		// Perfil y usuario activos y, al filtrar por servicio, solo si ese servicio está activo
		verify(professionals, times(3))
				.search(eq(Boolean.TRUE), eq(Boolean.TRUE), eq(7L), eq(Boolean.TRUE), any(Pageable.class));
		verify(professionals, times(3))
				.search(eq(Boolean.TRUE), eq(Boolean.TRUE), isNull(), eq(Boolean.TRUE), any(Pageable.class));
	}

	@Test
	void adminListsAllOrFiltersByActive() {
		authenticateAs(Role.ADMIN);
		when(professionals.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, 7L, PageRequest.of(0, 20));
		service.list(false, null, PageRequest.of(0, 20));

		verify(professionals).search(isNull(), isNull(), eq(7L), isNull(), any(Pageable.class));
		verify(professionals).search(eq(Boolean.FALSE), isNull(), isNull(), isNull(), any(Pageable.class));
	}

	@Test
	void sortIsWhitelistedAndFullNameSortsByTheUsersName() {
		when(professionals.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fullName")));
		service.list(null, null, PageRequest.of(1, 5));

		verify(professionals).search(Boolean.TRUE, Boolean.TRUE, null, Boolean.TRUE,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "user.fullName").and(Sort.by("id"))));
		verify(professionals).search(Boolean.TRUE, Boolean.TRUE, null, Boolean.TRUE, PageRequest.of(1, 5, Sort.by("id")));

		for (String field : new String[] { "email", "user.fullName", "bio" }) {
			assertThatThrownBy(() -> service.list(null, null, PageRequest.of(0, 20, Sort.by(field))))
					.isInstanceOf(BadRequestException.class)
					.extracting("code").isEqualTo("INVALID_SORT");
		}
	}

	@Test
	void publicViewOmitsContactDataAndInactiveServices() {
		Professional existing = professional(5L, true, offering(3L, true), offering(1L, false), offering(2L, true));
		when(professionals.findWithDetailsById(5L)).thenReturn(Optional.of(existing));

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThat(service.get(5L)).isInstanceOfSatisfying(ProfessionalResponse.class, view -> {
				assertThat(view.fullName()).isEqualTo("Profesional 5");
				assertThat(view.services()).extracting(ProfessionalResponse.OfferedService::id).containsExactly(2L, 3L);
				assertThat(view.toString()).doesNotContain("pro@mail.test", "3001112233");
			});
		}
		authenticateAs(Role.ADMIN);
		assertThat(service.get(5L)).isInstanceOfSatisfying(ProfessionalAdminResponse.class, view -> {
			assertThat(view.email()).isEqualTo("pro@mail.test");
			assertThat(view.phone()).isEqualTo("3001112233");
			assertThat(view.userId()).isEqualTo(45L);
			assertThat(view.services()).extracting(ProfessionalAdminResponse.OfferedService::id).containsExactly(1L, 2L, 3L);
		});
	}

	@Test
	void inactiveProfessionalIsHiddenFromEveryoneButAdmins() {
		when(professionals.findWithDetailsById(7L)).thenReturn(Optional.of(professional(7L, false)));

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThatThrownBy(() -> service.get(7L)).isInstanceOf(ResourceNotFoundException.class);
		}
		authenticateAs(Role.ADMIN);
		assertThat(service.get(7L)).isInstanceOfSatisfying(ProfessionalAdminResponse.class,
				view -> assertThat(view.active()).isFalse());
	}

	@Test
	void professionalWhoseUserIsInactiveIsHiddenFromEveryoneButAdmins() {
		Professional existing = professional(8L, true);
		existing.getUser().setActive(false);                         // PATCH /users/{id}/active con false
		when(professionals.findWithDetailsById(8L)).thenReturn(Optional.of(existing));

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThatThrownBy(() -> service.get(8L)).isInstanceOf(ResourceNotFoundException.class);
		}
		authenticateAs(Role.ADMIN);
		assertThat(service.get(8L)).isInstanceOfSatisfying(ProfessionalAdminResponse.class, view -> {
			assertThat(view.active()).isTrue();
			assertThat(view.userActive()).isFalse();
		});
	}

	// ---------- utilidades ----------

	private static void authenticateAs(Role role) {
		SecurityContextHolder.clearContext();
		if (role != null) {
			AuthUser user = new AuthUser(1L, "usuario@mail.test", "hash-sintetico", role, true);
			SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		}
	}

	private static CreateProfessionalRequest createRequest(String email, List<Long> serviceIds) {
		return new CreateProfessionalRequest("Ana Pérez", email, PASSWORD, null, "Fisioterapia", null, serviceIds);
	}

	private static ServiceOffering offering(Long id, boolean active) {
		ServiceOffering offering = new ServiceOffering();
		offering.setId(id);
		offering.setName("Servicio " + id);
		offering.setDurationMinutes(30);
		offering.setPrice(new BigDecimal("50000.00"));
		offering.setActive(active);
		return offering;
	}

	private static Professional professional(Long id, boolean active, ServiceOffering... offered) {
		User user = new User();
		user.setId(40L + id);
		user.setFullName("Profesional " + id);
		user.setEmail("pro@mail.test");
		user.setPhone("3001112233");
		user.setRole(Role.PROFESSIONAL);
		Professional professional = new Professional();
		professional.setId(id);
		professional.setUser(user);
		professional.setSpecialty("Fisioterapia");
		professional.setActive(active);
		professional.getServices().addAll(List.of(offered));
		return professional;
	}

}
