import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import { ToneBadge, TONE_CLASS } from "@/components/ui/tone-badge";
import { SignalBadge } from "../../components/dashboard/SignalBadge";
import { SalaryDistributionChart } from "../../components/dashboard/SalaryDistributionChart";
import { NativeSelect } from "../../components/onboarding/NativeSelect";
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
          <div className="rounded-xl border border-carix-border bg-carix-surface p-6">
            <EmptyState
              data-testid="empty-state-plain"
              variant="plain"
              title="plain — 카드 안에 놓인 경우"
              description="테두리와 배경을 벗어 박스 안의 박스를 막습니다."
            />
          </div>
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
