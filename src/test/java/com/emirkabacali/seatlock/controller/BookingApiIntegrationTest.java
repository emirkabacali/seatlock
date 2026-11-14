package com.emirkabacali.seatlock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emirkabacali.seatlock.AbstractIntegrationTest;
import com.emirkabacali.seatlock.support.TestData;
import com.emirkabacali.seatlock.support.TestData.OnSaleEvent;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestData.class)
class BookingApiIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TestData testData;

	@Test
	void bookingHoldsSeatsAndCanBeCancelled() throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(3);
		long userId = testData.users(1).get(0);
		List<Long> seats = event.seatIds();

		String body = book(userId, event.eventId(), seats.get(0), seats.get(1))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.seats", hasSize(2)))
				.andExpect(jsonPath("$.totalPrice").value(200.00))
				.andExpect(jsonPath("$.expiresAt").exists())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) JsonPath.read(body, "$.id")).longValue();

		mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()).param("status", "HELD"))
				.andExpect(jsonPath("$", hasSize(2)));

		mockMvc.perform(get("/api/v1/bookings/me").with(asUser(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(bookingId));

		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId).with(asUser(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()).param("status", "AVAILABLE"))
				.andExpect(jsonPath("$", hasSize(3)));

		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId).with(asUser(userId)))
				.andExpect(status().isConflict());

		// Cancelling released the Redis holds too, so another user can book the seats immediately.
		long otherUser = testData.users(1).get(0);
		book(otherUser, event.eventId(), seats.get(0), seats.get(1)).andExpect(status().isCreated());
	}

	@Test
	void heldSeatCannotBeBookedAgain() throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(2);
		List<Long> users = testData.users(2);
		Long seat = event.seatIds().get(0);

		book(users.get(0), event.eventId(), seat).andExpect(status().isCreated());
		book(users.get(1), event.eventId(), seat, event.seatIds().get(1))
				.andExpect(status().isConflict())
				// Rejected by the Redis hold before reaching the database
				.andExpect(jsonPath("$.detail").value("One or more seats are currently held by another booking"));

		// The failed booking must not have held the other seat either (all or nothing).
		mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()).param("status", "AVAILABLE"))
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void usersCannotSeeOrCancelOthersBookings() throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		List<Long> users = testData.users(2);
		String body = book(users.get(0), event.eventId(), event.seatIds().get(0))
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) JsonPath.read(body, "$.id")).longValue();

		mockMvc.perform(get("/api/v1/bookings/{id}", bookingId).with(asUser(users.get(1))))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId).with(asUser(users.get(1))))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidBookingRequests() throws Exception {
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		OnSaleEvent otherEvent = testData.onSaleEventWithSeats(1);
		long userId = testData.users(1).get(0);
		Long seat = event.seatIds().get(0);

		book(userId, event.eventId(), seat, seat).andExpect(status().isBadRequest());
		book(userId, event.eventId(), otherEvent.seatIds().get(0)).andExpect(status().isBadRequest());
		book(userId, 999_999L, seat).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/bookings").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "seatIds": []}
						""".formatted(event.eventId())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void bookingRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": 1, "seatIds": [1]}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void draftEventCannotBeBooked() throws Exception {
		long userId = testData.users(1).get(0);
		long venueId = ((Number) JsonPath.read(mockMvc.perform(post("/api/v1/venues").with(asAdmin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "Draft Arena", "city": "Istanbul", "address": "X"}
						""")).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
		long eventId = ((Number) JsonPath.read(mockMvc.perform(post("/api/v1/events").with(asAdmin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"venueId": %d, "name": "Draft Show", "startsAt": "2030-01-01T20:00:00Z"}
						""".formatted(venueId))).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
		mockMvc.perform(post("/api/v1/events/{id}/seats", eventId).with(asAdmin()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sections": [{"name": "A", "rows": 1, "seatsPerRow": 1, "price": 10}]}
						"""));
		long seatId = ((Number) JsonPath.read(mockMvc.perform(get("/api/v1/events/{id}/seats", eventId))
				.andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

		book(userId, eventId, seatId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Event " + eventId + " is not on sale"));
	}

	/** The same guarantee as the service-level test, observed through HTTP: one 201, the rest 409. */
	@Test
	void concurrentHttpRequestsForTheSameSeatProduceOne201AndOnly409s() throws Exception {
		int threads = 20;
		OnSaleEvent event = testData.onSaleEventWithSeats(1);
		Long seat = event.seatIds().get(0);
		List<Long> users = testData.users(threads);

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (Long userId : users) {
				Callable<Integer> task = () -> {
					ready.countDown();
					start.await();
					return book(userId, event.eventId(), seat).andReturn().getResponse().getStatus();
				};
				futures.add(pool.submit(task));
			}
			ready.await(10, TimeUnit.SECONDS);
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get(60, TimeUnit.SECONDS));
			}

			assertThat(statuses.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting())))
					.containsOnlyKeys(201, 409)
					.containsEntry(201, 1L)
					.containsEntry(409, (long) threads - 1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private ResultActions book(long userId, long eventId, Long... seatIds) throws Exception {
		String ids = List.of(seatIds).stream().map(String::valueOf).collect(Collectors.joining(","));
		return mockMvc.perform(post("/api/v1/bookings").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"eventId": %d, "seatIds": [%s]}
						""".formatted(eventId, ids)));
	}

}
