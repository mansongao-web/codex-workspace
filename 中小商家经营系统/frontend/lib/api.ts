export const services = {
  catalog: process.env.NEXT_PUBLIC_CATALOG_API ?? "http://localhost:8081",
  inventory: process.env.NEXT_PUBLIC_INVENTORY_API ?? "http://localhost:8082",
  pos: process.env.NEXT_PUBLIC_POS_API ?? "http://localhost:8083",
  report: process.env.NEXT_PUBLIC_REPORT_API ?? "http://localhost:8084"
};

export type Metric = {
  revenue: number;
  profit: number;
  orderCount: number;
  averageOrderValue: number;
  grossMarginRate: number;
};

export type Dashboard = {
  today: Metric;
  week: Metric;
  year: Metric;
  openAlerts: number;
  lowStockCount: number;
  topProducts: TopProduct[];
};

export type Product = {
  id: number;
  sku: string;
  barcode: string | null;
  name: string;
  categoryId: number | null;
  categoryName: string | null;
  supplierId: number | null;
  supplierName: string | null;
  unit: string;
  costPrice: number;
  retailPrice: number;
  status: string;
};

export type StockLevel = {
  productSku: string;
  quantity: number;
  minQuantity: number;
  maxQuantity: number;
  location: string;
  updatedAt: string;
};

export type InventoryAlert = {
  id: number;
  productSku: string;
  alertType: string;
  severity: string;
  message: string;
  resolved: boolean;
  createdAt: string;
};

export type CheckoutItem = {
  productSku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  unitCost: number;
  lineAmount: number;
  profitAmount: number;
};

export type CheckoutResponse = {
  orderNo: string;
  businessDate: string;
  subtotalAmount: number;
  discountAmount: number;
  payableAmount: number;
  costAmount: number;
  profitAmount: number;
  paymentMethod: string;
  cashier: string;
  status: string;
  createdAt: string;
  items: CheckoutItem[];
};

export type SalesBucket = {
  label: string;
  revenue: number;
  cost: number;
  profit: number;
  orderCount: number;
};

export type SalesSummary = {
  period: string;
  from: string;
  to: string;
  buckets: SalesBucket[];
  total: Metric;
};

export type TopProduct = {
  productSku: string;
  productName: string;
  soldQuantity: number;
  revenue: number;
  profit: number;
};

export type SlowProduct = {
  productSku: string;
  productName: string;
  soldQuantity: number;
  stockQuantity: number;
  lastSoldDate: string | null;
};

export type ProfitDistribution = {
  bucket: string;
  lineCount: number;
  revenue: number;
  profit: number;
};

export const demoProducts: Product[] = [
  {
    id: 1,
    sku: "SKU-WATER-550",
    barcode: "6900000000011",
    name: "瓶装水 550ml",
    categoryId: 1,
    categoryName: "饮料",
    supplierId: 1,
    supplierName: "城市批发市场",
    unit: "瓶",
    costPrice: 0.8,
    retailPrice: 2,
    status: "ACTIVE"
  },
  {
    id: 2,
    sku: "SKU-COLA-330",
    barcode: "6900000000028",
    name: "可乐 330ml",
    categoryId: 1,
    categoryName: "饮料",
    supplierId: 1,
    supplierName: "城市批发市场",
    unit: "罐",
    costPrice: 1.8,
    retailPrice: 3.5,
    status: "ACTIVE"
  },
  {
    id: 3,
    sku: "SKU-CHIPS-60",
    barcode: "6900000000035",
    name: "薯片 60g",
    categoryId: 2,
    categoryName: "零食",
    supplierId: 2,
    supplierName: "社区配送商",
    unit: "袋",
    costPrice: 2.2,
    retailPrice: 5,
    status: "ACTIVE"
  }
];

export const demoStocks: StockLevel[] = [
  { productSku: "SKU-WATER-550", quantity: 8, minQuantity: 12, maxQuantity: 240, location: "MAIN", updatedAt: "" },
  { productSku: "SKU-COLA-330", quantity: 46, minQuantity: 15, maxQuantity: 180, location: "MAIN", updatedAt: "" },
  { productSku: "SKU-CHIPS-60", quantity: 35, minQuantity: 12, maxQuantity: 160, location: "MAIN", updatedAt: "" }
];

const demoTopProducts: TopProduct[] = [
  { productSku: "SKU-CHIPS-60", productName: "薯片 60g", soldQuantity: 12, revenue: 60, profit: 33.6 },
  { productSku: "SKU-COLA-330", productName: "可乐 330ml", soldQuantity: 10, revenue: 35, profit: 17 },
  { productSku: "SKU-WATER-550", productName: "瓶装水 550ml", soldQuantity: 8, revenue: 16, profit: 9.6 }
];

export const fallbackDashboard: Dashboard = {
  today: { revenue: 17, profit: 8.4, orderCount: 1, averageOrderValue: 17, grossMarginRate: 0.4941 },
  week: { revenue: 30, profit: 15.2, orderCount: 2, averageOrderValue: 15, grossMarginRate: 0.5067 },
  year: { revenue: 30, profit: 15.2, orderCount: 2, averageOrderValue: 15, grossMarginRate: 0.5067 },
  openAlerts: 2,
  lowStockCount: 2,
  topProducts: demoTopProducts
};

export async function apiGet<T>(url: string, fallback: T): Promise<T> {
  try {
    const response = await fetch(url, { cache: "no-store" });
    if (!response.ok) {
      throw new Error(await response.text());
    }
    return response.json() as Promise<T>;
  } catch {
    return fallback;
  }
}

export async function apiPost<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function money(value: number): string {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2
  }).format(value);
}

export function percent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
