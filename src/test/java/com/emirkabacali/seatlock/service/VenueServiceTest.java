package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.dto.venue.VenueRequest;
import com.emirkabacali.seatlock.dto.venue.VenueResponse;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.exception.ResourceNotFoundException;
import com.emirkabacali.seatlock.repository.EventRepository;
import com.emirkabacali.seatlock.repository.VenueRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

	@Mock
	private VenueRepository venueRepository;

	@Mock
	private EventRepository eventRepository;

	@InjectMocks
	private VenueService venueService;

	@Test
	void createSavesVenueAndReturnsResponse() {
		when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));

		VenueResponse response = venueService.create(new VenueRequest("Arena", "Istanbul", "Main St 1"));

		assertThat(response.name()).isEqualTo("Arena");
		assertThat(response.city()).isEqualTo("Istanbul");
	}

	@Test
	void updateChangesFieldsOfExistingVenue() {
		Venue venue = new Venue("Old", "Ankara", "Old St");
		when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

		VenueResponse response = venueService.update(1L, new VenueRequest("New", "Izmir", "New St"));

		assertThat(response.name()).isEqualTo("New");
		assertThat(venue.getCity()).isEqualTo("Izmir");
	}

	@Test
	void findByIdThrowsWhenVenueDoesNotExist() {
		when(venueRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> venueService.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deleteIsRejectedWhenVenueHasEvents() {
		Venue venue = new Venue("Arena", "Istanbul", "Main St 1");
		when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
		when(eventRepository.existsByVenueId(1L)).thenReturn(true);

		assertThatThrownBy(() -> venueService.delete(1L)).isInstanceOf(ConflictException.class);
		verify(venueRepository, never()).delete(any());
	}

	@Test
	void deleteRemovesVenueWithoutEvents() {
		Venue venue = new Venue("Arena", "Istanbul", "Main St 1");
		when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
		when(eventRepository.existsByVenueId(1L)).thenReturn(false);

		venueService.delete(1L);

		verify(venueRepository).delete(venue);
	}

}
