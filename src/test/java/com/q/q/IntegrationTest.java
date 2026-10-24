package com.q.q;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.q.q.integration.AdminOnlyProbeController;

/**
 * Configuración común de las pruebas de extremo a extremo: MySQL real (Testcontainers), MockMvc con la
 * cadena de seguridad completa y valores sintéticos que no dependen del .env del desarrollador.
 * Todas las clases que la usan comparten el mismo contexto de Spring (y el mismo contenedor).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, AdminOnlyProbeController.class })
@TestPropertySource(properties = {
		"app.jwt.secret=" + IntegrationTest.JWT_SECRET,
		"app.jwt.expiration-minutes=60",
		"app.admin.email=" + IntegrationTest.ADMIN_EMAIL,
		"app.admin.password=" + IntegrationTest.ADMIN_PASSWORD })
public @interface IntegrationTest {

	// Valores de prueba (no son secretos reales): el secreto es Base64 de un texto fijo de 44 bytes
	String JWT_SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtbmV2ZXItdXNlLWluLXByb2R1Y3Rpb24=";
	String ADMIN_EMAIL = "Admin@Citas.Test";
	String ADMIN_PASSWORD = "admin-test-password";

}
