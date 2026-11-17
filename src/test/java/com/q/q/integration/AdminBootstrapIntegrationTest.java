package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.q.q.IntegrationTest;
import com.q.q.TestDatabase;
import com.q.q.config.AdminBootstrap;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.repository.UserRepository;

@IntegrationTest
class AdminBootstrapIntegrationTest {

	private static final String ADMIN_EMAIL = User.normalizeEmail(IntegrationTest.ADMIN_EMAIL);

	@Autowired
	private AdminBootstrap bootstrap;

	@Autowired
	private UserRepository users;

	@Autowired
	private TestDatabase database;

	// La base es compartida con las demás clases de integración: esta clase la encuentra y la deja solo con el ADMIN
	@BeforeAll
	static void startFromAKnownDatabase(@Autowired TestDatabase database) {
		database.reset();
	}

	@AfterAll
	static void leaveAKnownDatabase(@Autowired TestDatabase database) {
		database.reset();
	}

	@Test
	void initialAdminExistsAfterStartup() {
		assertThat(users.findByEmail(ADMIN_EMAIL)).get()
				.satisfies(admin -> {
					assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
					assertThat(admin.isActive()).isTrue();
				});
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void createsInitialAdminOnlyOnceAndNeverLogsItsPassword(CapturedOutput output) {
		// El arranque ya lo creó: se vacía la base para observar la creación con el log capturado. clean() borra antes
		// lo que lo referencia (p. ej. citas reservadas a su nombre), que un borrado suelto del usuario no podría
		database.clean();

		bootstrap.run(new DefaultApplicationArguments());
		bootstrap.run(new DefaultApplicationArguments());                    // segunda ejecución: no duplica

		List<User> admins = users.findAll().stream().filter(user -> ADMIN_EMAIL.equals(user.getEmail())).toList();
		assertThat(admins).singleElement().satisfies(admin -> assertThat(admin.getRole()).isEqualTo(Role.ADMIN));
		assertThat(output)
				.contains("Administrador inicial creado")
				.doesNotContain(IntegrationTest.ADMIN_PASSWORD, ADMIN_EMAIL);
	}

}
