package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.emirkabacali.seatlock.dto.seat.SeatGenerationRequest;
import com.emirkabacali.seatlock.dto.seat.SeatGenerationResponse;
import com.emirkabacali.seatlock.dto.seat.SectionLayout;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.entity.Venue;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.SeatRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

	@Mock
	private SeatRepository seatRepository;

	@Mock
	private EventService eventService;

	@InjectMocks
	private SeatService seatService;

	@Captor
	private ArgumentCaptor<List<Seat>> seatsCaptor;

	private final Event draftEvent = new Event(new Venue("Arena", "Istanbul", "Main St 1"), "Concert", null,
			Instant.now().plus(30, ChronoUnit.DAYS));

	@Test
	void generatesSectionsTimesRowsTimesSeats() {
		when(eventService.getEvent(1L)).thenReturn(draftEvent);
		when(seatRepository.existsByEventId(1L)).thenReturn(false);
		SeatGenerationRequest request = new SeatGenerationRequest(List.of(
				new SectionLayout("VIP", 2, 3, new BigDecimal("500.00")),
				new SectionLayout("B", 1, 4, new BigDecimal("100.00"))));

		SeatGenerationResponse response = seatService.generate(1L, request);

		assertThat(response.seatsCreated()).isEqualTo(2 * 3 + 4);
		verify(seatRepository).saveAll(seatsCaptor.capture());
		List<Seat> seats = seatsCaptor.getValue();
		assertThat(seats).hasSize(10);
		assertThat(seats).allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE);
		assertThat(seats.get(0)).extracting(Seat::getSection, Seat::getRowLabel, Seat::getSeatNumber)
				.containsExactly("VIP", "A", 1);
		assertThat(seats.get(5)).extracting(Seat::getSection, Seat::getRowLabel, Seat::getSeatNumber)
				.containsExactly("VIP", "B", 3);
		assertThat(seats.get(9)).extracting(Seat::getSection, Seat::getRowLabel, Seat::getPrice)
				.containsExactly("B", "A", new BigDecimal("100.00"));
	}

	@Test
	void rejectsGenerationWhenEventIsNotDraft() {
		draftEvent.changeStatus(EventStatus.ON_SALE);
		when(eventService.getEvent(1L)).thenReturn(draftEvent);

		assertThatThrownBy(() -> seatService.generate(1L, singleSection(1, 1)))
				.isInstanceOf(ConflictException.class);
		verify(seatRepository, never()).saveAll(any());
	}

	@Test
	void rejectsGenerationWhenSeatsAlreadyExist() {
		when(eventService.getEvent(1L)).thenReturn(draftEvent);
		when(seatRepository.existsByEventId(1L)).thenReturn(true);

		assertThatThrownBy(() -> seatService.generate(1L, singleSection(1, 1)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("already has seats");
	}

	@Test
	void rejectsDuplicateSectionNames() {
		SeatGenerationRequest request = new SeatGenerationRequest(List.of(
				new SectionLayout("A", 1, 1, BigDecimal.TEN),
				new SectionLayout("A", 1, 1, BigDecimal.TEN)));

		assertThatThrownBy(() -> seatService.generate(1L, request))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Duplicate section");
	}

	@Test
	void rejectsSeatMapsAboveTheLimit() {
		// 100 rows x 101 seats = 10,100 > 10,000
		assertThatThrownBy(() -> seatService.generate(1L, singleSection(100, 101)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("maximum");
	}

	private static SeatGenerationRequest singleSection(int rows, int seatsPerRow) {
		return new SeatGenerationRequest(List.of(new SectionLayout("A", rows, seatsPerRow, BigDecimal.TEN)));
	}

}
