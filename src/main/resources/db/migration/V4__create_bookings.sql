CREATE TABLE bookings (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT         NOT NULL REFERENCES users (id),
    event_id    BIGINT         NOT NULL REFERENCES events (id),
    total_price NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    expires_at  TIMESTAMPTZ    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Optimistic locking for concurrent state changes (pay / cancel / expire) on the same booking.
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_bookings_total_price_non_negative CHECK (total_price >= 0)
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
-- Supports finding PENDING bookings whose hold has expired.
CREATE INDEX idx_bookings_status_expires_at ON bookings (status, expires_at);

CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id    BIGINT NOT NULL REFERENCES seats (id),
    PRIMARY KEY (booking_id, seat_id)
);

CREATE INDEX idx_booking_seats_seat_id ON booking_seats (seat_id);
