# Orbit Lockers

**Orbit** is a smart parcel locker network for the **Aveiro zone (Portugal)**, built for the TQS HW1 midterm
assignment.
Partners (online shops) register parcels for a locker station through a REST API or the web. A courier collects the
parcel, then drops it off at the station terminal. The recipient picks it up with a one-time code. An operator
supervises stations, expired parcels and faulty compartments.

The project is a Spring Boot 4 / Java 21 backend with server-rendered Thymeleaf pages and PostgreSQL. It follows a
ports-and-adapters design, so the locker hardware, the geocoding provider, notifications and time can all be
replaced by test doubles.

---

## Contents

1. [Quick start](#1-quick-start)
2. [Demo walkthrough](#2-demo-walkthrough)
3. [Pages (web UI)](#3-pages-web-ui)
4. [Architecture](#4-architecture)
5. [Parcel lifecycle](#5-parcel-lifecycle-state-machine)
6. [Business rules](#6-business-rules-configurable)
7. [REST API](#7-rest-api)
8. [Non-functional requirements](#8-non-functional-requirements)
9. [Design decisions](#9-design-decisions)
10. [Tests and quality](#10-tests-and-quality-assurance)
11. [Configuration reference](#11-configuration-reference)
12. [Project structure](#12-project-structure)
13. [Assumptions, interpretations and limitations](#13-assumptions-interpretations-and-limitations)

---

## 1. Quick start

### Requirements

| Tool | Needed for |
|---|---|
| JDK 21+ (built with `release 21`) | building and running |
| Docker | PostgreSQL, and the integration/functional/BDD tests (Testcontainers) |
| (optional) k6 | load tests |

The Maven wrapper (`./mvnw`) is included, so you don't need a local Maven.

### Option A: everything in Docker

```bash
docker compose up --build
# open http://localhost:8080   (port busy? ORBIT_PORT=8091 docker compose up --build)
```

### Option B: database in Docker, app from source (for development)

```bash
docker compose up -d db          # PostgreSQL 17 on localhost:5432 (db/user/password: orbit)
./mvnw spring-boot:run           # http://localhost:8080
```

Flyway creates the schema and seeds **5 stations in the Aveiro zone** with real coordinates: Fórum Aveiro,
Estação de Aveiro, Universidade de Aveiro, Glicínias Plaza and Ílhavo Centro. Each station has 16 compartments in
mixed sizes (Glicínias Plaza has 12). With demo mode on (the default), three demo parcels are also
created at startup.

### Useful URLs

| URL | What |
|---|---|
| http://localhost:8080/ | Find stations (recipient) |
| http://localhost:8080/register | Register a locker (partner, 3 steps) |
| http://localhost:8080/tracking | Track a parcel |
| http://localhost:8080/operator | Operator workspace |
| http://localhost:8080/terminal | Locker terminal simulator (pick a station) |
| http://localhost:8080/courier | Courier: scan delivery code (collect), report location |
| http://localhost:8080/demo | Demo tools: notification inbox, fault injection, clock fast-forward |
| http://localhost:8080/swagger-ui.html | OpenAPI / Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health check |

### Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `ORBIT_DB_URL` / `ORBIT_DB_USER` / `ORBIT_DB_PASSWORD` | `jdbc:postgresql://localhost:5432/orbit` / `orbit` / `orbit` | Database |
| `ORBIT_GEOCODER` | `nominatim` | `nominatim` (real OpenStreetMap API) or `stub` (offline, fixed Aveiro addresses) |
| `ORBIT_DEMO` | `true` | Enables `/demo` and `/api/v1/demo/**` (inbox, fault injection, clock). **Set to `false` in production.** |
| `ORBIT_DEMO_SEED` | `true` | Seeds three demo parcels when the database has none |
| `ORBIT_CODE_SECRET` | dev value | HMAC secret used to store PINs (change it in production) |
| `PORT` | `8080` | HTTP port of the app (when run from source) |
| `ORBIT_PORT` | `8080` | Host port published by `docker compose` |
| `SPRING_PROFILES_ACTIVE` | | `loadtest` = stub geocoder, no seed, quiet logs |

---

## 2. Demo walkthrough

Real SMS/e-mail are out of scope. Every notification goes through the **notifier port**: it is logged (without the
code) and stored in an in-memory outbox that the **Demo tools** page shows as an inbox. Use that inbox to read the
pickup code "sent" to the recipient.

1. **Find a locker (UC1):** `/` lists every station when it opens. Search `Rua de Coimbra 10, Aveiro` to rank them by
   distance, and pick a **radius** (any distance, or 1/2/5/10/20 km) to keep only the closest. *Show all stations*
   goes back to the full list. Click a station to see its free compartments.
2. **Register (UC2):** go to `/register`, choose *Medium* and a station, then pick a green compartment and continue.
   Fill in the lease and the contacts, then submit. The confirmation shows the **tracking code**, the **delivery code
   (QR)** and the **drop-off PIN**.
3. **Courier collects:** on `/courier`, paste the delivery code. The parcel becomes `ON_ITS_WAY`. You can also report
   a location here.
4. **Drop-off (UC3):** open `/terminal`, pick the station, then *Drop off a parcel*. Enter the drop-off PIN, the door
   "opens", then press *I have closed the door*. The parcel becomes `READY_FOR_PICKUP`, and the pickup code appears in
   the demo inbox.
5. **Pick up (UC4):** on the same terminal, choose *Collect a parcel* and enter the pickup code. The parcel becomes
   `PICKED_UP`.
6. **Track (UC5):** `/tracking` shows the current state, the timestamped history and the last location.
7. **Supervise (UC6):** `/operator`, then a station. From there you can filter parcels by state, register the removal
   of expired parcels and put compartments out of service. Malfunction reports are listed too.
8. **Hardware failure:** on `/demo`, inject *Door does not open* for a compartment, then try the drop-off. The
   terminal offers *Use another compartment*, the new drop-off code arrives in the inbox, and the incident shows up on
   the operator page.
9. **Time-based rules:** on `/demo`, use *+72 h* or *+15 days*. The clock moves forward and the deadline job runs
   immediately: leases end (`CANCELLED`) and pickup windows end (`EXPIRED`).

---

## 3. Pages (web UI)

The pages follow the layout of the mockups in `../Mockup` (Find stations, Register 1/2/3, Tracking, Tracking detail,
Operator, Operator parcel detail). The mockups show Warsaw; the application is set in Aveiro instead.
The terminal, courier, station supervision and demo pages had no mockup, so they reuse the same design system.

| Page | Route | Actor | Mockup |
|---|---|---|---|
| Find stations | `/` | Recipient | ✔ |
| Register: find a station | `/register` | Partner | ✔ |
| Register: choose a compartment | `/register/{station}?size=M` | Partner | ✔ (available and reserved states) |
| Register: your details + confirmation | `/register/{station}/{compartment}?size=M` | Partner | ✔ |
| Tracking (search, error) | `/tracking` | Recipient / Partner | ✔ |
| Tracking detail | `/tracking/{trackingCode}` | Recipient / Partner | ✔ |
| Operator workspace | `/operator` | Operator | ✔ |
| Station supervision | `/operator/stations/{station}` | Operator | (new) |
| Parcel details + lifecycle | `/operator/parcels/{trackingCode}` | Operator | ✔ |
| Locker terminal (kiosk) | `/terminal/{station}` | Courier / Recipient | (new) |
| Courier | `/courier` | Courier | (new) |
| Demo tools | `/demo` | Demo only | (new) |

Pages are rendered by Thymeleaf (layout, navigation, static content). Live data is loaded from the REST API by small
vanilla-JS modules in `src/main/resources/static/js`, and the station and parcel views refresh every 60 s (NFR7).
Maps use Leaflet with OpenStreetMap tiles. They are optional: the pages still work offline and in tests.

---

## 4. Architecture

```
            Thymeleaf pages + JS                 Partner systems
                     │                                  │
┌────────────────────┼──────────────────────────────────┼─────────────────────┐
│ Spring Boot (Java 21)                                                       │
│   Web: PageController (views) · REST controllers (validation, status codes, │
│        RFC 9457 errors) · OpenAPI                                           │
│   Domain services: StationService · ParcelService · TerminalService ·       │
│        OperatorService · DeadlineService · CompartmentAllocator ·           │
│        CodeService/PinIssuer · AttemptGuard · PricingPolicy                 │
│   Domain model: Parcel + ParcelStateMachine (pure Java) · Station ·         │
│        Compartment · Incident                                               │
│                                                                             │
│   PORTS (owned by the domain)        ADAPTERS                TEST DOUBLES   │
│   LockerHardwarePort  ──────────────  FakeLockerHardware      (same fake,   │
│                                       + LoggingLockerHardware  fault inj.)  │
│   NotifierPort        ──────────────  LogNotifier + Outbox    RecordingNotifier│
│   GeocoderPort        ──────────────  CachingGeocoder(Nominatim) StubGeocoder,│
│                                                               WireMock       │
│   Repositories (Spring Data JPA) ───  PostgreSQL               Testcontainers│
│   java.time.Clock     ──────────────  MutableClock (system)   MutableClock   │
│                                                               (fixed, moved) │
└─────────────────────────────────────────────────────────────────────────────┘
          │                                         │
   OpenStreetMap Nominatim                   PostgreSQL 17 (Docker)
```

Tech stack: Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator, Flyway), Java 21, Maven, PostgreSQL 17,
Thymeleaf, springdoc-openapi 3, Leaflet. All public classes and members carry Javadoc (see §10.8). Tests use JUnit 6, AssertJ, Mockito, Spring Boot test slices,
Testcontainers 2, WireMock 3, Playwright for Java, axe-core, Cucumber 8 and k6. Coverage is measured with JaCoCo
and static analysis with SonarQube.

**Logging.** Each of the following is written to the log:

* every state transition (logger `orbit.lifecycle`)
* every hardware port call (`orbit.hardware`, through the `LoggingLockerHardware` decorator)
* every geocoding request with its latency and result (`orbit.geocoding`)
* every notification (`orbit.notifications`, contacts masked and codes never logged)
* every incident

---

## 5. Parcel lifecycle (state machine)

This follows `../StateMachine/StateMachineDiagram.png` and the report template.

```
            ┌────────────► CANCELLED (end)
            │ CANCEL (partner/recipient) · DROP_OFF_DEADLINE_PASSED (system)
 CREATED ───┤
            │ COLLECT (courier scans QR)
            └────────────► ON_ITS_WAY ──DEPOSIT (courier, door closed)──► READY_FOR_PICKUP
                                                                            │        │
                                               PICK_UP (recipient) ◄────────┘        │ PICKUP_DEADLINE_PASSED (system)
                                                     │                               ▼
            PICKED_UP (end) ◄── PICK_UP ──────────── EXPIRED ── MARK_TO_RETURN (system/operator) ──► TO_RETURN
                    ▲                                                                                │   │
                    └───────────────────────── PICK_UP (recipient) ──────────────────────────────────┘   │
                                                                     REMOVE (courier/operator) ──► RETURNED (end)
```

| From | Event | Allowed actors | To |
|---|---|---|---|
| (none) | register | PARTNER | CREATED |
| CREATED | CANCEL | PARTNER, RECIPIENT | CANCELLED |
| CREATED | DROP_OFF_DEADLINE_PASSED | SYSTEM | CANCELLED |
| CREATED | COLLECT | COURIER | ON_ITS_WAY |
| ON_ITS_WAY | DEPOSIT | COURIER | READY_FOR_PICKUP |
| READY_FOR_PICKUP | PICK_UP | RECIPIENT | PICKED_UP |
| READY_FOR_PICKUP | PICKUP_DEADLINE_PASSED | SYSTEM | EXPIRED |
| EXPIRED | PICK_UP | RECIPIENT | PICKED_UP |
| EXPIRED | MARK_TO_RETURN | SYSTEM, OPERATOR | TO_RETURN |
| TO_RETURN | PICK_UP | RECIPIENT | PICKED_UP |
| TO_RETURN | REMOVE | COURIER, OPERATOR | RETURNED |

* The table lives in `domain/ParcelStateMachine.java`, which is pure Java. `Parcel.apply(event, actor, time, note)` is
  the only way a parcel's state changes. Each call records a `ParcelTransition` with the timestamp, the actor and a
  note.
* Any other (state, event) pair, or an actor not allowed for the event, is rejected with
  `InvalidTransitionException`. The API turns that into **409 `INVALID_TRANSITION`** with `currentState`, `event`,
  `actor` and `allowedEvents`.
* `TO_RETURN` is set **automatically** (actor SYSTEM) when an `EXPIRED` parcel's compartment is allocated to a new
  parcel, or **manually** by an operator. `RETURNED` is set automatically (actor COURIER) when the next parcel is
  deposited in that compartment and the door closes. An operator can also *register the removal*, which records
  `EXPIRED → TO_RETURN → RETURNED`.

---

## 6. Business rules (configurable)

All values live under `orbit.*` in `src/main/resources/application.yml` and are bound to `config/OrbitProperties`.

| Rule / theme | Value and rationale | Configured in | Tested in |
|---|---|---|---|
| **Compartment allocation** | A parcel only goes to an **in-service compartment of the same size** (S/M/L). One active reservation (CREATED, ON_ITS_WAY, READY_FOR_PICKUP) per compartment. A compartment holding an **EXPIRED** parcel *can* be allocated; the old parcel becomes TO_RETURN and the courier removes it on the next drop-off. The partner may pick a compartment (web front view) or let the system choose the first free one. | code: `CompartmentAllocator`; DB: partial unique index `uq_parcel_active_compartment` | `CompartmentAllocatorTest`, `RepositoryIT`, `ParcelLifecycleApiIT`, `02_register_parcel.feature` |
| **Station search radius** | The **user chooses** the radius for each search: *Any distance* (default) or one of the configured options (1, 2, 5, 10, 20 km). The API accepts any `radiusKm` in (0, 20] km; anything else is **422 `INVALID_RADIUS`**. Without a search, the Find stations page lists **all stations** alphabetically. An address search returns at most 10 stations. | `orbit.search.radius-options-km`, `max-radius-km`, `max-results` | `StationRankingTest`, `ParcelLifecycleApiIT`, `FindStationsWebIT`, `01_find_locker.feature` |
| **Station full** | No free compatible compartment: **409 `STATION_FULL`**. A chosen compartment that is already taken: **409 `COMPARTMENT_NOT_AVAILABLE`**. | | `ParcelLifecycleApiIT`, BDD |
| **Two partners, last compartment** | Allocation locks the station row (`SELECT … FOR UPDATE`), and the partial unique index is a second line of defence. | `StationRepository.findByCodeForUpdate` | `ConcurrentAllocationIT` (24 concurrent HTTP requests) |
| **Drop-off deadline (lease)** | The partner reserves the compartment for a **lease of 24, 48 or 72 h** (as in the mockups), starting now or up to 7 days ahead. The lease end is the drop-off deadline: a parcel still CREATED then is **CANCELLED by the system** and the compartment is released. | `orbit.rules.lease-hours-options`, `default-lease-hours`, `max-lease-start-ahead` | `DeadlineServiceTest`, `DeadlineAndReturnIT`, `06_deadlines.feature` |
| **Price** | **3.00 EUR per started day** of lease, VAT included: 24 h = 3, 48 h = 6, 72 h = 9 EUR. | `orbit.rules.price-per-day`, `currency` | `PricingPolicyTest` |
| **Pickup deadline** | Pickup is allowed for **15 days after drop-off**. After that the parcel is **EXPIRED**, but the recipient can still collect it with the same code until it is removed (TO_RETURN → RETURNED). | `orbit.rules.pickup-window` | `DeadlineServiceTest`, `DeadlineAndReturnIT`, BDD |
| **Delivery code** | `DLV-` + 12 random characters, shown as a **QR code** on the label. The courier scans it when collecting at the partner (CREATED → ON_ITS_WAY). | `CodeService` | `CodeServiceTest` |
| **Tracking code** | `ORB-yyMMdd-NNNN` (Lisbon date + 4 digits), unique and public. | `CodeService` | `CodeServiceTest` |
| **Drop-off code** | **6-digit PIN** (`SecureRandom`) printed on the label, unique among the station's pending drop-offs. It becomes invalid after the deposit. | `orbit.codes.pin-length` | `CodeServiceTest`, `TerminalServiceTest` |
| **Pickup code** | **6-digit PIN** (`SecureRandom`) sent to the recipient by the notifier when the parcel is deposited. It is always different from the drop-off code and unique among collectable parcels at the station. It is **single use** (consumed when the door opens) and valid while READY_FOR_PICKUP, EXPIRED or TO_RETURN. | | `TerminalServiceTest`, `ParcelLifecycleApiIT`, `04_pickup.feature` |
| **Protection against guessing** | After **3 consecutive wrong codes** at a station terminal (drop-off and pickup counted separately), code entry is **blocked for 1 hour** (429 + `Retry-After`). A correct code resets the counter. PINs are stored only as **HMAC-SHA256**. | `orbit.codes.max-failed-attempts`, `lockout`, `hmac-secret` | `AttemptGuardTest`, `TerminalServiceTest`, `DeadlineAndReturnIT`, BDD, `TerminalWebIT` |
| **Door does not open (drop-off)** | The compartment goes **out of service**, an incident is recorded and the **operator is alerted**. The terminal offers *Use another compartment*: if a free compartment of the same size exists, the reservation moves there and a **new drop-off code** goes to the sender's contact. Entering it opens the new compartment. | `TerminalService` | `TerminalServiceTest`, `HardwareFailureIT`, `03_drop_off.feature`, `TerminalWebIT` |
| **Door does not close (drop-off)** | Same as above. The parcel stays ON_ITS_WAY, so the courier takes it out and uses the replacement. | | `TerminalServiceTest`, `HardwareFailureIT` |
| **Door does not open (pickup)** | The parcel is physically inside, so it can't move. The compartment goes out of service, the operator is alerted and the **code stays valid**. The recipient is told to call assistance. | | `TerminalServiceTest` |
| **Door does not close (pickup)** | The pickup stands (PICKED_UP). The compartment goes out of service and the operator is alerted. | | `TerminalServiceTest` |
| **Interface does not respond** (controller unreachable) | **503 `HARDWARE_UNAVAILABLE`**. No state changes, and an incident is recorded. The terminal shows the **assistance number**. | `orbit.assistance-phone` | `TerminalServiceTest`, `HardwareFailureIT`, BDD |
| **Operator puts a compartment out of service** | Allowed. A pending reservation (CREATED/ON_ITS_WAY) moves to another free compartment of the same size, with a new drop-off code. If none is free: **409 `NO_REPLACEMENT_AVAILABLE`**. | | `HardwareFailureIT`, `07_operator.feature`, `OperatorWebIT` |
| **Time-based rules** | Every rule reads time from an injected `Clock`. `DeadlineService` runs every 60 s; tests call it after moving a `MutableClock`, so **no test sleeps**. | `orbit.rules.deadline-check-interval` | all time tests |

---

## 7. REST API

Base path: `/api/v1`. The full documentation is in Swagger UI at `/swagger-ui.html`. JSON timestamps are ISO-8601
UTC instants.

### Error format (RFC 9457 problem details)

Every error is `application/problem+json` with a stable machine-readable `code`:

```json
{
  "type": "https://orbit.example/problems/invalid-transition",
  "title": "Conflict",
  "status": 409,
  "detail": "Event CANCEL is not allowed in state PICKED_UP",
  "instance": "/api/v1/parcels/ORB-261025-7552/cancel",
  "code": "INVALID_TRANSITION",
  "timestamp": "2026-10-25T08:34:53.256Z",
  "currentState": "PICKED_UP", "event": "CANCEL", "actor": "PARTNER", "allowedEvents": []
}
```

| Status | Codes |
|---|---|
| 400 | `VALIDATION_FAILED` (with `errors{field: message}`), `MALFORMED_REQUEST` |
| 404 | `STATION_NOT_FOUND`, `PARCEL_NOT_FOUND`, `COMPARTMENT_NOT_FOUND`, `ADDRESS_NOT_FOUND`, `DELIVERY_CODE_NOT_FOUND`, `SESSION_NOT_FOUND` |
| 409 | `INVALID_TRANSITION`, `STATION_FULL`, `COMPARTMENT_NOT_AVAILABLE`, `NO_REPLACEMENT_AVAILABLE`, `NO_FAILURE_REPORTED`, `INVALID_STATE`, `DROP_OFF_DEADLINE_PASSED`, `CONCURRENT_UPDATE` |
| 422 | `INVALID_RADIUS` (with `maxRadiusKm`), `INVALID_CODE` (with `remainingAttempts`), `INVALID_LEASE_DURATION`, `INVALID_LEASE_START`, `SIZE_MISMATCH`, `INVALID_ACTOR`, `ADDRESS_REQUIRED`, `INVALID_COORDINATES` |
| 429 | `TOO_MANY_ATTEMPTS` (with `lockedUntil`, `Retry-After` header) |
| 503 | `GEOCODER_UNAVAILABLE` (with `reason`, `fallback: /api/v1/stations`, `Retry-After`), `HARDWARE_UNAVAILABLE` (with `assistancePhone`) |

### Stations (UC1)

| Method | Path | Description | Responses |
|---|---|---|---|
| GET | `/stations?size=S\|M\|L` | All stations with free compartments per size; with `size`, only those with one free of that size | 200 |
| GET | `/stations/nearby?address=…[&size=][&radiusKm=]` | Geocodes the address (Nominatim, results limited to Portugal) and returns stations nearest first. `radiusKm` is chosen by the user; omitted means any distance | 200, 404 `ADDRESS_NOT_FOUND`, 422 `INVALID_RADIUS` (outside (0, 20] km, with `maxRadiusKm`), 503 `GEOCODER_UNAVAILABLE` |
| GET | `/stations/nearby?lat=…&lon=…[&size=][&radiusKm=]` | Same, from coordinates ("use my location") | 200, 422 |
| GET | `/stations/{code}` | Station detail and the front view of every compartment (`AVAILABLE`/`RESERVED`/`OUT_OF_SERVICE`, `reservedUntil`) | 200, 404 |

```bash
curl "http://localhost:8080/api/v1/stations/nearby?address=Rua%20de%20Coimbra%2010,%20Aveiro&size=M&radiusKm=2"
```

### Parcels: partner and recipient (UC2, UC5)

| Method | Path | Description | Responses |
|---|---|---|---|
| POST | `/parcels` | Register a parcel and reserve a compartment | 201 + `Location`, 400, 404, 409, 422 |
| GET | `/parcels/{trackingCode}` | Track: state, history (timestamp + actor), locations, deadlines, allowed events. The recipient name is masked. | 200, 404 |
| POST | `/parcels/{trackingCode}/cancel` | Body `{"actor":"PARTNER"\|"RECIPIENT","reason":"…"}` (optional). Only valid in CREATED. | 200, 409, 422 |
| POST | `/parcels/{trackingCode}/locations` | Courier location update `{"label","latitude","longitude"}`. Only valid in ON_ITS_WAY. | 200, 409 |

```bash
curl -X POST http://localhost:8080/api/v1/parcels -H 'Content-Type: application/json' -d '{
  "stationCode": "ORB-AVR-001", "size": "M", "compartmentCode": "M-07",
  "leaseStart": "2026-10-25T10:00:00Z", "leaseHours": 48,
  "sender":    {"name": "Ana Silva", "email": "ana.silva@example.com", "phone": "+351 912 345 678"},
  "recipient": {"name": "João Santos",   "email": "joao.santos@example.com",   "phone": "+351 934 567 890"}}'
```

`compartmentCode` and `leaseStart` are optional (defaults: first free compartment, now). `leaseHours` defaults to 48.

```json
{ "trackingCode": "ORB-261025-7552", "state": "CREATED", "stationCode": "ORB-AVR-001", "stationName": "Fórum Aveiro",
  "compartmentCode": "M-07", "size": "M", "leaseStart": "…", "leaseHours": 48, "dropOffDeadline": "…",
  "price": 6.00, "currency": "EUR", "deliveryCode": "DLV-UMS2AMJBRPED", "dropOffCode": "991106" }
```

The drop-off PIN is returned only once, in this response. The database stores just its HMAC.

### Courier

| Method | Path | Description | Responses |
|---|---|---|---|
| POST | `/courier/collections` | `{"deliveryCode":"DLV-…","locationLabel":"…"}`, moves CREATED to ON_ITS_WAY | 200, 404, 409 |

### Locker terminal (UC3, UC4)

The interaction is synchronous and takes two steps. Step 1 opens the door and returns a short-lived `sessionToken`
(5 min). Step 2 confirms that the door is closed, after the backend reads the door sensor through the hardware port.
Hardware outcomes come back as **200** with an `outcome` field, because they are expected situations the terminal
must handle, not client errors.

| Method | Path | Body | Outcomes / errors |
|---|---|---|---|
| POST | `/terminals/{station}/drop-offs` | `{"code":"123456"}` | `DOOR_OPENED`, `DOOR_DID_NOT_OPEN` (+`replacementAvailable`) · 409 `INVALID_TRANSITION` (not collected) · 422 `INVALID_CODE` · 429 · 503 |
| POST | `/terminals/{station}/drop-offs/{token}/door-closed` | | `DEPOSITED` (pickup code sent), `DOOR_DID_NOT_CLOSE` |
| POST | `/terminals/{station}/drop-offs/{token}/replacement` | | `REPLACEMENT_ISSUED` (new code sent), `NO_REPLACEMENT_AVAILABLE` |
| POST | `/terminals/{station}/pickups` | `{"code":"654321"}` | `DOOR_OPENED` (parcel PICKED_UP, code consumed), `DOOR_DID_NOT_OPEN` (code stays valid) · 422 · 429 · 503 |
| POST | `/terminals/{station}/pickups/{token}/door-closed` | | `COMPLETED`, `DOOR_DID_NOT_CLOSE` |

```json
{ "outcome": "DOOR_OPENED", "message": "Compartment M-03 is open. Place the parcel inside and close the door.",
  "trackingCode": "ORB-261025-7552", "state": "ON_ITS_WAY", "compartmentCode": "M-03",
  "sessionToken": "724a7a0c-…", "replacementAvailable": null, "assistancePhone": "+351 234 100 200" }
```

### Operator (UC6)

| Method | Path | Description | Responses |
|---|---|---|---|
| GET | `/operator/parcels/recent` | Latest 20 parcels across the network | 200 |
| GET | `/operator/parcels/{trackingCode}` | Full detail (contacts, price, history, allowed events) | 200, 404 |
| GET | `/operator/stations/{station}/parcels?state=EXPIRED&state=…` | Parcels of a station, optionally filtered by state | 200, 404 |
| GET | `/operator/stations/{station}/incidents` | Malfunction reports | 200 |
| POST | `/operator/parcels/{trackingCode}/removal` | Register the removal of an EXPIRED or TO_RETURN parcel, which becomes RETURNED | 200, 409 |
| POST | `/operator/parcels/{trackingCode}/mark-to-return` | EXPIRED to TO_RETURN | 200, 409 |
| PUT | `/operator/stations/{station}/compartments/{code}/status` | `{"status":"OUT_OF_SERVICE"\|"IN_SERVICE","reason":"…"}` | 204, 404, 409 |

### Other

| Method | Path | Description |
|---|---|---|
| GET | `/config` | Public business parameters for the pages (lease options, price, radius options and maximum, assistance phone) |
| GET / DELETE | `/demo/notifications` | Demo inbox *(demo mode only)* |
| GET / POST / DELETE | `/demo/hardware/faults` | Inject `{"stationCode","compartmentCode","fault":"DOOR_WONT_OPEN\|DOOR_WONT_CLOSE\|UNREACHABLE"}` *(demo only)* |
| GET | `/demo/clock` · POST `/demo/clock/advance {"hours":72}` · POST `/demo/clock/reset` | Clock fast-forward, which also runs the deadline job *(demo only)* |

---

## 8. Non-functional requirements

| ID | Requirement (measurable) | Verification |
|---|---|---|
| NFR1 | p95 response time **< 500 ms with 50 concurrent users** | k6 `load-tests/orbit-load.js` (threshold `http_req_duration p(95)<500`). Measured locally: **p95 = 15 ms**, 0% errors, 8,218 requests in 70 s |
| NFR2 | **WCAG 2.2 AA** on all pages (REST API excluded) | `AccessibilityWebIT`: axe-core on 13 pages, tags wcag2a/aa, 21a/aa, 22aa, no violations allowed. Plus a manual keyboard walkthrough (see §10.6) |
| NFR3 | 6-digit codes generated with **SecureRandom**, stored only as HMAC | `CodeServiceTest` (format, distribution, keyed hash) and code review |
| NFR4 | **Nominatim usage policy**: at most 1 req/s, identifying User-Agent, cached results, and no calls from tests | `NominatimGeocoderIT` (User-Agent, 1 req/s enforced), `CachingGeocoderTest`. Every test and the load test use the stub or WireMock |
| NFR5 | p95 **< 300 ms** for read endpoints with 50 concurrent users | k6 threshold `http_req_duration{type:read} p(95)<300`. Measured **p95 = 11.5 ms** |
| NFR6 | Pickup codes are **single use** | `TerminalServiceTest`, `ParcelLifecycleApiIT.pickupCodeIsSingleUse`, `04_pickup.feature` |
| NFR7 | Client state refreshes **at least every 2 minutes** | Pages poll every 60 s (`setInterval(…, 60000)` in the page scripts) |
| NFR8 | Code entry **blocks after 3 consecutive wrong attempts** at a terminal | `AttemptGuardTest`, `TerminalServiceTest`, `TerminalWebIT`, BDD |

---

## 9. Design decisions

| Decision | Option chosen | Impact on testing |
|---|---|---|
| Locker terminal simulation | A web kiosk page (`/terminal/{station}`) calling the terminal REST endpoints. The backend talks to the hardware port; the page never does. | UI journeys are automated with Playwright (`TerminalWebIT`), and the same rules are tested without a browser at service and API level. |
| Parcel lifecycle in code | An explicit transition table (`ParcelStateMachine`, pure Java, no library) plus `Parcel.apply`, the single mutator, which records history. | Exhaustive **parameterized unit test over all 8×8 (state, event) pairs** plus actor rules. No Spring or DB needed. |
| Controllable time | A `java.time.Clock` bean, always a `MutableClock`. Production uses system time with offset 0. | Tests freeze and move the clock. `DeadlineService.processDeadlines()` is called directly, with **no sleeps**. The demo page reuses this for fast-forward. |
| Hardware port | `LockerHardwarePort { boolean openDoor(ref); DoorStatus readDoor(ref); }`, **synchronous**. A two-step terminal flow with a session token. | `FakeLockerHardware` keeps door state per compartment and injects `DOOR_WONT_OPEN`, `DOOR_WONT_CLOSE` and `UNREACHABLE`. With `auto-close=true` it behaves as a working locker (demo). The same fake is used by unit tests, ITs, BDD and web tests. |
| Concurrent allocation | Pessimistic lock on the **station row** (serialises allocations per station) plus a **partial unique index** on active reservations. | `ConcurrentAllocationIT` fires 24 parallel HTTP requests at real PostgreSQL. `RepositoryIT` checks that the index rejects a second active reservation. |
| Pickup codes | 6-digit `SecureRandom` PINs, stored as **HMAC-SHA256** (keyed, so the DB never holds the PIN, while lookup stays one indexed query). Unique per station among parcels that can use them. Consumed on first use. A per-terminal lockout guards against guessing. | Unit tests for generation, hashing and lockout. ITs/BDD for single use and per-station validity. |
| Geocoding provider | **OpenStreetMap Nominatim** (free, documented policy). `CachingGeocoder` (24 h positive, 1 h negative, bounded) wraps `NominatimGeocoder`, which uses 2 s connect / 3 s read timeouts and enforces at most 1 req/s locally. Slow, error, 429/403 or unreachable becomes `GeocodingUnavailableException`, which the API returns as **503** with a fallback hint. The UI then lists all stations unranked. Unknown address returns **404**. | `NominatimGeocoderIT` against **WireMock** (success, empty, timeout, 5xx, 429, malformed, rate limit). The other tests and the load test use `StubGeocoder` (`orbit.geocoding.provider=stub`). Distance and ranking are our code (`DistanceCalculatorTest`, `StationRankingTest`). |
| REST API design | Resource-oriented under `/api/v1`. Commands are explicit sub-resources (`/cancel`, `/removal`, `/drop-offs`). RFC 9457 errors carry a stable `code`. 409 means "not valid in the current state", 422 a rule violation, 429 a lockout, 503 a dependency down. Hardware outcomes are 200 with `outcome`. | `ApiContractWebMvcTest` (a `@WebMvcTest` slice with mocked services) checks status codes and error bodies. ITs check them end to end. |
| Notifications | `NotifierPort`, implemented by `LogNotifier`, which logs without secrets and keeps an in-memory outbox (the demo inbox). | `RecordingNotifier` fake in unit tests. ITs read codes from the outbox. |

---

## 10. Tests and quality assurance

### 10.1 How to run

| Command | Runs | Needs |
|---|---|---|
| `./mvnw test` | **Unit and slice tests** (surefire, `*Test`): 197 tests | nothing (no Docker) |
| `./mvnw verify` | Everything above, plus **integration, functional (Playwright), accessibility and BDD** (failsafe, `*IT`): 88 more, and a merged JaCoCo report | Docker (Testcontainers starts `postgres:17-alpine`). Playwright downloads Chromium on first run. |
| `./mvnw verify -Dit.test='*WebIT'` | Only the browser tests | Docker |
| `./mvnw verify -Dit.test=CucumberIT` | Only the BDD features (report at `target/cucumber-report.html`) | Docker |
| `k6 run load-tests/orbit-load.js` | Load test (set `BASE_URL`; the app must run with `SPRING_PROFILES_ACTIVE=loadtest`) | a running app |
| `./mvnw verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.token=…` | Static analysis and coverage in SonarQube (`docker run -d -p 9000:9000 sonarqube:community`) | SonarQube |

Reports:

* `target/site/jacoco/index.html` (unit coverage)
* `target/site/jacoco-merged/index.html` (unit + integration coverage, the report used by Sonar)
* `target/cucumber-report.html`
* `target/surefire-reports`, `target/failsafe-reports`

### 10.2 Overview

| Level | Where | What | Test doubles |
|---|---|---|---|
| Unit | `src/test/java/.../domain`, `service`, `adapter` | State machine (77 parameterized cases), parcel history, allocation, deadlines, codes, lockout, pricing, distance and ranking, terminal and **hardware failure rules**, caching | **Fakes**: `FakeLockerHardware`, `RecordingNotifier`, `StubGeocoder`, `MutableClock`. **Mocks** (Mockito) for repositories and to count provider calls in `CachingGeocoderTest`. |
| Web slice | `web/ApiContractWebMvcTest` | Validation, status codes, RFC 9457 bodies, `Retry-After` | `@MockitoBean` services |
| Integration | `integration/*IT` | JPA + Flyway + partial unique index (`@DataJpaTest`), full API lifecycle, **concurrency**, time rules, hardware failures over HTTP, Nominatim client | Testcontainers PostgreSQL, **WireMock** for Nominatim, the in-app fakes |
| Functional | `functional/*WebIT` | Browser journeys: find stations (and geocoder fallback), 3-step registration and tracking, **terminal drop-off and pickup**, wrong-code lockout, door failure and replacement, operator removal and out of service | Playwright (headless Chromium). External hosts are blocked so tests stay hermetic. |
| Accessibility | `functional/AccessibilityWebIT` | axe-core WCAG 2.2 AA on 13 pages | |
| Acceptance (BDD) | `src/test/resources/features/*.feature` + `bdd/ParcelSteps` | 21 scenarios for UC1–UC6, including **time-based** (`06_deadlines`) and **hardware failure** (`03_drop_off`) scenarios | Steps drive the **REST API**, the boundary every actor uses. Time and hardware are controlled through the fakes. |
| Load | `load-tests/orbit-load.js` | 40 VUs browsing and tracking + 10 VUs registering and cancelling | Stub geocoder (`loadtest` profile) |

All integration, functional and BDD tests share **one Spring context and one PostgreSQL container** (through the
context cache). Each test resets the database and the fakes.

### 10.3 Stability of browser tests

* Locators use `data-testid` and ARIA roles.
* Playwright web-first assertions auto-wait, so there are no sleeps.
* Data is arranged through the API.
* The clock and hardware are deterministic.
* External network calls (map tiles, CDN, fonts) are aborted.

### 10.4 Results (local run)

| Suite | Tests | Result |
|---|---|---|
| Unit + slice | 197 | ✅ |
| Integration | 39 | ✅ |
| Functional + accessibility | 28 | ✅ |
| BDD (Cucumber) | 21 scenarios | ✅ |
| Load (k6, 50 VUs, 70 s) | p95 15.43 ms (all), 11.46 ms (reads), 0 failed of 8,218 | ✅ thresholds met |
| Coverage (JaCoCo, unit + IT merged) | lines 92.8%, instructions 91.8%, branches 79.5% | |

### 10.5 Problems the tests found (and fixes)

* **axe-core** flagged out-of-service compartment labels at a contrast ratio of 4.44:1 (needs 4.5:1), inline links
  distinguishable only by colour, and muted text on tinted backgrounds. The colours were darkened and inline links
  underlined.
* **Playwright** caught a layout shift in the registration form. Blurring one field validated *all* fields, so an
  error appeared under an untouched field and then vanished, which moved the terms checkbox between mousedown and
  mouseup. Errors are now shown only for fields the user has already left (or on submit).

### 10.6 Manual keyboard walkthrough

Tab order follows the visual order, and a *Skip to content* link appears first. Every control is a native
button/link/input with a visible focus ring. The compartment front view is made of buttons with descriptive
`aria-label`s ("M-07, Medium, available"). The terminal keypad mirrors a regular, focusable PIN input. Status and
alert regions use `aria-live`/`role="alert"`. **Not verifiable automatically:** the clarity of messages, the
reading order on the map (the map is decorative, and the same information is in the list), and colour-only meaning
in the lifecycle diagram (each state also has a text label).

### 10.7 Code quality (SonarQube)

JaCoCo produces the unit, integration and merged reports. The merged XML is configured as
`sonar.coverage.jacoco.xmlReportPaths`. Suggested Quality Gate ("Sonar way" plus coverage):

* coverage on new code ≥ 80%
* no new bugs or vulnerabilities
* security hotspots reviewed
* duplicated lines < 3%

`OrbitApplication` and the demo package are excluded from coverage. CI (`.github/workflows/ci.yml`) runs
`./mvnw verify` and, when `SONAR_TOKEN` is set, runs the analysis with `sonar.qualitygate.wait=true`, so the build
fails when the gate fails.

### 10.8 Javadoc

Every public and package-visible class, constructor, method, record component, enum constant and package in
`src/main/java` has Javadoc. Business rules, the error codes thrown and the REST endpoint of each controller method
are documented in place. The build checks it strictly (`doclint=all`, warnings fail the goal):

```bash
./mvnw javadoc:javadoc        # -> target/reports/apidocs/index.html
```

---

## 11. Configuration reference

The full file is `src/main/resources/application.yml`. The main keys:

```yaml
orbit:
  assistance-phone: "+351 234 100 200"
  rules:   { lease-hours-options: [24,48,72], default-lease-hours: 48, price-per-day: 3.00, currency: EUR,
             pickup-window: 15d, max-lease-start-ahead: 7d, terminal-session-timeout: 5m, deadline-check-interval: 60s }
  codes:   { pin-length: 6, max-failed-attempts: 3, lockout: 1h, hmac-secret: ${ORBIT_CODE_SECRET} }
  search:  { radius-options-km: [1, 2, 5, 10, 20], max-radius-km: 20.0, max-results: 10 }
  geocoding: { provider: nominatim|stub, base-url: https://nominatim.openstreetmap.org, country-codes: pt,
               connect-timeout: 2s, read-timeout: 3s, min-interval: 1s, cache-ttl: 24h, negative-cache-ttl: 1h }
  hardware: { auto-close: true }      # fake behaves as a working locker
  demo:     { enabled: true, seed-parcels: true }
```

The profiles are `test` (`src/test/resources/application-test.yml`: stub geocoder, no seed, scheduler off) and
`loadtest`.

---

## 12. Project structure

```
orbit/
├── pom.xml · mvnw · Dockerfile · docker-compose.yml · .github/workflows/ci.yml
├── load-tests/orbit-load.js
├── src/main/java/pt/ua/deti/tqs/orbit/
│   ├── domain/        Parcel, ParcelStateMachine, ParcelState/Event, Actor, Station, Compartment, Incident, …
│   ├── port/          LockerHardwarePort, GeocoderPort, NotifierPort (+ value types / exceptions)
│   ├── adapter/       hardware/ (FakeLockerHardware, LoggingLockerHardware) · geocoding/ (Nominatim, Caching, Stub)
│   │                  notification/ (LogNotifier, NotificationOutbox)
│   ├── repository/    Spring Data JPA repositories (the persistence port)
│   ├── service/       domain services + view records
│   ├── web/api/       REST controllers, DTOs, ApiExceptionHandler
│   ├── web/view/      Thymeleaf PageController
│   ├── demo/          demo-only endpoints and seed data
│   └── config/        OrbitProperties, MutableClock, OpenAPI
├── src/main/resources/
│   ├── application.yml · application-loadtest.yml
│   ├── db/migration/  V1__schema.sql · V2__seed_stations.sql
│   ├── templates/     Thymeleaf pages + fragments
│   └── static/        css/orbit.css · js/*.js · img/icons.svg
└── src/test/
    ├── java/…/domain · service · adapter · web        unit + slice tests
    ├── java/…/integration                             *IT with Testcontainers / WireMock
    ├── java/…/functional                              Playwright + axe (*WebIT)
    ├── java/…/bdd                                     Cucumber runner + steps
    ├── java/…/support                                 fakes, fixtures, Testcontainers config
    └── resources/features/*.feature                   Gherkin
```

---

## 13. Assumptions, interpretations and limitations

* **City and data:** the network is in the **Aveiro zone, Portugal**: five stations in Aveiro and Ílhavo with real
  coordinates, prices in EUR, Portuguese postal codes and phone numbers, and times shown in Europe/Lisbon. Nominatim
  results are restricted to Portugal (`countrycodes=pt`). The mockups were drawn for Warsaw; only their layout and
  behaviour were kept.
* **Lease vs. drop-off deadline:** the mockups sell a **24/48/72 h lease** (priced per day; 3.00 EUR/day here), while
  the report template says "drop-off allowed up to 5 working days after the predicted delivery date". These are
  reconciled by using the **lease end as the drop-off deadline**, which is configurable. The pickup window (15 days
  after drop-off) follows the report.
* **Drop-off deadline while ON_ITS_WAY:** the state diagram has no ON_ITS_WAY → CANCELLED transition, so the
  automatic cancellation only applies to parcels still CREATED. A late courier can still drop off a parcel that is on
  its way.
* **Drop-off requires collection first:** per the diagram, READY_FOR_PICKUP is only reachable from ON_ITS_WAY. The
  courier must scan the QR (delivery code) before the terminal accepts the drop-off PIN, otherwise the result is 409.
* **Recipient door failures:** the report proposes "replace the locker" for any door failure. For a **pickup** the
  parcel is physically inside the faulty compartment, so it cannot move. The compartment goes out of service, the
  operator is alerted, the code stays valid and the recipient is told to call assistance.
* **Replacement code delivery:** the new drop-off code after a door failure is sent to the **sender's contact** (the
  partner prints labels). In the demo it appears in the inbox.
* **Lockout scope:** "3 wrong attempts" is counted **per station terminal and per code type**, because a code is
  typed without identifying the parcel. The lockout counter is in memory, so it resets on restart and is not shared
  between instances. That is acceptable for a single instance; a shared store would be needed to scale out.
* **No authentication** (out of scope): each actor has its own pages and endpoints, so operator endpoints are open.
  The demo endpoints must be disabled in production (`ORBIT_DEMO=false`).
* **Personas:** the header shows the actor role (Recipient, Partner, Operator) instead of a logged-in user, because
  there are no accounts.
* **Maps** are decorative (Leaflet + OSM tiles loaded from public CDNs). All information is also in text.
* **Not done:** real SMS/e-mail, payments, a mobile app, return-to-sender logistics, multi-city support.
