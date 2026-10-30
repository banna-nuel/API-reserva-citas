package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.q.q.IntegrationTest;
import com.q.q.entity.Professional;
import com.q.q.repository.ProfessionalRepository;

// ProfessionalRepository.search contra MySQL real: filtro serviceId y los predicados de visibilidad que usan las
// lecturas públicas (perfil, usuario y servicio activos) y las del ADMIN (parámetros en null)
@IntegrationTest
class ProfessionalRepositoryIntegrationTest extends AppointmentTestSupport {

	@Autowired
	private ProfessionalRepository professionals;

	private record Scenario(Long shared, Long retired, Long visible, Long inactiveProfile, Long inactiveUser,
			Long offersRetired) {}

	// Tres profesionales que ofrecen el mismo servicio (uno visible, uno con el perfil inactivo y otro con el usuario
	// inactivo) y un cuarto, visible, que ofrece un servicio inactivo
	private Scenario scenario() throws Exception {
		Catalog visible = catalog(30);
		Catalog inactiveProfile = catalog(30);
		Catalog inactiveUser = catalog(30);
		Catalog offersRetired = catalog(30);
		String admin = visible.admin();
		offer(inactiveProfile, visible.serviceId());
		offer(inactiveUser, visible.serviceId());
		setActive(admin, PROFESSIONALS + "/" + inactiveProfile.professionalId());
		setActive(admin, "/api/v1/users/" + inactiveUser.professionalUserId());
		setActive(admin, SERVICES + "/" + offersRetired.serviceId());
		return new Scenario(visible.serviceId(), offersRetired.serviceId(), visible.professionalId(),
				inactiveProfile.professionalId(), inactiveUser.professionalId(), offersRetired.professionalId());
	}

	@Test
	void thePublicSearchNeedsActiveProfileUserAndService() throws Exception {
		Scenario s = scenario();

		assertThat(ids(professionals.search(true, true, s.shared(), true, all()))).containsExactly(s.visible());
		assertThat(ids(professionals.search(true, true, s.retired(), true, all()))).isEmpty();
		// Sin filtro de servicio: aparecen los visibles, aunque ofrezcan un servicio inactivo
		assertThat(ids(professionals.search(true, true, null, null, all())))
				.contains(s.visible(), s.offersRetired())
				.doesNotContain(s.inactiveProfile(), s.inactiveUser());
	}

	@Test
	void theAdminSearchFiltersOnlyByWhatItAsks() throws Exception {
		Scenario s = scenario();

		assertThat(ids(professionals.search(null, null, s.shared(), null, all())))
				.containsExactlyInAnyOrder(s.visible(), s.inactiveProfile(), s.inactiveUser());
		assertThat(ids(professionals.search(false, null, s.shared(), null, all()))).containsExactly(s.inactiveProfile());
		assertThat(ids(professionals.search(null, false, s.shared(), null, all()))).containsExactly(s.inactiveUser());
		assertThat(ids(professionals.search(null, null, s.retired(), null, all()))).containsExactly(s.offersRetired());
	}

	// El join fetch del usuario no rompe la paginación: el total sale de la countQuery
	@Test
	void pagingKeepsTheRightTotals() throws Exception {
		Scenario s = scenario();

		Page<Professional> page = professionals.search(null, null, s.shared(), null, PageRequest.of(1, 1, Sort.by("id")));

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(3);
		assertThat(ids(page)).containsExactly(s.inactiveProfile());
	}

	@Test
	void findWithDetailsByIdLoadsTheUserAndTheServicesTogether() throws Exception {
		Scenario s = scenario();

		Professional professional = professionals.findWithDetailsById(s.visible()).orElseThrow();

		// Fuera de la transacción: si no hubieran llegado con la consulta, aquí fallaría la carga perezosa
		assertThat(Hibernate.isInitialized(professional.getUser())).isTrue();
		assertThat(Hibernate.isInitialized(professional.getServices())).isTrue();
		assertThat(professional.getServices()).extracting("id").containsExactly(s.shared());
	}

	private static PageRequest all() {
		return PageRequest.of(0, 10_000, Sort.by("id"));
	}

	private static List<Long> ids(Page<Professional> page) {
		return page.map(Professional::getId).getContent();
	}

	private void setActive(String admin, String resource) throws Exception {
		mvc.perform(withJson(patch(resource + "/active"), admin, Map.of("active", false))).andExpect(status().isOk());
	}

}
