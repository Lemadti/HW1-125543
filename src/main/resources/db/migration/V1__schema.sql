CREATE TABLE station (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32)      NOT NULL UNIQUE,
    name         VARCHAR(100)     NOT NULL,
    address      VARCHAR(200)     NOT NULL,
    postal_code  VARCHAR(16)      NOT NULL,
    city         VARCHAR(100)     NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    access_notes VARCHAR(300)
);

CREATE TABLE compartment (
    id            BIGSERIAL PRIMARY KEY,
    station_id    BIGINT      NOT NULL REFERENCES station (id),
    code          VARCHAR(16) NOT NULL,
    position      INT         NOT NULL,
    size          VARCHAR(1)  NOT NULL CHECK (size IN ('S', 'M', 'L')),
    status        VARCHAR(20) NOT NULL DEFAULT 'IN_SERVICE' CHECK (status IN ('IN_SERVICE', 'OUT_OF_SERVICE')),
    status_reason VARCHAR(300),
    CONSTRAINT uq_compartment_code UNIQUE (station_id, code)
);

CREATE TABLE parcel (
    id                          BIGSERIAL PRIMARY KEY,
    version                     BIGINT         NOT NULL DEFAULT 0,
    tracking_code               VARCHAR(32)    NOT NULL UNIQUE,
    delivery_code               VARCHAR(64)    NOT NULL UNIQUE,
    station_id                  BIGINT         NOT NULL REFERENCES station (id),
    compartment_id              BIGINT         NOT NULL REFERENCES compartment (id),
    size                        VARCHAR(1)     NOT NULL,
    state                       VARCHAR(20)    NOT NULL,
    sender_name                 VARCHAR(120)   NOT NULL,
    sender_email                VARCHAR(200)   NOT NULL,
    sender_phone                VARCHAR(40)    NOT NULL,
    recipient_name              VARCHAR(120)   NOT NULL,
    recipient_email             VARCHAR(200)   NOT NULL,
    recipient_phone             VARCHAR(40)    NOT NULL,
    lease_start                 TIMESTAMPTZ    NOT NULL,
    lease_hours                 INT            NOT NULL,
    price                       NUMERIC(10, 2) NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL,
    drop_off_deadline           TIMESTAMPTZ    NOT NULL,
    deposited_at                TIMESTAMPTZ,
    pickup_deadline             TIMESTAMPTZ,
    drop_off_code_hash          VARCHAR(128),
    pickup_code_hash            VARCHAR(128),
    terminal_session            VARCHAR(64),
    terminal_session_expires_at TIMESTAMPTZ
);

-- Safety net for the allocation rule "one parcel per compartment": at most one parcel may hold a reservation
-- (CREATED, ON_ITS_WAY, READY_FOR_PICKUP) on a compartment. EXPIRED / TO_RETURN parcels may coexist with a new
-- reservation (the expired parcel is returned on the next drop-off).
CREATE UNIQUE INDEX uq_parcel_active_compartment
    ON parcel (compartment_id)
    WHERE state IN ('CREATED', 'ON_ITS_WAY', 'READY_FOR_PICKUP');

CREATE INDEX ix_parcel_station_state ON parcel (station_id, state);
CREATE INDEX ix_parcel_drop_off_deadline ON parcel (drop_off_deadline) WHERE state = 'CREATED';
CREATE INDEX ix_parcel_pickup_deadline ON parcel (pickup_deadline) WHERE state = 'READY_FOR_PICKUP';

CREATE TABLE parcel_transition (
    id          BIGSERIAL PRIMARY KEY,
    parcel_id   BIGINT       NOT NULL REFERENCES parcel (id),
    from_state  VARCHAR(20),
    to_state    VARCHAR(20)  NOT NULL,
    event       VARCHAR(40),
    actor       VARCHAR(20)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    note        VARCHAR(300)
);
CREATE INDEX ix_transition_parcel ON parcel_transition (parcel_id);

CREATE TABLE location_scan (
    id         BIGSERIAL PRIMARY KEY,
    parcel_id  BIGINT       NOT NULL REFERENCES parcel (id),
    label      VARCHAR(200) NOT NULL,
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION,
    scanned_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_location_parcel ON location_scan (parcel_id);

CREATE TABLE incident (
    id             BIGSERIAL PRIMARY KEY,
    compartment_id BIGINT       NOT NULL REFERENCES compartment (id),
    tracking_code  VARCHAR(32),
    type           VARCHAR(40)  NOT NULL,
    reported_by    VARCHAR(20)  NOT NULL,
    reported_at    TIMESTAMPTZ  NOT NULL,
    description    VARCHAR(500)
);
CREATE INDEX ix_incident_compartment ON incident (compartment_id);
