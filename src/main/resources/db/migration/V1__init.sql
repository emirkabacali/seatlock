-- Core catalog schema: venues, events and their seats.

CREATE TABLE venues (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    address    VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    venue_id    BIGINT       NOT NULL REFERENCES venues (id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    starts_at   TIMESTAMPTZ  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'ON_SALE', 'CANCELLED', 'FINISHED'))
);

CREATE INDEX idx_events_venue_id ON events (venue_id);

-- "row" is a reserved word in SQL, so the column is called row_label.
-- version is used by JPA optimistic locking (@Version).
CREATE TABLE seats (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    section     VARCHAR(20)   NOT NULL,
    row_label   VARCHAR(10)   NOT NULL,
    seat_number INT           NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_seats_event_section_row_number UNIQUE (event_id, section, row_label, seat_number),
    CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    CONSTRAINT ck_seats_number_positive CHECK (seat_number > 0),
    CONSTRAINT ck_seats_price_non_negative CHECK (price >= 0)
);
