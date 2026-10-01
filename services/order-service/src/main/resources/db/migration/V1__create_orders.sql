CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT
);
CREATE INDEX orders_customer_id_idx ON orders (customer_id);

CREATE TABLE order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_event_unpublished_idx ON outbox_event (published_at) WHERE published_at IS NULL;
