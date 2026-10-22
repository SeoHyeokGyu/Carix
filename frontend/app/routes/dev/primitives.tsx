import { RotateCw } from "lucide-react";

import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TextLink } from "@/components/ui/text-link";
import { TONE_CLASS, ToneBadge } from "@/components/ui/tone-badge";

import { SalaryDistributionChart } from "../../components/dashboard/SalaryDistributionChart";
import { SignalBadge } from "../../components/dashboard/SignalBadge";
import { NativeSelect } from "../../components/onboarding/NativeSelect";
import { CumulativeChart } from "../../components/simulator/CumulativeChart";
import { FirstYearWaterfall } from "../../components/simulator/FirstYearWaterfall";
import { RaiseAxis } from "../../components/simulator/RaiseAxis";
import type { SalaryDistributionResponse } from "../../lib/dashboardMarketApi";

/**
 * 프리미티브 갤러리 — 세션 가드 없이 여는 개발 프리뷰 (`dev/skills`·`dev/trends` 와 같은 자리).
 *
 * 시각 회귀 베이스라인이 이 화면을 찍는다. 인증이 필요한 화면은 `CARIX_E2E_EMAIL` 계정이 있어야
 * 돌기 때문에 (`e2e/onboarding-flow.spec.ts`) 프리미티브 계층에서 잠근다.
 */
const BUTTON_VARIANTS = [
  "default",
  "destructive",
  "outline",
  "secondary",
  "ghost",
  "link",
] as const;

const BADGE_VARIANTS = ["default", "secondary", "outline"] as const;

const SIGNAL_CODES = [
  "SEEK_JOB",
  "NEGOTIATE",
  "UPSKILL",
  "STAY",
  "INSUFFICIENT_DATA",
  "RELOCATE",
] as const;

const TONES = Object.keys(TONE_CLASS) as (keyof typeof TONE_CLASS)[];

const SELECT_OPTIONS = [
  { value: "a", label: "선택지 A" },
  { value: "b", label: "선택지 B" },
] as const;

/**
 * 마커 셋(공식 중앙값·공고 중앙값·사용자)이 모두 그려지는 모양. `dark:ring-carix-surface` 는
 * `AppShell` 밖인 이 화면에서 육안으로는 확인되지 않는다 — 스펙이 클래스를 직접 토글해서 잰다.
 */
const SALARY_FIXTURE: SalaryDistributionResponse = {
  status: "OK",
  sampleSize: 50,
  minRequired: 10,
  baseline: {
    basis: "AGE_BAND_PROXY",
    source: "KOSIS",
    median: 65_000_000,
    wageDefinition: "임금직무정보시스템 500인 이상",
  },
  postingReference: {
    min: 40_000_000,
    p25: 52_000_000,
    median: 68_000_000,
    p75: 85_000_000,
    max: 120_000_000,
    sampleSize: 50,
    missingRate: 66.7,
  },
  userPosition: { grossSalary: 72_000_000, percentile: 65 },
  peerGroup: {
    fallbackLevel: 1,
    appliedConditions: "서울 전체 / 경력 4~7년(MID) / 회사규모 무관",
  },
  trend: null,
};

const SIM_MONTHS = 48;
const SIM_GAP_MONTHS = 3;
const SIM_STAY_MONTHLY = 2_800_000;
const SIM_MOVE_MONTHLY = 3_300_000;
const SIM_ONE_TIME_COST = 3_000_000;

/** 시뮬레이터 그림 고정 데이터 — 공백 3개월 뒤 월 50만 원씩 앞서는 모양. */
const SIM_STAY_CUMULATIVE = Array.from(
  { length: SIM_MONTHS },
  (_, i) => SIM_STAY_MONTHLY * (i + 1),
);
const SIM_MOVE_CUMULATIVE = Array.from(
  { length: SIM_MONTHS },
  (_, i) =>
    SIM_MOVE_MONTHLY * Math.max(0, i + 1 - SIM_GAP_MONTHS) - SIM_ONE_TIME_COST,
);
const SIM_CROSSOVER_MONTH =
  SIM_MOVE_CUMULATIVE.findIndex((v, i) => v >= SIM_STAY_CUMULATIVE[i]) + 1;

