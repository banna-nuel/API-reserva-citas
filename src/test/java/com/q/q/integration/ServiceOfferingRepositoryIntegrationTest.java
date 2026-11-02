package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import com.q.q.IntegrationTest;
import com.q.q.entity.ServiceOffering;
import com.q.q.repository.ServiceOfferingRepository;

// ServiceOfferingRepository contra MySQL real. Cada prueba usa un prefijo propio para no ver los datos de las demás.
// El servicio pasa el patrón de "q" en minúsculas y con % y _ escapados con '!'
@IntegrationTest
class ServiceOfferingRepositoryIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ServiceOfferingRepository services;

	@Test
	void percentAndUnderscoreAreSearchedAsLiteralText() {
		String prefix = prefix();
		Long withPercent = save(prefix + " Descuento 50% total", true);
		Long withoutPercent = save(prefix + " Descuento 50 total", true);
		Long withUnderscore = save(prefix + " Tarifa_plana", true);
		Long withSpace = save(prefix + " Tarifa plana", true);

		// "50%" escapado: sin el escape, % sería comodín y también traería "50 total"
		assertThat(search(null, "%" + prefix + " descuento 50!%%")).containsExactly(withPercent);
		// "a_p" escapado: sin el escape, _ sería un carácter cualquiera y también traería "a p"
		assertThat(search(null, "%" + prefix + "%a!_p%")).containsExactly(withUnderscore);
		assertThat(search(null, "%" + prefix + "%")).containsExactlyInAnyOrder(withPercent, withoutPercent,
				withUnderscore, withSpace);
	}

	@Test
	void theActiveFilterIsOptional() {
		String prefix = prefix();
		Long active = save(prefix + " Vigente", true);
		Long inactive = save(prefix + " Retirado", false);
		String pattern = "%" + prefix + "%";

		assertThat(search(Boolean.TRUE, pattern)).containsExactly(active);
		assertThat(search(Boolean.FALSE, pattern)).containsExactly(inactive);
		assertThat(search(null, pattern)).containsExactlyInAnyOrder(active, inactive);
	}

	// Colación utf8mb4_0900_ai_ci: el nombre se compara sin distinguir mayúsculas ni tildes
	@Test
	void namesAreUniqueIgnoringCaseAndAccents() {
		String prefix = prefix();
		String name = prefix + " Masaje terapéutico";
		Long id = save(name, true);
		String sameName = (prefix + " MASAJE TERAPEUTICO").toUpperCase(Locale.ROOT);

		assertThat(services.existsByNameIgnoreCase(sameName)).isTrue();
		// Al editar, el propio servicio no cuenta como duplicado
		assertThat(services.existsByNameIgnoreCaseAndIdNot(sameName, id)).isFalse();
		assertThat(services.existsByNameIgnoreCaseAndIdNot(sameName, id + 1_000_000)).isTrue();
		// Y si algo se saltara esa comprobación, el índice único lo frena
		assertThatThrownBy(() -> services.saveAndFlush(offering(prefix + " masaje terapeutico", true)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private List<Long> search(Boolean active, String namePattern) {
		return services.search(active, namePattern, Pageable.unpaged()).map(ServiceOffering::getId).getContent();
	}

	private Long save(String name, boolean active) {
		return services.saveAndFlush(offering(name, active)).getId();
	}

	private static ServiceOffering offering(String name, boolean active) {
		ServiceOffering offering = new ServiceOffering();
		offering.setName(name);
		offering.setDurationMinutes(30);
		offering.setPrice(new BigDecimal("50000.00"));
		offering.setActive(active);
		return offering;
	}

	private static String prefix() {
		return "repo" + System.nanoTime();
	}

}
