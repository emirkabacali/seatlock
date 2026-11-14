package com.emirkabacali.seatlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SeatlockApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private Flyway flyway;

	@Test
	void healthEndpointReportsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void flywayAppliesAllMigrations() {
		assertThat(flyway.info().applied()).isNotEmpty();
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void openApiDocumentIsServed() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("SeatLock API"));
	}

}
