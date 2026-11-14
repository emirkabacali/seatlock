package com.emirkabacali.seatlock.service;

import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import com.emirkabacali.seatlock.exception.BadRequestException;
import com.emirkabacali.seatlock.exception.ConflictException;
import com.emirkabacali.seatlock.repository.SeatRepository;
import java.util.List;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves seats from AVAILABLE to HELD for a new booking, using one of three strategies.
 *
 * <p>All methods must run inside the caller's transaction ({@link Propagation#MANDATORY}), so the
 * seat update and the booking insert are committed or rolled back together.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class SeatReserver {

	private final SeatRepository seatRepository;

	public SeatReserver(SeatRepository seatRepository) {
		this.seatRepository = seatRepository;
	}

	public List<Seat> reserve(Long eventId, Set<Long> seatIds, LockingStrategy strategy) {
		return switch (strategy) {
			case NAIVE -> reserveNaive(eventId, seatIds);
			case OPTIMISTIC -> reserveOptimistic(eventId, seatIds);
			case PESSIMISTIC -> reservePessimistic(eventId, seatIds);
		};
	}

	/**
	 * UNSAFE. Read, check, then write with a bulk update that ignores {@code @Version}.
	 * Between the check and the write another transaction can do exactly the same, so both
	 * see AVAILABLE and both "successfully" hold the seat: a double booking.
	 */
	private List<Seat> reserveNaive(Long eventId, Set<Long> seatIds) {
		List<Seat> seats = seatRepository.findByEventIdAndIdInOrderByIdAsc(eventId, seatIds);
		requireAllAvailable(seats, seatIds);
		seatRepository.updateStatusWithoutVersionCheck(seatIds, SeatStatus.HELD);
		return seats;
	}

	/**
	 * Read without locks, change the entities, flush. Hibernate issues
	 * {@code UPDATE seats ... WHERE id = ? AND version = ?}. If another transaction updated the
	 * seat after we read it, the version no longer matches, zero rows are updated and Hibernate
	 * throws: the loser gets a conflict instead of a double booking.
	 */
	private List<Seat> reserveOptimistic(Long eventId, Set<Long> seatIds) {
		List<Seat> seats = seatRepository.findByEventIdAndIdInOrderByIdAsc(eventId, seatIds);
		requireAllAvailable(seats, seatIds);
		seats.forEach(Seat::hold);
		try {
			seatRepository.flush();
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			throw new ConflictException("One or more seats were just booked by someone else");
		}
		return seats;
	}

	/**
	 * Lock the rows first ({@code SELECT ... FOR UPDATE}), then check and write. A concurrent
	 * transaction blocks on the lock until we commit, then reads the new status (HELD) and fails
	 * the availability check.
	 */
	private List<Seat> reservePessimistic(Long eventId, Set<Long> seatIds) {
		List<Seat> seats = seatRepository.findForUpdate(eventId, seatIds);
		requireAllAvailable(seats, seatIds);
		seats.forEach(Seat::hold);
		return seats;
	}

	private static void requireAllAvailable(List<Seat> seats, Set<Long> requestedIds) {
		if (seats.size() != requestedIds.size()) {
			throw new BadRequestException("One or more seats do not exist or do not belong to this event");
		}
		List<Long> unavailable = seats.stream().filter(seat -> !seat.isAvailable()).map(Seat::getId).toList();
		if (!unavailable.isEmpty()) {
			throw new ConflictException("Seats not available: " + unavailable);
		}
	}

}
