package com.emirkabacali.seatlock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI seatLockOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("SeatLock API")
						.description("Event ticket booking API with concurrency-safe seat reservation. "
								+ "Log in via /api/v1/auth/login and use the token with the Authorize button.")
						.version("v1")
						.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
