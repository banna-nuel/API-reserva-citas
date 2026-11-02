package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.q.q.IntegrationTest;

// /v3/api-docs contra la tabla 8.2 de docs/GUIA.md: cada operación del contrato, y solo esas, con su tag, su summary,
// su respuesta de éxito, sus errores (con el esquema de ApiError) y bearerAuth solo en las protegidas
@IntegrationTest
class OpenApiDocsTest extends IntegrationTestSupport {

	private record Expected(boolean secured, String... codes) {}

	private static final Map<String, Expected> CONTRACT = Map.ofEntries(
			// Auth
			Map.entry("POST /api/v1/auth/register", new Expected(false, "201", "400", "409")),
			Map.entry("POST /api/v1/auth/login", new Expected(false, "200", "400", "401")),
			// Usuarios
			Map.entry("GET /api/v1/users/me", new Expected(true, "200", "401")),
			Map.entry("PUT /api/v1/users/me", new Expected(true, "200", "400", "401")),
			Map.entry("PATCH /api/v1/users/me/password", new Expected(true, "204", "400", "401", "422")),
			Map.entry("GET /api/v1/users", new Expected(true, "200", "400", "401", "403")),
			Map.entry("PATCH /api/v1/users/{id}/active", new Expected(true, "200", "400", "401", "403", "404", "422")),
			// Servicios
			Map.entry("GET /api/v1/services", new Expected(false, "200", "400")),
			Map.entry("GET /api/v1/services/{id}", new Expected(false, "200", "400", "404")),
			Map.entry("POST /api/v1/services", new Expected(true, "201", "400", "401", "403", "409")),
			Map.entry("PUT /api/v1/services/{id}", new Expected(true, "200", "400", "401", "403", "404", "409")),
			Map.entry("DELETE /api/v1/services/{id}", new Expected(true, "204", "400", "401", "403", "404")),
			Map.entry("PATCH /api/v1/services/{id}/active", new Expected(true, "200", "400", "401", "403", "404")),
			// Profesionales, horario y disponibilidad
			Map.entry("GET /api/v1/professionals", new Expected(false, "200", "400")),
			Map.entry("GET /api/v1/professionals/{id}", new Expected(false, "200", "400", "404")),
			Map.entry("POST /api/v1/professionals", new Expected(true, "201", "400", "401", "403", "409", "422")),
			Map.entry("PUT /api/v1/professionals/{id}", new Expected(true, "200", "400", "401", "403", "404")),
			Map.entry("PATCH /api/v1/professionals/{id}/active", new Expected(true, "200", "400", "401", "403", "404")),
			Map.entry("PUT /api/v1/professionals/{id}/services", new Expected(true, "200", "400", "401", "403", "404", "422")),
			Map.entry("GET /api/v1/professionals/{professionalId}/working-hours", new Expected(false, "200", "400", "404")),
			Map.entry("PUT /api/v1/professionals/{professionalId}/working-hours",
					new Expected(true, "200", "400", "401", "403", "404", "422")),
			Map.entry("GET /api/v1/professionals/{professionalId}/availability", new Expected(false, "200", "400", "404", "422")),
			// Citas
			Map.entry("POST /api/v1/appointments", new Expected(true, "201", "400", "401", "403", "404", "409", "422")),
			Map.entry("GET /api/v1/appointments", new Expected(true, "200", "400", "401")),
			Map.entry("GET /api/v1/appointments/{id}", new Expected(true, "200", "400", "401", "404")),
			Map.entry("PATCH /api/v1/appointments/{id}/confirm", new Expected(true, "200", "400", "401", "403", "404", "409")),
			Map.entry("PATCH /api/v1/appointments/{id}/complete",
					new Expected(true, "200", "400", "401", "403", "404", "409", "422")),
			Map.entry("PATCH /api/v1/appointments/{id}/no-show",
					new Expected(true, "200", "400", "401", "403", "404", "409", "422")),
			Map.entry("PATCH /api/v1/appointments/{id}/cancel", new Expected(true, "200", "400", "401", "404", "409", "422")),
			Map.entry("PATCH /api/v1/appointments/{id}/reschedule",
					new Expected(true, "200", "400", "401", "403", "404", "409", "422")));

	@Test
	@SuppressWarnings("unchecked")
	void everyContractOperationIsDocumentedWithItsResponsesAndSecurity() throws Exception {
		String docs = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());

		Map<String, Map<String, Object>> operations = new TreeMap<>();
		JsonPath.<Map<String, Map<String, Map<String, Object>>>>read(docs, "$.paths").forEach((path, methods) -> {
			if (!path.equals(AdminOnlyProbeController.PATH)) {          // controlador que solo existe en las pruebas
				methods.forEach((method, operation) -> operations.put(method.toUpperCase() + " " + path, operation));
			}
		});
		assertThat(operations).hasSize(30);
		assertThat(operations.keySet()).containsExactlyInAnyOrderElementsOf(CONTRACT.keySet());

		operations.forEach((key, operation) -> {
			Expected expected = CONTRACT.get(key);
			assertThat((List<String>) operation.get("tags")).as(key).hasSize(1);
			assertThat((String) operation.get("summary")).as(key).isNotBlank();
			assertThat((String) operation.get("description")).as(key).isNotBlank();
			Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) operation.get("responses");
			assertThat(responses.keySet()).as(key).containsExactlyInAnyOrder(expected.codes());
			responses.forEach((code, response) -> {
				assertThat((String) response.get("description")).as(key + " " + code).isNotBlank();
				if (code.startsWith("4")) {
					assertThat(JsonPath.<String>read(response, "$.content['application/json'].schema['$ref']"))
							.as(key + " " + code).isEqualTo("#/components/schemas/ApiError");
				} else if (!code.equals("204")) {
					assertThat(response.get("content")).as(key + " " + code + " sin esquema de éxito").isNotNull();
				}
			});
			if (expected.secured()) {
				assertThat(operation.get("security")).as(key).isEqualTo(List.of(Map.of("bearerAuth", List.of())));
			} else {
				assertThat(operation).as(key + " es público").doesNotContainKey("security");
			}
		});

		assertThat(JsonPath.<Map<String, Object>>read(docs, "$.components.schemas.ApiError.properties"))
				.containsOnlyKeys("timestamp", "status", "error", "code", "message", "path", "details");
		assertThat(JsonPath.<String>read(docs, "$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
	}

}
