export function StatusPill({ tone, children }: { tone: "good" | "warn" | "bad" | "neutral"; children: string }) {
  return <span className={`status-pill ${tone}`}>{children}</span>;
}
