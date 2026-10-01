# Project charter

Team 1 will deliver an enterprise ecommerce platform that a new maintainer can run, deploy, test under load, and explain. Customers browse products, place orders, track their own orders, and receive outcomes. Administrators manage catalogue and stock. The capstone uses eight independent Spring applications in one repository, with a review and rating service planned for L6.

The team works through the S25 kickoff and S26–S28 gates toward the S29 demonstration. Each layer must run before the next gate: L0 establishes platform and infrastructure; L1–L3 deliver commerce behavior and recovery; L4 deploys it; L5 measures it; L6 adds reviews and completes the demo. Success includes the handbook's functional requirements, security, 60% service-layer coverage, cached product-read P95 below 200 ms, order-create P95 below 800 ms at 20 VUs, and at least 50 read requests per second through Gateway.

Team membership, signatures, and story ownership are recorded by the team in its separate charter and backlog.
