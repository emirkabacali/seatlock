package com.emirkabacali.seatlock.controller;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Registration, login and role-based access control, using real tokens issued by the API. */
class AuthSecurityIntegrationTest extends AbstractIntegrationTest {

	private static final String VENUE_JSON = """
			{"name": "Secured Arena", "city": "Istanbul", "address": "Huzur Mah. 1"}
			""";

	@Test
	void registerCreatesUserWithoutExposingPassword() throws Exception {
		String email = uniqueEmail();

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email.toUpperCase(), "secret-password")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email)) // normalized to lower case
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registeringTheSameEmailTwiceReturns409() throws Exception {
		String email = uniqueEmail();
		register(email, "secret-password");

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, "another-password")))
				.andExpect(status().isConflict());
	}

	@Test
	void registerValidatesEmailAndPasswordLength() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("not-an-email", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void loginReturnsBearerToken() throws Exception {
		String email = uniqueEmail();
		register(email, "secret-password");

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, "secret-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
				.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void loginWithWrongPasswordOrUnknownEmailReturnsSame401() throws Exception {
		String email = uniqueEmail();
		register(email, "secret-password");

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, "wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid email or password"));

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(uniqueEmail(), "secret-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid email or password"));
	}

	@Test
	void catalogIsReadableWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/venues")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/events")).andExpect(status().isOk());
	}

	@Test
	void writingWithoutTokenReturns401ProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Unauthorized"));
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void regularUserCannotManageCatalog() throws Exception {
		String email = uniqueEmail();
		register(email, "secret-password");
		String token = login(email, "secret-password");

		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void bootstrappedAdminCanManageCatalog() throws Exception {
		String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content(VENUE_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated());
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password)))
				.andExpect(status().isCreated());
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String credentials(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
