package com.q.q.security;

import java.nio.charset.StandardCharsets;

/**
 * RN-11: contraseñas de 8 a 72 caracteres y, como BCrypt solo usa los primeros 72 bytes,
 * nunca más de 72 bytes en UTF-8. Una clave más larga jamás puede coincidir con una guardada.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	public static boolean exceedsBcryptLimit(String rawPassword) {
		return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}

}
