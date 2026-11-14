package com.emirkabacali.seatlock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests: full application context, MockMvc and real infrastructure
 * from Testcontainers. All subclasses share one cached Spring context (and one set of containers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

}
