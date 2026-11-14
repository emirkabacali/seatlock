package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Seat;
import com.emirkabacali.seatlock.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	// Seats are generated section by section, row by row, so id order is the natural seat-map order.
	// (Sorting by row label would put "AA" between "A" and "B".)
	List<Seat> findByEventIdOrderByIdAsc(Long eventId);

	List<Seat> findByEventIdAndStatusOrderByIdAsc(Long eventId, SeatStatus status);

	boolean existsByEventId(Long eventId);

	/** Plain read without any lock (used by the naive and the optimistic strategy). */
	List<Seat> findByEventIdAndIdInOrderByIdAsc(Long eventId, Collection<Long> ids);

	/**
	 * {@code SELECT ... FOR UPDATE}: other transactions that try to lock the same rows wait until
	 * this transaction ends. Rows are locked in id order so that two bookings with overlapping
	 * seats always acquire locks in the same order and cannot deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seat s where s.event.id = :eventId and s.id in :ids order by s.id")
	List<Seat> findForUpdate(@Param("eventId") Long eventId, @Param("ids") Collection<Long> ids);

	/**
	 * Bulk JPQL update. It does NOT check or increment {@code @Version}, so it provides no
	 * concurrency protection. Only used by the naive strategy to demonstrate the race condition.
	 */
	@Modifying
	@Query("update Seat s set s.status = :status where s.id in :ids")
	int updateStatusWithoutVersionCheck(@Param("ids") Collection<Long> ids, @Param("status") SeatStatus status);

}
