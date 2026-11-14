package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.dto.event.EventRequest;
import com.emirkabacali.seatlock.dto.event.EventResponse;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.EventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	private static final Instant NEXT_MONTH = Instant.now().plus(30, ChronoUnit.DAYS);

	@Mock
	private EventRepository eventRepository;

	@Mock
	private VenueService venueService;

	@InjectMocks
	private EventService eventService;

	private final Venue venue = new Venue("Arena", "Istanbul", "Main St 1");

	@Test
	void newEventStartsAsDraft() {
		when(venueService.getVenue(1L)).thenReturn(venue);
		when(eventRepository.save(any(Event.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		EventResponse response = eventService.create(new EventRequest(1L, "Concert", null, NEXT_MONTH));

		assertThat(response.status()).isEqualTo(EventStatus.DRAFT);
		assertThat(response.venueName()).isEqualTo("Arena");
	}

	@Test
	void changeStatusAppliesAllowedTransition() {
		Event event = new Event(venue, "Concert", null, NEXT_MONTH);
		when(eventRepository.findWithVenueById(1L)).thenReturn(Optional.of(event));

		EventResponse response = eventService.changeStatus(1L, EventStatus.ON_SALE);

		assertThat(response.status()).isEqualTo(EventStatus.ON_SALE);
	}

	@Test
	void changeStatusRejectsInvalidTransition() {
		Event event = new Event(venue, "Concert", null, NEXT_MONTH);
		when(eventRepository.findWithVenueById(1L)).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.changeStatus(1L, EventStatus.FINISHED))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("DRAFT to FINISHED");
		assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
	}

	@Test
	void updateIsRejectedForCancelledEvent() {
		Event event = new Event(venue, "Concert", null, NEXT_MONTH);
		event.changeStatus(EventStatus.CANCELLED);
		when(eventRepository.findWithVenueById(1L)).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> eventService.update(1L, new EventRequest(1L, "Renamed", null, NEXT_MONTH)))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void findByIdThrowsWhenEventDoesNotExist() {
		when(eventRepository.findWithVenueById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> eventService.findById(42L)).isInstanceOf(ResourceNotFoundException.class);
	}

}
