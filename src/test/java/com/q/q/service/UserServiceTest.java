package com.q.q.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.q.q.dto.request.ChangePasswordRequest;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.mapper.UserMapper;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final AuthUser ADMIN = new AuthUser(1L, "admin@mail.test", "hash-sintetico", Role.ADMIN, true);
	private static final AuthUser CLIENT = new AuthUser(2L, "ana@mail.test", "hash-sintetico", Role.CLIENT, true);

	@Mock
	private UserRepository users;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(users, new UserMapper(), passwordEncoder);
	}

	// El servicio vuelve a comprobar el rol aunque el controlador ya tenga @PreAuthorize

	@Test
	void listingUsersRejectsNonAdmins() {
		assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 20), CLIENT))
				.isInstanceOf(ForbiddenOperationException.class);
		verifyNoInteractions(users);
	}

	@Test
	void changingActiveFlagRejectsNonAdmins() {
		assertThatThrownBy(() -> service.setActive(3L, false, CLIENT))
				.isInstanceOf(ForbiddenOperationException.class);
		verifyNoInteractions(users);
	}

	@Test
	void sortingByNonWhitelistedFieldIsRejected() {
		assertThatThrownBy(() -> service.list(null, PageRequest.of(0, 20, Sort.by("passwordHash")), ADMIN))
				.isInstanceOf(BadRequestException.class)
				.extracting("code").isEqualTo("INVALID_SORT");
		verifyNoInteractions(users);
	}

	@Test
	void idIsAlwaysAddedAsTieBreaker() {
		when(users.findAll(any(Pageable.class))).thenReturn(Page.empty());

		service.list(null, PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "createdAt")), ADMIN);

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(users).findAll(captor.capture());
		assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
		assertThat(captor.getValue().getPageSize()).isEqualTo(10);
		assertThat(captor.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
	}

	@Test
	void defaultOrderIsById() {
		when(users.findByRole(Role.CLIENT, PageRequest.of(0, 20, Sort.by("id")))).thenReturn(Page.empty());

		service.list(Role.CLIENT, PageRequest.of(0, 20), ADMIN);

		verify(users).findByRole(Role.CLIENT, PageRequest.of(0, 20, Sort.by("id")));
	}

	@Test
	void currentPasswordLongerThan72BytesIsNeverAccepted() {
		User user = new User();
		user.setPasswordHash("hash-sintetico");
		when(users.findById(2L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.changePassword(2L, new ChangePasswordRequest("a".repeat(73), "nueva-clave-123")))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("INVALID_CURRENT_PASSWORD");
		verify(passwordEncoder, never()).matches(anyString(), anyString());
		verify(passwordEncoder, never()).encode(anyString());
	}

}
