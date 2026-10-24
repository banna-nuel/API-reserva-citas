package com.q.q.integration;

import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que solo existe en src/test para probar @PreAuthorize: un CLIENT debe recibir 403 con el JSON de 8.4.
 * Es @TestComponent para que el escaneo no lo recoja; lo registra @IntegrationTest con @Import.
 */
@TestComponent
@RestController
public class AdminOnlyProbeController {

	public static final String PATH = "/api/v1/test-probe/admin-only";

	@GetMapping(PATH)
	@PreAuthorize("hasRole('ADMIN')")
	public Map<String, String> adminOnly() {
		return Map.of("status", "ok");
	}

}
