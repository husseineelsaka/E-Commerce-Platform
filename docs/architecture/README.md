# Architecture drawings

Three paper drawings, photographed at S25. They were drawn before the ADD (`docs/adr/ADD-team-1.md`) was written and are its visual reference.

| File | Drawing | Author | ADD sections | Status |
| --- | --- | --- | --- | --- |
| `01-service-boundaries.jpg` | B1 service boundaries: what `review-service` owns and how it touches the existing services | Ahmed Qamar | §1, §2 | pending |
| `02-order-flow-sequence.jpg` | Place-order sequence: happy path to `CONFIRMED`, and the payment-failure compensation path to `CANCELLED` | Ahmed Khalaf | §3.3, §5, §6 | pending |
| `03-data-locations.jpg` | Where data lives: each database and its tables, Redis, and the service with no database | Sahar Attia | §4 | pending |

Each author's PR adds the photo and changes its status to `added`.
