import { DATA_FLOW_NODES } from "../../content/landing";

export function DataFlowDiagram() {
  return (
    <section aria-labelledby="data-flow-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <h2
          id="data-flow-heading"
          className="font-display text-2xl font-semibold text-carix-text"
        >
          데이터 연결 구조
        </h2>
        <ol className="mt-8 flex flex-col gap-3 md:flex-row md:flex-wrap md:items-center">
          {DATA_FLOW_NODES.map((node, index) => (
            <li key={node.id} className="flex items-center gap-3">
              <div className="rounded-lg border border-carix-border bg-carix-surface/80 px-4 py-3">
                <p className="font-display text-sm font-medium text-carix-text">
                  {node.label}
                </p>
                {node.detail && (
                  <p className="mt-1 text-xs text-carix-text/70">
                    {node.detail}
                  </p>
                )}
              </div>
              {index < DATA_FLOW_NODES.length - 1 && (
                <span aria-hidden="true" className="text-carix-info">
                  →
                </span>
              )}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
