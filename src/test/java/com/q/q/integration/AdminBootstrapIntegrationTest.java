package com.q.q.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.q.q.IntegrationTest;
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
		// El arranque del contexto ya lo creó: se borra para observar la creación con el log capturado
		users.findByEmail(ADMIN_EMAIL).ifPresent(users::delete);

		bootstrap.run(new DefaultApplicationArguments());
		bootstrap.run(new DefaultApplicationArguments());                    // segunda ejecución: no duplica

		List<User> admins = users.findAll().stream().filter(user -> ADMIN_EMAIL.equals(user.getEmail())).toList();
		assertThat(admins).singleElement().satisfies(admin -> assertThat(admin.getRole()).isEqualTo(Role.ADMIN));
		assertThat(output)
				.contains("Administrador inicial creado")
				.doesNotContain(IntegrationTest.ADMIN_PASSWORD, ADMIN_EMAIL);
	}

}
