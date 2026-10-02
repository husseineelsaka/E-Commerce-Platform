-- B1 (S15): Product's rating read model, built from ReviewSubmitted (ADD §2, §4).
-- Sum and count, not an average: one atomic UPDATE per event and no rounding drift.
CREATE TABLE product_rating (
    product_id BIGINT PRIMARY KEY REFERENCES product(id) ON DELETE CASCADE,
    review_count INTEGER NOT NULL CHECK (review_count > 0),
    rating_sum BIGINT NOT NULL CHECK (rating_sum > 0)
);

-- Consumer deduplication: a redelivered ReviewSubmitted is counted once (ADD §6 F1).
CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
