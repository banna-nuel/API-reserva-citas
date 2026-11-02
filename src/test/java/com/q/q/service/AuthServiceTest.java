package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.q.q.dto.request.LoginRequest;
import com.q.q.dto.request.RegisterRequest;
import com.q.q.dto.response.AuthResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ConflictException;
import com.q.q.mapper.UserMapper;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;
import com.q.q.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String PASSWORD = "clave-sintetica-1";

	@Mock
	private UserRepository users;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtService jwtService;

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, passwordEncoder, authenticationManager, jwtService, new UserMapper());
	}

	// ---------- registro ----------

	@Test
	void registerCreatesAClientWithANormalizedEmailAndAHashedPassword() {
		when(users.existsByEmail("ana@example.com")).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-sintetico");
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(7L);
			return user;
		});

		// role no existe en el body: el registro público solo crea clientes
		UserResponse response = service.register(new RegisterRequest("  Ana Pérez  ", "  Ana@Example.COM ", PASSWORD, null));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");          // RN-11
		assertThat(saved.getValue().getFullName()).isEqualTo("Ana Pérez");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash-sintetico");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.CLIENT);
		assertThat(saved.getValue().isActive()).isTrue();
		assertThat(response.email()).isEqualTo("ana@example.com");
		assertThat(response.role()).isEqualTo(Role.CLIENT);
	}

	@Test
	void anEmailAlreadyUsedInAnyCaseIs409WithoutSaving() {
		when(users.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ANA@example.com", PASSWORD, null)))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_USED"));
		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	void twoSimultaneousRegistrationsEndInA409NotA500() {
		when(users.existsByEmail("ana@example.com")).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-sintetico");
		// El otro registro ganó la carrera: el índice único rechaza este INSERT
		when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

		assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@example.com", PASSWORD, null)))
				.isInstanceOfSatisfying(ConflictException.class,
						ex -> assertThat(ex.getCode()).isEqualTo("EMAIL_ALREADY_USED"));
	}

	// ---------- login ----------

	@Test
	void loginAuthenticatesWithTheNormalizedEmailAndReturnsABearerToken() {
		AuthUser principal = new AuthUser(7L, "ana@example.com", "hash-sintetico", Role.CLIENT, true);
		Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null,
				principal.getAuthorities());
		when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticated);
		when(jwtService.generateToken(principal)).thenReturn("token-sintetico");
		when(jwtService.getExpirationSeconds()).thenReturn(3600L);

		AuthResponse response = service.login(new LoginRequest(" Ana@Example.COM ", PASSWORD));

		ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationManager).authenticate(attempt.capture());
		assertThat(attempt.getValue().getName()).isEqualTo("ana@example.com");
		assertThat(attempt.getValue().getCredentials()).isEqualTo(PASSWORD);
		assertThat(response).isEqualTo(new AuthResponse("token-sintetico", "Bearer", 3600L));
	}

	@Test
	void aPasswordLongerThan72BytesIsRejectedBeforeBcrypt() {
		// 37 caracteres de 2 bytes = 74 bytes: BCrypt solo compararía los primeros 72
		String tooLong = "ñ".repeat(37);

		assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", tooLong)))
				.isInstanceOf(BadCredentialsException.class);
		verifyNoInteractions(authenticationManager, jwtService);
	}

	@Test
	void wrongCredentialsPropagateAndNoTokenIsIssued() {
		when(authenticationManager.authenticate(any(Authentication.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", PASSWORD)))
				.isInstanceOf(BadCredentialsException.class);
		verifyNoInteractions(jwtService);
	}

}
