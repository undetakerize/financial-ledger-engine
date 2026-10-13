# High-Throughput Financial Ledger & Event-Driven Transaction Engine

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.4+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Kafka / Redpanda](https://img.shields.io/badge/Streaming-Redpanda%2FKafka-red.svg)](https://redpanda.com/)
[![Architecture](https://img.shields.io/badge/Pattern-Double--Entry%20%7C%20Outbox-darkviolet.svg)](#core-architecture-patterns)

> **Mission-Critical Core Banking & Ledger Subsystem** engineered to guarantee mathematical consistency, zero double-spending, and dead-lock freedom under high concurrent transaction contention.

Designed and authored by **[Adi Hadiansyah](https://adihadiansyah.site)** (Backend Engineer).

---

## 🏛️ System Overview

In financial transaction architectures, distributed eventual consistency is hazardous when handling core balances. `financial-ledger-engine` applies battle-tested core banking paradigms:

1. **Strict Double-Entry Bookkeeping:** Balances are never adjusted in a vacuum. Every transaction must consist of matched Debit and Credit entries satisfying the invariant $\sum \text{Debits} = \sum \text{Credits}$.
2. **Deterministic Pessimistic Lock Ordering:** Solves the classic deadlock problem ($A \to B$ vs $B \to A$) under high concurrency by deterministically sorting account IDs before acquiring `SELECT ... FOR UPDATE` row locks.
3. **Transactional Outbox Pattern with `SKIP LOCKED`:** Eliminates dual-write anomalies between relational state updates and message brokers (Kafka/Redpanda) without distributed 2PC bottlenecks.

---

## ⚡ Core Architecture Patterns

### 1. Deterministic Lock Ordering (Deadlock-Free Concurrency)
When Account 1 transfers to Account 2 simultaneously with Account 2 transferring to Account 1, naive locking causes circular lock acquisition deadlocks.
```java
// Deterministic lock acquisition order:
UUID firstLockId = fromId.compareTo(toId) < 0 ? fromId : toId;
UUID secondLockId = fromId.compareTo(toId) < 0 ? toId : fromId;

accountRepository.findByIdForUpdate(firstLockId);
accountRepository.findByIdForUpdate(secondLockId);
```
Regardless of transfer direction, competing threads always lock rows in identical order, completely eliminating deadlock probability.

### 2. Transactional Outbox via PostgreSQL `FOR UPDATE SKIP LOCKED`
Kafka messages are never dispatched inside the JPA database transaction (which causes lost messages if Kafka commits but DB rolls back, or duplicate events if DB commits but Kafka crashes).
Instead, an event record is written into `outbox_events` in the exact same ACID transaction. Background worker threads poll pending events using:
```sql
SELECT * FROM outbox_events 
WHERE status = 'PENDING' 
ORDER BY created_at ASC 
LIMIT 50 
FOR UPDATE SKIP LOCKED;
```
`SKIP LOCKED` guarantees multiple concurrent background workers process distinct batches with zero row lock contention or waiting.

---

## 🛠️ Tech Stack & Constraints

- **Language:** Java 17 (LTS)
- **Framework:** Spring Boot 3.4+
- **Persistence:** Spring Data JPA / Hibernate, PostgreSQL 16
- **Message Broker:** Redpanda / Apache Kafka
- **Containerization:** Docker Compose (PostgreSQL on 5433, Redpanda on 19092)

---

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- JDK 17+
- Maven 3.9+

### Run Local Infrastructure
```bash
docker compose up -d
```
Verifies PostgreSQL (port `5433`) and Redpanda Kafka broker (port `19092`).

### Run Application
```bash
./mvnw spring-boot:run
```

---

## 📄 License & Attribution
Part of the production engineering portfolio by [Adi Hadiansyah](https://adihadiansyah.site). Open for technical evaluation and recruiter review.
