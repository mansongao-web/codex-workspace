"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3, Boxes, LayoutDashboard, ReceiptText, Store } from "lucide-react";
import type { ReactNode } from "react";

const navItems = [
  { href: "/", label: "经营概览", icon: LayoutDashboard },
  { href: "/pos", label: "收银", icon: ReceiptText },
  { href: "/products", label: "商品库存", icon: Boxes },
  { href: "/reports", label: "报表", icon: BarChart3 }
];

export function Shell({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link className="brand" href="/">
          <span className="brand-mark">
            <Store size={18} aria-hidden />
          </span>
          <span>
            <strong>RetailOps</strong>
            <small>小店经营台</small>
          </span>
        </Link>

        <nav className="nav-list" aria-label="主导航">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
            return (
              <Link key={item.href} className={active ? "nav-item active" : "nav-item"} href={item.href}>
                <Icon size={18} aria-hidden />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className="main-column">
        <header className="topbar">
          <div>
            <strong>默认门店</strong>
            <span>在线经营</span>
          </div>
          <div className="topbar-actions">
            <span className="status-dot" />
            <span>服务状态</span>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
