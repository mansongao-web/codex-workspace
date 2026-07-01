# RetailOps Architecture

RetailOps is a small-merchant operating system for convenience stores, snack shops, and neighborhood retail counters. The first release focuses on the business loop that matters most:

1. Maintain product and price data.
2. Receive and adjust inventory.
3. Sell through a POS checkout flow.
4. Deduct stock and keep inventory movement history.
5. Report revenue, profit, top products, slow products, and low-stock alerts.

## Expert Routing Used

The task was routed through an Agents Orchestrator perspective and narrowed into these expert lenses:

- Product Manager: define the merchant workflows and MVP priority.
- Software Architect: split the system into services with clear ownership.
- Database Optimizer: protect inventory, order, and reporting data integrity.
- Frontend Developer: keep the first screen as an operations console, not a landing page.
- Analytics Reporter: expose sales, profit, top-N, slow-moving, and margin distribution reports.

## Systems to Build

| System | Implemented now | Responsibility |
| --- | --- | --- |
| Catalog Service | Yes | Products, SKU/barcode lookup, categories, suppliers, prices, costs. |
| Inventory Service | Yes | Stock levels, purchase-in, manual adjustments, sale reservations, stock movement history, low-stock alerts. |
| POS Service | Yes | Product lookup, checkout, paid order creation, sale inventory reservation. |
| Report Service | Yes | Dashboard metrics, day/week/year summaries, top products, slow products, profit distribution. |
| Web Console | Yes | Dashboard, POS, product/inventory, reports. |
| Auth/Gateway | Roadmap | Login, roles, tenant/store isolation, unified routing and auditing. |
| Notification Service | Roadmap | Low-stock, abnormal refund, negative margin, and shift reminders. |

## Service Boundaries

The MVP uses one MySQL database to reduce local setup cost, but write ownership is still separated:

- `catalog-service` owns `products`, `categories`, and `suppliers`.
- `inventory-service` owns `stock_levels`, `stock_movements`, and `inventory_alerts`.
- `pos-service` owns `sales_orders` and `sales_order_items`.
- `report-service` is read-only and should eventually read from daily aggregate tables or events.

This keeps the code ready for later migration to separate schemas or event-driven reporting.

## Core Invariants

- Sales order items store the sale-time `unit_cost`; historical profit never depends on the current product cost.
- Every stock change writes a movement row.
- Checkout calls inventory reservation before writing a paid order.
- Stock cannot go negative.
- Low-stock alerts are events, not just a computed flag.
- Reporting endpoints aggregate server-side; the frontend does not pull raw order history to calculate business metrics.

## API Surface

Catalog:

```text
GET  /api/products?keyword=&status=
GET  /api/products/{id}
GET  /api/products/by-sku/{sku}
GET  /api/products/lookup/{skuOrBarcode}
POST /api/products
PUT  /api/products/{id}
GET  /api/categories
POST /api/categories
GET  /api/suppliers
POST /api/suppliers
```

Inventory:

```text
GET  /api/inventory/levels?lowOnly=
GET  /api/inventory/levels/{sku}
GET  /api/inventory/movements?sku=
POST /api/inventory/purchases
POST /api/inventory/adjustments
POST /api/inventory/sale-reservations
GET  /api/inventory/alerts?includeResolved=
POST /api/inventory/alerts/{id}/resolve
```

POS:

```text
GET  /api/pos/lookup/{skuOrBarcode}
POST /api/pos/checkout
GET  /api/pos/orders?date=
GET  /api/pos/orders/{orderNo}
```

Reports:

```text
GET /api/reports/dashboard
GET /api/reports/sales-summary?period=DAY|WEEK|MONTH|YEAR&from=&to=
GET /api/reports/top-products?from=&to=&limit=&orderBy=quantity|revenue|profit
GET /api/reports/slow-products?days=&limit=
GET /api/reports/profit-distribution?from=&to=
```

## Roadmap

P0 is implemented in this repository: product, inventory, checkout, dashboard, and reports.

P1 should add authentication, cashier shifts, refunds, receipt printing, CSV/Excel import, and daily aggregate report tables.

P2 should add multi-store tenant isolation, supplier purchase orders, promotion rules, member pricing, async notifications, and a gateway/BFF layer.
