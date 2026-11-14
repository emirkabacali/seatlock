package com.emirkabacali.seatlock.repository;

import com.emirkabacali.seatlock.entity.Booking;
import com.emirkabacali.seatlock.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	@EntityGraph(attributePaths = { "event", "seats" })
	Optional<Booking> findByIdAndUserId(Long id, Long userId);

	/**
	 * Only the event is fetched with the page. Join-fetching a collection (seats) would make
	 * Hibernate apply LIMIT/OFFSET in memory, so seats are loaded lazily in batches instead
	 * (see {@code hibernate.default_batch_fetch_size}).
	 */
	@EntityGraph(attributePaths = "event")
	@Query(value = "select b from Booking b where b.user.id = :userId",
			countQuery = "select count(b) from Booking b where b.user.id = :userId")
	Page<Booking> findByUserId(@Param("userId") Long userId, Pageable pageable);

	/**
	 * PENDING bookings whose hold has expired, locked with {@code FOR UPDATE SKIP LOCKED}
	 * (lock timeout -2 = SKIP LOCKED in Hibernate). Rows locked by another transaction — e.g. a
	 * payment in progress or a second application instance running the same job — are skipped
	 * instead of waited for, so several instances can share the work safely.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select b from Booking b where b.status = com.emirkabacali.seatlock.entity.BookingStatus.PENDING "
			+ "and b.expiresAt <= :now order by b.expiresAt")
	List<Booking> findOverdueForUpdate(@Param("now") Instant now, Pageable pageable);

	/** Number of bookings in the given states that contain the seat. */
	@Query("select count(b) from Booking b join b.seats s where s.id = :seatId and b.status in :statuses")
	long countBySeatIdAndStatusIn(@Param("seatId") Long seatId, @Param("statuses") Collection<BookingStatus> statuses);

}
