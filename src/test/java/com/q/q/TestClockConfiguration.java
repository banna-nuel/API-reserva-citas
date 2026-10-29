package com.q.q;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.q.q.config.AppProperties;

/**
 * Reloj fijo para las pruebas de extremo a extremo: las fechas de las reservas no dependen del día en que corre la
 * suite. JwtService emite y valida los tokens con este mismo reloj, así que siguen siendo coherentes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	// Lunes 2 de noviembre de 2026, 08:00 en la zona del negocio
	public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0);

	@Bean
	@Primary
	Clock fixedClock(AppProperties props) {
		ZoneId zone = ZoneId.of(props.timezone());
		return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
	}

}
