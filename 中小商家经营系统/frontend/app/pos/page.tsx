"use client";

import { useMemo, useState } from "react";
import { Minus, Plus, ScanLine, Trash2 } from "lucide-react";
import {
  apiGet,
  apiPost,
  demoProducts,
  money,
  services,
  type CheckoutResponse,
  type Product
} from "@/lib/api";

type CartLine = {
  product: Product;
  quantity: number;
};

export default function PosPage() {
  const [lookupValue, setLookupValue] = useState("6900000000028");
  const [cart, setCart] = useState<CartLine[]>([]);
  const [discount, setDiscount] = useState(0);
  const [cashier, setCashier] = useState("demo-cashier");
  const [paymentMethod, setPaymentMethod] = useState("WECHAT");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const subtotal = useMemo(
    () => cart.reduce((sum, line) => sum + line.product.retailPrice * line.quantity, 0),
    [cart]
  );
  const payable = Math.max(0, subtotal - discount);

  async function addByLookup(value: string) {
    const normalized = value.trim();
    if (!normalized) {
      return;
    }
    setError("");
    setMessage("");
    const fallback = demoProducts.find((item) => item.sku === normalized || item.barcode === normalized) ?? demoProducts[0];
    const product = await apiGet<Product>(`${services.pos}/api/pos/lookup/${encodeURIComponent(normalized)}`, fallback);
    addProduct(product);
    setLookupValue("");
  }

  function addProduct(product: Product) {
    setCart((current) => {
      const existing = current.find((line) => line.product.sku === product.sku);
      if (existing) {
        return current.map((line) =>
          line.product.sku === product.sku ? { ...line, quantity: line.quantity + 1 } : line
        );
      }
      return [...current, { product, quantity: 1 }];
    });
  }

  function changeQuantity(sku: string, delta: number) {
    setCart((current) =>
      current
        .map((line) => (line.product.sku === sku ? { ...line, quantity: Math.max(0, line.quantity + delta) } : line))
        .filter((line) => line.quantity > 0)
    );
  }

  async function checkout() {
    if (cart.length === 0) {
      setError("购物车为空。");
      return;
    }
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      const result = await apiPost<CheckoutResponse>(`${services.pos}/api/pos/checkout`, {
        cashier,
        paymentMethod,
        discountAmount: discount,
        items: cart.map((line) => ({
          skuOrBarcode: line.product.sku,
          quantity: line.quantity
        }))
      });
      setMessage(`已完成订单 ${result.orderNo}，实收 ${money(result.payableAmount)}。`);
      setCart([]);
      setDiscount(0);
    } catch (err) {
      setError(err instanceof Error ? err.message : "结算失败。");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>收银</h1>
          <p>扫码、加购、结算并自动扣减库存。</p>
        </div>
      </div>

      <div className="pos-layout">
        <section className="panel">
          <div className="toolbar">
            <input
              className="input"
              style={{ minWidth: 280 }}
              value={lookupValue}
              onChange={(event) => setLookupValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  void addByLookup(lookupValue);
                }
              }}
              placeholder="条码 / SKU"
              aria-label="条码或SKU"
            />
            <button className="button" type="button" onClick={() => void addByLookup(lookupValue)}>
              <ScanLine size={18} aria-hidden />
              加入
            </button>
          </div>

          <div className="product-grid">
            {demoProducts.map((product) => (
              <button className="product-tile" key={product.sku} type="button" onClick={() => addProduct(product)}>
                <strong>{product.name}</strong>
                <span>{product.sku}</span>
                <span>{money(product.retailPrice)}</span>
              </button>
            ))}
          </div>
        </section>

        <aside className="panel">
          <h2>购物车</h2>
          {cart.length === 0 ? (
            <p style={{ color: "var(--muted)" }}>暂无商品</p>
          ) : (
            cart.map((line) => (
              <div className="cart-line" key={line.product.sku}>
                <div>
                  <strong>{line.product.name}</strong>
                  <span>
                    {money(line.product.retailPrice)} x {line.quantity}
                  </span>
                </div>
                <div>
                  <div className="stepper" aria-label={`${line.product.name} 数量`}>
                    <button type="button" onClick={() => changeQuantity(line.product.sku, -1)} aria-label="减少">
                      <Minus size={16} aria-hidden />
                    </button>
                    <span>{line.quantity}</span>
                    <button type="button" onClick={() => changeQuantity(line.product.sku, 1)} aria-label="增加">
                      <Plus size={16} aria-hidden />
                    </button>
                  </div>
                  <button
                    className="button secondary"
                    style={{ marginTop: 8, width: "100%" }}
                    type="button"
                    onClick={() => changeQuantity(line.product.sku, -line.quantity)}
                    aria-label="删除"
                  >
                    <Trash2 size={16} aria-hidden />
                  </button>
                </div>
              </div>
            ))
          )}

          <div className="total-row">
            <span>小计</span>
            <strong>{money(subtotal)}</strong>
          </div>
          <div className="toolbar" style={{ marginTop: 12 }}>
            <input
              className="input"
              type="number"
              min={0}
              max={subtotal}
              step="0.5"
              value={discount}
              onChange={(event) => setDiscount(Number(event.target.value))}
              aria-label="折扣金额"
            />
            <select className="select" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}>
              <option value="WECHAT">微信</option>
              <option value="ALIPAY">支付宝</option>
              <option value="CASH">现金</option>
              <option value="CARD">银行卡</option>
            </select>
            <input
              className="input"
              value={cashier}
              onChange={(event) => setCashier(event.target.value)}
              aria-label="收银员"
            />
          </div>
          <div className="total-row">
            <span>应收</span>
            <strong>{money(payable)}</strong>
          </div>
          <button className="button" style={{ width: "100%", marginTop: 14 }} disabled={submitting} onClick={checkout}>
            {submitting ? "结算中" : "完成收款"}
          </button>
          {message && <div className="message">{message}</div>}
          {error && <div className="message error">{error}</div>}
        </aside>
      </div>
    </>
  );
}
