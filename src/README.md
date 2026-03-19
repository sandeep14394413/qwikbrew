# ☕ QwikBrew — Enterprise Corporate Café Platform

> A full-stack microservices application inspired by QwikCafe (Pine Labs), rebuilt with
> Spring Boot 3, Spring Cloud, Kafka, JWT security, and a premium modern UI.

---

## 🏗️ Architecture Overview

```
                          ┌─────────────────────────────────────────┐
                          │           React / Mobile App            │
                          └──────────────────┬──────────────────────┘
                                             │ HTTPS
                          ┌──────────────────▼──────────────────────┐
                          │           API Gateway  :8080             │
                          │  Spring Cloud Gateway + JWT Filter       │
                          │  + Circuit Breaker + Rate Limiting       │
                          └──┬──────┬──────┬──────┬────────┬────────┘
                             │      │      │      │        │
              ┌──────────────▼──┐ ┌─▼──┐ ┌▼───┐ ┌▼──────┐ ┌▼──────────────┐
              │  User Service   │ │Menu│ │Ord.│ │Pay.   │ │Notification   │
              │  :8081          │ │Svc │ │Svc │ │Svc    │ │Service        │
              │  ─────────────  │ │:82 │ │:83 │ │:8084  │ │:8085          │
              │  • Register     │ │    │ │    │ │       │ │               │
              │  • Login (JWT)  │ │Cat.│ │Plac│ │Wallet │ │Push (FCM)     │
              │  • Profile      │ │Ite.│ │e   │ │TopUp  │ │Email (SMTP)   │
              │  • Wallet       │ │Sea.│ │Tra.│ │Charge │ │In-App         │
              │  • BrewPoints   │ │rch │ │ck  │ │Refund │ │Kafka Consumer │
              └────────┬────────┘ └─┬──┘ └──┬─┘ └───────┘ └──────────────┘
                       │            │       │
              ┌─────── ▼────────────▼───────▼──────┐
              │          PostgreSQL (per-service DB) │
              │  userdb │ menudb │ orderdb │ paydb   │
              └─────────────────────────────────────┘

                         ┌───────────────────────┐
                         │      Apache Kafka      │
                         │  order-placed          │
                         │  order-ready           │
                         │  order-cancelled       │
                         │  wallet-topup          │
                         └───────────────────────┘

              ┌──────────────────┐   ┌────────────────────────┐
              │  Eureka Discovery│   │  Redis Cache           │
              │  :8761            │   │  (Menu items 15m TTL)  │
              └──────────────────┘   └────────────────────────┘

              ┌──────────────────┐   ┌────────────────────────┐
              │  Prometheus :9090│   │  Grafana :3001         │
              └──────────────────┘   └────────────────────────┘
```

---

## 📦 Microservices

| Service              | Port | Responsibility                                              |
|----------------------|------|-------------------------------------------------------------|
| `api-gateway`        | 8080 | Routing, JWT auth, rate limiting, circuit breaking          |
| `service-discovery`  | 8761 | Eureka service registry                                     |
| `user-service`       | 8081 | Registration, login, profiles, wallet, BrewPoints           |
| `menu-service`       | 8082 | Menu CRUD, category filtering, search, Redis caching        |
| `order-service`      | 8083 | Order lifecycle, pricing, BrewPoints earning                |
| `payment-service`    | 8084 | Wallet, UPI/Card processing, refunds, transaction history   |
| `notification-service`| 8085| Push (FCM), Email (SMTP), in-app notifications via Kafka    |

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local dev)
- Maven 3.9+

### Run everything with Docker
```bash
git clone https://github.com/yourorg/qwikbrew.git
cd qwikbrew

# Copy and edit environment variables
cp .env.example .env

# Build and start all services
docker-compose up --build
```

### Access
| Endpoint             | URL                              |
|----------------------|----------------------------------|
| API Gateway          | http://localhost:8080            |
| Eureka Dashboard     | http://localhost:8761            |
| Grafana              | http://localhost:3001            |
| Prometheus           | http://localhost:9090            |

---

## 🔐 Security

- **JWT Authentication** — Access token (60 min) + Refresh token (30 days)
- **Role-based access** — `EMPLOYEE`, `CAFE_STAFF`, `ADMIN`
- **Optimistic locking** on wallet to prevent double-spend
- **OTP** required for wallet top-up > ₹1000
- **bcrypt** password hashing
- **HTTPS** enforced in production via the gateway

---

## 🛠️ Tech Stack

| Layer            | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 17                             |
| Framework        | Spring Boot 3.2, Spring Cloud 2023  |
| API Gateway      | Spring Cloud Gateway                |
| Service Discovery| Netflix Eureka                      |
| Database         | PostgreSQL 16 (one DB per service)  |
| Cache            | Redis 7                             |
| Messaging        | Apache Kafka 3.6                    |
| Security         | Spring Security + JJWT              |
| ORM              | Spring Data JPA / Hibernate 6       |
| Build            | Maven                               |
| Containers       | Docker + Docker Compose             |
| Monitoring       | Micrometer + Prometheus + Grafana   |
| Resilience       | Resilience4j Circuit Breaker        |
| Frontend         | React / HTML5 (in `/frontend`)      |

---

## 📱 UI Improvements over QwikCafe

| Feature                  | QwikCafe (Pine Labs)      | QwikBrew                         |
|--------------------------|---------------------------|----------------------------------|
| Design language          | Basic Material Design     | Premium café aesthetic (Playfair)|
| Color palette            | Generic blue/white        | Espresso/amber/cream luxury tones|
| Wallet UX                | Basic balance view        | Rich wallet card + transaction log|
| Order tracking           | Not available             | Real-time step-by-step tracker   |
| Brew Points / Loyalty    | None                      | Tiered points (Bronze→Platinum)  |
| Notifications            | Basic push only           | Push + Email + In-App centre     |
| Search                   | Not available             | Full-text menu search            |
| Feedback                 | Basic rating              | Per-item rating + comments       |
| Animations               | Minimal                   | Smooth transitions & micro-UX    |

---

## 📡 Key API Endpoints

```
POST   /api/v1/users/register           Register new employee
POST   /api/v1/users/login              Login → JWT
GET    /api/v1/menu?category=DRINKS     Browse menu
GET    /api/v1/menu/search?q=dosa       Search menu
POST   /api/v1/orders                   Place order
GET    /api/v1/orders/{id}              Get order details
PATCH  /api/v1/orders/{id}/ready        Mark order ready (staff)
POST   /api/v1/payments/wallet/topup    Add money to wallet
GET    /api/v1/notifications/user/{id}  Get in-app notifications
```

---

## 🧪 Running Tests

```bash
# Unit + integration tests per service
mvn test -pl user-service
mvn test -pl order-service

# All services
mvn test
```

---

*Built with ❤️ for the corporate caffeine crowd.*
