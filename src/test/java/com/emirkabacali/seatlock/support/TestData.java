package com.emirkabacali.seatlock.support;

import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Role;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.User;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.SeatRepository;
import com.emirkabacali.seatlock.repository.UserRepository;
import com.emirkabacali.seatlock.repository.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;

/** Creates database fixtures directly through repositories (faster than going through the API). */
@TestComponent
public class TestData {

	private final VenueRepository venueRepository;
	private final EventRepository eventRepository;
	private final SeatRepository seatRepository;
	private final UserRepository userRepository;

	public TestData(VenueRepository venueRepository, EventRepository eventRepository, SeatRepository seatRepository,
			UserRepository userRepository) {
		this.venueRepository = venueRepository;
		this.eventRepository = eventRepository;
		this.seatRepository = seatRepository;
		this.userRepository = userRepository;
	}

	/** An ON_SALE event with {@code seatCount} seats in one row, 100.00 each. Returns the seat ids. */
	public OnSaleEvent onSaleEventWithSeats(int seatCount) {
		Venue venue = venueRepository.save(new Venue("Test Arena", "Istanbul", "Test St 1"));
		Event event = eventRepository.save(
				new Event(venue, "Test Event " + UUID.randomUUID(), null, Instant.now().plus(30, ChronoUnit.DAYS)));
		List<Seat> seats = new ArrayList<>();
		for (int number = 1; number <= seatCount; number++) {
			seats.add(new Seat(event, "A", "A", number, new BigDecimal("100.00")));
		}
		List<Long> seatIds = seatRepository.saveAll(seats).stream().map(Seat::getId).toList();
		event.changeStatus(EventStatus.ON_SALE);
		eventRepository.save(event);
		return new OnSaleEvent(event.getId(), seatIds);
	}

	public List<Long> users(int count) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(userRepository.save(new User("user-" + UUID.randomUUID() + "@example.com", "{noop}unused", Role.USER))
					.getId());
		}
		return ids;
	}

	public record OnSaleEvent(Long eventId, List<Long> seatIds) {
	}

}
