package com.q.q.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.request.AssignServicesRequest;
import com.q.q.dto.request.CreateProfessionalRequest;
import com.q.q.dto.request.UpdateProfessionalRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ProfessionalAdminResponse;
import com.q.q.dto.response.ProfessionalView;
import com.q.q.entity.Professional;
import com.q.q.entity.ServiceOffering;
import com.q.q.entity.User;
import com.q.q.entity.enums.Role;
import com.q.q.exception.ApiError;
import com.q.q.exception.BusinessRuleException;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.ProfessionalMapper;
import com.q.q.repository.ProfessionalRepository;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.repository.UserRepository;
import com.q.q.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfessionalService {

	// Campos por los que GET /professionals puede ordenar; fullName vive en el usuario
	private static final List<String> SORTABLE_FIELDS = List.of("id", "fullName", "specialty", "createdAt");
	private static final Map<String, String> SORT_PATHS = Map.of("fullName", "user.fullName");

	private final ProfessionalRepository professionals;
	private final UserRepository users;
	private final ServiceOfferingRepository services;
	private final PasswordEncoder passwordEncoder;
	private final ProfessionalMapper mapper;

	@Transactional(readOnly = true)
	public PageResponse<ProfessionalView> list(Boolean active, Long serviceId, Pageable pageable) {
		boolean admin = CurrentUser.isAdmin();
		Pageable safePageable = SafeSort.apply(pageable, SORTABLE_FIELDS, SORT_PATHS);
		// Quien no es ADMIN solo ve perfiles activos con el usuario activo, y filtrar por un servicio inactivo
		// no le devuelve a nadie
		Page<Professional> page = admin
				? professionals.search(active, null, serviceId, null, safePageable)
				: professionals.search(Boolean.TRUE, Boolean.TRUE, serviceId, Boolean.TRUE, safePageable);
		return PageResponse.from(page.map(professional -> toView(professional, admin)));
	}

	@Transactional(readOnly = true)
	public ProfessionalView get(Long id) {
		boolean admin = CurrentUser.isAdmin();
		Professional professional = findProfessional(id);
		if (!admin && !professional.isPubliclyVisible()) {
			throw notFound();                        // para el público, un perfil o usuario inactivo no existe
		}
		return toView(professional, admin);
	}

	// Usuario y perfil en la misma transacción: si algo falla no queda ninguno de los dos
	@Transactional
	public ProfessionalAdminResponse create(CreateProfessionalRequest req) {
		requireAdmin();
		String email = User.normalizeEmail(req.email());                     // RN-11
		if (users.existsByEmail(email)) {
			throw emailAlreadyUsed();
		}
		Set<ServiceOffering> offered = resolveServices(req.serviceIds());    // el 422 sale antes de escribir nada
		User user = new User();
		user.setFullName(req.fullName().strip());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(req.password()));
		user.setPhone(req.phone());
		user.setRole(Role.PROFESSIONAL);
		try {
			users.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {                      // dos altas simultáneas con el mismo email
			throw emailAlreadyUsed();
		}
		Professional professional = new Professional();
		professional.setUser(user);
		professional.setSpecialty(req.specialty());                         // el request ya llega sin espacios sobrantes
		professional.setBio(req.bio());
		professional.setServices(offered);
		return mapper.toAdminResponse(professionals.save(professional));
	}

	@Transactional
	public ProfessionalAdminResponse update(Long id, UpdateProfessionalRequest req) {
		requireAdmin();
		Professional professional = findProfessional(id);
		professional.setSpecialty(req.specialty());                         // active y los datos del usuario no se tocan
		professional.setBio(req.bio());
		return mapper.toAdminResponse(professional);
	}

	@Transactional
	public ProfessionalAdminResponse setActive(Long id, boolean active) {
		requireAdmin();
		Professional professional = findProfessional(id);
		professional.setActive(active);              // RN-10; su usuario sigue como esté (eso es PATCH /users/{id}/active)
		return mapper.toAdminResponse(professional);
	}

	@Transactional
	public ProfessionalAdminResponse replaceServices(Long id, AssignServicesRequest req) {
		requireAdmin();
		Professional professional = findProfessional(id);
		Set<ServiceOffering> offered = resolveServices(req.serviceIds());   // con un id inválido no cambia nada
		professional.getServices().clear();
		professional.getServices().addAll(offered);
		return mapper.toAdminResponse(professional);
	}

	// Los ids repetidos se ignoran; si alguno no existe o está inactivo -> 422 con esos ids en details
	private Set<ServiceOffering> resolveServices(List<Long> serviceIds) {
		if (serviceIds == null || serviceIds.isEmpty()) {
			return new HashSet<>();
		}
		Set<Long> wanted = new TreeSet<>(serviceIds);                       // sin repetidos y en orden
		Map<Long, ServiceOffering> found = services.findAllById(wanted).stream()
				.collect(Collectors.toMap(ServiceOffering::getId, Function.identity()));
		List<ApiError.FieldIssue> invalid = wanted.stream()
				.filter(id -> !found.containsKey(id) || !found.get(id).isActive())
				.map(id -> new ApiError.FieldIssue("serviceIds", "El servicio " + id + " no existe o está inactivo"))
				.toList();
		if (!invalid.isEmpty()) {
			throw new BusinessRuleException("INVALID_SERVICE_IDS", "Hay servicios que no existen o están inactivos",
					invalid);
		}
		return new HashSet<>(found.values());
	}

	private ProfessionalView toView(Professional professional, boolean admin) {
		return admin ? mapper.toAdminResponse(professional) : mapper.toPublicResponse(professional);
	}

	private Professional findProfessional(Long id) {
		return professionals.findWithDetailsById(id).orElseThrow(ProfessionalService::notFound);
	}

	// Defensa en profundidad: SecurityConfig y @PreAuthorize ya exigen ADMIN
	private static void requireAdmin() {
		if (!CurrentUser.isAdmin()) {
			throw new ForbiddenOperationException("Solo un administrador puede gestionar profesionales");
		}
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Profesional no encontrado");
	}

	private static ConflictException emailAlreadyUsed() {
		return new ConflictException("EMAIL_ALREADY_USED", "El email ya está registrado");
	}

}
