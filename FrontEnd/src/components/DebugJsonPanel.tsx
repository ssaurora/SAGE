"use client";

import { useState } from "react";

export default function DebugJsonPanel({
  title = "Debug JSON",
  payload,
  defaultExpanded = false,
}: {
  title?: string;
  payload: unknown;
  defaultExpanded?: boolean;
}) {
  const [open, setOpen] = useState(defaultExpanded);

  return (
    <div className="card debug-panel">
      <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
        <h2>{title}</h2>
        <button onClick={() => setOpen((current) => !current)}>
          {open ? "Hide Debug JSON" : "Show Debug JSON"}
        </button>
      </div>
      {open ? <pre>{JSON.stringify(payload ?? null, null, 2)}</pre> : <p className="muted">Debug payload is hidden by default.</p>}
    </div>
  );
}
