package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	// Seats are generated section by section, row by row, so id order is the natural seat-map order.
	// (Sorting by row label would put "AA" between "A" and "B".)
	List<Seat> findByEventIdOrderByIdAsc(Long eventId);

	List<Seat> findByEventIdAndStatusOrderByIdAsc(Long eventId, SeatStatus status);

	boolean existsByEventId(Long eventId);

}
