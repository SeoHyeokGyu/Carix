import { DATA_FLOW_NODES } from "../../content/landing";

export function DataFlowDiagram() {
  return (
    <section aria-labelledby="data-flow-heading" className="px-6 py-16">
      <div className="mx-auto max-w-5xl">
        <div className="flex items-center justify-center gap-2">
          <span className="text-2xl" aria-hidden="true">
            🔄
          </span>
          <h2
            id="data-flow-heading"
            className="font-display text-2xl font-bold text-carix-text"
          >
            데이터 연결 구조
          </h2>
        </div>
        <p className="mt-2 text-sm text-carix-text/75 text-center">
          개인 데이터와 시장 정보를 연결하여 가치를 분석하는 유기적 흐름입니다.
        </p>

        {/* 모바일은 세로 피드, 데스크톱은 가로 레이아웃 배치 */}
        <div className="mt-10 flex flex-col gap-4 md:flex-row md:flex-wrap md:items-stretch md:justify-center">
          {DATA_FLOW_NODES.map((node, index) => (
            <div
              key={node.id}
              className="flex flex-col items-center md:flex-row md:items-center md:gap-3"
            >
              <div className="w-full rounded-2xl border border-carix-border bg-carix-surface p-5 shadow-sm text-center md:text-left md:flex md:flex-col md:justify-center md:max-w-[260px] md:min-h-[110px]">
                <div>
                  <div className="flex items-center justify-center md:justify-start gap-2">
                    <span className="flex h-5 w-5 items-center justify-center rounded-full bg-carix-info/10 text-[10px] font-bold text-carix-info">
                      {index + 1}
                    </span>
                    <h3 className="font-display text-base font-bold text-carix-text">
                      {node.label}
                    </h3>
                  </div>
                  {node.detail && (
                    <p className="mt-2 text-xs leading-relaxed text-carix-text/70">
                      {node.detail}
                    </p>
                  )}
                </div>
              </div>
              {index < DATA_FLOW_NODES.length - 1 && (
                <>
                  <div
                    className="my-2 text-carix-info text-xl font-bold md:hidden"
                    aria-hidden="true"
                  >
                    ↓
                  </div>
                  <div
                    className="hidden md:block text-carix-info text-xl font-bold"
                    aria-hidden="true"
                  >
                    →
                  </div>
                </>
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
