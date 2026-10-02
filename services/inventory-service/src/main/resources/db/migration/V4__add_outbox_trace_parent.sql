-- W3C traceparent of the transaction that wrote the event, so the outbox publisher continues that trace (NFR-06).
ALTER TABLE outbox_event ADD COLUMN trace_parent VARCHAR(64);
