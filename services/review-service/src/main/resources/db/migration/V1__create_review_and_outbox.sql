-- B1 (S15): reviews and the outbox for ReviewSubmitted (ADD §4).
CREATE TABLE review (
    id UUID PRIMARY KEY,
    product_id BIGINT NOT NULL,
    customer_id VARCHAR NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- One review per customer per product; holds even when two submits race (ADD §6 F6).
    CONSTRAINT review_product_customer_uq UNIQUE (product_id, customer_id)
);
CREATE INDEX review_product_created_idx ON review (product_id, created_at DESC);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    trace_parent VARCHAR(64)
);
CREATE INDEX outbox_event_unpublished_idx ON outbox_event (published_at) WHERE published_at IS NULL;
