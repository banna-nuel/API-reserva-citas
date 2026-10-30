package com.q.q.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.q.q.exception.ApiError;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Citas API", version = "v1",
		description = "API REST para reserva de citas. Todos los errores tienen el formato de ApiError (sección 8.4 de "
				+ "docs/GUIA.md). Los endpoints con candado necesitan el JWT de POST /auth/login en Authorize."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	private static final String API_ERROR_REF = "#/components/schemas/ApiError";

	// Toda respuesta 4xx lleva el cuerpo de ApiError: así cada @ApiResponse de error solo declara su código y su
	// descripción. Se reemplaza siempre: springdoc rellena las respuestas sin contenido con el tipo que devuelve el
	// método, y un 404 de GET /users/me aparecería como un UserResponse
	@Bean
	OpenApiCustomizer errorResponsesUseApiError() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			ResolvedSchema apiError = ModelConverters.getInstance()
					.resolveAsResolvedSchema(new AnnotatedType(ApiError.class).resolveAsRef(false));
			openApi.getComponents().addSchemas("ApiError", apiError.schema);
			apiError.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
					operation.getResponses().forEach((code, response) -> {
						if (code.startsWith("4")) {
							response.setContent(new Content().addMediaType("application/json",
									new MediaType().schema(new Schema<>().$ref(API_ERROR_REF))));
						}
					})));
		};
	}

}
