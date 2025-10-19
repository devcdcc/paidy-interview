# Forex Service — Architecture Overview

## 🧩 High-Level Architecture

The project is a **Scala (Cats Effect + Http4s)** backend that proxies and caches foreign‑exchange rates from the **One‑Frame API**. Its goal is to minimize upstream calls while ensuring every quote is fresh (≤ 5 minutes old).

### Core Flow

```
Client → HTTP Routes → Program → CachedRates (Algebra) → OneFrameClient → One‑Frame API
```

### How to test
Please run
`./start-locally.sh` and then use the following Postman collection.

https://speeding-comet-586408.postman.co/workspace/Human-Singularity~c7ee1a6a-dbbf-470d-9047-bf06fdc810df/collection/4028422-139e8dde-b1ce-4b9a-890f-6163a5069d77?action=share&creator=4028422&active-environment=4028422-74ab02fc-8349-41ac-a136-a7b812b3a222


### Main Components

| Layer               | Class / File           | Responsibility                                                                                                                     |
| ------------------- | ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| **HTTP Layer**      | `RatesHttpRoutes`      | Exposes `/rates?from&to` and optional `/rates/batch` endpoints. Delegates to `Program`.                                            |
| **Program Layer**   | `Program.scala`        | Business logic wrapper over the `Algebra` (service) layer. Calls `get` or `getMany`.                                               |
| **Service Algebra** | `Algebra.scala`        | Defines abstract interface for obtaining FX rates.                                                                                 |
| **Implementation**  | `CachedRates.scala`    | Implements `Algebra` using an in‑memory TTL cache. Groups cache misses and performs one batch call to One‑Frame.                   |
| **HTTP Client**     | `OneFrameClient.scala` | Low‑level client that performs **a single GET** to One‑Frame with multiple `pair=` query params. No knowledge of cache or algebra. |
| **Wiring**          | `Interpreters.scala`   | Builds `OneFrameClient`, wraps it with `CachedRates`, and provides the final service implementation.                               |
| **Configuration**   | `OneFrameConfig.scala` | Loads One‑Frame and cache settings from `application.conf`.                                                                        |
| **Server Module**   | `Module.scala`         | Assembles routes, middleware, and services into an `HttpApp`.                                                                      |

## ⚙️ How It Works Today

1. Each incoming `/rates` request checks the in‑memory cache (`Map[(Currency,Currency) → Rate]`).
2. Cache hits are returned immediately.
3. Cache misses are **grouped** and passed to `OneFrameClient.fetchMany`, which sends **one HTTP request** with multiple `pair=...` params.
4. The results are cached (TTL = 5 minutes) and returned to clients.
5. All requests share the same cache instance, so the upstream (One‑Frame) is hit minimally.

## 🚀 Assumptions

1. **Unlimited Pair Requests (temporary):** We assume we can send as many `pair=` parameters as needed in a single call to One‑Frame (at least for the subset of currencies already implemented). This simplifies the initial iteration.
2. **Monolithic, in‑memory, and ephemeral:** We run as a single **monolith**; there’s no immediate need for disk persistence. Cache/state live in memory and are ephemeral for now.
3. **No authentication (public API):** For this exercise we assume the service is internal/public and **does not require authentication**.

## 🔄 Future Improvements

* **Micro‑batches to One‑Frame:** Add request chunking (e.g., 50–100 pairs per call) to avoid URL length limits and keep upstream calls reliable when many pairs are cold.
* **Redis for horizontal scale:** Move the in‑memory cache to **Redis** so multiple instances share `(from,to) → Rate` with TTL.
* **Akka Cluster + Redis for lower latency:** If Redis alone isn’t enough (I/O blocking concerns) and we need lower latency distribution, add **Akka Cluster** for coordination and in‑memory sharding while **Redis** remains the durable backing store.
* **Logging & Metrics:** Implement structured **logging** (request id, pair count, hit/miss, upstream status) and **basic counters/histograms** (requests, cache hit ratio, upstream latency, error rates). Start minimal; Prometheus-friendly is ideal.
* **Testing:** Add unit tests (domain codecs, timestamp freshness), component tests (Routes + Program via http4s client), and an integration test hitting a stubbed One‑Frame (WireMock) to validate batching and error mapping.
* **Deployment & environment config:** Expose critical settings via **environment variables** (API token, cache TTL, max batch size, timeouts). Add a **Dockerfile** and a minimal **deployment script** (currently only docker‑compose) to build and run the monolith in various environments.

---

**Summary:** The service fetches multiple currency pairs in a single upstream request per cache‑miss group. Next steps: add **mini‑batching**, introduce a **distributed cache (Redis)** or **Akka Cluster + Redis** for horizontal scaling, wire **logging/metrics**, add **tests**, and complete deployment wiring with env vars and a Dockerfile.
