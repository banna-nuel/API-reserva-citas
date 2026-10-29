package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;
import com.q.q.entity.enums.AppointmentStatus;

import jakarta.persistence.EntityManagerFactory;

// GET /appointments y GET /appointments/{id}: alcance por rol (RN-13), filtros, orden y número de consultas
@IntegrationTest
class AppointmentQueryIntegrationTest extends AppointmentTestSupport {

	private static final String[] APPOINTMENT_KEYS = { "id", "client", "professional", "service", "startTime", "endTime",
			"status", "price", "notes", "cancelReason", "createdAt" };
	private static final long MISSING_ID = 99_999_999L;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	// Dos profesionales y dos clientas:
	//   anaFirst      Ana con el primero, jueves 10:00, PENDING      (50000)
	//   anaSecond     Ana con el segundo, viernes 10:00, CONFIRMED  (70000)
	//   beaFirst      Bea con el primero, jueves 11:00, PENDING      (50000)
	//   beaCancelled  Bea con el primero, lunes 9 a las 09:00, CANCELLED
	private record Agenda(Catalog first, Catalog second, Client ana, Client bea, Long anaFirst, Long anaSecond,
			Long beaFirst, Long beaCancelled) {}

	private Agenda agenda() throws Exception {
		Catalog first = catalog("Primer profesional", uniqueName("Consulta"), 30, "50000.00");
		Catalog second = catalog("Segundo profesional", uniqueName("Terapia"), 30, "70000.00");
		Client ana = newClient("Ana");
		Client bea = newClient("Bea");
		Long anaFirst = bookOk(ana.token(), first, THURSDAY.atTime(10, 0));
		Long anaSecond = bookOk(ana.token(), second, FRIDAY.atTime(10, 0));
		setStatus(anaSecond, AppointmentStatus.CONFIRMED);
		Long beaFirst = bookOk(bea.token(), first, THURSDAY.atTime(11, 0));
		Long beaCancelled = bookOk(bea.token(), first, MONDAY.plusDays(7).atTime(9, 0));
		setStatus(beaCancelled, AppointmentStatus.CANCELLED);
		return new Agenda(first, second, ana, bea, anaFirst, anaSecond, beaFirst, beaCancelled);
	}

	// ---------- alcance por rol ----------

	@Test
	void eachRoleSeesOnlyItsOwnScope() throws Exception {
		Agenda a = agenda();
		String firstPro = professionalToken(a.first());
		String secondPro = professionalToken(a.second());
		String admin = a.first().admin();

		assertThat(ids(a.ana().token())).containsExactly(a.anaFirst(), a.anaSecond());
		assertThat(ids(a.bea().token())).containsExactly(a.beaFirst(), a.beaCancelled());
		assertThat(ids(firstPro)).containsExactly(a.anaFirst(), a.beaFirst(), a.beaCancelled());
		assertThat(ids(secondPro)).containsExactly(a.anaSecond());
		assertThat(ids(admin, "professionalId", a.first().professionalId().toString()))
				.containsExactly(a.anaFirst(), a.beaFirst(), a.beaCancelled());
		assertThat(ids(admin, "professionalId", a.second().professionalId().toString())).containsExactly(a.anaSecond());
		assertThat(JsonPath.<Integer>read(list(admin), "$.totalElements")).isGreaterThanOrEqualTo(4);
	}

	@Test
	void filtersApplyWithinTheScopeAndOutOfScopeFiltersGiveAnEmptyPage() throws Exception {
		Agenda a = agenda();
		String firstPro = professionalToken(a.first());
		String secondPro = professionalToken(a.second());
		String ana = a.ana().token();

		assertThat(ids(ana, "status", "CONFIRMED")).containsExactly(a.anaSecond());
		assertThat(ids(ana, "status", "CANCELLED")).isEmpty();
		assertThat(ids(ana, "professionalId", a.second().professionalId().toString())).containsExactly(a.anaSecond());
		// from y to son inclusivas sobre el día de startTime
		assertThat(ids(firstPro, "from", THURSDAY.toString(), "to", THURSDAY.toString()))
				.containsExactly(a.anaFirst(), a.beaFirst());
		assertThat(ids(firstPro, "from", FRIDAY.toString())).containsExactly(a.beaCancelled());
		assertThat(ids(firstPro, "to", FRIDAY.toString())).containsExactly(a.anaFirst(), a.beaFirst());
		assertThat(ids(a.first().admin(), "status", "CANCELLED", "professionalId", a.first().professionalId().toString()))
				.containsExactly(a.beaCancelled());

		// Fuera del alcance: página vacía, no 403
		assertThat(ids(a.bea().token(), "professionalId", a.second().professionalId().toString())).isEmpty();
		assertThat(ids(secondPro, "professionalId", a.first().professionalId().toString())).isEmpty();
		assertThat(ids(ana, "professionalId", String.valueOf(MISSING_ID))).isEmpty();
	}

	@Test
	void theListIsSortedByStartTimeByDefaultAndPagedLikeTheOtherListings() throws Exception {
		Agenda a = agenda();
		String ana = a.ana().token();
		String firstPro = professionalToken(a.first());

		assertThat(ids(ana, "sort", "startTime,desc")).containsExactly(a.anaSecond(), a.anaFirst());
		assertThat(ids(ana, "sort", "price,desc")).containsExactly(a.anaSecond(), a.anaFirst());
		assertThat(ids(ana, "sort", "price,asc")).containsExactly(a.anaFirst(), a.anaSecond());
		assertThat(ids(firstPro, "sort", "status")).containsExactly(a.beaCancelled(), a.anaFirst(), a.beaFirst());

		String page = list(firstPro, "size", "1", "page", "1");
		assertThat(keys(page)).containsExactlyInAnyOrder(PAGE_KEYS);
		assertThat(JsonPath.<List<Integer>>read(page, "$.content[*].id")).containsExactly(a.beaFirst().intValue());
		assertThat(JsonPath.<Integer>read(page, "$.totalElements")).isEqualTo(3);
		assertThat(JsonPath.<Integer>read(page, "$.totalPages")).isEqualTo(3);

		for (String forbidden : new String[] { "notes", "client.fullName", "endTime", "price;drop" }) {
			mvc.perform(authorized(get(APPOINTMENTS).param("sort", forbidden), ana))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_SORT", APPOINTMENTS));
		}
	}

