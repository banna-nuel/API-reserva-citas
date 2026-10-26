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
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.q.q.dto.request.ServiceOfferingRequest;
import com.q.q.dto.response.ServiceOfferingResponse;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.ServiceOfferingMapper;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.security.AuthUser;

@ExtendWith(MockitoExtension.class)
class ServiceOfferingServiceTest {

	private static final Role[] NON_ADMINS = { null, Role.CLIENT, Role.PROFESSIONAL };   // null = anónimo

	@Mock
	private ServiceOfferingRepository services;

	private ServiceOfferingService service;

	@BeforeEach
	void setUp() {
		service = new ServiceOfferingService(services, new ServiceOfferingMapper());
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// El servicio vuelve a comprobar el rol aunque SecurityConfig y @PreAuthorize ya lo hagan

	@Test
	void writesRejectAnonymousAndNonAdminUsers() {
		ServiceOfferingRequest req = request("Consulta");
		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThatThrownBy(() -> service.create(req)).isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.update(1L, req)).isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.deactivate(1L)).isInstanceOf(ForbiddenOperationException.class);
			assertThatThrownBy(() -> service.setActive(1L, true)).isInstanceOf(ForbiddenOperationException.class);
		}
		verifyNoInteractions(services);
	}

	@Test
	void createStoresTrimmedFieldsAndPriceWithTwoDecimals() {
		authenticateAs(Role.ADMIN);
		when(services.saveAndFlush(any(ServiceOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ServiceOfferingResponse created = service.create(
				new ServiceOfferingRequest("  Consulta  ", "   ", 30, new BigDecimal("80000")));

		verify(services).existsByNameIgnoreCase("Consulta");
		assertThat(created.name()).isEqualTo("Consulta");
		assertThat(created.description()).isNull();
		assertThat(created.price()).isEqualTo(new BigDecimal("80000.00"));
		assertThat(created.active()).isTrue();
	}

	@Test
	void createRejectsAnExistingNameWithoutWriting() {
		authenticateAs(Role.ADMIN);
		when(services.existsByNameIgnoreCase("Consulta")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request("  Consulta  ")))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("SERVICE_NAME_ALREADY_EXISTS");
		verify(services, never()).saveAndFlush(any());
	}

	@Test
	void uniqueIndexViolationFromARaceIsReportedAs409() {
		authenticateAs(Role.ADMIN);
		when(services.saveAndFlush(any(ServiceOffering.class)))
				.thenThrow(new DataIntegrityViolationException("Duplicate entry for key uq_service_offerings_name"));

		assertThatThrownBy(() -> service.create(request("Consulta")))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("SERVICE_NAME_ALREADY_EXISTS");
	}

	@Test
	void updateComparesTheNameWithOtherServicesOnlyAndKeepsTheActiveFlag() {
		authenticateAs(Role.ADMIN);
		ServiceOffering existing = offering(5L, "Consulta", false);
		when(services.findById(5L)).thenReturn(Optional.of(existing));
		when(services.saveAndFlush(existing)).thenReturn(existing);

		ServiceOfferingResponse updated = service.update(5L,
				new ServiceOfferingRequest("CONSULTA", "Nueva descripción", 45, new BigDecimal("1.5")));

		verify(services).existsByNameIgnoreCaseAndIdNot("CONSULTA", 5L);
		assertThat(updated.name()).isEqualTo("CONSULTA");
		assertThat(updated.description()).isEqualTo("Nueva descripción");
		assertThat(updated.durationMinutes()).isEqualTo(45);
		assertThat(updated.price()).isEqualTo(new BigDecimal("1.50"));
		assertThat(updated.active()).isFalse();
	}

	@Test
	void updateRejectsANameUsedByAnotherService() {
		authenticateAs(Role.ADMIN);
		when(services.findById(5L)).thenReturn(Optional.of(offering(5L, "Consulta", true)));
		when(services.existsByNameIgnoreCaseAndIdNot("Masaje", 5L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(5L, request("Masaje")))
				.isInstanceOf(ConflictException.class)
				.extracting("code").isEqualTo("SERVICE_NAME_ALREADY_EXISTS");
		verify(services, never()).saveAndFlush(any());
	}

	@Test
	void deactivatingTwiceIsHarmless() {
		authenticateAs(Role.ADMIN);
		ServiceOffering existing = offering(3L, "Consulta", true);
		when(services.findById(3L)).thenReturn(Optional.of(existing));

		service.deactivate(3L);
		service.deactivate(3L);

		assertThat(existing.isActive()).isFalse();
	}

	@Test
	void unknownServiceIsNotFound() {
		authenticateAs(Role.ADMIN);
		when(services.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.update(99L, request("Consulta"))).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.deactivate(99L)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.setActive(99L, true)).isInstanceOf(ResourceNotFoundException.class);
	}

	// ---------- lecturas ----------

	@Test
	void nonAdminsOnlyListActiveServicesWhateverTheyAskFor() {
		when(services.search(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			service.list(false, null, PageRequest.of(0, 20));
			service.list(null, null, PageRequest.of(0, 20));
		}

		verify(services, times(6)).search(eq(Boolean.TRUE), isNull(), any(Pageable.class));
	}

	@Test
	void adminListsAllOrFiltersByActive() {
		authenticateAs(Role.ADMIN);
		when(services.search(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, null, PageRequest.of(0, 20));
		service.list(false, null, PageRequest.of(0, 20));

		verify(services).search(isNull(), isNull(), any(Pageable.class));
		verify(services).search(eq(Boolean.FALSE), isNull(), any(Pageable.class));
	}

	@Test
	void searchTermIsTrimmedLowercasedAndEscapedWithDefaultOrderById() {
		when(services.search(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, "  50%_Off!  ", PageRequest.of(0, 20));
		service.list(null, "   ", PageRequest.of(0, 20));

		verify(services).search(Boolean.TRUE, "%50!%!_off!!%", PageRequest.of(0, 20, Sort.by("id")));
		verify(services).search(Boolean.TRUE, null, PageRequest.of(0, 20, Sort.by("id")));
	}

	@Test
	void sortingByNonWhitelistedFieldIsRejected() {
		assertThatThrownBy(() -> service.list(null, null, PageRequest.of(0, 20, Sort.by("description"))))
				.isInstanceOf(BadRequestException.class)
				.extracting("code").isEqualTo("INVALID_SORT");
		verifyNoInteractions(services);
	}

	@Test
	void inactiveServiceIsHiddenFromEveryoneButAdmins() {
		when(services.findById(7L)).thenReturn(Optional.of(offering(7L, "Antiguo", false)));

		for (Role role : NON_ADMINS) {
			authenticateAs(role);
			assertThatThrownBy(() -> service.get(7L)).isInstanceOf(ResourceNotFoundException.class);
		}
		authenticateAs(Role.ADMIN);
		assertThat(service.get(7L).active()).isFalse();
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

	private static ServiceOfferingRequest request(String name) {
		return new ServiceOfferingRequest(name, null, 30, new BigDecimal("80000.00"));
	}

	private static ServiceOffering offering(Long id, String name, boolean active) {
		ServiceOffering offering = new ServiceOffering();
		offering.setId(id);
		offering.setName(name);
		offering.setDurationMinutes(30);
		offering.setPrice(new BigDecimal("80000.00"));
		offering.setActive(active);
		return offering;
	}

}
