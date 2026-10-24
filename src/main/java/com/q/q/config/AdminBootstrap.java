package com.q.q.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Crea el primer ADMIN con app.admin.* al arrancar, solo si todavía no existe.
 * Los mensajes de log nunca incluyen el email ni la clave configurados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

	private final AppProperties props;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		AppProperties.Admin admin = props.admin();
		if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
			log.warn("ADMIN_EMAIL o ADMIN_PASSWORD vacíos: no se crea el administrador inicial");
			return;
		}
		String password = admin.password();
		if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {   // RN-11
			log.warn("ADMIN_PASSWORD no cumple RN-11 (8 a 72 caracteres, máximo 72 bytes): no se crea el administrador inicial");
			return;
		}
		String email = User.normalizeEmail(admin.email());
		if (users.existsByEmail(email)) {
			log.debug("El administrador inicial ya existe");
			return;
		}
		User user = new User();
		user.setFullName("Administrador");
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(Role.ADMIN);
		users.save(user);
		log.info("Administrador inicial creado (id={})", user.getId());
	}

}