	@Test
	void invalidFiltersAre400ButExtremeDatesAreNot() throws Exception {
		Agenda a = agenda();
		String ana = a.ana().token();

		for (String[] param : new String[][] { { "status", "FOO" }, { "status", "confirmed" }, { "from", "2026-13-01" },
				{ "to", "05/11/2026" }, { "professionalId", "abc" } }) {
			mvc.perform(authorized(get(APPOINTMENTS).param(param[0], param[1]), ana))
					.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", APPOINTMENTS));
		}
		mvc.perform(authorized(get(APPOINTMENTS).param("from", FRIDAY.toString()).param("to", THURSDAY.toString()), ana))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", APPOINTMENTS));
		mvc.perform(get(APPOINTMENTS))
				.andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", APPOINTMENTS));

		// Fechas fuera del rango de DATETIME de MySQL: no fallan, simplemente no limitan (o no dejan nada)
		assertThat(ids(ana, "from", "-999999999-01-01", "to", "+999999999-12-31"))
				.containsExactly(a.anaFirst(), a.anaSecond());
		assertThat(ids(ana, "from", "+999999999-12-31")).isEmpty();
		assertThat(ids(ana, "to", "0001-01-01")).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void eachItemCarriesOnlyTheContractKeysAndNoContactData() throws Exception {
		Agenda a = agenda();

		String page = list(a.ana().token());
		for (Map<String, Object> item : JsonPath.<List<Map<String, Object>>>read(page, "$.content")) {
			assertThat(item).containsOnlyKeys(APPOINTMENT_KEYS);
			assertThat((Map<String, Object>) item.get("client")).containsOnlyKeys("id", "fullName");
			assertThat((Map<String, Object>) item.get("professional")).containsOnlyKeys("id", "fullName");
			assertThat((Map<String, Object>) item.get("service")).containsOnlyKeys("id", "name", "durationMinutes");
		}
		assertThat(page).doesNotContain("email", "phone", "@mail.test", "passwordHash");
	}

	// Sin N+1: cliente, profesional con su usuario y servicio llegan con la página, tenga 2 citas o 6
	@Test
	void listingRunsAFixedNumberOfQueriesWhateverThePageHolds() throws Exception {
		Agenda a = agenda();
		Client busy = newClient();
		for (int i = 0; i < 3; i++) {
			bookOk(busy.token(), a.first(), FRIDAY.atTime(9, 0).plusMinutes(30L * i));
			bookOk(busy.token(), a.second(), FRIDAY.atTime(14, 0).plusMinutes(30L * i));
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			for (Map.Entry<String, Integer> client : Map.of(a.ana().token(), 2, busy.token(), 6).entrySet()) {
				statistics.clear();
				assertThat(ids(client.getKey())).hasSize(client.getValue());
				// El usuario del token (filtro JWT) + la página con todo lo demás. El count sobra porque la página
				// no está llena
				assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
			}
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	// ---------- detalle ----------

	@Test
	void theDetailIsVisibleToTheClientTheAssignedProfessionalAndAdminsAndIs404ForEveryoneElse() throws Exception {
		Agenda a = agenda();
		String path = APPOINTMENTS + "/" + a.anaFirst();

		for (String viewer : new String[] { a.ana().token(), professionalToken(a.first()), a.first().admin() }) {
			String detail = body(mvc.perform(authorized(get(path), viewer)).andExpect(status().isOk()).andReturn());
			assertThat(keys(detail)).containsExactlyInAnyOrder(APPOINTMENT_KEYS);
			assertThat(JsonPath.<Number>read(detail, "$.id").longValue()).isEqualTo(a.anaFirst());
		}
		// Otra clienta y otro profesional: 404, igual que una cita que no existe (RN-13)
		for (String stranger : new String[] { a.bea().token(), professionalToken(a.second()) }) {
			mvc.perform(authorized(get(path), stranger))
					.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", path));
		}
		String missing = APPOINTMENTS + "/" + MISSING_ID;
		mvc.perform(authorized(get(missing), a.first().admin()))
				.andExpectAll(apiError(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", missing));
		mvc.perform(get(path)).andExpectAll(apiError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", path));
		mvc.perform(authorized(get(APPOINTMENTS + "/abc"), a.ana().token()))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", APPOINTMENTS + "/abc"));
	}

	// ---------- utilidades ----------

	// GET /appointments con pares nombre/valor (size=100 si no se indica); devuelve el cuerpo de una respuesta 200
	private String list(String token, String... params) throws Exception {
		MockHttpServletRequestBuilder request = get(APPOINTMENTS);
		boolean sized = false;
		for (int i = 0; i < params.length; i += 2) {
			request = request.param(params[i], params[i + 1]);
			sized |= params[i].equals("size");
		}
		if (!sized) {
			request = request.param("size", "100");
		}
		return body(mvc.perform(authorized(request, token)).andExpect(status().isOk()).andReturn());
	}

	private List<Long> ids(String token, String... params) throws Exception {
		return JsonPath.<List<Number>>read(list(token, params), "$.content[*].id").stream().map(Number::longValue).toList();
	}

}
