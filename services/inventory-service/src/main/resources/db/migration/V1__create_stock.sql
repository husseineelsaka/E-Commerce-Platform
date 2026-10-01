CREATE TABLE stock (
    product_id BIGINT PRIMARY KEY,
    available INT NOT NULL CHECK (available >= 0),
    reserved INT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version BIGINT NOT NULL DEFAULT 0
);
