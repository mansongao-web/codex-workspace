"use client";

import { useEffect, useMemo, useState } from "react";
import { BarChart3, CalendarRange } from "lucide-react";
import { KpiCard } from "@/components/kpi-card";
import {
  apiGet,
  fallbackDashboard,
  money,
  services,
  todayIso,
  type ProfitDistribution,
  type SalesSummary,
  type SlowProduct,
  type TopProduct
} from "@/lib/api";

const fallbackSummary: SalesSummary = {
  period: "DAY",
  from: todayIso(),
  to: todayIso(),
  buckets: [
    { label: todayIso(), revenue: 17, cost: 8.6, profit: 8.4, orderCount: 1 },
    { label: "前日", revenue: 13, cost: 6.2, profit: 6.8, orderCount: 1 }
  ],
  total: fallbackDashboard.week
};

const fallbackSlow: SlowProduct[] = [
  { productSku: "SKU-TISSUE-10", productName: "纸巾 10包", soldQuantity: 0, stockQuantity: 6, lastSoldDate: null },
  { productSku: "SKU-WATER-550", productName: "瓶装水 550ml", soldQuantity: 2, stockQuantity: 8, lastSoldDate: todayIso() }
];

const fallbackDistribution: ProfitDistribution[] = [
  { bucket: "20-30%", lineCount: 1, revenue: 6, profit: 1.8 },
  { bucket: "30-50%", lineCount: 2, revenue: 20, profit: 8.5 },
  { bucket: "50%+", lineCount: 1, revenue: 4, profit: 2.4 }
];

export default function ReportsPage() {
  const [period, setPeriod] = useState("DAY");
  const [summary, setSummary] = useState<SalesSummary>(fallbackSummary);
  const [topProducts, setTopProducts] = useState<TopProduct[]>(fallbackDashboard.topProducts);
  const [slowProducts, setSlowProducts] = useState<SlowProduct[]>(fallbackSlow);
  const [distribution, setDistribution] = useState<ProfitDistribution[]>(fallbackDistribution);

  async function load() {
    const [summaryData, topData, slowData, distributionData] = await Promise.all([
      apiGet<SalesSummary>(`${services.report}/api/reports/sales-summary?period=${period}`, fallbackSummary),
      apiGet<TopProduct[]>(`${services.report}/api/reports/top-products?limit=8&orderBy=revenue`, fallbackDashboard.topProducts),
      apiGet<SlowProduct[]>(`${services.report}/api/reports/slow-products?days=30&limit=8`, fallbackSlow),
      apiGet<ProfitDistribution[]>(`${services.report}/api/reports/profit-distribution`, fallbackDistribution)
    ]);
    setSummary(summaryData);
    setTopProducts(topData);
    setSlowProducts(slowData);
    setDistribution(distributionData);
  }

  useEffect(() => {
    void load();
  }, [period]);

  const maxRevenue = useMemo(() => Math.max(...distribution.map((item) => item.revenue), 1), [distribution]);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>报表</h1>
          <p>营业额、利润、畅销、滞销和毛利分布。</p>
        </div>
        <select className="select" value={period} onChange={(event) => setPeriod(event.target.value)}>
          <option value="DAY">按日</option>
          <option value="WEEK">按周</option>
          <option value="MONTH">按月</option>
          <option value="YEAR">按年</option>
        </select>
      </div>

      <div className="grid-4">
        <KpiCard
          label="区间营业额"
          value={money(summary.total.revenue)}
          hint={`${summary.buckets.length} 个周期`}
          icon={<CalendarRange size={20} aria-hidden />}
        />
        <KpiCard
          label="区间毛利"
          value={money(summary.total.profit)}
          hint={`订单 ${summary.total.orderCount} 笔`}
          icon={<BarChart3 size={20} aria-hidden />}
        />
        <KpiCard label="客单价" value={money(summary.total.averageOrderValue)} hint="区间平均" icon={<BarChart3 size={20} aria-hidden />} />
        <KpiCard label="畅销商品数" value={`${topProducts.length}`} hint="按销售额排序" icon={<BarChart3 size={20} aria-hidden />} />
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <section className="panel">
          <h2>销售趋势</h2>
          <div className="bar-list">
            {summary.buckets.map((bucket) => (
              <div className="bar-row" key={bucket.label}>
                <header>
                  <strong>{bucket.label}</strong>
                  <span>{money(bucket.revenue)}</span>
                </header>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{ width: `${Math.max(8, (bucket.revenue / Math.max(summary.total.revenue, 1)) * 100)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="panel">
          <h2>毛利分布</h2>
          <div className="bar-list">
            {distribution.map((item) => (
              <div className="bar-row" key={item.bucket}>
                <header>
                  <strong>{item.bucket}</strong>
                  <span>{money(item.profit)}</span>
                </header>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width: `${Math.max(8, (item.revenue / maxRevenue) * 100)}%` }} />
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <section className="panel">
          <h2>畅销 Top N</h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th className="number">销量</th>
                  <th className="number">营业额</th>
                  <th className="number">毛利</th>
                </tr>
              </thead>
              <tbody>
                {topProducts.map((item) => (
                  <tr key={item.productSku}>
                    <td>{item.productName}</td>
                    <td className="number">{item.soldQuantity}</td>
                    <td className="number">{money(item.revenue)}</td>
                    <td className="number">{money(item.profit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel">
          <h2>滞销商品</h2>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th className="number">近30天销量</th>
                  <th className="number">库存</th>
                  <th>最近售出</th>
                </tr>
              </thead>
              <tbody>
                {slowProducts.map((item) => (
                  <tr key={item.productSku}>
                    <td>{item.productName}</td>
                    <td className="number">{item.soldQuantity}</td>
                    <td className="number">{item.stockQuantity}</td>
                    <td>{item.lastSoldDate ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
