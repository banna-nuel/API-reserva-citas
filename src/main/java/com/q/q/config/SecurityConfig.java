package com.q.q.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.q.q.security.CustomUserDetailsService;
import com.q.q.security.JwtAuthenticationFilter;
import com.q.q.security.JwtService;
import com.q.q.security.RestAccessDeniedHandler;
import com.q.q.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity                                    // habilita @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtService jwtService;
	private final CustomUserDetailsService userDetailsService;
	private final RestAuthenticationEntryPoint entryPoint;     // responde 401 con el JSON de error
	private final RestAccessDeniedHandler deniedHandler;       // responde 403 con el JSON de error

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())                                           // API sin sesión
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
			.authorizeHttpRequests(auth -> auth
				// Endpoints solo-ADMIN: se cortan aquí con 403 antes de validar parámetros o body
				// (además llevan @PreAuthorize y la comprobación en el servicio)
				.requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/active").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/v1/services").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PUT, "/api/v1/services/*").hasRole("ADMIN")
				.requestMatchers(HttpMethod.DELETE, "/api/v1/services/*").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PATCH, "/api/v1/services/*/active").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/v1/professionals").hasRole("ADMIN")
				// Rutas exactas: el horario laboral (abajo) admite también al propio profesional
				.requestMatchers(HttpMethod.PUT, "/api/v1/professionals/*", "/api/v1/professionals/*/services").hasRole("ADMIN")
				.requestMatchers(HttpMethod.PATCH, "/api/v1/professionals/*/active").hasRole("ADMIN")
				// Acceso mixto: un CLIENT recibe 403 antes de validar el body; que sea el dueño lo comprueba el servicio
				.requestMatchers(HttpMethod.PUT, "/api/v1/professionals/*/working-hours").hasAnyRole("ADMIN", "PROFESSIONAL")
				.requestMatchers("/api/v1/auth/**", "/actuator/health/**").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/services/**", "/api/v1/professionals/**").permitAll()
				.anyRequest().authenticated())                                      // todo lo demás, cerrado
			.addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
					UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		// Primero la clave y después el estado de la cuenta: un usuario inactivo responde
		// igual (y tarda lo mismo) que una clave incorrecta, así no se revela que existe
		provider.setPreAuthenticationChecks(user -> { });
		provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
		return new ProviderManager(provider);
	}

}
