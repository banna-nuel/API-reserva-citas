package com.q.q.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.q.q.dto.request.ChangePasswordRequest;
import com.q.q.dto.request.UpdateProfileRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.enums.Role;
import com.q.q.security.AuthUser;
import com.q.q.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerSliceSupport.SliceBeans.class)
class UserControllerTest extends ControllerSliceSupport {

	private static final UserResponse ANA = new UserResponse(7L, "Ana Pérez", "ana@example.com", null, Role.CLIENT, true,
			LocalDateTime.of(2026, 11, 2, 8, 0));

	@MockitoBean
	private UserService userService;

	@Test
	void theProfileEndpointsWorkOnTheAuthenticatedUser() throws Exception {
		authenticateAs(7L, Role.CLIENT);
		when(userService.getProfile(7L)).thenReturn(ANA);
		when(userService.updateProfile(eq(7L), any())).thenReturn(ANA);

		mvc.perform(get("/api/v1/users/me")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@example.com"));
		mvc.perform(json(put("/api/v1/users/me"), "{ \"fullName\": \"Ana María\", \"phone\": \"300\", \"role\": \"ADMIN\" }"))
				.andExpect(status().isOk());
		mvc.perform(json(patch("/api/v1/users/me/password"),
				"{ \"currentPassword\": \"la-clave-actual\", \"newPassword\": \"una-clave-nueva\" }"))
				.andExpect(status().isNoContent());

		verify(userService).updateProfile(7L, new UpdateProfileRequest("Ana María", "300"));
		verify(userService).changePassword(7L, new ChangePasswordRequest("la-clave-actual", "una-clave-nueva"));
	}

	@Test
	void invalidProfileAndPasswordBodiesAre400() throws Exception {
		authenticateAs(7L, Role.CLIENT);

		mvc.perform(json(put("/api/v1/users/me"), "{ \"fullName\": \"\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "fullName"));
		mvc.perform(json(patch("/api/v1/users/me/password"), "{ \"currentPassword\": \"x\", \"newPassword\": \"corta\" }"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "newPassword"));
		verifyNoInteractions(userService);
	}

	@Test
	void theListBindsRoleAndPagingAndCapsTheSizeAt100() throws Exception {
		AuthUser admin = authenticateAs(1L, Role.ADMIN);
		when(userService.list(any(), any(), any())).thenReturn(new PageResponse<>(List.of(ANA), 1, 100, 1, 1));

		mvc.perform(get("/api/v1/users").param("role", "CLIENT").param("page", "1").param("size", "500")
				.param("sort", "email,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(7));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(userService).list(eq(Role.CLIENT), pageable.capture(), eq(admin));
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "email"));
	}

	@Test
	void anUnknownRoleOrAMissingActiveFlagIs400() throws Exception {
		authenticateAs(1L, Role.ADMIN);

		mvc.perform(get("/api/v1/users").param("role", "SUPERADMIN"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"));
		mvc.perform(json(patch("/api/v1/users/7/active"), "{}"))
				.andExpectAll(apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "active"));
		verifyNoInteractions(userService);
	}

	@Test
	void setActivePassesThePathIdTheFlagAndTheActor() throws Exception {
		AuthUser admin = authenticateAs(1L, Role.ADMIN);
		when(userService.setActive(7L, false, admin)).thenReturn(ANA);

		mvc.perform(json(patch("/api/v1/users/7/active"), "{ \"active\": false }")).andExpect(status().isOk());

		verify(userService).setActive(7L, false, admin);
	}

}
