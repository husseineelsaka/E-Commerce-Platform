# Architecture drawings

Three architecture drawings, kept as SVG files so they can be reviewed in PRs and updated with the ADD (`docs/adr/ADD-team-1.md`). They are the ADD's visual reference. The team replaced the handbook's photographed paper drawings with these digital drawings; see `docs/phases/L0.md`.

| File | Drawing | Author | ADD sections | Status |
| --- | --- | --- | --- | --- |
| `01-service-boundaries.svg` | B1 service boundaries: what `review-service` owns and how it touches the existing services | Ahmed Qamar | §1, §2 | added |
| `02-order-flow-sequence.svg` | Place-order sequence: happy path to `CONFIRMED`, and the payment-failure compensation path to `CANCELLED` | Ahmed Khalaf | §3.3, §5, §6 | pending |
| `03-data-locations.svg` | Where data lives: each database and its tables, Redis, and the service with no database | Sahar Attia | §4 | pending |

Each author's commit adds the drawing and changes its status to `added`.
