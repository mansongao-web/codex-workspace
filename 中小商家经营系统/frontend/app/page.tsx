"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, BadgeDollarSign, Receipt, TrendingUp } from "lucide-react";
import { KpiCard } from "@/components/kpi-card";
import { StatusPill } from "@/components/status-pill";
import { apiGet, fallbackDashboard, money, percent, services, type Dashboard } from "@/lib/api";

export default function DashboardPage() {
  const [dashboard, setDashboard] = useState<Dashboard>(fallbackDashboard);

  useEffect(() => {
    void apiGet<Dashboard>(`${services.report}/api/reports/dashboard`, fallbackDashboard).then(setDashboard);
  }, []);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>经营概览</h1>
          <p>今日收入、毛利、库存预警和畅销商品。</p>
        </div>
        <StatusPill tone={dashboard.openAlerts > 0 ? "warn" : "good"}>
          {dashboard.openAlerts > 0 ? `${dashboard.openAlerts} 个预警` : "库存正常"}
        </StatusPill>
      </div>

      <div className="grid-4">
        <KpiCard
          label="今日营业额"
          value={money(dashboard.today.revenue)}
          hint={`${dashboard.today.orderCount} 笔订单`}
          icon={<BadgeDollarSign size={20} aria-hidden />}
        />
        <KpiCard
          label="今日毛利"
          value={money(dashboard.today.profit)}
          hint={`毛利率 ${percent(dashboard.today.grossMarginRate)}`}
          icon={<TrendingUp size={20} aria-hidden />}
        />
        <KpiCard
          label="本周营业额"
          value={money(dashboard.week.revenue)}
          hint={`客单价 ${money(dashboard.week.averageOrderValue)}`}
          icon={<Receipt size={20} aria-hidden />}
        />
        <KpiCard
          label="低库存商品"
          value={`${dashboard.lowStockCount}`}
          hint="低于预警线"
          icon={<AlertTriangle size={20} aria-hidden />}
        />
      </div>

      <div className="grid-2" style={{ marginTop: 16 }}>
        <section className="panel">
          <h2>畅销商品</h2>
          <div className="bar-list">
            {dashboard.topProducts.map((item) => {
              const max = Math.max(...dashboard.topProducts.map((p) => p.revenue), 1);
              return (
                <div className="bar-row" key={item.productSku}>
                  <header>
                    <strong>{item.productName}</strong>
                    <span>{money(item.revenue)}</span>
                  </header>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${Math.max(8, (item.revenue / max) * 100)}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <section className="panel">
          <h2>年度经营</h2>
          <table className="table">
            <tbody>
              <tr>
                <th>营业额</th>
                <td className="number">{money(dashboard.year.revenue)}</td>
              </tr>
              <tr>
                <th>毛利</th>
                <td className="number">{money(dashboard.year.profit)}</td>
              </tr>
              <tr>
                <th>订单数</th>
                <td className="number">{dashboard.year.orderCount}</td>
              </tr>
              <tr>
                <th>毛利率</th>
                <td className="number">{percent(dashboard.year.grossMarginRate)}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </>
  );
}
