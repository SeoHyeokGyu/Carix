import { DATA_FLOW_NODES } from "../../content/landing";

export function DataFlowDiagram() {
  return (
    <section aria-labelledby="data-flow-heading" className="px-6 py-16">
      <div className="mx-auto max-w-7xl">
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

        {/* 좁으면 세로 피드, 넓으면 여섯 단계를 한 줄에. 줄바꿈을 허용하면 마지막 단계만 아래로 떨어지고
            앞줄 끝에 화살표가 홀로 남는다. */}
        <div className="mt-10 flex flex-col gap-4 xl:flex-row xl:items-stretch xl:gap-3">
          {DATA_FLOW_NODES.map((node, index) => (
            <div
              key={node.id}
              className="flex flex-col items-center xl:min-w-0 xl:flex-1 xl:flex-row xl:items-stretch xl:gap-3"
            >
              <div className="w-full max-w-xl break-keep rounded-2xl border border-carix-border bg-carix-surface p-5 shadow-sm text-center xl:flex xl:max-w-none xl:min-h-[110px] xl:flex-col xl:justify-center xl:text-left">
                <div>
                  <div className="flex items-center justify-center gap-2 xl:justify-start">
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
                    className="my-2 text-carix-info text-xl font-bold xl:hidden"
                    aria-hidden="true"
                  >
                    ↓
                  </div>
                  <div
                    className="hidden self-center text-carix-info text-xl font-bold xl:block"
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
