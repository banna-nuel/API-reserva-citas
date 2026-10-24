package com.q.q.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.q.q.entity.enums.Role;
import com.q.q.repository.UserRepository;

@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class AdminBootstrapTest {

	private static final String EMAIL = "admin@example.com";
	private static final String PASSWORD = "synthetic-pass-123";

	@Mock
	private UserRepository users;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	void emptyEmailCreatesNothingAndWarnsWithoutValues(CapturedOutput output) {
		run(new AppProperties.Admin("", PASSWORD));

		verifyNoInteractions(users, passwordEncoder);
		assertThat(output).contains("no se crea el administrador inicial").doesNotContain(PASSWORD);
	}

	@Test
	void emptyPasswordCreatesNothingAndWarnsWithoutValues(CapturedOutput output) {
		run(new AppProperties.Admin(EMAIL, "   "));

		verifyNoInteractions(users, passwordEncoder);
		assertThat(output).contains("no se crea el administrador inicial").doesNotContain(EMAIL);
	}

	@Test
	void passwordOutsideRn11CreatesNothingAndWarnsWithoutValues(CapturedOutput output) {
		run(new AppProperties.Admin(EMAIL, "corta"));

		verifyNoInteractions(users, passwordEncoder);
		assertThat(output).contains("no se crea el administrador inicial").doesNotContain(EMAIL, "corta");
	}

	@Test
	void createsAdminWithNormalizedEmailAndHashedPassword(CapturedOutput output) {
		when(users.existsByEmail(EMAIL)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-sintetico");

		run(new AppProperties.Admin("Admin@Example.COM", PASSWORD));

		verify(users).save(argThat(user -> EMAIL.equals(user.getEmail())
				&& user.getRole() == Role.ADMIN
				&& user.isActive()
				&& "hash-sintetico".equals(user.getPasswordHash())));
		assertThat(output).contains("Administrador inicial creado").doesNotContain(PASSWORD, EMAIL);
	}

	@Test
	void existingAdminIsNotCreatedAgain() {
		when(users.existsByEmail(EMAIL)).thenReturn(true);

		run(new AppProperties.Admin(EMAIL, PASSWORD));

		verify(users, never()).save(any());
		verifyNoInteractions(passwordEncoder);
	}

	private void run(AppProperties.Admin admin) {
		AppProperties props = new AppProperties("America/Bogota", null, null, admin);
		new AdminBootstrap(props, users, passwordEncoder).run(new DefaultApplicationArguments());
	}

}
