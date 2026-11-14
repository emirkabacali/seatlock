package com.emirkabacali.seatlock;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base class for integration tests: full application context, MockMvc and real infrastructure
 * from Testcontainers. All subclasses share one cached Spring context (and one set of containers).
 */
@SpringBootTest(properties = {
		"seatlock.security.jwt.secret=test-secret-that-is-long-enough-for-hs256-signing",
		"seatlock.security.admin.email=admin@test.local",
		"seatlock.security.admin.password=admin-password",
		// Tests trigger expiry explicitly instead of waiting for the scheduler.
		"seatlock.booking.expiry-job.enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

	protected static final String ADMIN_EMAIL = "admin@test.local";
	protected static final String ADMIN_PASSWORD = "admin-password";

	@Autowired
	protected MockMvc mockMvc;

	/** Authenticates the request as an admin without going through login (token is mocked). */
	protected static RequestPostProcessor asAdmin() {
		return jwt().jwt(token -> token.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	/** Authenticates the request as a regular user with the given id (token is mocked). */
	protected static RequestPostProcessor asUser(long userId) {
		return jwt().jwt(token -> token.subject(String.valueOf(userId)))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

}
