# IPL High-Concurrency Ticket Booking System

A distributed, event-driven microservices platform designed to handle high-traffic flash-sale ticket drops, real-time stadium seat allocation, payment verification, and turnstile entry validation for the Indian Premier League.

Built with **Java 21**, **Spring Boot 3**, **Spring Cloud Gateway**, **Apache Kafka**, **Redis**, and **PostgreSQL**.

---

## 1. System Architecture

```
                          [ Client / Fans / Gate Scanners ]
                                         │
                                         ▼
                           ┌───────────────────────────┐
                           │  API Gateway (Port 8090)  │
                           │  - RS256 JWT Validation   │
                           │  - CORS & Rate Limiting   │
                           │  - Downstream Context     │
                           └─────────────┬─────────────┘
                                         │
         ┌───────────────────┬───────────┴───────────┬───────────────────┐
         │                   │                       │                   │
         ▼                   ▼                       ▼                   ▼
┌─────────────────┐ ┌─────────────────┐     ┌─────────────────┐ ┌─────────────────┐
│ipl-auth-service │ │ipl-event-service│     │ipl-order-service│ │ipl-notification │
│   (Port 8081)   │ │   (Port 8082)   │     │   (Port 8083)   │ │   (Port 8084)   │
│ - DB: auth_db   │ │ - DB: event_db  │     │ - DB: order_db  │ │ - DB: notif_db  │
│ - Redis (TTL)   │ │ - Outbox Pattern│     │ - Redis (Lua)   │ │ - In-App Inbox  │
└─────────────────┘ └────────┬────────┘     └────────▲────────┘ └────────▲────────┘
                             │                       │                   │
                             └───────────► [ Apache Kafka ] ─────────────┴─┘
                                           Topics:
                                           - ipl.match.scheduled
                                           - ipl.match.approved
                                           - ipl.match.tickets-opened
                                           - ipl.order.confirmed
                                           - ipl.ticket.released
```

---

## 2. Core Architectural & Design Decisions

### A. High-Concurrency Flash Sale (Redis Lua vs. Database Row Locks)
* **The Problem**: During an IPL ticket drop, 100,000+ users attempt to reserve tickets within seconds. Relying solely on PostgreSQL pessimistic locks (`SELECT ... FOR UPDATE`) leads to database connection pool starvation, lock contention, and request timeouts.
* **The Solution**: We implemented an in-memory inventory engine in `ipl-order-service` using **atomic Redis Lua scripts** (`EVAL`):
  ```lua
  local stock = redis.call('get', KEYS[1])
  if not stock or tonumber(stock) < tonumber(ARGV[1]) then
      return -1
  end
  return redis.call('decrby', KEYS[1], ARGV[1])
  ```
  - Reservation availability checks execute in **under 2ms** in Redis RAM.
  - Sold-out categories are rejected immediately without waking up PostgreSQL.
  - Successful reservations then persist the order record with an exclusive 10-minute hold window.

### B. Asymmetric RS256 JWT Authentication Boundary
* **The Problem**: Shared symmetric secrets (HMAC-SHA256) require distributing the signing secret to the Gateway and every microservice. A vulnerability in any downstream service compromises the entire auth boundary.
* **The Solution**: We use **asymmetric RSA (RS256)**:
  - `ipl-auth-service` exclusively holds the **RSA Private Key** to issue and sign tokens.
  - The API Gateway and all other microservices only receive the **RSA Public Key**.
  - Services verify token validity and extract user identity without making synchronous network calls to the auth database.
  - Immediate token invalidation on logout is achieved by storing the token's `jti` in a **Redis Blocklist** with an automatic TTL matching the token's remaining lifespan.

### C. Reliable Messaging via the Transactional Outbox Pattern
* **The Problem**: Directly publishing to Kafka inside a Spring `@Transactional` method creates a dual-write hazard. If Kafka blips or the network drops after the database commits, the event is permanently lost.
* **The Solution**: `ipl-event-service` writes domain events (e.g. `MatchApprovedEvent`) into a local `outbox_events` table inside the exact same database transaction as the match update. A background polling worker (`OutboxPublisherService`) batches pending events, publishes them to Kafka with exponential backoff retries, and marks them `PUBLISHED`.

