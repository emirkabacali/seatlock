package com.emirkabacali.seatlock.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end tests of the venue / event / seat API against a real PostgreSQL. */
class CatalogApiIntegrationTest extends AbstractIntegrationTest {

	private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

	@Test
	void venueCrudRoundTrip() throws Exception {
		long venueId = createVenue("Volkswagen Arena");

		mockMvc.perform(get("/api/v1/venues/{id}", venueId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Volkswagen Arena"));

		mockMvc.perform(put("/api/v1/venues/{id}", venueId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Renamed Arena", "city": "Istanbul", "address": "Huzur Mah. 1"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed Arena"));

		mockMvc.perform(get("/api/v1/venues").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].name", hasItem("Renamed Arena")));

		mockMvc.perform(delete("/api/v1/venues/{id}", venueId)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/venues/{id}", venueId)).andExpect(status().isNotFound());
	}

	@Test
	void invalidVenueReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "city": "Istanbul"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("name")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("address")));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownVenueReturns404ProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/venues/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Venue with id 999999 was not found"));
	}

	@Test
	void invalidSortPropertyReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/venues").param("sort", "doesNotExist"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void venueWithEventsCannotBeDeleted() throws Exception {
		long venueId = createVenue("Busy Arena");
		createEvent(venueId, "Some Concert");

		mockMvc.perform(delete("/api/v1/venues/{id}", venueId))
				.andExpect(status().isConflict());
	}

	@Test
	void eventInThePastIsRejected() throws Exception {
		long venueId = createVenue("Past Arena");

		mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"venueId": %d, "name": "Old Show", "startsAt": "2020-01-01T20:00:00Z"}
						""".formatted(venueId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("startsAt"));
	}

	@Test
	void fullEventLifecycleWithSeatGeneration() throws Exception {
		long venueId = createVenue("Lifecycle Arena");
		long eventId = createEvent(venueId, "Rock Night");

		mockMvc.perform(get("/api/v1/events/{id}", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.venueName").value("Lifecycle Arena"));

		// 2 sections: VIP 2x5 + B 30x4 = 130 seats (30 rows exercises labels beyond Z)
		mockMvc.perform(post("/api/v1/events/{id}/seats", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sections": [
						  {"name": "VIP", "rows": 2, "seatsPerRow": 5, "price": 750.00},
						  {"name": "B", "rows": 30, "seatsPerRow": 4, "price": 120.50}
						]}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.seatsCreated").value(130));

		mockMvc.perform(get("/api/v1/events/{id}/seats", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(130)))
				.andExpect(jsonPath("$[0].section").value("VIP"))
				.andExpect(jsonPath("$[0].row").value("A"))
				.andExpect(jsonPath("$[0].number").value(1))
				.andExpect(jsonPath("$[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$[129].row").value("AD"))
				.andExpect(jsonPath("$[129].price").value(120.50));

		// The seat map cannot be generated twice
		mockMvc.perform(post("/api/v1/events/{id}/seats", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sections": [{"name": "C", "rows": 1, "seatsPerRow": 1, "price": 10}]}
						"""))
				.andExpect(status().isConflict());

		changeStatus(eventId, "ON_SALE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ON_SALE"));

		mockMvc.perform(get("/api/v1/events").param("status", "ON_SALE").param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id", hasItem((int) eventId)));

		// ON_SALE -> DRAFT is not an allowed transition
		changeStatus(eventId, "DRAFT").andExpect(status().isConflict());
		changeStatus(eventId, "FINISHED").andExpect(status().isOk());
	}

	@Test
	void seatGenerationValidatesLayout() throws Exception {
		long eventId = createEvent(createVenue("Validation Arena"), "Validation Show");

		mockMvc.perform(post("/api/v1/events/{id}/seats", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sections": [{"name": "A", "rows": 0, "seatsPerRow": 10, "price": -5}]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].rows")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("sections[0].price")));
	}

	@Test
	void invalidStatusValueReturns400() throws Exception {
		long eventId = createEvent(createVenue("Status Arena"), "Status Show");

		changeStatus(eventId, "SOLD_OUT").andExpect(status().isBadRequest());
	}

	private long createVenue(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "city": "Istanbul", "address": "Huzur Mah. 1"}
						""".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn();
		return idOf(result);
	}

	private long createEvent(long venueId, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"venueId": %d, "name": "%s", "description": "Test event", "startsAt": "%s"}
						""".formatted(venueId, name, FUTURE)))
				.andExpect(status().isCreated())
				.andReturn();
		return idOf(result);
	}

	private org.springframework.test.web.servlet.ResultActions changeStatus(long eventId, String status)
			throws Exception {
		return mockMvc.perform(patch("/api/v1/events/{id}/status", eventId).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "%s"}
						""".formatted(status)));
	}

	private static long idOf(MvcResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

}
