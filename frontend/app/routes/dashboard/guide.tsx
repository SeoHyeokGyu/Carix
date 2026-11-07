import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { HEADING } from "@/components/ui/heading";
import { GUIDE_SECTION } from "@/lib/guideSections";

import { SignalBadge } from "../../components/dashboard/SignalBadge";

/**
 * 판정 기준 문구. 임계값은 `DecisionSignalEngine`(core-api) 규칙표와 같아야 한다 — 엔진을
 * 바꾸면 여기도 고친다. 신호 이름·색은 `decisionSignal.ts` 가 정본이라 여기서 적지 않는다.
 */
const SIGNAL_RULES = [
  {
    code: "INSUFFICIENT_DATA",
    rule: "가장 먼저 확인합니다. 소득·스킬 입력, 동료집단 표본(10건 미만), 임금 기준선 중 하나라도 없으면 이 신호입니다. 시장 집계가 아직 수집되지 않은 구간이면 입력과 무관하게 이 신호가 나옵니다. 수치를 지어내지 않고 무엇이 부족한지 보여줍니다.",
  },
  {
    code: "SEEK_JOB",
    rule: "연봉 격차 15% 이상이고 시장가치 지수 110 이상일 때. 내 연봉이 통계청 전 직무 중앙값보다 뚜렷이 낮고 보유 스킬 수요가 오르고 있습니다.",
  },
  {
    code: "NEGOTIATE",
    rule: "연봉 격차 5% 이상이고 시장가치 지수 90 이상(수요가 줄지 않음)일 때. 이직보다 현 직장에서의 연봉 협상을 먼저 검토할 시점입니다.",
  },
  {
    code: "UPSKILL",
    rule: "시장가치 지수 90 미만이고, 구매력 지수 95 미만이거나 연봉 격차 5% 이상일 때. 보유 스킬 수요가 줄고 있어 협상보다 스킬 보강이 먼저입니다.",
  },
  {
    code: "STAY",
    rule: "위 조건에 하나도 해당하지 않을 때. 연봉 격차가 5% 미만이라 지금 바로 움직일 신호가 없습니다.",
  },
] as const;

/** 고정 헤더 아래로 앵커가 숨지 않게 띄운다. 좁은 화면 헤더가 약 65px 다. */
const ANCHOR_OFFSET = "scroll-mt-24";

