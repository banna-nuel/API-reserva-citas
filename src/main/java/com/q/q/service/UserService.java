package com.q.q.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.request.ChangePasswordRequest;
import com.q.q.dto.request.UpdateProfileRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.UserResponse;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.BadRequestException;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.UserMapper;
import com.q.q.repository.UserRepository;
import com.q.q.security.AuthUser;
import com.q.q.security.PasswordPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	// Campos por los que GET /users puede ordenar (lista blanca: nunca passwordHash)
	private static final List<String> SORTABLE_FIELDS = List.of("id", "fullName", "email", "role", "active", "createdAt");

	private final UserRepository users;
	private final UserMapper mapper;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserResponse getProfile(Long userId) {
		return mapper.toResponse(findUser(userId));
	}

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
		User user = findUser(userId);
		user.setFullName(req.fullName().strip());
		user.setPhone(req.phone());
		return mapper.toResponse(user);
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest req) {
		User user = findUser(userId);
		// Una clave de más de 72 bytes nunca es la actual: BCrypt ignoraría lo que sobra y podría "coincidir"
		if (PasswordPolicy.exceedsBcryptLimit(req.currentPassword())
				|| !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
			throw new BusinessRuleException("INVALID_CURRENT_PASSWORD", "La contraseña actual no es correcta");
		}
		if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
			throw new BusinessRuleException("PASSWORD_UNCHANGED", "La nueva contraseña debe ser distinta de la actual");
		}
		user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(Role role, Pageable pageable, AuthUser actor) {
		requireAdmin(actor);
		Pageable safePageable = withSafeSort(pageable);
		Page<User> page = role == null ? users.findAll(safePageable) : users.findByRole(role, safePageable);
		return PageResponse.from(page.map(mapper::toResponse));
	}

	@Transactional
	public UserResponse setActive(Long userId, boolean active, AuthUser actor) {
		requireAdmin(actor);
		User user = findUser(userId);
		if (!active && user.getId().equals(actor.id())) {
			throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF", "Un administrador no puede desactivarse a sí mismo");
		}
		user.setActive(active);              // el filtro JWT recarga el usuario en cada petición: el corte es inmediato
		return mapper.toResponse(user);
	}

	private User findUser(Long userId) {
		return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
	}

	// Defensa en profundidad: el controlador ya exige ADMIN con @PreAuthorize
	private static void requireAdmin(AuthUser actor) {
		if (actor == null || actor.role() != Role.ADMIN) {
			throw new ForbiddenOperationException("Solo un administrador puede gestionar usuarios");
		}
	}

	// Solo campos de la lista blanca y siempre "id" como desempate, para que el orden sea estable entre páginas
	private static Pageable withSafeSort(Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_FIELDS.contains(order.getProperty())) {
				throw new BadRequestException("INVALID_SORT", "No se puede ordenar por '" + order.getProperty()
						+ "'. Campos permitidos: " + String.join(", ", SORTABLE_FIELDS));
			}
		}
		Sort sort = pageable.getSort().getOrderFor("id") == null
				? pageable.getSort().and(Sort.by("id"))
				: pageable.getSort();
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
	}

}
