# S25 task split — Team 1

Each member finishes their part on their own branch, commits from their own GitHub account, and opens a PR that one teammate reviews. Commit messages use `capstone-L0: ...`.

## Ahmed Khalaf

1. **Review and own ADD §5 Communication and §6 Failure modes** in `docs/adr/ADD-team-1.md`. Check every decision matches what we agreed. Add any risk raised at the Architecture Review to §6.
   Commit: `capstone-L0: review-add-communication-and-failure-modes`
2. **Draw drawing 2 — main flow sequence** (paper, photograph, save as `docs/architecture/02-order-flow-sequence.jpg`). It must show:
   - Columns: Customer, Gateway, Order, Inventory, Kafka, Payment, Notification.
   - Happy path: POST order → Gateway validates JWT → Order Feign stock check → Order saves `PENDING` + outbox row → returns orderId → `OrderPlaced` → Inventory reserves → `InventoryReserved` → Payment charges → `PaymentCompleted` → Order `CONFIRMED` → `OrderConfirmed` → Notification.
   - One failure path (draw it in a different colour): `PaymentFailed` → Inventory releases stock → `InventoryReleased` → Order `CANCELLED` → `OrderCancelled` → Notification sends cancel notice.
   Commit: `capstone-L0: add-order-flow-drawing`
3. Sign `docs/TEAM-CHARTER.md`.

## Ahmed Qamar

1. **Review and own ADD §1 Problem statement and §2 Bounded context.** Confirm the B1 scope and the "no product check on submit" decision.
   Commit: `capstone-L0: review-add-problem-and-bounded-context`
2. **Draw drawing 1 — B1 service boundaries** (save as `docs/architecture/01-service-boundaries.jpg`). It must show:
   - A box per service: Gateway, Product, Order, Inventory, Payment, Notification, and the new Review service (highlighted).
   - Review's own database `review_db`; Product's `product_rating` table.
   - Arrows: Customer → Gateway → Review (submit / read reviews); Review → Kafka `review-events` → Product (`ReviewSubmitted`).
   - A dashed line around Review showing what it owns (reviews, one-per-customer rule, the event) and a note of what it does not own (products, orders, users).
   Commit: `capstone-L0: add-service-boundaries-drawing`
3. Sign `docs/TEAM-CHARTER.md`.

## Sahar Attia

1. **Review and own ADD §3 API contract + events and §4 Data model.** Check every path, status code, event field, and table against the handbook.
   Commit: `capstone-L0: review-add-api-and-data-model`
2. **Draw drawing 3 — where data lives** (save as `docs/architecture/03-data-locations.jpg`). It must show one PostgreSQL box with five databases inside, and the tables in each:
   - `product_db`: category, product, product_rating, processed_event
   - `order_db`: orders, order_item, outbox_event, processed_event
   - `inventory_db`: stock, reservation, outbox_event, processed_event
   - `payment_db`: payment, outbox_event, processed_event
   - `review_db`: review, outbox_event
   - Redis next to it: product cache + Gateway rate-limit counters. Notification: no database.
   Commit: `capstone-L0: add-data-locations-drawing`
3. Sign `docs/TEAM-CHARTER.md`.

## Hussein Elsaka

1. **Review and own ADD §7 Security & deployment and §8 Test & load plan.** Confirm the rate-limit numbers and the k6 targets.
   Commit: `capstone-L0: review-add-security-and-test-plan`
2. **Own `docs/BACKLOG.md`:** walk through all 15 stories with the team, confirm owners and estimates, and split any story someone thinks is over 4 hours.
   Commit: `capstone-L0: finalize-backlog`
3. **Own L0 verification:** `docker compose up` healthy, Config Server and Eureka registered, `scripts/verify-l0.sh` passes. Paste the output into `docs/phases/L0.md` under Evidence.
   Commit: `capstone-L0: add-l0-verification-evidence`
4. Update `docs/architecture/README.md` to list the three drawings, then sign `docs/TEAM-CHARTER.md`.

## Order of work

1. Everyone draws first (the handbook says draw before writing the ADD), then reviews their ADD sections against their drawing.
2. Swap ADD sections for peer review using the reviewer table at the top of the ADD.
3. Hussein merges the backlog after the team agrees on owners.
4. Last: all four sign the charter in one PR, so it's merged once with every signature.