### D. Two-Phase Ticket Hold with Distributed Leases
* **Phase 1 (Reserve)**: When a fan selects seats or general categories, the system acquires an in-memory lease (`SET seat:match:1:seat:101 <userId> NX EX 600`) and creates an `Order` in `PENDING` state with `expiresAt = now + 10 mins`.
* **Phase 2 (Pay)**: The user completes checkout via Razorpay or direct UPI. Cryptographic signatures are verified before transitioning the order to `CONFIRMED`.
* **Automatic Rollback**: A background scheduler (`OrderExpiryScheduler`) runs every 30 seconds to reclaim abandoned reservations, returning inventory to both Redis and PostgreSQL and releasing seat leases.

### E. Idempotent API Design
* Network timeouts often cause mobile clients to retry mutating requests. Every critical endpoint (`/orders/reserve`, `/orders/reserve-seats`, `/orders/{id}/pay`, `/orders/{id}/cancel`, `/events/matches/{id}/approve`) supports an `Idempotency-Key` header.
* Duplicate calls return the existing resource representation with `200 OK` rather than duplicating inventory deductions or throwing state errors.

### F. Tamper-Proof QR Ticketing & Turnstile Validation
* Confirmed orders generate digital tickets with cryptographically signed QR payloads (`IPL2026|<ticketNumber>|<matchId>|<seatNumber>|SIG:<hmacSha256>`).
* Base64 QR code images are rendered on-the-fly via Google ZXing.
* The turnstile validation endpoint (`POST /orders/tickets/validate-turnstile`) verifies the cryptographic signature offline and checks ticket state. Scanning an already used ticket immediately rejects entry with `DUPLICATE ENTRY DETECTED`, recording gate ID and timestamp.

---

## 3. Microservices Breakdown

| Service | Port | Database | Primary Responsibilities |
| :--- | :---: | :--- | :--- |
| **`ipl-api-gateway`** | `8090` | None | Single entry point, coarse RBAC routing, RS256 token verification, identity header injection (`X-User-Id`, `X-User-Role`), global CORS. |
| **`ipl-auth-service`** | `8081` | `auth_db` | User registration (Admin, Franchise, Fan), RS256 JWT generation, refresh token rotation, Redis token blocklist on logout. |
| **`ipl-event-service`** | `8082` | `event_db` | Stadium and Franchise management, match fixture scheduling, category capacity constraint validation ($Sum(Seats) \le Capacity$), Transactional Outbox. |
| **`ipl-order-service`** | `8083` | `order_db` | Redis Lua flash-sale inventory, physical seat map allocation, Razorpay payment verification, order expiration scheduler, turnstile gate scanner. |
| **`ipl-notification-service`** | `8084` | `notification_db` | Consumes Kafka events (`ipl.order.confirmed`, `ipl.ticket.released`, `ipl.match.tickets-opened`) to maintain in-system fan notification inboxes and broadcast alerts. |

---

## 4. API Endpoints Reference

All requests pass through the **API Gateway** on port `8090`.

### Authentication (`/auth/**`)
* `POST /auth/login` - Authenticate credentials; returns access & refresh tokens.
* `POST /auth/register` - Public fan self-registration (`ROLE_USER`).
* `POST /auth/franchise/register` - BCCI Admin endpoint to register team accounts (`ROLE_FRANCHISE`).
* `POST /auth/refresh` - Rotate refresh token.
* `POST /auth/logout` - Blacklist active token JTI in Redis.
* `GET  /auth/me` - Get authenticated user profile.
* `GET  /auth/public-key` - Retrieve RS256 public key.

