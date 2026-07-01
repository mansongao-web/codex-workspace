"use client";

import { useEffect, useMemo, useState } from "react";
import { PackagePlus, RotateCw } from "lucide-react";
import { StatusPill } from "@/components/status-pill";
import {
  apiGet,
  apiPost,
  demoProducts,
  demoStocks,
  money,
  services,
  type InventoryAlert,
  type Product,
  type StockLevel
} from "@/lib/api";

export default function ProductsPage() {
  const [products, setProducts] = useState<Product[]>(demoProducts);
  const [stocks, setStocks] = useState<StockLevel[]>(demoStocks);
  const [alerts, setAlerts] = useState<InventoryAlert[]>([]);
  const [keyword, setKeyword] = useState("");
  const [sku, setSku] = useState("SKU-WATER-550");
  const [quantity, setQuantity] = useState(12);
  const [note, setNote] = useState("补货入库");

  async function load() {
    const [productData, stockData, alertData] = await Promise.all([
      apiGet<Product[]>(`${services.catalog}/api/products?status=ACTIVE`, demoProducts),
      apiGet<StockLevel[]>(`${services.inventory}/api/inventory/levels`, demoStocks),
      apiGet<InventoryAlert[]>(`${services.inventory}/api/inventory/alerts`, [])
    ]);
    setProducts(productData);
    setStocks(stockData);
    setAlerts(alertData);
  }

  useEffect(() => {
    void load();
  }, []);

  const stockBySku = useMemo(() => new Map(stocks.map((item) => [item.productSku, item])), [stocks]);
  const filteredProducts = products.filter((product) => {
    const text = `${product.name} ${product.sku} ${product.barcode ?? ""}`.toLowerCase();
    return text.includes(keyword.toLowerCase());
  });

  async function purchase() {
    await apiPost<StockLevel>(`${services.inventory}/api/inventory/purchases`, {
      productSku: sku,
      quantity,
      referenceId: `PO-${Date.now()}`,
      note
    });
    await load();
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>商品库存</h1>
          <p>商品售价、成本、当前库存和预警状态。</p>
        </div>
        <button className="button secondary" type="button" onClick={() => void load()}>
          <RotateCw size={18} aria-hidden />
          刷新
        </button>
      </div>

      <div className="grid-2">
        <section className="panel">
          <h2>快速入库</h2>
          <div className="toolbar">
            <input className="input" value={sku} onChange={(event) => setSku(event.target.value)} aria-label="SKU" />
            <input
              className="input"
              type="number"
              min={1}
              value={quantity}
              onChange={(event) => setQuantity(Number(event.target.value))}
              aria-label="入库数量"
            />
            <input className="input" value={note} onChange={(event) => setNote(event.target.value)} aria-label="备注" />
            <button className="button" type="button" onClick={() => void purchase()}>
              <PackagePlus size={18} aria-hidden />
              入库
            </button>
          </div>
        </section>

        <section className="panel">
          <h2>库存预警</h2>
          {alerts.length === 0 ? (
            <p style={{ color: "var(--muted)" }}>当前没有未处理预警</p>
          ) : (
            <div className="bar-list">
              {alerts.slice(0, 4).map((alert) => (
                <div className="bar-row" key={alert.id}>
                  <header>
                    <strong>{alert.productSku}</strong>
                    <StatusPill tone={alert.severity === "HIGH" ? "bad" : "warn"}>{alert.severity}</StatusPill>
                  </header>
                  <span style={{ color: "var(--muted)", fontSize: 13 }}>{alert.message}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      <section className="panel" style={{ marginTop: 16 }}>
        <div className="toolbar">
          <input
            className="input"
            style={{ minWidth: 280 }}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索商品 / SKU / 条码"
            aria-label="搜索"
          />
        </div>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>商品</th>
                <th>SKU</th>
                <th>分类</th>
                <th className="number">售价</th>
                <th className="number">成本</th>
                <th className="number">库存</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {filteredProducts.map((product) => {
                const stock = stockBySku.get(product.sku);
                const low = stock ? stock.quantity <= stock.minQuantity : false;
                return (
                  <tr key={product.sku}>
                    <td>
                      <strong>{product.name}</strong>
                      <div style={{ color: "var(--muted)", fontSize: 13 }}>{product.barcode}</div>
                    </td>
                    <td>{product.sku}</td>
                    <td>{product.categoryName ?? "-"}</td>
                    <td className="number">{money(product.retailPrice)}</td>
                    <td className="number">{money(product.costPrice)}</td>
                    <td className="number">{stock ? stock.quantity : 0}</td>
                    <td>
                      <StatusPill tone={low ? "warn" : "good"}>{low ? "需补货" : "正常"}</StatusPill>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
