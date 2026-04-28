# Quick Picks Game Service — Backend

The **Quick Picks Game Service** is a high-performance, multi-tenant Kotlin / Spring Boot application that serves as the core engine for the Quick Picks game family. It manages the full game lifecycle, from sports feed ingestion and slate building to entry placement, real-time trending, and prize settlement.

## 🚀 Key Features

- **Multi-Tenant Architecture**: Strict data isolation using Postgres Row Level Security (RLS) and a `TenantAwareDataSource` decorator to prevent connection-bleed.
- **Transactional Integrity**: Uses the Outbox pattern to ensure exactly-once delivery of events to Optimove Streams.
- **Real-Money Safety**: Settlement engine with a mandatory 30-minute cooldown and post-cooldown fixture result verification.
- **High-Performance Trending**: Atomic Redis counters for real-time pick distribution with a database-backed T-60 freeze logic.
- **Distributed Scheduling**: Coordinated worker tasks using ShedLock to prevent duplicate execution across multiple instances.
- **Wallet Integration**: Secure, HMAC-signed communication with host casino wallets for debits and credits.
- **Advanced Player Targeting**: Granular access control, player exclusions, and automatic "First Entry Free" promotion logic.

## 🛠 Tech Stack

- **Language**: Kotlin 2.1 (JDK 21)
- **Framework**: Spring Boot 3.4.3
- **Database**: Postgres 16 (with RLS)
- **Caching/Concurrency**: Redis 7
- **Migrations**: Flyway
- **Serialization**: Jackson (Strictly no Coroutines)
- **API Documentation**: SpringDoc OpenAPI 3

## 🏗 System Architecture

The service is designed to run in four isolated worker profiles from a single Docker image:

1.  **`api`**: Serves the REST API for both the Player Widget and the Manager Console.
2.  **`worker-feed`**: Polls sports feeds (API-Football) and updates fixture data.
3.  **`worker-outbox`**: Processes the transactional outbox to publish events to Optimove.
4.  **`worker-reconciliation`**: Handles nightly wallet ledger reconciliation and data cleanup.

## 📋 Core Domain Concepts

### Slates & Rounds
- **Slate**: A collection of 12 football fixtures selected by an operator. Follows a lifecycle: `DRAFT` -> `SUBMITTED` -> `PUBLISHED`.
- **Round**: Created automatically when a slate is published. This is the entity players actually enter.
- **Status Flow**: `OPEN` (Accepting entries) -> `LOCKED` (Matches started, trending frozen) -> `SETTLED` (Prizes paid).

### Entries & Picks
- **Entry**: A player's submission for a round. Contains 12 picks (Home/Draw/Away) and a Tie-Breaker (Golden Goal time).
- **Validation**: Strict 12-pick count, no duplicate fixtures, and mandatory idempotency keys for wallet safety.

### Settlement
- **Cooldown**: 30-minute mandatory wait after the last match finishes.
- **Sanity Check**: Re-fetches results from the provider before any payout.
- **Golden Goal**: Tie-breaker logic for the jackpot bonus (closest guess without going over).

## 🔐 Security & Multi-Tenancy

### Tenant Isolation
Every database query is intercepted by the `TenantAwareDataSource`. It extracts the `tenant_id` from the `TenantContext` (set during authentication) and executes `SET LOCAL app.current_tenant = ...` on the Postgres connection before the query runs, triggering RLS policies.

### Authentication
- **Player JWT**: Issued by host casinos, contains `playerId` and `tenantId`.
- **Operator JWT**: Issued for the Manager Console, contains `operatorId` and `tenantId` with `ROLE_TENANT_ADMIN`.

## 🖥 Local Development

### Prerequisites
- Docker & Docker Compose
- JDK 21

### Running Locally
1.  **Start Infrastructure**:
    ```bash
    docker-compose up -d postgres redis
    ```
2.  **Run the Application**:
    ```bash
    ./gradlew bootRun --args='--spring.profiles.active=dev,api'
    ```

### API Documentation
Once running, you can access:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## 🔌 API Examples

### Place an Entry (Player)
**POST** `/api/v1/rounds/{roundId}/entries`
```json
{
  "picks": {
    "picks": [
      { "providerMatchId": "123", "outcome": "HOME" },
      ... (12 total)
    ]
  },
  "tiebreaker": 42,
  "idempotencyKey": "uuid-v4-key"
}
```

### Create a Slate (Operator)
**POST** `/api/v1/admin/slates`
```json
{
  "roundWindowStart": "2024-05-01T15:00:00Z",
  "roundWindowEnd": "2024-05-02T22:00:00Z"
}
```

## ⚙️ Configuration

Polling intervals and worker cadences are managed in the database (`feed_providers` table) to allow runtime adjustment without redeployment.

```sql
UPDATE feed_providers 
SET polling_intervals_json = '{"outbox_worker_ms": 1000, "feed_ingestor_ms": 60000}'
WHERE id = 'api-football';
```
