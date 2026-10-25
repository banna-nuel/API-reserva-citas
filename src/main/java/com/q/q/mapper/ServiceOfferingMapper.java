package com.q.q.mapper;

import org.springframework.stereotype.Component;

import com.q.q.dto.response.ServiceOfferingResponse;
import com.q.q.entity.ServiceOffering;

@Component
public class ServiceOfferingMapper {

	public ServiceOfferingResponse toResponse(ServiceOffering service) {
		return new ServiceOfferingResponse(service.getId(), service.getName(), service.getDescription(),
				service.getDurationMinutes(), service.getPrice(), service.isActive(), service.getCreatedAt());
	}

}
