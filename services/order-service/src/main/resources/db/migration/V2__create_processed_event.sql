-- Consumer deduplication: one row per consumed eventId, written in the same transaction as the state change.
CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
