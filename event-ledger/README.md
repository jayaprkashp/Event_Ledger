# Event Ledger

A two-service system for processing financial transaction events that may arrive out of
order or be delivered more than once.

## Architecture overview

```
Client ──REST──▶ Event Gateway (public, :8080) ──REST + trace header──▶ Account Service (internal, :8081)
                       │  H2 (event records, idempotency)                    │  H2 (balances, transactions)
```

- **Event Gateway** is the only externally-reachable service. It validates incoming events,
  enforces idempotency by `eventId`, stores every event it receives (regardless of whether it
  can reach the Account Service), and calls the Account Service to apply the transaction. It
  answers `GET /events/{id}` and `GET /events?account=` purely from its own database, so those
  endpoints keep working even if the Account Service is down.
- **Account Service** owns account balances and transaction history. It is only ever called by
  the Gateway. Balance is a denormalized running total, updated atomically with each transaction
  under a pessimistic row lock (plus an optimistic `@Version` safety net), so the final balance
  is correct regardless of the order transactions arrive in — CREDIT/DEBIT summation is
  order-independent by construction. Listing endpoints order explicitly by `eventTimestamp`.
- The two services share **no database and no in-process state** — the REST contract between
  them (see below) is the only integration point. Each defines its own DTOs; there is no shared
  library, by design, so either service can be redeployed independently.
- **Resiliency**: the Gateway wraps its call to the Account Service with a **Resilience4j
  circuit breaker**, a **3-second timeout**, and **bounded retry with exponential backoff**
  (see "Resiliency pattern" below).
- **Tracing**: the Gateway generates a trace ID per incoming request (or reuses one supplied via
  `X-Trace-Id`), propagates it to the Account Service over that header, and both services include
  it in every structured JSON log line and response header.

## API contracts

**Event Gateway** (`localhost:9090`)

| Method | Endpoint | Notes |
|---|---|---|
| `POST` | `/events` | `201` new + applied, `200` duplicate, `400` invalid, `502` account service rejected it, `503` account service unreachable |
| `GET` | `/events/{id}` | Local lookup only — works even if the Account Service is down |
| `GET` | `/events?account={accountId}` | Ordered by `eventTimestamp`; local lookup only |
| `GET` | `/health` | `UP` / `DEGRADED` (account service down but gateway itself fine) |

**Account Service** (`localhost:9091`, internal — see the port-exposure note below)

| Method | Endpoint | Notes |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | Applies a transaction; idempotent by `eventId` |
| `GET` | `/accounts/{accountId}/balance` | |
| `GET` | `/accounts/{accountId}` | Includes up to the 50 most recent transactions, most recent first |
| `GET` | `/health` | `200` UP / `503` DOWN |

## Prerequisites

- JDK 21
- Maven 3.9+ (or use the included Docker build, which needs no local Maven install)
- Docker + Docker Compose, if running via containers

## Running locally (no Docker)

From the repo root:

```bash
# Terminal 1
mvn -pl account-service -am spring-boot:run

# Terminal 2
mvn -pl event-gateway -am spring-boot:run
```

The Gateway defaults to calling the Account Service at `http://localhost:8081` (see
`event-gateway/src/main/resources/application.yml`).

## Running with Docker Compose

```bash
docker compose up --build
```

This builds both images from the repo root (needed so the Maven reactor's parent POM resolves
correctly during the build) and starts the Account Service first, waiting for its `/health` to
report healthy before starting the Gateway — this avoids the Gateway seeing spurious `503`s
during startup.

- Gateway: `http://localhost:9090`
- Account Service: `http://localhost:9091`

> **Note on the Account Service's exposed port:** architecturally the Account Service is
> internal-only — only the Gateway should call it. `docker-compose.yml` still publishes
> `8081:8081` to the host for local debugging and so you can exercise it directly while
> reviewing this project (e.g. to simulate an outage with `docker compose stop account-service`
> and watch the Gateway degrade gracefully). In a production deployment, that port mapping would
> be removed so the service is reachable only over the internal `ledger-net` bridge network.

### Simulating an outage manually

```bash
docker compose stop account-service
curl -X POST localhost:8080/events -H 'Content-Type: application/json' -d '{...}'   # expect 503
curl localhost:8080/events?account=acct-123                                          # still 200
docker compose start account-service   # breaker should recover to CLOSED within ~10s
```

## Running the tests

```bash
mvn test      # unit + slice + resiliency + trace-propagation tests (fast, no external process)
mvn verify    # adds the cross-service integration test (MockServer-backed)
```

`mvn test` alone already covers idempotency, out-of-order correctness, balance computation,
validation, circuit breaker behavior, and trace propagation. `mvn verify` additionally runs
`GatewayAccountServiceIntegrationTest`, which exercises the full Gateway → Account Service flow
against a MockServer stub standing in for the Account Service (a contract-level integration
test — Docker Compose is what exercises the two *real* processes together end-to-end).

## Resiliency pattern: circuit breaker



## What's out of scope

