package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.dto.seat.SeatGenerationRequest;
import com.emirkabacali.seatlock.dto.seat.SeatGenerationResponse;
import com.emirkabacali.seatlock.dto.seat.SeatResponse;
import com.emirkabacali.seatlock.dto.seat.SectionLayout;
import com.emirkabacali.seatlock.entity.Event;
import com.emirkabacali.seatlock.entity.EventStatus;
import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.SeatRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SeatService {

	static final int MAX_SEATS_PER_EVENT = 10_000;

	private final SeatRepository seatRepository;
	private final EventService eventService;

	public SeatService(SeatRepository seatRepository, EventService eventService) {
		this.seatRepository = seatRepository;
		this.eventService = eventService;
	}

	public List<SeatResponse> findByEvent(Long eventId, SeatStatus status) {
		eventService.getEvent(eventId); // 404 if the event does not exist
		List<Seat> seats = status == null
				? seatRepository.findByEventIdOrderByIdAsc(eventId)
				: seatRepository.findByEventIdAndStatusOrderByIdAsc(eventId, status);
		return seats.stream().map(SeatResponse::from).toList();
	}

	/**
	 * Generates the full seat map of an event in one go. Only allowed while the event is a DRAFT
	 * and has no seats yet, so the seat map can never change once tickets are on sale.
	 */
	@Transactional
	public SeatGenerationResponse generate(Long eventId, SeatGenerationRequest request) {
		validateLayout(request.sections());
		Event event = eventService.getEvent(eventId);
		if (event.getStatus() != EventStatus.DRAFT) {
			throw new ConflictException("Seats can only be generated for DRAFT events");
		}
		if (seatRepository.existsByEventId(eventId)) {
			throw new ConflictException("Event " + eventId + " already has seats");
		}

		List<Seat> seats = new ArrayList<>();
		for (SectionLayout section : request.sections()) {
			for (int row = 0; row < section.rows(); row++) {
				String rowLabel = RowLabels.of(row);
				for (int number = 1; number <= section.seatsPerRow(); number++) {
					seats.add(new Seat(event, section.name(), rowLabel, number, section.price()));
				}
			}
		}
		seatRepository.saveAll(seats);
		return new SeatGenerationResponse(eventId, seats.size());
	}

	private void validateLayout(List<SectionLayout> sections) {
		Set<String> names = new HashSet<>();
		long total = 0;
		for (SectionLayout section : sections) {
			if (!names.add(section.name())) {
				throw new BadRequestException("Duplicate section name: " + section.name());
			}
			total += (long) section.rows() * section.seatsPerRow();
		}
		if (total > MAX_SEATS_PER_EVENT) {
			throw new BadRequestException(
					"Seat map has " + total + " seats; the maximum is " + MAX_SEATS_PER_EVENT);
		}
	}

}
