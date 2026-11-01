import { Client } from "@stomp/stompjs";
import { useEffect, useMemo, useState } from "react";

type Exposure = {
  account: string;
  marginRequired: number;
  collateral: number;
  inBreach: boolean;
  emittedAtMs: number;
};

const BROKER_URL = "ws://localhost:8080/ws";

const money = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 0 });

export default function App() {
  const [rows, setRows] = useState<Record<string, Exposure>>({});
  const [lagMs, setLagMs] = useState<number | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      brokerURL: BROKER_URL,
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/exposure", (message) => {
          const exposure: Exposure = JSON.parse(message.body);
          setRows((previous) => ({ ...previous, [exposure.account]: exposure }));
          // Engine clock to browser clock; only meaningful when both run on the same machine.
          setLagMs(Date.now() - exposure.emittedAtMs);
        });
      },
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, []);

  const sorted = useMemo(
    () =>
      Object.values(rows).sort(
        (a, b) => Number(b.inBreach) - Number(a.inBreach) || a.account.localeCompare(b.account),
      ),
    [rows],
  );

  return (
    <main style={{ fontFamily: "system-ui, sans-serif", maxWidth: 820, margin: "2rem auto", padding: "0 1rem" }}>
      <h1>Live exposure</h1>
      <p>
        {connected ? "Connected" : "Disconnected"}
        {lagMs !== null && ` · last update lag ${lagMs} ms (same-machine clocks)`}
      </p>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ textAlign: "left", borderBottom: "2px solid #ccc" }}>
            <th>Account</th>
            <th style={{ textAlign: "right" }}>Margin required</th>
            <th style={{ textAlign: "right" }}>Collateral</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((row) => (
            <tr key={row.account} style={{ borderBottom: "1px solid #eee" }}>
              <td>{row.account}</td>
              <td style={{ textAlign: "right" }}>{money.format(row.marginRequired)}</td>
              <td style={{ textAlign: "right" }}>{money.format(row.collateral)}</td>
              <td style={{ color: row.inBreach ? "#b00020" : "#1b5e20", fontWeight: 600 }}>
                {row.inBreach ? "Margin call" : "Covered"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  );
}
