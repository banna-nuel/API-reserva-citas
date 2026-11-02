package com.q.q.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

	@Mock
	private UserRepository users;

	@InjectMocks
	private CustomUserDetailsService service;

	@Test
	void loadsTheUserByItsNormalizedEmailWithItsRoleAndState() {
		User user = new User();
		user.setId(7L);
		user.setEmail("ana@example.com");
		user.setPasswordHash("hash-sintetico");
		user.setRole(Role.PROFESSIONAL);
		user.setActive(false);
		when(users.findByEmail("ana@example.com")).thenReturn(Optional.of(user));

		AuthUser loaded = service.loadUserByUsername("  Ana@Example.COM ");

		assertThat(loaded.id()).isEqualTo(7L);
		assertThat(loaded.getUsername()).isEqualTo("ana@example.com");
		assertThat(loaded.getPassword()).isEqualTo("hash-sintetico");
		assertThat(loaded.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_PROFESSIONAL");
		assertThat(loaded.isEnabled()).isFalse();                // el filtro JWT y el login lo rechazan
	}

	@Test
	void anUnknownEmailIsNotFoundAndTheMessageDoesNotRevealIt() {
		when(users.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("nadie@example.com"))
				.isInstanceOf(UsernameNotFoundException.class)
				.message().doesNotContain("nadie");
	}

}
