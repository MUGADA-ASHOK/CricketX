# IPL High-Concurrency Ticket Booking System

A distributed, event-driven microservices platform designed to handle high-traffic IPL ticket drops, real-time stadium seat allocation, payment verification, and turnstile entry validation.

**Technology stack:** Java 21, Spring Boot 3, Spring Cloud Gateway, Apache Kafka, Redis, PostgreSQL, React 18, Vite, and Tailwind CSS.

> **Important:** This README documents the architecture and behavior described for the project. Verify that each feature, endpoint, test, and configuration is implemented in the current codebase before presenting it as completed or production-ready. Never commit real credentials, private keys, payment secrets, or production tokens.

---

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Core Architectural and Design Decisions](#2-core-architectural-and-design-decisions)
3. [Microservices Breakdown](#3-microservices-breakdown)
4. [Real-Time Event Choreography](#4-real-time-event-choreography)
5. [API Endpoints Reference](#5-api-endpoints-reference)
6. [Frontend Multi-Role Portals](#6-frontend-multi-role-portals)
7. [Local Setup and Running](#7-local-setup-and-running)
8. [Master Credentials](#8-master-credentials)
9. [Testing and Quality Assurance](#9-testing-and-quality-assurance)

---

## 1. System Architecture

```text
                    [ Clients / Fans / Gate Scanners ]
                                     |
                                     v
                       +---------------------------+
                       | API Gateway (Port 8090)   |
                       | - RS256 JWT validation    |
                       | - CORS and rate limiting  |
                       | - Downstream context      |
                       +-------------+-------------+
                                     |
          +--------------------------+--------------------------+
          |              |              |                       |
          v              v              v                       v
 +----------------+ +----------------+ +----------------+ +--------------------+
 | ipl-auth-      | | ipl-event-     | | ipl-order-     | | ipl-notification-  |
 | service        | | service        | | service        | | service            |
 | Port: 8081     | | Port: 8082     | | Port: 8083     | | Port: 8084          |
 | DB: auth_db    | | DB: event_db   | | DB: order_db   | | DB: notification_db|
 | Redis TTL      | | Outbox pattern | | Redis Lua      | | In-app inbox       |
 +----------------+ +-------+--------+ +-------+--------+ +---------+----------+
                            |                  ^                    ^
                            |                  |                    |
                            +------------------+--------------------+
                                               |
                                      +--------v--------+
                                      |  Apache Kafka   |
                                      | Event messaging |
                                      +-----------------+
```

### Kafka Topics

- `ipl.match.scheduled`
- `ipl.match.approved`
- `ipl.match.tickets-opened`
- `ipl.order.confirmed`
- `ipl.ticket.released`

---

## 2. Core Architectural and Design Decisions

### A. High-Concurrency Flash Sale: Redis Lua vs. Database Row Locks

**Problem:** During an IPL ticket drop, a large number of users may attempt to reserve tickets within seconds. Relying only on PostgreSQL pessimistic locks (`SELECT ... FOR UPDATE`) can cause lock contention, connection-pool pressure, and request timeouts.

**Approach:** The `ipl-order-service` uses atomic Redis Lua scripts (`EVAL`) for inventory checks and decrements.

```lua
local stock = redis.call('get', KEYS[1])

if not stock or tonumber(stock) < tonumber(ARGV[1]) then
    return -1
end

return redis.call('decrby', KEYS[1], ARGV[1])
```

- Inventory checks and decrements are performed atomically in Redis.
- Sold-out categories can be rejected before creating database work.
- Successful reservations are persisted as orders with a 10-minute hold window.

### B. Asymmetric RS256 JWT Authentication Boundary

**Problem:** With a shared symmetric signing secret, the gateway and every downstream service need access to the same secret. A compromised service could therefore expose the token-signing secret.

**Approach:** The system uses asymmetric RSA signing with RS256.

- `ipl-auth-service` holds the RSA private key and signs tokens.
- The API Gateway and other services use the RSA public key to verify tokens.
- Services can validate tokens and extract user identity without making a synchronous request to the authentication database.
- Logout invalidation uses the token's `jti` in a Redis blocklist, with a TTL matching the token's remaining lifetime.

### C. Reliable Messaging: Transactional Outbox Pattern

**Problem:** Publishing directly to Kafka from a Spring `@Transactional` method creates a dual-write problem. The database transaction may commit while Kafka publishing fails, leaving the event unsent.

**Approach:** `ipl-event-service` stores domain events, such as `MatchApprovedEvent`, in an `outbox_events` table in the same database transaction as the match update.

A background worker (`OutboxPublisherService`) then:

1. Reads pending outbox events in batches.
2. Publishes them to Kafka.
3. Retries failures using backoff.
4. Marks successfully published events as `PUBLISHED`.

### D. Two-Phase Ticket Hold with Distributed Leases

**Phase 1 — Reserve**

When a fan selects seats or a general ticket category:

- The system acquires a Redis lease for a physical seat, where applicable.
- Example lease command:

```text
SET seat:match:1:seat:101 <userId> NX EX 600
```

- An order is created in the `PENDING` state.
- The order expires after 10 minutes if payment is not completed.

**Phase 2 — Pay**

- The user completes checkout through Razorpay or the supported UPI flow.
- Payment signatures are verified before the order transitions to `CONFIRMED`.

**Automatic rollback**

A background scheduler (`OrderExpiryScheduler`) runs every 30 seconds to reclaim expired reservations, restore inventory in Redis and PostgreSQL, and release seat leases.

### E. Idempotent API Design

Network timeouts can cause clients to retry requests that change server state. Critical operations support an `Idempotency-Key` header, including:

- `POST /orders/reserve`
- `POST /orders/reserve-seats`
- `POST /orders/{id}/pay`
- `POST /orders/{id}/cancel`
- `POST /events/matches/{id}/approve`

Repeated requests with the same idempotency key should return the existing operation result rather than deducting inventory or performing the operation again.

### F. Tamper-Resistant QR Ticketing and Turnstile Validation

Confirmed orders generate digital tickets with signed QR payloads. The documented payload structure is:

```text
IPL2026|<ticketNumber>|<matchId>|<seatNumber>|SIG:<hmacSha256>
```

- The frontend ticket wallet renders QR passes using `qrcode.react`.
- The turnstile endpoint is `POST /orders/tickets/validate-turnstile`.
- The endpoint verifies the QR signature and ticket state.
- A previously used ticket is rejected as a duplicate entry, with gate ID and timestamp recorded.

### G. Domain Role Hierarchy and Home Venue Integrity

- **BCCI Admin:** Schedules fixtures. Selecting a home franchise resolves and locks its home stadium (`homeStadiumId`).
- **Home Franchise:** Receives a Kafka notification to configure ticket-category quotas and pricing. The assigned venue remains locked.
- **BCCI Final Approval:** After pricing configuration, the match moves to `WAITING_FOR_APPROVAL`. Admin approval publishes the ticket-opening event and starts the documented 7-day booking window.

---

## 3. Microservices Breakdown

| Service | Port | Database | Primary Responsibilities |
|---|---:|---|---|
| `ipl-api-gateway` | 8090 | None | Single entry point, coarse-grained RBAC routing, RS256 token verification, identity header injection (`X-User-Id`, `X-User-Role`), and global CORS |
| `ipl-auth-service` | 8081 | `auth_db` | User registration, RS256 JWT generation, refresh-token rotation, and Redis token blocklist on logout |
| `ipl-event-service` | 8082 | `event_db` | Stadium and franchise management, fixture scheduling, category-capacity validation, and Transactional Outbox |
| `ipl-order-service` | 8083 | `order_db` | Redis Lua flash-sale inventory, physical seat allocation, Razorpay payment verification, order expiry, and turnstile scanning |
| `ipl-notification-service` | 8084 | `notification_db` | Kafka consumers that maintain fan notification inboxes and broadcast alerts |

**Capacity constraint:**

\[
\sum \text{Category Seats} \leq \text{Stadium Capacity}
\]

---

## 4. Real-Time Event Choreography

### Fixture Lifecycle: BCCI and Franchise Workflow

The documented workflow is:

1. BCCI Admin schedules a match and selects the home franchise.
2. The system resolves and locks the franchise's home stadium.
3. The franchise receives a notification that the fixture requires ticket configuration.
4. The franchise configures category quotas and pricing.
5. The match transitions to `WAITING_FOR_APPROVAL`.
6. BCCI Admin approves the match.
7. The event service records the approval and publishes the ticket-opening event through the outbox workflow.
8. The booking window opens according to the configured schedule.

**Kafka events involved:**

- `ipl.match.scheduled`
- `ipl.match.approved`
- `ipl.match.tickets-opened`

---

## 5. API Endpoints Reference

All client and web traffic is routed through the API Gateway on port `8090`.

### Authentication — `/auth/**`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/login` | Authenticate credentials and return access and refresh tokens |
| POST | `/auth/register` | Public fan self-registration (`ROLE_USER`) |
| POST | `/auth/franchise/register` | BCCI Admin endpoint to register franchise accounts (`ROLE_FRANCHISE`) |
| POST | `/auth/refresh` | Rotate refresh token |
| POST | `/auth/logout` | Add the active token JTI to the Redis blocklist |
| GET | `/auth/me` | Get the authenticated user's profile |
| GET | `/auth/public-key` | Retrieve the RS256 public key |

### Events and Fixtures — `/events/**`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/events/stadiums` | Register a stadium with its capacity (Admin) |
| GET | `/events/stadiums` | List stadiums |
| POST | `/events/franchises` | Register a franchise linked to its home stadium (Admin) |
| GET | `/events/franchises` | List franchises |
| POST | `/events/matches` | Schedule a match (`CONFIGURATION_PENDING`) |
| POST | `/events/matches/{id}/configuration` | Configure seat tiers and pricing (Franchise) |
| POST | `/events/matches/{id}/approve` | Approve a match, set ticket opening date, and enqueue a Kafka event (Admin) |
| GET | `/events/matches` | List scheduled matches |

### Orders and Ticketing — `/orders/**`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/orders/inventory/{matchId}` | Query live category inventory |
| POST | `/orders/reserve` | Reserve general-category tickets with a 10-minute hold |
| GET | `/orders/matches/{matchId}/seats` | Get the live seat map (`AVAILABLE`, `HELD`, `BOOKED`) |
| POST | `/orders/reserve-seats` | Reserve physical seats using Redis distributed leases |
| POST | `/orders/{id}/razorpay/create` | Initiate a Razorpay payment order |
| POST | `/orders/{id}/pay` | Confirm payment using Razorpay HMAC signature or verified UPI |
| POST | `/orders/{id}/cancel` | Cancel a reservation and release inventory |
| GET | `/orders/my-orders` | List the fan's booking history |
| GET | `/orders/tickets/my-tickets` | Retrieve digital tickets |
| POST | `/orders/tickets/validate-turnstile` | Verify a ticket at the stadium gate |

### In-System Notifications — `/notifications/**`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/notifications` | Fetch the user's in-app notification inbox |
| GET | `/notifications/unread-count` | Get the unread notification count |
| PATCH | `/notifications/{id}/read` | Mark one notification as read |
| PATCH | `/notifications/read-all` | Mark all notifications as read |
| POST | `/notifications/broadcast` | Broadcast an announcement to all fans (Admin) |

---

## 6. Frontend Multi-Role Portals

The frontend application, `ipl-frontend`, uses React 18, Vite, and Tailwind CSS. Its visual direction is an IPL dark stadium-night theme.

### BCCI Admin Portal — `/admin`

- Stadium and franchise registration with home-venue linking.
- Fixture scheduling with automatic home-stadium locking.
- Match-approval dashboard with workflow states, from awaiting franchise pricing to approval for ticket sale.
- Turnstile gate-scanner simulator.

### Franchise Dashboard — `/franchise`

- Match fixture feed filtered to the franchise's matches.
- Stadium category quota and pricing configuration.
- Kafka-driven inbox alerts when new fixtures are scheduled.

### Fan Ticketing Hub — `/fan`

- Live fixture feed with 7-day countdown timers.
- Seat-map selection and flash-sale category reservation.
- 10-minute reservation countdown.
- Razorpay or supported UPI checkout.
- Digital ticket wallet at `/fan/tickets`, with QR passes rendered using `qrcode.react`.

---

## 7. Local Setup and Running

### Prerequisites

- Java 21
- Node.js 18+ and npm
- Maven 3.9+
- Docker and Docker Compose
- PostgreSQL

### Step 1: Start Infrastructure

Start Redis and Kafka (configured to run in KRaft mode):

```bash
docker compose up -d
```

### Step 2: Create PostgreSQL Databases

Connect to your local PostgreSQL instance (default port `5432`) and create the service databases:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE event_db;
CREATE DATABASE order_db;
CREATE DATABASE notification_db;
```

### Step 3: Start the Backend Microservices

Run each service in a separate terminal.

```bash
# API Gateway — Port 8090
cd ipl-api-gateway
./mvnw spring-boot:run
```

```bash
# Auth Service — Port 8081
cd ipl-auth-service
./mvnw spring-boot:run
```

```bash
# Event Service — Port 8082
cd ipl-event-service
./mvnw spring-boot:run
```

```bash
# Order Service — Port 8083
cd ipl-order-service
./mvnw spring-boot:run
```

```bash
# Notification Service — Port 8084
cd ipl-notification-service
./mvnw spring-boot:run
```

Run these commands from the repository root, adjusting paths if your project directory structure differs. On Windows PowerShell, use `.\mvnw.cmd spring-boot:run` if the Maven wrapper script is provided as `mvnw.cmd`.

### Step 4: Start the Frontend

```bash
cd ipl-frontend
npm install
npm run dev
```

Open the frontend URL printed by Vite in the terminal. If Vite is configured to use port `3000`, open:

```text
http://localhost:3000
```

---

## 8. Master Credentials

The original project notes state that the BCCI administrator account is initialized automatically. Do not publish a real or shared password in a public repository.

Configure the initial administrator credentials through environment variables or a secrets manager. For example, document variable names without committing their values:

```env
BCCI_ADMIN_EMAIL=<configure-locally>
BCCI_ADMIN_PASSWORD=<configure-a-strong-password>
```

Other accounts:

| Role | Account Creation | Portal |
|---|---|---|
| BCCI Admin | Initialized through secure application configuration | `/admin` |
| Franchise Admin | Created through the Admin Console | `/franchise` |
| Fan User | Self-registers through `/auth/register` | `/fan` |

If credentials have already been committed to a public repository, changing the README is not enough: rotate the exposed password/secret and remove it from the repository's tracked configuration and history as appropriate.

---

## 9. Testing and Quality Assurance

### Run Backend Test Suites

Run the tests for each microservice:

```bash
./mvnw test -f ipl-order-service/pom.xml
./mvnw test -f ipl-event-service/pom.xml
./mvnw test -f ipl-auth-service/pom.xml
./mvnw test -f ipl-notification-service/pom.xml
```

### Documented Test Coverage

The project notes identify the following test areas:

- Atomic Redis flash-sale inventory decrements and sold-out rejection.
- Capacity-boundary validation:

\[
\sum \text{Category Seats} \leq \text{Stadium Capacity}
\]

- Razorpay HMAC-SHA256 signature verification and replay protection.
- HMAC-SHA256 QR-code generation and duplicate-entry rejection at turnstiles.
- Idempotency guarantees across reservation, payment, and match-approval APIs.

---
## Conclusion
The IPL High-Concurrency Ticket Booking System brings together the core challenges of a real-world, high-demand ticketing platform: handling sudden traffic spikes, protecting limited inventory, coordinating distributed services, processing payments reliably, and validating tickets at stadium entry points.

By combining Spring Boot microservices, Redis atomic operations, Apache Kafka, PostgreSQL, and RS256-based authentication, the project demonstrates how a complex application can be divided into independently focused services while maintaining clear communication and security boundaries.

Design patterns such as the Transactional Outbox, idempotent APIs, distributed reservation leases, and automatic expiry handling address important failure scenarios that can occur in distributed systems.

The completed application brings these backend capabilities together with role-specific portals for BCCI administrators, franchise administrators, and fans. From fixture scheduling and ticket configuration to reservation, payment confirmation, digital ticket management, and gate validation, the platform covers the major stages of the ticket-booking lifecycle.

More importantly, this project represents the practical application of software engineering principles beyond basic CRUD development. It required careful consideration of concurrency, consistency, fault handling, authentication, service communication, and the reliability of state-changing operations.

The result is a complete IPL ticket-booking application that demonstrates how modern backend technologies and distributed-system design principles can be applied to a demanding, real-world use case.
