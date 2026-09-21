import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { SignalBadge } from "../../components/dashboard/SignalBadge";

export default function DashboardGuide() {
  return (
    <section className="mx-auto w-full max-w-4xl px-6 py-10">
      <div>
        <h1 className="font-display text-2xl font-bold text-carix-text">
          지표 해석 및 결정 신호 가이드
        </h1>
        <p className="mt-1 text-sm text-carix-text/70">
          Carix가 제공하는 3대 핵심 지표와 결정 신호의 산출 배경 및 해석 방법을
          안내합니다.
        </p>
      </div>

      <div className="mt-8 flex flex-col gap-8">
        {/* 1. 핵심 지표 3종 상세 안내 */}
        <div>
          <h2 className="text-lg font-semibold text-carix-text mb-4">
            📊 3대 핵심 지표 (기준선 100의 의미)
          </h2>
          <div className="grid gap-4 md:grid-cols-3">
            {/* 구매력 지수 */}
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base font-bold text-carix-text">
                      구매력 지수 (PPI)
                    </CardTitle>
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
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base font-bold text-carix-text">
                      시장가치 지수 (MVI)
                    </CardTitle>
                    <Badge variant="secondary">스킬 수요 가치</Badge>
                  </div>
                  <p className="text-xs text-carix-text/60 mt-1">
                    Market Value Index
                  </p>
                </CardHeader>
                <CardContent className="p-0 flex flex-col gap-3 text-xs text-carix-text/80">
                  <p>
                    보유한 스킬들의 <b>채용 시장 수요율</b>과 연차 조건을
                    반영하여 산출한 내 시장 가치를 고용노동부 공식 임금 통계와
                    비교합니다.
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
                          희소/고수요 스킬 보유로 시장 평균 대비 높은 가치 형성
                        </span>
                      </li>
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-info/10 text-carix-info font-semibold text-[11px]">
                          100 기준
                        </span>
                        <span>
                          동료집단(동일 연차·직무) 공식 임금 중앙값 수준
                        </span>
                      </li>
                    </ul>
                  </div>
                </CardContent>
              </div>
            </Card>

            {/* 개방창 지수 */}
            <Card className="p-5 flex flex-col justify-between">
              <div>
                <CardHeader className="p-0 mb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base font-bold text-carix-text">
                      개방창 지수 (OWI)
                    </CardTitle>
                    <Badge variant="secondary">채용 모집 속도</Badge>
                  </div>
                  <p className="text-xs text-carix-text/60 mt-1">
                    Opening Window Index
                  </p>
                </CardHeader>
                <CardContent className="p-0 flex flex-col gap-3 text-xs text-carix-text/80">
                  <p>
                    동료집단 채용 공고의 <b>평균 모집 기간(등록일~마감일)</b>을
                    전체 채용 시장의 중앙값과 비교한 시장 속도 지표입니다.
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
                          공고 모집 기간이 길어 지원 기회가 여유롭거나 상시채용
                          성향
                        </span>
                      </li>
                      <li className="flex items-start gap-2">
                        <span className="shrink-0 px-1.5 py-0.5 rounded bg-carix-warning/10 text-carix-warning font-semibold text-[11px]">
                          100 미만
                        </span>
                        <span>
                          공고가 빠르게 마감되는 집중 채용 성향 (빠른 지원 필요)
                        </span>
                      </li>
                    </ul>
                  </div>
                </CardContent>
              </div>
            </Card>
          </div>
        </div>

        {/* 2. 결정 신호 (Decision Signal) 안내 */}
        <div>
          <h2 className="text-lg font-semibold text-carix-text mb-4">
            🚦 결정 신호 (Decision Signal) 해석
          </h2>
          <Card className="p-5">
            <div className="text-xs text-carix-text/80 leading-relaxed mb-4">
              결정 신호는 구매력 지수, 시장가치 지수, 개방창 지수 및 현재 내
              실제 연봉과 시장 추정 연봉 간의 격차를 룰 엔진이 종합 분석하여
              제시하는 행동 권고 신호입니다.
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-lg border border-carix-border p-3 flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <SignalBadge code="SEEK_JOB" serverLabel="이직 탐색" />
                </div>
                <p className="text-xs text-carix-text/70 mt-1">
                  내 시장가치가 현재 연봉보다 15% 이상 높거나, 구매력이
                  지속적으로 저하되고 채용 시장 기회가 열려 있을 때 권장됩니다.
                </p>
              </div>

              <div className="rounded-lg border border-carix-border p-3 flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <SignalBadge code="RENEGOTIATE" serverLabel="연봉 협상" />
                </div>
                <p className="text-xs text-carix-text/70 mt-1">
                  시장가치는 충분히 높으나 이직보다는 현재 재직 기업에서의 연봉
                  인상 또는 승진 협상이 더 유리한 시점에 권장됩니다.
                </p>
              </div>

              <div className="rounded-lg border border-carix-border p-3 flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <SignalBadge code="SKILL_UP" serverLabel="스킬 강화" />
                </div>
                <p className="text-xs text-carix-text/70 mt-1">
                  현재 보유 스킬의 시장 수요율이 정체되어 있거나, 시장가치를
                  높이기 위해 최신 고수요 스킬 습득이 필요한 시점에 제안됩니다.
                </p>
              </div>

              <div className="rounded-lg border border-carix-border p-3 flex flex-col gap-1.5">
                <div className="flex items-center gap-2">
                  <SignalBadge code="HOLD" serverLabel="현재 유지" />
                </div>
                <p className="text-xs text-carix-text/70 mt-1">
                  현재 연봉과 시장가치가 균형을 이루고 있으며, 실질 구매력이
                  안정적으로 유지되고 있는 상태입니다.
                </p>
              </div>
            </div>
          </Card>
        </div>

        {/* 3. 자주 묻는 질문 (FAQ) */}
        <div>
          <h2 className="text-lg font-semibold text-carix-text mb-4">
            ❓ 자주 묻는 질문
          </h2>
          <div className="flex flex-col gap-3">
            <Card className="p-4">
              <h3 className="text-sm font-semibold text-carix-text">
                Q. 수치가 100 미만이면 무조건 나쁜 건가요?
              </h3>
              <p className="mt-1 text-xs text-carix-text/70 leading-relaxed">
                그렇지 않습니다. 개방창 지수(OWI)의 경우 100 미만은 우수
                기업들이 지원자를 빠르게 확보하기 위해 마감일을 짧게 설정하는
                경쟁 채용 분위기를 의미할 수도 있습니다. 각 지표는 단독이 아닌
                다른 지표들과 함께 교차 분석하여 해석하는 것이 좋습니다.
              </p>
            </Card>

            <Card className="p-4">
              <h3 className="text-sm font-semibold text-carix-text">
                Q. 과거 스냅샷 데이터에 새로운 지표가 비어 있어요.
              </h3>
              <p className="mt-1 text-xs text-carix-text/70 leading-relaxed">
                대시보드 메인 화면의 <b>[지표 재계산]</b> 버튼을 누르면, 온보딩
                이후 축적된 소득 및 스킬 정보를 바탕으로 과거 모든 월의 스냅샷을
                최신 지표 알고리즘으로 소급 재계산합니다.
              </p>
            </Card>
          </div>
        </div>
      </div>
    </section>
  );
}
