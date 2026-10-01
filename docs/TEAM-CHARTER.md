# Team Charter — Team 1

Enterprise E-Commerce Platform capstone (S25–S29). This charter records who we are, how we split the work, and the rules we agree to follow until the S29 final presentation.

## Members and primary ownership

| Member | Primary services and layers | Backup for |
| --- | --- | --- |
| Ahmed Khalaf | product-service (L1), notification-service (L3), Gateway rate limiting (L1) | Hussein Elsaka |
| Ahmed Qamar | inventory-service (L2/L3), payment-service (L2/L3), review-service (L6) | Sahar Attia |
| Sahar Attia | api-gateway security (L1), order-service and the Saga (L2/L3) | Ahmed Qamar |
| Hussein Elsaka | L0 platform and infrastructure, Redis cache (L1), Docker/CI/Helm/kind/ArgoCD (L4), observability and k6 (L5) | Ahmed Khalaf |

Primary owners write most of the code in their area. Backups review those PRs and must be able to explain and demo that area at a gate if the owner is absent. Every member owns at least one story in each gate window, so the git log shows work from all four of us.

## Rotating Tech Lead

| Window | Tech Lead |
| --- | --- |
| S25 → G1 (S26) | Hussein Elsaka |
| G1 → G2 (S27) | Ahmed Khalaf |
| G2 → G3 (S28) | Ahmed Qamar |
| G3 → Final (S29) | Sahar Attia |

The Tech Lead runs the daily check-in, decides when two reviewers disagree on a PR, keeps the gate status (Green / Amber / Red) up to date, and tells the trainer early if the team is behind.

## Working agreements

1. **Branches and PRs:** `main` is protected. We work on short-lived branches (at most 1–2 days) and merge through a Pull Request with one teammate review. Nobody pushes directly to `main` after L0.
2. **Commits:** `capstone-Lx: short-description` (for example `capstone-L2: add-payment-idempotency`). Each member commits their own work from their own GitHub account. Pair work carries `Co-authored-by: Name <email>` for the partner.
3. **Definition of Done:** a story is done when its named test or demo command passes, the docs that describe it are updated, CI is green, and a teammate has approved the PR.
4. **No secrets in Git**, ever. We use environment variables, Kubernetes Secrets, and GitHub Actions secrets.
5. **Shared files:** we never reformat the parent `pom.xml` or `docker-compose.yml`. Changes to them go in their own small PR.
6. **Pull `main` daily** before starting work.
7. **Time:** each member plans about 2 hours per day. If a story will exceed its 4-hour estimate, the owner splits it and tells the team the same day.

## Communication

- **Daily check-in:** 15 minutes, every evening during the build window. Each member says what they finished, what is next, and what blocks them.
- **Channel:** the team group chat for quick questions; GitHub PR comments for anything about code.
- **Response time:** PR reviews within 24 hours. Blockers are raised in the group chat as soon as they appear.

## Decision making and conflict

1. Technical decisions are written in the ADD (`docs/adr/ADD-team-1.md`) with DECISION · OPTIONS CONSIDERED · REASON · TRADE-OFF · WHAT WOULD MAKE US REVISIT.
2. If two members disagree, each states their option and its trade-off in the PR or chat. The current Tech Lead decides if there is no agreement within one day.
3. If a member repeatedly misses agreed work, the team first talks to them directly; if it continues, the Tech Lead raises it with the trainer.

## Scope rule when behind

Cut in this order: stretch goals → Bonus Could stories → FR-14 → FR-13 → FR-12 → Bonus Core scope (reduce, never drop). We never cut tests, security rules, the compensation path, or deployment.

## Signatures

By signing, each member agrees to this charter.

| Member | GitHub handle | Signature | Date |
| --- | --- | --- | --- |
| Ahmed Khalaf | `@5alafawyyy` | | |
| Ahmed Qamar | `@AhmeddKamar` | | |
| Sahar Attia | `@SaherAttia26` | | |
| Hussein Elsaka | `@husseineelsaka` | | |