### Events & Fixtures (`/events/**`)
* `POST /events/stadiums` - Register stadium venue with capacity (Admin).
* `GET  /events/stadiums` - List stadiums.
* `POST /events/franchises` - Register franchise team linked to home stadium (Admin).
* `GET  /events/franchises` - List franchises.
* `POST /events/matches` - Schedule match fixture (Status: `CONFIGURATION_PENDING`).
* `POST /events/matches/{id}/configuration` - Configure seat tiers and pricing (Franchise).
* `POST /events/matches/{id}/approve` - Approve match, set ticket open date, enqueue Kafka event (Admin).
* `GET  /events/matches` - List scheduled matches.

### Orders & Ticketing (`/orders/**`)
* `GET  /orders/inventory/{matchId}` - Query live category inventory.
* `POST /orders/reserve` - Reserve general category tickets (10-minute hold).
* `GET  /orders/matches/{matchId}/seats` - Live stadium seat map (`AVAILABLE`, `HELD`, `BOOKED`).
* `POST /orders/reserve-seats` - Reserve specific physical seats via Redis distributed lease.
* `POST /orders/{id}/razorpay/create` - Initiate Razorpay payment order.
* `POST /orders/{id}/pay` - Confirm payment via Razorpay HMAC signature or verified UPI.
* `POST /orders/{id}/cancel` - Cancel reservation and release inventory.
* `GET  /orders/my-orders` - List fan's booking history.
* `GET  /orders/tickets/my-tickets` - Retrieve digital tickets with Base64 QR codes.
* `POST /orders/tickets/validate-turnstile` - Stadium gate scanner verification.

### In-System Notifications (`/notifications/**`)
* `GET   /notifications` - Fetch user's in-app notification inbox.
* `GET   /notifications/unread-count` - Get unread count for UI badge.
* `PATCH /notifications/{id}/read` - Mark single notification as read.
* `PATCH /notifications/read-all` - Mark all notifications as read.
* `POST  /notifications/broadcast` - Broadcast announcement to all fans (Admin).

---

## 5. Local Setup & Running

### Prerequisites
* **Java 21**
* **Maven 3.9+**
* **Docker & Docker Compose**

### Step 1: Start Infrastructure
Start Redis and Kafka (running in KRaft mode):
```bash
docker compose up -d
```

### Step 2: Create PostgreSQL Databases
Connect to your PostgreSQL instance and create the required databases:
```sql
CREATE DATABASE auth_db;
CREATE DATABASE event_db;
CREATE DATABASE order_db;
CREATE DATABASE notification_db;
```

### Step 3: Run the Microservices
Start each service in a separate terminal:
```bash
# 1. API Gateway (Port 8090)
cd ipl-api-gateway && ./mvnw spring-boot:run

# 2. Auth Service (Port 8081)
cd ipl-auth-service && ./mvnw spring-boot:run

# 3. Event Service (Port 8082)
cd ipl-event-service && ./mvnw spring-boot:run

# 4. Order Service (Port 8083)
cd ipl-order-service && ./mvnw spring-boot:run

# 5. Notification Service (Port 8084)
cd ipl-notification-service && ./mvnw spring-boot:run
```

---

## 6. Testing

### Automated Unit & Logical Flow Tests
Run test suites across the services:
```bash
./mvnw test -f ipl-order-service/pom.xml
./mvnw test -f ipl-event-service/pom.xml
./mvnw test -f ipl-auth-service/pom.xml
./mvnw test -f ipl-notification-service/pom.xml
```

The test suite covers:
- In-memory Redis flash-sale atomic decrements and fast rejection.
- Capacity overflow boundary validations ($Sum(CategorySeats) \le StadiumCapacity$).
- Razorpay HMAC-SHA256 signature verification and UPI anti-replay fraud prevention.
- Cryptographic QR code generation and turnstile duplicate-entry defense.
- Idempotency guarantees across reservation, payment, and approval APIs.

### End-to-End Postman Collection
Import [`IPL_Ticket_Booking.postman_collection.json`](./IPL_Ticket_Booking.postman_collection.json) into Postman. It contains **41 automated requests** with dynamic variable chaining (IDs, tokens, and cryptographic signatures are passed automatically between steps).

