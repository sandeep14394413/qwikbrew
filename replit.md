# QwikBrew — Corporate Café Management System

## Overview
QwikBrew is a microservices-based coffee shop / corporate café management app. It includes user authentication, menu browsing, order placement, payments, and notifications.

## Architecture
- **Frontend**: Static React 18 app (via CDN + Babel) — served by `server.js` (Node.js HTTP server) on port 5000
- **Backend** (Java Spring Boot microservices, not running in Replit dev):
  - `service-discovery` (Eureka) — port 8761
  - `api-gateway` (Spring Cloud Gateway) — port 8080
  - `user-service` — port 8081
  - `menu-service` — port 8082
  - `order-service` — port 8083
  - `payment-service` — port 8084
  - `notification-service` — port 8085
- **Infrastructure** (requires Docker/K8s, not available in Replit dev):
  - PostgreSQL 16 (separate DB per service)
  - Apache Kafka + Zookeeper
  - Redis

## Running in Replit
Only the frontend is served in the Replit dev environment. It uses built-in sample/mock data fallbacks when the API is unavailable.

- **Workflow**: `Start application` — runs `node server.js` on port 5000
- **Frontend entry**: `frontend/index.html`
- **Server**: `server.js` (plain Node.js HTTP server, no dependencies)

## Frontend API Logic
- When accessed via `localhost`/`127.0.0.1`, the frontend calls `http://localhost:8080` (the API gateway)
- When accessed via any other host (Replit proxy), it uses relative URLs — all API calls gracefully fall back to sample data on error

## Build / Package Manager
- **Java backend**: Apache Maven 3.x, Java 17
- **Frontend**: No build step — static HTML/JS with CDN dependencies
- **Node.js**: v20 (for serving static files)

## Deployment
- Configured as `autoscale` deployment
- Run command: `node server.js`

## Key Files
- `server.js` — Node.js static file server (port 5000)
- `frontend/index.html` — Single-file React app
- `frontend/nginx.conf` — Nginx config (used in Docker/production builds)
- `pom.xml` — Maven parent POM for all Java services
- `docker-compose.local.yml` — Local full-stack setup (requires Docker)
- `api-gateway-overrides.properties` — Spring config overrides for API gateway
- `scripts/init-dbs.sql` — PostgreSQL database initialization
