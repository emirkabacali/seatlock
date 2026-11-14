package com.emirkabacali.seatlock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI seatLockOpenApi() {
		return new OpenAPI().info(new Info()
				.title("SeatLock API")
				.description("Event ticket booking API with concurrency-safe seat reservation")
				.version("v1")
				.license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
	}

}
