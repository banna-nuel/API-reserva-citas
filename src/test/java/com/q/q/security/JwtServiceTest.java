package com.q.q.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.q.q.IntegrationTest;
import com.q.q.config.AppProperties;
import com.q.q.entity.enums.Role;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

	// Secretos de prueba: Base64 de textos fijos (44, 42 y 16 bytes)
	private static final String SECRET = IntegrationTest.JWT_SECRET;
	private static final String OTHER_SECRET = "YW5vdGhlci10ZXN0LW9ubHktc2VjcmV0LXdpdGgtZW5vdWdoLWJ5dGVz";
	private static final String WEAK_SECRET = "b25seS0xNi1ieXRlcy1hYg==";

	private static final Instant ISSUED_AT = Instant.parse("2026-10-24T15:00:00Z");
	private static final AuthUser ANA = new AuthUser(7L, "ana@mail.test", "hash-sintetico", Role.CLIENT, true);
	private static final AuthUser LUIS = new AuthUser(8L, "luis@mail.test", "hash-sintetico", Role.CLIENT, true);

	@Test
	void tokenIsValidUntilItsExpirationAccordingToTheInjectedClock() {
		String token = serviceAt(SECRET, ISSUED_AT).generateToken(ANA);

		JwtService oneMinuteBefore = serviceAt(SECRET, ISSUED_AT.plus(59, ChronoUnit.MINUTES));
		assertThat(oneMinuteBefore.extractUsername(token)).isEqualTo("ana@mail.test");
		assertThat(oneMinuteBefore.isValid(token, ANA)).isTrue();

		JwtService oneMinuteAfter = serviceAt(SECRET, ISSUED_AT.plus(61, ChronoUnit.MINUTES));
		assertThatThrownBy(() -> oneMinuteAfter.isValid(token, ANA)).isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void tokenOfAnotherUserIsNotValidForThisOne() {
		String token = serviceAt(SECRET, ISSUED_AT).generateToken(ANA);

		assertThat(serviceAt(SECRET, ISSUED_AT).isValid(token, LUIS)).isFalse();
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		String foreign = serviceAt(OTHER_SECRET, ISSUED_AT).generateToken(ANA);

		assertThatThrownBy(() -> serviceAt(SECRET, ISSUED_AT).extractUsername(foreign))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void expirationComesFromConfiguration() {
		assertThat(serviceAt(SECRET, ISSUED_AT).getExpirationSeconds()).isEqualTo(3600);
	}

	@Test
	void invalidOrWeakSecretsFailWithoutRevealingTheSecret() {
		assertThatThrownBy(() -> serviceAt(WEAK_SECRET, ISSUED_AT))
				.isInstanceOf(IllegalStateException.class)
				.hasNoCause()
				.message().doesNotContain(WEAK_SECRET);
		assertThatThrownBy(() -> serviceAt("no-es-base64-$$$", ISSUED_AT))
				.isInstanceOf(IllegalStateException.class)
				.hasNoCause()
				.message().doesNotContain("$$$");
	}

	private static JwtService serviceAt(String secret, Instant now) {
		AppProperties props = new AppProperties("America/Bogota", new AppProperties.Jwt(secret, 60), null, null);
		return new JwtService(props, Clock.fixed(now, ZoneId.of("America/Bogota")));
	}

}
