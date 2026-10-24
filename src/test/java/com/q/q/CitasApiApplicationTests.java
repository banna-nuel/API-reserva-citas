package com.q.q;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import com.q.q.config.AppProperties;
import com.q.q.security.JwtAuthenticationFilter;

@IntegrationTest
class CitasApiApplicationTests {

	@Autowired
	private Clock clock;

	@Autowired
	private AppProperties props;

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void jwtFilterIsNotABeanAndRunsOnlyInsideTheSecurityChain() {
		// Si fuera un bean de tipo Filter, Spring Boot lo registraría también fuera de la cadena de seguridad
		assertThat(context.getBeansOfType(JwtAuthenticationFilter.class)).isEmpty();
		assertThat(context.getBeansOfType(SecurityFilterChain.class).values()).singleElement()
				.satisfies(chain -> assertThat(chain.getFilters()).hasAtLeastOneElementOfType(JwtAuthenticationFilter.class));
	}

	@Test
	void bindsAppPropertiesAndUsesBusinessTimezone() {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Bogota"));
		assertThat(props.booking()).isEqualTo(new AppProperties.Booking(30, 60, 2, 60));
		assertThat(props.jwt().expirationMinutes()).isEqualTo(60);
	}

}
