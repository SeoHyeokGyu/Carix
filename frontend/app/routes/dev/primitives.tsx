import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { SignalBadge } from "../../components/dashboard/SignalBadge";

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

const BADGE_VARIANTS = [
  "default",
  "secondary",
  "destructive",
  "outline",
  "ghost",
  "link",
] as const;

const SIGNAL_CODES = [
  "SEEK_JOB",
  "NEGOTIATE",
  "UPSKILL",
  "STAY",
  "INSUFFICIENT_DATA",
  "RELOCATE",
] as const;

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
        <div className="overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs">
          <table className="w-full text-left text-sm text-carix-text">
            <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-medium text-carix-text/70">
              <tr>
                <th scope="col" className="px-4 py-3">
                  기준월
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  구매력 지수
                </th>
                <th scope="col" className="px-4 py-3 text-center">
                  결정 신호
                </th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-carix-border/60">
                <td className="px-4 py-3">2026-09</td>
                <td className="px-4 py-3 text-center">102.4</td>
                <td className="px-4 py-3 text-center">
                  <SignalBadge code="STAY" />
                </td>
              </tr>
              <tr>
                <td className="px-4 py-3">2026-08</td>
                <td className="px-4 py-3 text-center">-</td>
                <td className="px-4 py-3 text-center">
                  <SignalBadge code="INSUFFICIENT_DATA" />
                </td>
              </tr>
            </tbody>
          </table>
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
      <div className="light">
        <Gallery />
      </div>
      <div className="dark">
        <Gallery />
      </div>
    </main>
  );
}
