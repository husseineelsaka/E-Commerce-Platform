# review-service

B1 Product Reviews & Ratings (port 8086, database `review_db`).

| Endpoint | Caller | Result |
| --- | --- | --- |
| `POST /api/v1/products/{productId}/reviews` | CUSTOMER through the Gateway | `201` review; `400` rating outside 1–5 or empty text; `409` this customer already reviewed the product |
| `GET /api/v1/products/{productId}/reviews?page&size` | anyone through the Gateway | `200` page, newest first; `400` bad paging |

A submitted review and its `ReviewSubmitted` event are written in one transaction. The outbox poller publishes the
event to `review-events`, where product-service updates the product's average rating and review count.
