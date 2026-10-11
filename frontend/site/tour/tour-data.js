// Content of the guided tour. Three chapters: why microservices, the course session by session, the live platform.
// "say" is what the voice reads; the other fields are what the screen shows. Plain words on purpose.
'use strict';

window.TOUR = [
  // ------------------------------------------------------------------ chapter 1
  {
    chapter: 'why', id: 'why-1', kicker: 'Why microservices · 1 of 7', title: 'Where every system starts: the monolith',
    story: 'One codebase. One deployment. One database. One team touches everything.',
    points: [
      'Any small change means building and redeploying the whole application.',
      'One bug or one memory leak can crash every feature at once.',
      'To handle more shoppers you must scale everything, even the parts nobody is using.',
      'Teams block each other: one release train, one shared schema, one merge queue.',
    ],
    project: 'For a shop this means: a slow payment provider or a bug in the review page can take down product browsing too.',
    say: 'Every system starts as a monolith. One codebase, one deployment, one database, and one team that touches everything. ' +
      'That is simple at first. But as the shop grows, any small change means redeploying the whole application. ' +
      'One bug can crash every feature at once. To handle more shoppers you must scale everything, even parts nobody uses. ' +
      'And teams block each other, because they share one release and one database schema.',
  },
  {
    chapter: 'why', id: 'why-2', kicker: 'Why microservices · 2 of 7', title: 'The question the course asks',
    story: '"What if Product, Order and Payment could each be built, deployed and scaled independently?" (Session 1)',
    points: [
      'Netflix deploys to production hundreds of times per day.',
      'Amazon deploys a new version about every 11.7 seconds.',
      'Uber runs more than 2,000 microservices in production.',
      'A microservice is a small, independently deployable service that owns one business capability and its own data.',
    ],
    project: 'Our platform: nine independent Spring Boot applications, each with its own job, its own database and its own release.',
    say: 'Session one asks one question. What if Product, Order and Payment could each be built, deployed and scaled independently? ' +
      'That is the microservices idea. A microservice is a small service that owns one business capability and its own data, ' +
      'and can be deployed on its own. Big companies work this way. Netflix deploys hundreds of times a day, ' +
      'and Uber runs more than two thousand services.',
  },
  {
    chapter: 'why', id: 'why-3', kicker: 'Why microservices · 3 of 7', title: 'Why it fits our shop',
    story: 'We did not choose microservices because they are fashionable. Our shop has four real reasons.',
    points: [
      'Different load: browsing the catalogue is many times heavier than paying. We scale and cache Product alone.',
      'Failure isolation: if Payment is down, shoppers still browse and orders wait safely as PENDING.',
      'Different change rate: prices change rarely, stock changes on every order, so Product and Inventory are separate.',
      'Team ownership: four people, each owning services, each able to release without waiting for the others.',
    ],
    project: 'Evidence: the chaos test stops Payment and Inventory while the catalogue keeps answering 200.',
    say: 'Why does this fit our shop? Four reasons. First, load is uneven. Browsing the catalogue is many times heavier than paying, ' +
      'so we cache and scale the product service alone. Second, failure isolation. If payment is down, shoppers can still browse, ' +
      'and orders wait safely as pending. Third, change rate. Prices change rarely, but stock changes on every order, ' +
      'so product and inventory are separate services. Fourth, ownership. Each of the four of us owns services and can release them alone.',
  },
  {
    chapter: 'why', id: 'why-4', kicker: 'Why microservices · 4 of 7', title: 'The price we pay, and the fix for each cost',
    story: 'Microservices trade one big problem for many smaller, distributed ones. The course is the list of fixes.',
    table: [
      ['A deploy in one module breaks everything', 'Independently deployable services', 'S1, S9, S13'],
      ['Order cannot find Inventory\'s address', 'Eureka service discovery', 'S1'],
      ['Eight services, eight scattered configs', 'Spring Cloud Config Server', 'S1'],
      ['One slow service eats all threads', 'Circuit breaker, bulkhead, time limiter', 'S4, S5'],
      ['Payment fails half-way, order stuck in limbo', 'Saga with compensation', 'S7, S12'],
      ['1,000 requests a minute crush the database', 'Redis cache-aside', 'S8'],
      ['Nobody can see where a request went', 'Tracing, metrics, JSON logs', 'S17'],
      ['Database saved but event lost', 'Transactional outbox', 'S22'],
    ],
    project: 'Every row of this table is running in our platform today.',
    say: 'Microservices are not free. The network can fail or be slow. There is no single database transaction across services. ' +
      'Debugging is harder, and there are more things to run. The whole course is a list of fixes for these costs. ' +
      'Service discovery and central config so services find each other. Circuit breakers so one slow service does not eat every thread. ' +
      'Sagas so a failed payment does not leave an order in limbo. Caching, tracing, and the outbox pattern. Every one of these runs in our platform.',
  },
  {
    chapter: 'why', id: 'why-5', kicker: 'Why microservices · 5 of 7', title: 'When a monolith would be the better choice',
    story: 'Say this before the examiner does. It shows judgement, not weakness.',
    points: [
      'A small team with an unclear domain: boundaries drawn too early are expensive to move.',
      'No automation yet: without CI, containers and monitoring, many services are many problems.',
      'Low, even traffic: there is nothing to scale separately.',
      'A good path is a modular monolith first, then split the modules that really need it.',
    ],
    project: 'Honest answer for us: four students could ship a monolith faster. We accepted the cost because the course and the business case (uneven load, isolation) need it, and we can show every cost is handled.',
    say: 'Be ready to say when a monolith is better. With a small team and an unclear domain, early boundaries are expensive to move. ' +
      'Without automation, many services mean many problems. With low, even traffic there is nothing to scale separately. ' +
      'Often the right start is a modular monolith. Our honest answer: four students could ship a monolith faster. ' +
      'We accepted the extra cost because the shop has uneven load and needs failure isolation, and we can show how each cost is handled.',
  },
  {
    chapter: 'why', id: 'why-6', kicker: 'Why microservices · 6 of 7', title: 'Our own choices on top of the course',
    story: 'Places where we decided something ourselves instead of copying the lab.',
    points: [
      'The Gateway swaps the user token for its own client token, so a stolen user token cannot call internal APIs.',
      'Choreography Saga, not orchestration: three steps in a straight line do not need a coordinator.',
      'The outbox row stores the trace id, so one order is one trace across HTTP and Kafka.',
      'Bonus B1: reviews became a new service, with a small rating copy inside Product fed by events.',
      'Real PostgreSQL in tests (Testcontainers), never H2.',
      'A storefront, an end-to-end script with 80 checks, and this tour.',
    ],
    project: 'Each choice has a trigger that would make us change it. They are written in docs/adr/ADD-team-1.md.',
    say: 'Here are the choices we made ourselves. The gateway swaps the user token for its own token, so a stolen user token cannot reach internal APIs. ' +
      'We kept a choreography saga, because three steps in a straight line do not need a coordinator. ' +
      'The outbox stores the trace id, so one order is one trace. Reviews became a new service with a small rating copy inside Product. ' +
      'Tests use a real Postgres database. And we added a storefront, an end-to-end test script, and this tour.',
  },
  {
    chapter: 'why', id: 'why-7', kicker: 'Why microservices · 7 of 7', title: 'The one sentence to remember',
    story: '"It works, but here is the trade-off we accepted, and here is what would make us reconsider it."',
    points: [
      'Choreography: we would switch to orchestration with a branching rule or a fourth participant.',
      'Polling outbox: we would move to change-data-capture if the delay shows in load tests.',
      'Eventual rating: we would rethink it if the rating had to be exact at once.',
    ],
    project: 'Use this sentence for any "why" question in the discussion.',
    say: 'If you remember one sentence for the discussion, remember this one. It works, but here is the trade-off we accepted, ' +
      'and here is what would make us reconsider it. For example: we chose choreography, and we would switch to orchestration ' +
      'if the order flow gets a branching rule or a fourth participant.',
  },

  // ------------------------------------------------------------------ chapter 2: sessions
  {
    chapter: 'course', id: 's0', kicker: 'Session 0 · Kickoff', title: 'How the course works',
    story: 'The course is one long project, not separate exercises. A bad design in Session 3 costs you in Session 7.',
    idea: 'Every session starts with a real problem, then the solution. Labs build on each other until they become the capstone.',
    points: ['No commit means no grade.', 'Unit tests are 20 % of the lab grade.', 'Pre-reading and a knowledge check open each session.'],
    project: 'Our capstone is the finished version of everything built in the labs.',
    say: 'Session zero explains how the course works. It is one long project, not separate exercises, so decisions carry forward. ' +
      'Every session starts with a real problem and then the solution. No commit means no grade, and tests are part of the mark.',
  },
  {
    chapter: 'course', id: 's1', kicker: 'Session 1 · Architecture & Spring Cloud', title: 'Service discovery and central configuration',
    story: 'Order needs to call Product, but where is Product? And eight services means eight scattered application.yml files.',
    idea: 'Eureka is a phone book: services register by name and callers look them up. Config Server serves every service\'s settings from one place.',
    points: ['Config Server starts first; clients use spring.config.import.', 'optional: lets a service start even if Config Server is down.', 'Draw service boundaries by business domain, not by database table.'],
    project: 'config-server :8888 reads config-repo/. eureka-server :8761. All seven apps register.',
    say: 'Session one starts from the monolith and asks how services find each other. ' +
      'The answer is Eureka, a phone book where each service registers by name. Config Server keeps every service setting in one place. ' +
      'Boundaries come from the business domain, not from the database. In our project, config server reads the config repo folder, and all seven apps register in Eureka.',
  },
  {
    chapter: 'course', id: 's2', kicker: 'Session 2 · API Gateway', title: 'One front door for every client',
    story: 'Clients had to know every service address and port. "If we add Inventory tomorrow, how many places must we update?"',
    idea: 'The API Gateway is the single entry point. Routes match a path and forward to a service name with lb://, resolved through Eureka.',
    points: ['Route = predicate (when) + filters (change) + uri (where).', 'The gateway is reactive: many short requests, few threads.', 'StripPrefix only when the service path differs.'],
    project: 'api-gateway :8080 is the only business port published. Everything else is internal.',
    say: 'Session two adds the API gateway. Before it, clients had to know the address of every service. ' +
      'Now there is one front door. A route matches a path and forwards to a service name, which Eureka turns into a real address. ' +
      'In our project, the gateway on port 8080 is the only business port open to the outside.',
  },
  {
    chapter: 'course', id: 's3', kicker: 'Session 3 · Gateway security', title: 'Check the token once, limit abusers',
    story: 'Anyone could call any endpoint, and a bot sending 10,000 requests a second could take Product down for everyone.',
    idea: 'Validate the JWT once at the gateway instead of in ten services. Rate limit with a Redis token bucket; an empty bucket answers 429.',
    points: ['Remove client-sent identity headers, then add the real ones.', 'Bucket refills at a fixed rate; burst = bucket size.', 'Redis keeps the bucket shared between gateway copies.'],
    project: 'Anonymous users: 100 per second by IP, burst 200. Signed-in users: 20 per second by user id, burst 40.',
    say: 'Session three makes the gateway smart. Without it, anyone could call anything, and one bot could flood the product service. ' +
      'The gateway checks the token once, so ten services do not repeat the same code. It removes fake identity headers and adds the real ones. ' +
      'And it rate limits with a token bucket in Redis. When the bucket is empty, the answer is 429, too many requests.',
  },
  {
    chapter: 'course', id: 's4', kicker: 'Session 4 · Resilience', title: 'Circuit breaker and retry',
    story: 'Black Friday: Payment slows from 200 ms to 10 s. Order\'s 200 threads all wait. The gateway returns 503 to everyone, even people just browsing. Adding retries made it worse.',
    idea: 'A circuit breaker stops calling a failing service (fail fast) and a fallback returns something useful (fail safe).',
    points: ['States: CLOSED, OPEN, HALF-OPEN (a few test calls).', 'Retry only short, temporary errors, and only idempotent operations.', 'Retries pile extra load on a struggling service.'],
    project: 'Order\'s calls to Product and Inventory: window 10 calls, min 5, opens at 50 % failures, stays open 10 s, retry 2.',
    say: 'Session four tells the Black Friday story. Payment gets slow, every order thread waits for it, and soon the whole shop returns errors. ' +
      'This is a cascading failure. A circuit breaker stops calling the failing service and fails fast, and a fallback returns something useful. ' +
      'It has three states: closed, open, and half open. Retry helps only for short, temporary errors.',
  },
  {
    chapter: 'course', id: 's5', kicker: 'Session 5 · Resilience advanced', title: 'Bulkhead and time limiter',
    story: 'A slow service is more dangerous than a dead one: the breaker does not see slowness as failure, and threads fill up in minutes.',
    idea: 'Time limiter: stop waiting after a set time. Bulkhead: only a few calls may use a dependency at once, so the rest of the app keeps working.',
    points: ['Time limiter needs a CompletableFuture method.', 'Bulkhead limits the blast radius.', 'Default order: Retry wraps CircuitBreaker wraps TimeLimiter wraps Bulkhead.'],
    project: 'Time limit 500 ms, bulkhead 20 concurrent calls. Inventory down: 503 in about 1 second, then 0.09 s once the circuit is open.',
    say: 'Session five handles slow services. A slow service is worse than a dead one, because the circuit breaker does not count slowness as failure. ' +
      'A time limiter stops waiting after a fixed time. A bulkhead lets only a few calls use one dependency at the same time, ' +
      'so the rest of the application stays free. We use a 500 millisecond limit and a bulkhead of twenty.',
  },
  {
    chapter: 'course', id: 's6', kicker: 'Session 6 · Communication', title: 'Sync or async? Ask one question',
    story: 'Order never checked stock: a customer could buy 5 units of a product with 0 in the warehouse.',
    idea: '"Do I need the answer immediately to continue?" Yes: synchronous HTTP (OpenFeign). No: asynchronous events (Kafka).',
    points: ['Feign: a Java interface becomes an HTTP client, names resolved by Eureka.', 'Sync creates time coupling and blocked threads.', 'Eventual consistency is fine for notices, not for a stock check before buying.'],
    project: 'Order asks Product (price) and Inventory (stock) with Feign before accepting. Everything after that is Kafka.',
    say: 'Session six is about how services talk. The key question is: do I need the answer now to continue? ' +
      'If yes, use a synchronous call, like OpenFeign. If no, publish an event to Kafka. ' +
      'Checking stock before accepting an order needs an answer now. Sending an email does not. ' +
      'In our project, order asks product for the price and inventory for stock with Feign, and everything after that is events.',
  },
  {
    chapter: 'course', id: 's7', kicker: 'Session 7 · Saga with Kafka', title: 'A business transaction across three databases',
    story: 'Order created, stock reserved, then payment fails. Each service did its job, yet the shop is wrong: stock is held for nothing.',
    idea: 'A Saga is a chain of local transactions joined by events. If a step fails, earlier steps are corrected by compensation, a new business action.',
    points: ['No global transaction: each service owns its database.', 'Two-phase commit is too costly at this scale.', 'Choreography: each service reacts to events, no central boss.', 'The client gets PENDING at once.'],
    project: 'OrderPlaced, then InventoryReserved, then PaymentCompleted, then CONFIRMED. On PaymentFailed, stock is released and the order is CANCELLED.',
    say: 'Session seven is the heart of the course. Placing an order touches three databases, and there is no single transaction across them. ' +
      'If payment fails after stock was reserved, the shop is wrong even though every service did its job. ' +
      'The answer is a saga: a chain of local transactions joined by events. When a step fails, earlier steps are corrected by compensation, ' +
      'for example releasing the stock. The customer gets pending immediately, and the final status arrives through events.',
  },
  {
    chapter: 'course', id: 's8', kicker: 'Session 8 · Caching + Clinic #1', title: 'Redis cache-aside',
    story: 'Each product read costs 50 ms in PostgreSQL. At 1,000 reads a minute the database CPU reaches 90 %. With Redis: 1 ms and under 5 % CPU.',
    idea: 'Cache-aside: read the cache; on a miss read the database and store the result. On a write, evict the cache entry.',
    points: ['Cache read-heavy data that can be a little old.', 'Never cache stock or order status: they change on every Saga step.', 'TTL is a safety net, eviction is the real rule.', 'Clinic #1: be the harshest critic of your own design.'],
    project: 'Keys products:item::id and products:list::page:size, evicted after commit. Cached read: 8.1 ms at the 95th percentile.',
    say: 'Session eight is about caching. Reading the same product from the database again and again wastes time. ' +
      'With cache aside, we read Redis first. On a miss we read the database and save the result. When a product changes, we delete the cached copy. ' +
      'Never cache things that change all the time, like stock or order status. This session also held the first architecture clinic, ' +
      'where you criticise your own design.',
  },
  {
    chapter: 'course', id: 's9', kicker: 'Session 9 · Docker', title: 'Same image, same behaviour, everywhere',
    story: '"It works on my machine." Different Java versions, missing environment variables, and localhost meaning a different thing inside a container.',
    idea: 'Package each service as an image. Multi-stage build: compile with Maven, ship only a small Java runtime. Compose runs them all on one network.',
    points: ['Inside a container, localhost is the container itself; use service names.', 'depends_on only orders start-up; health checks decide readiness.', 'Layer order: stable layers first for fast rebuilds.'],
    project: 'Multi-stage images, non-root user 10001, health checks, docker compose up --wait. Secrets only in .env, never in Git.',
    say: 'Session nine is Docker. The problem is: it works on my machine. Each service becomes an image that behaves the same everywhere. ' +
      'A multi-stage build compiles with Maven and ships only a small Java runtime. Docker compose runs everything on one network, ' +
      'where services call each other by name. Remember: inside a container, localhost means the container itself.',
  },
  {
    chapter: 'course', id: 's10', kicker: 'Session 10 · Testing', title: 'Unit and integration tests',
    story: '47 unit tests were green, but placing an order returned 500: H2 accepted a column type that PostgreSQL rejected.',
    idea: 'Test pyramid: many fast unit tests, fewer integration tests, few end-to-end. Testcontainers starts a real database for integration tests.',
    points: ['Green unit tests do not prove wiring or the real database.', 'Mock only across boundaries you do not own.', 'Automated tests make CI/CD possible.'],
    project: '82 unit and web tests, 55 Testcontainers tests on real PostgreSQL, Kafka and Redis, 87 to 94 % coverage.',
    say: 'Session ten is testing. The story: forty seven unit tests were green, but the real system failed, because the test database was not the real one. ' +
      'So we use the test pyramid. Many fast unit tests, fewer integration tests, and a few end-to-end tests. ' +
      'Testcontainers starts a real Postgres for the integration tests. We have 82 unit tests and 55 integration tests.',
  },
  {
    chapter: 'course', id: 's11', kicker: 'Session 11 · Contract & chaos', title: 'Test between services and test failure',
    story: 'Inventory renamed a field from "available" to "inStock". Both teams\' tests passed. In production every order failed.',
    idea: 'Contract tests (Pact) record what the consumer expects and check the provider against it. Chaos tests break things on purpose to prove resilience works.',
    points: ['Mocks inside one service hide changes in another.', 'WireMock fakes a real HTTP server, errors and delays.', 'A chaos test: hypothesis, experiment, result.'],
    project: 'We have no Pact tests (an honest gap). Our chaos tests are in scripts/e2e-test.sh --chaos: payment declined, payment down, inventory down, notification to the dead-letter topic.',
    say: 'Session eleven covers tests between services. In the story, one team renamed a field. Both teams had green tests, but production broke. ' +
      'Contract tests catch this by checking the provider against what the consumer expects. Chaos tests break things on purpose to prove the resilience settings work. ' +
      'We did not build contract tests, but our chaos script stops services and checks the platform recovers.',
  },
  {
    chapter: 'course', id: 's12', kicker: 'Session 12 · Orchestration', title: 'A saga with a brain',
    story: 'The flow grew to five steps with a rule: skip loyalty points if fraud risk is high and the total is over 500 EGP. Nobody could say where an order was.',
    idea: 'An orchestrator owns the decision tree and sends commands; services reply. "The orchestrator is the brain, services are the hands."',
    points: ['Choreography: loose, no bottleneck, but hard to see.', 'Orchestration: one place for state and compensation.', 'Commands go to one service; events are broadcast.'],
    project: 'We kept choreography: three steps, a straight line. We would switch when a branching rule or a fourth participant appears.',
    say: 'Session twelve adds orchestration. When a saga grows and has branching rules, choreography becomes hard to follow. ' +
      'An orchestrator acts as the brain. It sends commands and waits for replies, so there is one place to see the state. ' +
      'We kept choreography because our flow has only three steps in a straight line.',
  },
  {
    chapter: 'course', id: 's13', kicker: 'Session 13 · CI/CD', title: 'Let the pipeline ship it',
    story: 'Friday 4 PM: a developer builds with tests skipped, copies the jar to a server that runs Java 17 instead of 21, and it fails. 45 minutes of rollback, and everyone fears deploying.',
    idea: 'GitHub Actions runs the same steps every time: test, build, push an image tagged with the commit. Tests are a quality gate.',
    points: ['Never build an image from code that failed tests.', 'Tag with the Git commit for tracing and rollback.', 'Notification gets retries and a dead-letter topic.'],
    project: '.github/workflows/ci.yml: mvn verify with a 60 % coverage gate, chart check, 9 images to GHCR tagged with the commit SHA.',
    say: 'Session thirteen is continuous integration. The story is a Friday deployment that went wrong because of manual steps. ' +
      'A pipeline does the same steps every time: test, build, and push an image tagged with the commit. ' +
      'If the tests fail, nothing is published. Our pipeline builds nine images and pushes them to the GitHub container registry.',
  },
  {
    chapter: 'course', id: 's14', kicker: 'Session 14 · GitOps', title: 'Git is the truth, ArgoCD keeps the cluster honest',
    story: 'Someone fixed production by editing the cluster by hand and forgot to commit. The next deploy wiped the fix. Who changed it? When?',
    idea: 'Git holds the desired state. ArgoCD inside the cluster pulls it and keeps the real state equal to Git.',
    points: ['Synced (matches Git) is not the same as Healthy (works).', 'Rollback = git revert.', 'GitOps replaces the deploy step, not CI.'],
    project: 'deployment/argocd/applications.yaml with automatic sync, selfHeal and prune: 9 of 9 apps Synced and Healthy.',
    say: 'Session fourteen is GitOps. In the story, someone changed production by hand, and nobody knew who or when. ' +
      'With GitOps, Git holds the desired state, and ArgoCD keeps the cluster equal to Git. It undoes manual changes and deletes removed resources. ' +
      'A rollback is just a git revert.',
  },
  {
    chapter: 'course', id: 's15', kicker: 'Session 15 · Kubernetes core', title: 'Pods, services and health probes',
    story: 'A pod is "Running" but still starting, so the first 30 requests fail. Another pod is deadlocked for 6 hours and still says Running.',
    idea: 'Readiness probe: send traffic only when ready. Liveness probe: restart a stuck pod. A Service gives pods a stable name.',
    points: ['Database checks belong in readiness, never liveness.', 'ClusterIP for internal services; only the edge is exposed.', 'Secrets are base64, which is encoding, not encryption.'],
    project: 'One Helm chart per app with readiness and liveness probes, on a local kind cluster.',
    say: 'Session fifteen is Kubernetes. A pod can say running while it is still starting or completely stuck. ' +
      'The readiness probe sends traffic only when the app is ready. The liveness probe restarts a stuck app. ' +
      'Database checks go in readiness, never liveness, or one database outage would restart every pod.',
  },
  {
    chapter: 'course', id: 's16', kicker: 'Session 16 · Kubernetes advanced', title: 'Resources, autoscaling, Helm, least privilege',
    story: 'Launch day: traffic times ten. Memory limits too tight, a pod is killed, the last pod hits 100 % CPU, nobody adds pods. 22 minutes of outage.',
    idea: 'Requests and limits size each pod. The HorizontalPodAutoscaler adds pods when CPU rises. Helm packages charts. RBAC gives each service only the rights it needs.',
    points: ['HPA needs metrics-server and CPU requests.', 'Scaling takes time; it is a feedback loop.', 'Do not template just for the sake of templating.'],
    project: 'Helm chart per app, one ServiceAccount each with no token mounted. We did not add an HPA (a known gap).',
    say: 'Session sixteen covers resources and scaling. Each pod gets CPU and memory requests and limits. ' +
      'The autoscaler adds pods when CPU is high, but it needs the metrics server. ' +
      'Helm packages the YAML into reusable charts, and RBAC gives each service only the permissions it needs.',
  },
  {
    chapter: 'course', id: 's17', kicker: 'Session 17 · Observability', title: 'Logs, metrics and traces',
    story: 'A customer: "My order took 9 seconds and I am not sure it worked." Without tracing you compare timestamps across five log files and guess.',
    idea: 'One trace id is created once and travels with the request through HTTP and Kafka. Zipkin shows the waterfall, Prometheus stores metrics, Grafana draws them.',
    points: ['Metrics say what, traces say where, logs say why.', 'Every service inventing its own request id does not help.', 'Avoid labels like order id in metrics.'],
    project: 'One order is one trace of about 23 spans across 6 apps. JSON logs carry the trace id. Prometheus scrapes 9 targets.',
    say: 'Session seventeen is observability. When a request crosses many services, you need one trace id that travels with it. ' +
      'Zipkin shows where the time went, Prometheus collects numbers, and Grafana draws them. ' +
      'A simple rule: metrics say what is wrong, traces say where, and logs say why.',
  },
  {
    chapter: 'course', id: 's18', kicker: 'Session 18 · CQRS', title: 'Separate the write model from the read model',
    story: 'One product method served checkout and a heavy operations dashboard. Every change was a compromise for somebody.',
    idea: 'Command Query Responsibility Segregation: commands change data with all the rules; queries read a shape made for the screen.',
    points: ['CQRS is not the same as event sourcing.', 'A logical split can live in one database.', 'Use it only when you feel the pain.'],
    project: 'Bonus B1: review-service writes reviews and sends ReviewSubmitted. Product keeps a small rating copy (sum and count) for fast reads.',
    say: 'Session eighteen is CQRS. Writing and reading often need different shapes. ' +
      'CQRS separates them: commands change data with all the business rules, and queries read a shape made for the screen. ' +
      'In our bonus feature, the review service writes reviews, and product keeps a small rating copy that events keep up to date.',
  },
  {
    chapter: 'course', id: 's19', kicker: 'Session 19 · Keycloak', title: 'Real tokens from a real identity provider',
    story: 'Session 3 used a shared secret. There was no sign-up, no password reset, and no way to revoke one stolen token.',
    idea: 'Keycloak issues tokens. OAuth2 gives access tokens; OpenID Connect adds identity. Users log in with the Authorization Code flow.',
    points: ['Access token for APIs; ID token is not an API token.', 'PKCE protects the login code.', 'Trust moves out: services trust the gateway, the gateway trusts Keycloak.'],
    project: 'Realm ecommerce-platform, roles CUSTOMER and ADMIN, users customer-test and admin-test, Keycloak on :8180.',
    say: 'Session nineteen brings Keycloak, a real identity provider. Before, tokens used a shared secret and there was no sign-up or revocation. ' +
      'Now Keycloak issues the tokens, and the gateway only checks them. Users log in with the authorization code flow. ' +
      'Remember: services trust the gateway, and the gateway trusts Keycloak.',
  },
  {
    chapter: 'course', id: 's20', kicker: 'Session 20 · Service identity', title: 'Client credentials and resource servers',
    story: 'A nightly job asks Inventory for low stock. There is no user, so no user token. Who is calling? The service itself.',
    idea: 'Each service gets its own token with the Client Credentials flow. Every service checks tokens with Keycloak\'s public keys (JWKS) as a resource server.',
    points: ['401: not signed in. 403: signed in but not allowed.', 'Check the audience, not only the signature.', 'Map Keycloak roles to Spring roles.'],
    project: 'Clients gateway-service, order-service, payment-operator. A user token cannot call a service directly; an order-service token may check stock.',
    say: 'Session twenty gives services their own identity. A service calling another service may have no user at all. ' +
      'So each service gets its own token with the client credentials flow, and every service checks tokens using Keycloak public keys. ' +
      'Remember: 401 means not signed in, and 403 means signed in but not allowed.',
  },
  {
    chapter: 'course', id: 's21', kicker: 'Session 21 · Istio', title: 'Service mesh and mutual TLS',
    story: 'Inside the cluster, traffic between services is plain HTTP. And retries, tracing and security are copied into every service.',
    idea: 'A mesh puts a proxy next to every pod. It encrypts traffic with mutual TLS and applies retries and routing without code changes.',
    points: ['mTLS: both sides prove who they are.', 'JWT proves the user; mTLS proves the service.', 'Cost: a proxy per pod and more to operate.'],
    project: 'Optional; we did not deploy Istio. Resilience4j and token audiences cover the same needs in code.',
    say: 'Session twenty one is the service mesh. A proxy runs next to each service and handles encryption, retries and routing, without code changes. ' +
      'Mutual TLS means both sides prove who they are. We did not deploy Istio. It was optional, and we cover the same needs in code.',
  },
  {
    chapter: 'course', id: 's22', kicker: 'Session 22 · Outbox & idempotency', title: 'Never lose an event, never charge twice',
    story: 'The database commit succeeds, then the Kafka publish fails: the saga stalls. And a retried payment could charge the customer twice.',
    idea: 'Outbox: save the event in the same transaction as the data; a poller publishes it later. Idempotency key: the same request twice gives the same result once.',
    points: ['At-least-once delivery plus idempotent consumers.', 'A unique constraint beats check-then-insert.', 'Version APIs: /api/v1, keep the old version for a while.'],
    project: 'Outbox in every publishing service, processed_event in every consumer. Payment: missing key 400, replay 200 same payment, different body 422.',
    say: 'Session twenty two closes the technical debt. The dual write problem: the database saves, but the Kafka message is lost. ' +
      'The outbox fixes it by saving the event in the same transaction, and a poller publishes it later. ' +
      'Messages may arrive twice, so consumers remember which events they processed. ' +
      'And an idempotency key makes sure a retried payment charges only once.',
  },
  {
    chapter: 'course', id: 's23', kicker: 'Session 23 · Load testing', title: 'k6 and finding the bottleneck',
    story: 'Everything was tested with a few curl calls. Settings in application.yml are only a guess until real concurrent load proves them.',
    idea: 'k6 runs virtual users with thresholds. Look at percentiles, not averages. Find the slow hop, explain it, change one thing, test again.',
    points: ['Smoke, load, stress, spike, soak.', 'p95 shows what slow users feel.', 'The load generator can be the bottleneck too.'],
    project: 'Our gateway was the bottleneck: p95 518 ms at 800 req/s. After a fix: 18.8 ms with zero errors.',
    say: 'Session twenty three is load testing with k6. Settings are only a guess until real load tests them. ' +
      'Look at percentiles like p95, not averages. Find the slowest hop, explain it, change one thing, and test again. ' +
      'We found that our gateway was the bottleneck, and after the fix the response time dropped from 518 milliseconds to under 19.',
  },
  {
    chapter: 'course', id: 's24', kicker: 'Session 24 · Clinic #2', title: 'Defend every decision',
    story: '"It works, so it\'s fine" is not accepted. A strong answer names the trade-off and the condition that would change it.',
    idea: 'For any decision: problem, options, benefit, cost, evidence, alternative, when to reconsider.',
    points: ['"Was it needed?" is a fair question.', 'Debt is a decision log, not a confession.', '"All debt resolved" is a red flag.'],
    project: 'Our lessons slide lists what each of us would change and the debt we left on purpose.',
    say: 'Session twenty four is the second architecture clinic. You must defend every decision. ' +
      'It works is not enough. Say the problem, the cost you accepted, your evidence, and what would make you change it. ' +
      'That is exactly what tomorrow\'s discussion will test.',
  },

  // ------------------------------------------------------------------ chapter 3: live platform
  {
    chapter: 'live', id: 'live-map', kicker: 'Live tour · start', title: 'What is running right now',
    story: 'Nine Spring Boot applications plus PostgreSQL, Kafka, Redis, Keycloak, Zipkin, Prometheus and Grafana, started with one docker compose command.',
    points: ['Only the Gateway (8080) is open for business calls.', 'This page talks to the Gateway through the storefront, like a browser would.'],
    project: 'Started with: docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env up -d --build --wait',
    live: 'ping',
    say: 'Now the live tour. Everything you will see is running right now on this laptop. ' +
      'Nine Spring Boot applications, plus Postgres, Kafka, Redis, Keycloak and the monitoring tools, all started with one docker compose command.',
  },
  {
    chapter: 'live', id: 'live-store', kicker: 'Live tour · storefront', title: 'The shop a customer sees',
    story: 'A small web shop we built to show the platform. It calls only the public API through the Gateway.',
    points: ['Browse, sign in, order and watch the Saga, review, admin.', 'A side panel lists every API call with its status code.'],
    tool: { label: 'Open the storefront', url: 'http://localhost:8070/' },
    say: 'This is the storefront we built. A customer can browse, sign in, place an order and watch it move through the saga, and write a review. ' +
      'An admin can manage products and stock. The side panel lists every API call and its status code.',
  },
  {
    chapter: 'live', id: 'live-config', kicker: 'Live tour · Config Server', title: 'Settings come from one place',
    story: 'Order Service does not keep its resilience settings in its own jar. It asks Config Server at start-up.',
    points: ['Source: the config-repo folder in our repository.', 'Change a value, restart the service, no rebuild.'],
    live: 'config',
    say: 'This is config server. The order service does not keep its resilience settings inside its own code. ' +
      'At start-up it asks config server, which reads them from the config repo folder. Here are the live values for the order service.',
  },
  {
    chapter: 'live', id: 'live-eureka', kicker: 'Live tour · Eureka', title: 'Every service registers by name',
    story: 'When Order calls "INVENTORY-SERVICE", Eureka tells it which address to use. No hard-coded IPs.',
    points: ['Each instance sends a heartbeat.', 'A second copy of a service would appear here and share the load.'],
    live: 'eureka',
    say: 'This is Eureka, the service registry. Each service registers by name and sends a heartbeat. ' +
      'When order calls inventory, it asks Eureka for the address. Here is the live list of registered applications.',
  },
  {
    chapter: 'live', id: 'live-gateway', kicker: 'Live tour · Gateway', title: 'One door: public reads and protected writes',
    story: 'Browsing is public. Creating a product without a token must be refused before it reaches Product Service.',
    points: ['GET /api/v1/products: anyone, 200.', 'POST /api/v1/products without a token: 401.'],
    live: 'gateway',
    say: 'Now the gateway. First an anonymous request to list products. That is public, so we expect 200. ' +
      'Then we try to create a product with no token. The gateway must refuse it with 401 before it reaches the product service.',
  },
  {
    chapter: 'live', id: 'live-keycloak', kicker: 'Live tour · Keycloak', title: 'Sign in and look inside the token',
    story: 'The customer signs in at Keycloak and gets a signed JWT. The Gateway checks the signature with Keycloak\'s public key.',
    points: ['sub: the user id. azp: the client that asked. roles: CUSTOMER.', 'exp: the token expires after a few minutes.'],
    live: 'signin',
    needsPassword: true,
    tool: { label: 'Open Keycloak', url: 'http://localhost:8180/admin/' },
    say: 'Next, Keycloak. The customer signs in and receives a signed token. Let us decode it. ' +
      'You can see the user id, the client that asked for the token, the role customer, and when it expires.',
  },
  {
    chapter: 'live', id: 'live-roles', kicker: 'Live tour · roles', title: 'Signed in is not the same as allowed',
    story: 'A customer tries to create a product. The token is valid, but the role is wrong.',
    points: ['401: no or bad token.', '403: good token, wrong role.'],
    live: 'forbidden',
    say: 'Now the customer tries to create a product. The token is valid, but customers are not allowed to change the catalogue. ' +
      'So the answer should be 403, forbidden. Remember: 401 means not signed in, 403 means not allowed.',
  },
  {
    chapter: 'live', id: 'live-cache', kicker: 'Live tour · Redis cache', title: 'The second read comes from memory',
    story: 'The first read of a product may go to PostgreSQL. The next reads come from Redis.',
    points: ['Key: products:item::<id>.', 'Any write evicts it after the transaction commits.'],
    live: 'cache',
    say: 'Now the cache. We read the same product five times and time each call. ' +
      'After the first read, the product comes from Redis, so the calls are fast. When an admin changes the product, the cached copy is deleted.',
  },
  {
    chapter: 'live', id: 'live-rate', kicker: 'Live tour · rate limit', title: 'Too many requests',
    story: 'We fire 250 requests at once from this browser. Anonymous clients may make 100 per second with a burst of 200.',
    points: ['Extra requests answer 429.', 'A browser opens only a few connections at once, so it may not be fast enough; k6 always shows it.'],
    live: 'burst',
    say: 'Now the rate limit. We send 250 requests at once. Anonymous users may send one hundred per second, with a burst of two hundred. ' +
      'Anything above that answers 429, too many requests. A browser may not be fast enough to hit the limit, and that is fine; the k6 script always shows it.',
  },
  {
    chapter: 'live', id: 'live-order', kicker: 'Live tour · place order', title: 'Answer fast, finish in the background',
    story: 'Order asks Product for the price and Inventory for stock (Feign), then saves the order and an outbox row in one transaction.',
    points: ['201 with PENDING comes back at once.', 'No stock: 409, payment never touched.', 'Dependency down: 503 quickly, nothing saved.'],
    live: 'order',
    needsPassword: true,
    say: 'Now we place a real order as the customer. The order service asks product for the price and inventory for stock, ' +
      'then saves the order and an outbox event in one transaction. The answer comes back immediately with status pending.',
  },
  {
    chapter: 'live', id: 'live-saga', kicker: 'Live tour · Saga', title: 'Watch the order cross Kafka',
    story: 'Inventory reserves stock, Payment charges once, Order confirms, Notification tells the customer. All through Kafka events.',
    points: ['order-events, inventory-events, payment-events.', 'If payment fails: stock released, order CANCELLED.'],
    live: 'saga',
    needsPassword: true,
    say: 'Now we watch the saga. We ask for the order status again and again. Inventory reserves the stock, payment charges once, ' +
      'and the order becomes confirmed. All of this happens through Kafka events, after the customer already got an answer.',
  },
  {
    chapter: 'live', id: 'live-zipkin', kicker: 'Live tour · Zipkin', title: 'One order, one trace',
    story: 'The trace id started at the Gateway and travelled through Feign calls, the outbox and Kafka.',
    points: ['Each bar is a span: one unit of work.', 'The longest bar is where the time went.'],
    live: 'zipkin',
    tool: { label: 'Open Zipkin', url: 'http://localhost:9411/zipkin/' },
    say: 'This is Zipkin. Our order produced one trace that crosses the gateway, order, inventory, payment and notification. ' +
      'Each bar is one piece of work, and the longest bar shows where the time went.',
  },
  {
    chapter: 'live', id: 'live-notify', kicker: 'Live tour · Notification', title: 'Notices never block an order',
    story: 'Notification listens to order events and writes the notice to its JSON log with the same trace id.',
    points: ['If it keeps failing: 4 tries with doubling waits, then order-events.DLT.', 'The order is still correct; only the notice is late.'],
    project: 'See it: docker compose logs notification-service. Demo switch: NOTIFICATION_FAIL=true sends messages to the dead-letter topic.',
    say: 'The notification service listens to order events and writes the customer notice to its log. ' +
      'If sending keeps failing, it retries four times with growing waits, then moves the message to a dead letter topic. ' +
      'The order itself is still correct.',
  },
  {
    chapter: 'live', id: 'live-review', kicker: 'Live tour · reviews (Bonus B1)', title: 'A review updates the rating through an event',
    story: 'Review Service saves the review and an outbox row, then sends ReviewSubmitted. Product updates its rating copy once per event.',
    points: ['First review: 201.', 'Second review by the same customer: 409.', 'Average shows on product detail within a second.'],
    live: 'review',
    needsPassword: true,
    say: 'Now our bonus feature, reviews. The customer posts a five star review. The review service saves it and sends an event, ' +
      'and the product service updates its rating copy. If the same customer reviews again, the answer is 409, one review per customer.',
  },
  {
    chapter: 'live', id: 'live-prom', kicker: 'Live tour · Prometheus', title: 'Metrics from every service',
    story: 'Prometheus pulls /actuator/prometheus from each app every 15 seconds.',
    points: ['up = 1 means the target answered.', 'Request rate, p95, errors, circuit breakers, JVM, Kafka lag.'],
    live: 'prometheus',
    tool: { label: 'Open Prometheus', url: 'http://localhost:9090/targets' },
    say: 'This is Prometheus. Every fifteen seconds it pulls metrics from every service. A value of one means the service answered. ' +
      'Here is the live status of every target.',
  },
  {
    chapter: 'live', id: 'live-grafana', kicker: 'Live tour · Grafana', title: 'The dashboard',
    story: 'Grafana draws the Prometheus numbers: requests per second, p95 latency, 5xx errors, circuit-breaker state, heap, Kafka consumer lag.',
    points: ['During a payment outage, Kafka lag rises here.', 'Log in as admin (password in .env).'],
    tool: { label: 'Open Grafana', url: 'http://localhost:3000/d/ecommerce-platform' },
    say: 'Grafana draws the numbers from Prometheus. Requests per second, the 95th percentile response time, errors, circuit breaker state, memory, ' +
      'and Kafka lag. When payment is down, you can see the lag grow here.',
  },
  {
    chapter: 'live', id: 'live-swagger', kicker: 'Live tour · API docs', title: 'Swagger UI',
    story: 'Every public endpoint is described in docs/api/openapi.yaml and can be tried from the browser.',
    points: ['All APIs live under /api/v1.', 'One error shape: status, error, message, path.'],
    tool: { label: 'Open Swagger UI', url: 'http://localhost:8089/' },
    say: 'Swagger shows every public endpoint of the platform. All APIs are under slash api slash v1, and every error has the same shape.',
  },
  {
    chapter: 'live', id: 'live-cicd', kicker: 'Live tour · delivery', title: 'From pull request to a self-healing cluster',
    story: 'Pull request, then tests with a 60 % coverage gate, then 9 images to GHCR tagged with the commit, then ArgoCD syncs the kind cluster.',
    points: ['Rollback is a git revert.', 'ArgoCD selfHeal undoes manual changes; prune removes deleted resources.'],
    tool: { label: 'Open GitHub Actions', url: 'https://github.com/husseineelsaka/E-Commerce-Platform/actions' },
    say: 'Finally, delivery. Every pull request runs the tests with a coverage gate. Then nine images are built and pushed with the commit id as the tag. ' +
      'ArgoCD keeps the Kubernetes cluster equal to Git, and a rollback is just a git revert.',
  },
  {
    chapter: 'live', id: 'live-end', kicker: 'Live tour · end', title: 'Questions',
    story: '"It works, but here is the trade-off we accepted, and here is what would make us reconsider it."',
    points: ['Every one of us can explain every service.', 'Evidence: scripts/e2e-test.sh — 80 passed, 0 failed.'],
    say: 'That is the whole platform, live. Every one of us can explain every service. Thank you, we are ready for your questions.',
  },
];
