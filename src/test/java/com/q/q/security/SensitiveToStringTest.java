package com.q.q.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.q.q.dto.request.ChangePasswordRequest;
import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;

/** Claves, hashes y tokens nunca deben aparecer en un toString (podría terminar en un log). */
class SensitiveToStringTest {

	private static final String HASH = "$2a$10$hashSinteticoSoloParaPruebas";
	private static final String PASSWORD = "clave-secreta-1";
	private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.cGF5bG9hZA.firma";

	@Test
	void userToStringHasNeitherEmailNorHash() {
		User user = new User();
		user.setEmail("Ana@Mail.Test");
		user.setPasswordHash(HASH);
		user.setRole(Role.CLIENT);

		assertThat(user.toString()).doesNotContain(HASH, "ana@mail.test");
		assertThat(user.getEmail()).isEqualTo("ana@mail.test");                // RN-11
	}

	@Test
	void authUserToStringHasNoHash() {
		AuthUser authUser = new AuthUser(1L, "ana@mail.test", HASH, Role.CLIENT, true);

		assertThat(authUser.toString()).doesNotContain(HASH);
	}

	@Test
	void requestAndResponseDtosMaskSecrets() {
		assertThat(new RegisterRequest("Ana", "ana@mail.test", PASSWORD, null).toString()).doesNotContain(PASSWORD);
		assertThat(new LoginRequest("ana@mail.test", PASSWORD).toString()).doesNotContain(PASSWORD);
		assertThat(new AuthResponse(TOKEN, "Bearer", 3600).toString()).doesNotContain(TOKEN);
		assertThat(new ChangePasswordRequest(PASSWORD, "clave-nueva-2").toString()).doesNotContain(PASSWORD, "clave-nueva-2");
	}

	@Test
	void authUserExposesRoleAuthorityAndActiveFlag() {
		AuthUser inactiveAdmin = new AuthUser(1L, "admin@mail.test", HASH, Role.ADMIN, false);

		assertThat(inactiveAdmin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
		assertThat(inactiveAdmin.isEnabled()).isFalse();
		assertThat(inactiveAdmin.getUsername()).isEqualTo("admin@mail.test");
	}

}
