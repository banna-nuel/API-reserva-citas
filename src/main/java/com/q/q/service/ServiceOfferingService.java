package com.q.q.service;

import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.q.q.dto.request.ServiceOfferingRequest;
import com.q.q.dto.response.PageResponse;
import com.q.q.dto.response.ServiceOfferingResponse;
import com.q.q.entity.ServiceOffering;
import com.q.q.exception.ConflictException;
import com.q.q.exception.ForbiddenOperationException;
import com.q.q.exception.ResourceNotFoundException;
import com.q.q.mapper.ServiceOfferingMapper;
import com.q.q.repository.ServiceOfferingRepository;
import com.q.q.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

	// Campos por los que GET /services puede ordenar
	private static final List<String> SORTABLE_FIELDS = List.of("id", "name", "price", "durationMinutes", "createdAt");

	private final ServiceOfferingRepository services;
	private final ServiceOfferingMapper mapper;

	@Transactional(readOnly = true)
	public PageResponse<ServiceOfferingResponse> list(Boolean active, String q, Pageable pageable) {
		// Solo el ADMIN ve los inactivos; para los demás el parámetro active se ignora
		Boolean activeFilter = CurrentUser.isAdmin() ? active : Boolean.TRUE;
		Page<ServiceOffering> page = services.search(activeFilter, containsPattern(q),
				SafeSort.apply(pageable, SORTABLE_FIELDS));
		return PageResponse.from(page.map(mapper::toResponse));
	}

	@Transactional(readOnly = true)
	public ServiceOfferingResponse get(Long id) {
		ServiceOffering service = findService(id);
		if (!service.isActive() && !CurrentUser.isAdmin()) {
			throw notFound();                        // para el público, un servicio inactivo no existe
		}
		return mapper.toResponse(service);
	}

	@Transactional
	public ServiceOfferingResponse create(ServiceOfferingRequest req) {
		requireAdmin();
		if (services.existsByNameIgnoreCase(req.name())) {
			throw nameAlreadyExists();
		}
		ServiceOffering service = new ServiceOffering();
		applyFields(service, req);
		return saveCheckingName(service);
	}

	@Transactional
	public ServiceOfferingResponse update(Long id, ServiceOfferingRequest req) {
		requireAdmin();
		ServiceOffering service = findService(id);
		if (services.existsByNameIgnoreCaseAndIdNot(req.name(), id)) {      // conservar el propio nombre sí se puede
			throw nameAlreadyExists();
		}
		applyFields(service, req);                   // active no se toca
		return saveCheckingName(service);
	}

	@Transactional
	public void deactivate(Long id) {
		requireAdmin();
		findService(id).setActive(false);            // RN-10: borrado lógico; sobre uno ya inactivo no cambia nada
	}

	@Transactional
	public ServiceOfferingResponse setActive(Long id, boolean active) {
		requireAdmin();
		ServiceOffering service = findService(id);
		service.setActive(active);
		return mapper.toResponse(service);
	}

	private ServiceOfferingResponse saveCheckingName(ServiceOffering service) {
		try {
			return mapper.toResponse(services.saveAndFlush(service));
		} catch (DataIntegrityViolationException ex) {     // otro ADMIN guardó el mismo nombre entre la comprobación y el SQL
			throw nameAlreadyExists();
		}
	}

	private ServiceOffering findService(Long id) {
		return services.findById(id).orElseThrow(ServiceOfferingService::notFound);
	}

	private static void applyFields(ServiceOffering service, ServiceOfferingRequest req) {
		service.setName(req.name());                 // el request ya llega sin espacios sobrantes
		service.setDescription(req.description());
		service.setDurationMinutes(req.durationMinutes());
		service.setPrice(req.price().setScale(2, RoundingMode.UNNECESSARY));   // @Digits ya limita a 2 decimales
	}

	// "q" -> patrón LIKE de "contiene" en minúsculas; los % y _ del usuario se buscan como texto literal
	private static String containsPattern(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		String escaped = q.strip().toLowerCase(Locale.ROOT)
				.replace("!", "!!").replace("%", "!%").replace("_", "!_");   // '!' es el escape de la consulta
		return "%" + escaped + "%";
	}

	// Defensa en profundidad: SecurityConfig y @PreAuthorize ya exigen ADMIN
	private static void requireAdmin() {
		if (!CurrentUser.isAdmin()) {
			throw new ForbiddenOperationException("Solo un administrador puede gestionar el catálogo de servicios");
		}
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException("Servicio no encontrado");
	}

	private static ConflictException nameAlreadyExists() {
		return new ConflictException("SERVICE_NAME_ALREADY_EXISTS", "Ya existe un servicio con ese nombre");
	}

}
