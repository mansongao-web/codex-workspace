# RetailOps

RetailOps is a complete starter system for a small convenience store or neighborhood merchant. It includes product catalog management, inventory control, POS checkout, low-stock warnings, and operating reports for revenue, profit, top products, slow products, and profit distribution.

## Stack

- Frontend: Next.js App Router, TypeScript, plain CSS, lucide icons.
- Backend: Maven multi-module Spring Boot microservices.
- Database: MySQL 8.4 with SQL initialization.
- Runtime: Docker Compose for MySQL, services, and the web console.

## Services

```text
frontend              http://localhost:3000
catalog-service       http://localhost:8081
inventory-service     http://localhost:8082
pos-service           http://localhost:8083
report-service        http://localhost:8084
mysql                 localhost:3306
```

## Run With Docker

```powershell
docker compose up --build
```

On this Windows workspace, Docker BuildKit can be sensitive to the non-ASCII parent path. If buildx reports a gRPC/header error, use the compatibility build mode:

```powershell
$env:COMPOSE_BAKE='false'; $env:DOCKER_BUILDKIT='0'; docker compose up --build -d
```

Then open:

```text
http://localhost:3000
```

Demo barcodes/SKUs:

```text
6900000000011 / SKU-WATER-550
6900000000028 / SKU-COLA-330
6900000000035 / SKU-CHIPS-60
6900000000042 / SKU-NOODLE-BEEF
6900000000059 / SKU-TISSUE-10
```

## Run Services Locally

Start MySQL only:

```powershell
docker compose up mysql
```

Run backend services from `backend/` if Maven is installed:

```powershell
mvn -pl catalog-service spring-boot:run
mvn -pl inventory-service spring-boot:run
mvn -pl pos-service spring-boot:run
mvn -pl report-service spring-boot:run
```

Run frontend from `frontend/`:

```powershell
npm.cmd install
npm.cmd run dev
```

Stop the Docker stack:

```powershell
docker compose down
```

## What Was Implemented

- Product, category, supplier APIs.
- Stock level APIs, purchase-in, manual stock adjustment, sale reservation, stock movement history.
- POS lookup and checkout flow that deducts stock and stores sale-time cost snapshots.
- Dashboard metrics for today, week, year, alerts, low stock, and top products.
- Reports for grouped sales summary, top products, slow-moving products, and profit distribution.
- Next.js operations console with dashboard, POS, products/inventory, and reports pages.

See [docs/architecture.md](docs/architecture.md) for the expert-team decomposition and system boundaries.