function Section({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section data-testid={id} className="flex flex-col gap-3 p-6">
      <h2 className="font-display text-sm font-bold text-carix-text/70">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Gallery() {
  return (
    <div className="bg-carix-bg text-carix-text">
      <Section id="primitives-buttons" title="Button">
        <div className="flex flex-wrap items-center gap-3">
          {BUTTON_VARIANTS.map((variant) => (
            <Button key={variant} variant={variant}>
              {variant}
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {BUTTON_VARIANTS.map((variant) => (
            <Button key={`${variant}-disabled`} variant={variant} disabled>
              {variant} disabled
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="xs">xs</Button>
          <Button size="sm">sm</Button>
          <Button size="default">default</Button>
          <Button size="lg">lg</Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="icon-xs" aria-label="icon-xs">
            +
          </Button>
          <Button size="icon-sm" aria-label="icon-sm">
            +
          </Button>
          <Button size="icon" aria-label="icon">
            +
          </Button>
          <Button size="icon-lg" aria-label="icon-lg">
            +
          </Button>
        </div>
        <Button data-testid="button-focus-target">포커스 대상</Button>
      </Section>

      <Section id="primitives-text-link" title="TextLink">
        <div className="flex flex-wrap items-center gap-6">
          <TextLink to="/dev/primitives">앞으로 xs</TextLink>
          <TextLink
            to="/dev/primitives"
            direction="back"
            size="sm"
            data-testid="text-link-focus-target"
          >
            상위 sm
          </TextLink>
          <span className="text-sm text-carix-text/70">
            문장 속{" "}
            <TextLink to="/dev/primitives" direction="none" size="sm">
              링크
            </TextLink>
          </span>
          <span className="text-[11px] text-carix-text/70">
            표 셀{" "}
            <TextLink to="/dev/primitives" direction="none" size="inherit">
              202609
            </TextLink>
          </span>
        </div>
      </Section>
      <Section id="primitives-page-header" title="PageHeader">
        <div className="rounded-lg border border-carix-border p-4">
          <PageHeader
            back={{ to: "/dev/primitives", label: "상위" }}
            title="화면 제목"
            description="설명 한 줄"
            divider
            actions={
              <>
                <TextLink to="/dev/primitives">보조 링크</TextLink>
                <Button
                  variant="outline"
                  size="sm"
                  className="flex items-center gap-1.5"
                >
                  <RotateCw className="h-3.5 w-3.5" />
                  서버 동작
                </Button>
              </>
            }
          />
        </div>
      </Section>
      <Section id="primitives-page-section" title="PageSection · CardTitle">
        <div className="rounded-lg border border-carix-border p-4">
          <PageSection title="섹션 제목" description="설명 한 줄">
            <div className="grid gap-4 sm:grid-cols-2">
              <Card>
                <CardTitle>소제목 카드</CardTitle>
                <p className="text-sm">내용</p>
              </Card>
              <Card data-page-section="">
                <CardTitle as="h2">카드형 섹션</CardTitle>
                <p className="text-sm">내용</p>
              </Card>
            </div>
          </PageSection>
        </div>
      </Section>
      <Section
        id="gallery-state-feedback"
        title="InlineAlert · EmptyState 실패"
      >
        <InlineAlert message="요청을 처리하지 못했습니다." onRetry={() => {}} />
        <InlineAlert
          message="이미 등록되어 있는 스킬입니다."
          onDismiss={() => {}}
        />
        <EmptyState
          tone="danger"
          icon="⚠️"
          title="소득 이력을 불러오지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
        >
          <Button type="button" variant="outline" size="sm">
            다시 시도
          </Button>
        </EmptyState>
      </Section>
      <Section id="gallery-confirm-dialog" title="ConfirmDialog">
        <div className="flex flex-wrap gap-3">
          <ConfirmDialog
            trigger={
              <Button variant="destructive" data-testid="confirm-open-danger">
                삭제
              </Button>
            }
            title="소득 이력을 삭제할까요?"
            description="2026-02-01 소득 이력이 지워지고, 그 시점 이후 지수가 다시 계산됩니다. 되돌릴 수 없습니다."
            confirmLabel="삭제"
            onConfirm={() => {}}
          />
          <ConfirmDialog
            trigger={
              <Button variant="outline" data-testid="confirm-open-default">
                처음부터 다시 시작
              </Button>
            }
            title="온보딩을 처음부터 다시 할까요?"
            description="입력한 프로필·소득·소비·스킬은 그대로 남습니다. 다만 온보딩을 다시 마칠 때까지 판정 화면에 들어갈 수 없습니다."
            confirmLabel="다시 시작"
            tone="default"
            onConfirm={() => {}}
          />
        </div>
      </Section>
      <Section id="primitives-badges" title="Badge / SignalBadge">
        <div className="flex flex-wrap items-center gap-3">
          {BADGE_VARIANTS.map((variant) => (
            <Badge key={variant} variant={variant}>
              {variant}
            </Badge>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {SIGNAL_CODES.map((code) => (
            <SignalBadge key={code} code={code} serverLabel="서버 폴백 문구" />
          ))}
        </div>
      </Section>

      <Section id="gallery-tone-badge" title="ToneBadge">
        <div className="flex flex-wrap gap-2">
          {TONES.map((tone) => (
            <ToneBadge key={tone} tone={tone}>
              {tone}
            </ToneBadge>
          ))}
        </div>
      </Section>

      <Section id="gallery-empty-state" title="EmptyState">
        <div className="flex flex-col gap-4">
          <EmptyState data-testid="empty-state-title-only" title="제목만" />
          <EmptyState
            data-testid="empty-state-icon-description"
            icon="📋"
            title="아이콘과 설명"
            description="중립 원형 아이콘입니다."
          />
          <EmptyState
            data-testid="empty-state-tone-icon"
            icon="⚠️"
            tone="warning"
            title="톤 알약 아이콘"
            description="tone 을 주면 알약이 됩니다."
          >
            <Button type="button" variant="link" size="sm">
              행동 슬롯
            </Button>
          </EmptyState>
          <Card>
            <EmptyState
              data-testid="empty-state-plain"
              variant="plain"
              title="plain — 카드 안에 놓인 경우"
              description="테두리와 배경을 벗어 박스 안의 박스를 막습니다."
            />
          </Card>
        </div>
      </Section>

      <Section id="gallery-loading-state" title="LoadingState">
        <LoadingState message="불러오는 중입니다..." />
      </Section>

      <Section id="primitives-cards" title="Card">
        <div className="grid gap-4 sm:grid-cols-3">
          <Card className="p-5">
            <span className="text-xs text-carix-text/70">
              className 으로 p-5 를 준 카드
            </span>
          </Card>
          <Card>
            <CardContent>기본 카드</CardContent>
          </Card>
          <Card className="p-5 gap-3 transition-transform hover:-translate-y-0.5 duration-200">
            <span className="text-2xl font-bold tracking-tight">102.4</span>
          </Card>
        </div>
      </Section>

      <Section id="primitives-composed" title="합성 카드 — 간격 변화 감지용">
        <Card>
          <CardHeader>
            <CardTitle>
              헤더 · 본문 · 푸터를 실제 화면과 같은 순서로 쌓는다
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p>
              Card 의 gap 이 바뀌면 이 세 덩어리 사이 간격이 diff 로 드러난다.
            </p>
          </CardContent>
          <CardFooter>
            <Button size="sm">푸터 버튼</Button>
          </CardFooter>
        </Card>
      </Section>

      <Section id="primitives-table" title="Table">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead scope="col">기준월</TableHead>
              <TableHead scope="col" className="text-center">
                구매력 지수
              </TableHead>
              <TableHead scope="col" className="text-center">
                결정 신호
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>2026-09</TableCell>
              <TableCell className="text-center">102.4</TableCell>
              <TableCell className="text-center">
                <SignalBadge code="STAY" />
              </TableCell>
            </TableRow>
            <TableRow>
              <TableCell>2026-08</TableCell>
              <TableCell className="text-center">-</TableCell>
              <TableCell className="text-center">
                <SignalBadge code="INSUFFICIENT_DATA" />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Section>

      {/* 새 절은 맨 끝에 붙인다 — 앞에 끼우면 뒤 절들의 y 좌표가 소수로 밀려 캡처 높이가
          1px 반올림되고, 아무것도 안 바뀐 절의 베이스라인이 깨진다. */}
      <Section id="gallery-inputs" title="Input / NativeSelect">
        <div className="grid max-w-md gap-3">
          <Input data-testid="input-default" placeholder="기본 입력" />
          <Input data-testid="input-disabled" placeholder="비활성" disabled />
          <Input
            data-testid="input-invalid"
            placeholder="오류 상태"
            aria-invalid
          />
          <NativeSelect
            data-testid="native-select"
            options={SELECT_OPTIONS}
            placeholder="선택하세요"
            defaultValue=""
          />
        </div>
      </Section>

      <Section id="gallery-chart-marker" title="연봉 분포 마커 링">
        <SalaryDistributionChart data={SALARY_FIXTURE} />
      </Section>

      <Section id="gallery-simulator-charts" title="시뮬레이터 그림">
        <div className="grid max-w-2xl gap-8">
          <RaiseAxis
            expectedPercent={4}
            breakevenPersonal={3.2}
            breakevenGovernment={2.5}
            offerEquivalentPercent={9}
          />
          <RaiseAxis
            expectedPercent={4}
            breakevenPersonal={3.2}
            breakevenGovernment={2.5}
            offerEquivalentPercent={25}
          />
          <RaiseAxis
            expectedPercent={1.5}
            breakevenPersonal={null}
            breakevenGovernment={2.5}
          />
          <CumulativeChart
            stay={SIM_STAY_CUMULATIVE}
            move={SIM_MOVE_CUMULATIVE}
            crossoverMonth={SIM_CROSSOVER_MONTH}
          />
          <FirstYearWaterfall
            firstYear={{
              salaryDiff: 6_000_000,
              gap: -8_400_000,
              oneTimeCost: -SIM_ONE_TIME_COST,
              benefit: 5_000_000,
              total: -400_000,
            }}
          />
        </div>
      </Section>
    </div>
  );
}

export function meta() {
  return [{ title: "프리미티브 갤러리 | Carix" }];
}

export default function DevPrimitives() {
  return (
    <main className="min-h-screen">
      <Gallery />
    </main>
  );
}