export default function DashboardGuide() {
  return (
    <PageFrame>
      <PageHeader
        title="도움말"
        description="Carix가 제공하는 지표 3종과 결정 신호의 산출 배경 및 해석 방법을 안내합니다."
      />

      <div className="flex flex-col gap-8">
        {/* 1. 지표 3종 상세 안내 */}
        <PageSection title="지표 3종 (기준선 100의 의미)">
          <div className="grid gap-4 md:grid-cols-3">
            {/* 구매력 지수 */}
            <Card
              id={GUIDE_SECTION.ppi}
              className={`p-5 flex flex-col justify-between ${ANCHOR_OFFSET}`}
            >
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle>구매력 지수 (PPI)</CardTitle>
                    <Badge variant="secondary">소득 vs 물가</Badge>
                  </div>
                  <p className="text-xs text-carix-text/60 mt-1">
                    Purchasing Power Index
                  </p>
                </CardHeader>
                <CardContent className="p-0 flex flex-col gap-3 text-xs text-carix-text/80">
                  <p>
                    내 소득의 상승률과 통계청 <b>소비자물가지수(CPI)</b>를
                    비교하여 실질적인 소비 여력의 변화를 측정합니다.
                  </p>
                  <div className="rounded-md bg-carix-surface p-2.5 border border-carix-border flex flex-col gap-1.5">
                    <div className="font-medium text-carix-text">
                      기준선 100:
                    </div>
                    <ul className="space-y-1.5 text-carix-text/80">
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-positive/10 text-carix-positive font-semibold text-[11px]">
                          100 초과
                        </span>
                        <span>
                          물가 상승률보다 연봉 인상률이 높아 실질 소득 증가
                        </span>
                      </li>
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-warning/10 text-carix-warning font-semibold text-[11px]">
                          100 미만
                        </span>
                        <span>
                          물가 인상률에 못 미쳐 실질 구매력이 감소한 상태
                        </span>
                      </li>
                    </ul>
                  </div>
                </CardContent>
              </div>
            </Card>

            {/* 시장가치 지수 */}
            <Card
              id={GUIDE_SECTION.mvi}
              className={`p-5 flex flex-col justify-between ${ANCHOR_OFFSET}`}
            >
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle>시장가치 지수 (MVI)</CardTitle>
                    <Badge variant="secondary">스킬 수요 가치</Badge>
                  </div>
                  <p className="text-xs text-carix-text/60 mt-1">
                    Market Value Index
                  </p>
                </CardHeader>
                <CardContent className="p-0 flex flex-col gap-3 text-xs text-carix-text/80">
                  <p>
                    보유한 스킬들이 최근 3개월 채용공고에서 요구되는{" "}
                    <b>수요율</b>을 이전 12개월과 비교합니다. 직무 단위로 재므로
                    경력·지역은 비교에 들어가지 않습니다.
                  </p>
                  <div className="rounded-md bg-carix-surface p-2.5 border border-carix-border flex flex-col gap-1.5">
                    <div className="font-medium text-carix-text">
                      기준선 100:
                    </div>
                    <ul className="space-y-1.5 text-carix-text/80">
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-positive/10 text-carix-positive font-semibold text-[11px]">
                          100 초과
                        </span>
                        <span>보유 스킬 수요가 이전 12개월보다 늘었습니다</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-info/10 text-carix-info font-semibold text-[11px]">
                          100 기준
                        </span>
                        <span>이전 12개월과 같은 수요입니다</span>
                      </li>
                    </ul>
                  </div>
                </CardContent>
              </div>
            </Card>

            {/* 채용 활발도 */}
            <Card
              id={GUIDE_SECTION.owi}
              className={`p-5 flex flex-col justify-between ${ANCHOR_OFFSET}`}
            >
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle>채용 활발도 (OWI)</CardTitle>
                    <Badge variant="secondary">동료집단 채용 비중</Badge>
                  </div>
                  <p className="text-xs text-carix-text/60 mt-1">
                    Opening Window Index
                  </p>
                </CardHeader>
                <CardContent className="p-0 flex flex-col gap-3 text-xs text-carix-text/80">
                  <p>
                    전체 신규 수집 공고 중{" "}
                    <b>내 동료집단(직군·경력) 공고의 최근 3개월 비중</b>을 이전
                    12개월(4~15개월 전) 비중과 비교한 시장 내 채용 추이
                    지표입니다.
                  </p>
                  <div className="rounded-md bg-carix-surface p-2.5 border border-carix-border flex flex-col gap-1.5">
                    <div className="font-medium text-carix-text">
                      기준선 100:
                    </div>
                    <ul className="space-y-1.5 text-carix-text/80">
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-info/10 text-carix-info font-semibold text-[11px]">
                          100 초과
                        </span>
                        <span>
                          전체 채용 시장 내 내 동료집단 공고 비중이 이전
                          12개월보다 확대
                        </span>
                      </li>
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-warning/10 text-carix-warning font-semibold text-[11px]">
                          100 미만
                        </span>
                        <span>
                          전체 채용 시장 내 내 동료집단 공고 비중이 이전
                          12개월보다 축소
                        </span>
                      </li>
                    </ul>
                  </div>
                </CardContent>
              </div>
            </Card>
          </div>
        </PageSection>

        {/* 2. 결정 신호 (Decision Signal) 해석 */}
        <PageSection title="결정 신호 (Decision Signal) 해석">
          <Card id={GUIDE_SECTION.signal} className={`p-5 ${ANCHOR_OFFSET}`}>
            <div className="mb-4 max-w-3xl text-xs leading-relaxed text-carix-text/80">
              결정 신호는 구매력 지수, 시장가치 지수, 그리고 내 세전 연봉과 공식
              임금통계 중앙값의 격차(연봉 격차)를 규칙표로 조합해 다음에 검토할
              행동을 제시합니다. 위에서부터 차례로 맞춰 보고 처음 맞는 신호를
              냅니다. 시장가치 지수가 아직 없으면(수집 초기) 연봉 격차만으로
              협상 검토·현 상태 유지까지만 판정하고 「시장가치 미반영」을 함께
              표시합니다. 시장가치가 있어도 표본 오차 폭 안에서만 기준선을
              넘으면 같은 방식으로 판정하고 「시장가치 불확실」을 표시합니다.
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {SIGNAL_RULES.map(({ code, rule }) => (
                <div
                  key={code}
                  className="rounded-lg border border-carix-border p-3 flex flex-col gap-1.5"
                >
                  <div className="flex items-center gap-2">
                    <SignalBadge code={code} />
                  </div>
                  <p className="text-xs text-carix-text/70 mt-1">{rule}</p>
                </div>
              ))}
            </div>
          </Card>
        </PageSection>

        {/* 3. 자주 묻는 질문 (FAQ) */}
        <PageSection title="자주 묻는 질문">
          <div className="flex flex-col gap-3">
            <Card className="p-4">
              <h3 className={HEADING.sub}>
                Q. 수치가 100 미만이면 무조건 나쁜 건가요?
              </h3>
              <p className="mt-1 max-w-3xl text-xs leading-relaxed text-carix-text/70">
                그렇지 않습니다. 채용 활발도(OWI)가 100 미만이라도 보유 스킬의
                시장가치 지수(MVI)가 높다면, 전체 시장에서 동료집단 채용 비중은
                다소 줄었더라도 내 핵심 스킬을 찾는 수요는 오히려 늘어난 상황일
                수 있습니다. 각 지표는 단독이 아닌 다른 지표들과 함께 교차
                분석하여 해석하는 것이 좋습니다.
              </p>
            </Card>

            <Card className="p-4">
              <h3 className={HEADING.sub}>
                Q. 과거 스냅샷 데이터에 새로운 지표가 비어 있어요.
              </h3>
              <p className="mt-1 max-w-3xl text-xs leading-relaxed text-carix-text/70">
                대시보드 메인 화면의 <b>[지표 재계산]</b> 버튼을 누르면, 온보딩
                이후 축적된 소득 및 스킬 정보를 바탕으로 과거 모든 월의 스냅샷을
                최신 지표 알고리즘으로 소급 재계산합니다.
              </p>
            </Card>
          </div>
        </PageSection>
      </div>
    </PageFrame>
  );
}
