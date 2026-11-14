CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT         NOT NULL REFERENCES bookings (id),
    user_id         BIGINT         NOT NULL REFERENCES users (id),
    -- Client-supplied Idempotency-Key header; unique per user.
    idempotency_key VARCHAR(255)   NOT NULL,
    -- SHA-256 of the request, to detect a key being reused for a different request.
    request_hash    VARCHAR(64)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_payments_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_payments_booking_id ON payments (booking_id);

-- Database-level guarantee against double charging: at most one successful payment per booking.
CREATE UNIQUE INDEX uk_payments_booking_succeeded ON payments (booking_id) WHERE status = 'SUCCEEDED';
