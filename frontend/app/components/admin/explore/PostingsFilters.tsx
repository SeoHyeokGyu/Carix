import { type ReactNode, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";

import {
  fromMonthInput,
  parsePostingSort,
  POSTING_SORTS,
  toMonthInput,
} from "../../../lib/dataExplore";
import { FIELD } from "./exploreStyles";

type Apply = (next: Record<string, string | undefined>) => void;

/**
 * 입력 중인 값. URL 의 그 값이 바뀔 때만 따라간다 — 다른 조건이 바뀌거나 서버가 월을 채워도
 * 쓰던 글자를 지우지 않는다.
 */
function useDraft(urlValue: string) {
  const [draft, setDraft] = useState(urlValue);
  const [seen, setSeen] = useState(urlValue);
  if (urlValue !== seen) {
    setSeen(urlValue);
    setDraft(urlValue);
  }
  return [draft, setDraft] as const;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className={FIELD}>
      {label}
      {children}
    </label>
  );
}

function Select({
  label,
  name,
  value,
  options,
  onPick,
}: {
  label: string;
  name: string;
  value: string;
  options: [string, string][];
  onPick: (name: string, value: string) => void;
}) {
  return (
    <Field label={label}>
      <NativeSelect
        aria-label={label}
        value={value}
        onChange={(e) => onPick(name, e.target.value)}
        className="h-8 text-xs bg-carix-surface text-carix-text"
        options={options.map(([v, text]) => ({ value: v, label: text }))}
      />
    </Field>
  );
}

/**
 * 공고 조회 조건. 선택 상자는 고르는 즉시 적용하고, 월·회사·스킬은 Enter 나 칸을 벗어날 때 적용한다.
 * 월은 연도를 한 자씩 칠 때마다 값이 바뀌어 즉시 적용하지 않고, 회사·스킬은 월과 규칙을 맞춘다.
 * 선택 상자를 바꿀 때는 입력 중이던 월·회사·스킬도 함께 적용한다.
 */
export function PostingsFilters({
  params,
  onApply,
  onReset,
}: {
  params: URLSearchParams;
  onApply: Apply;
  onReset: () => void;
}) {
  const [month, setMonth] = useDraft(toMonthInput(params.get("month") ?? ""));
  const [company, setCompany] = useDraft(params.get("company") ?? "");
  const [skill, setSkill] = useDraft(params.get("skill") ?? "");

  const apply = (next: Record<string, string | undefined> = {}) =>
    onApply({
      month: fromMonthInput(month),
      company: company.trim() || undefined,
      skill: skill.trim() || undefined,
      page: undefined,
      ...next,
    });

  const pick = (name: string, value: string) =>
    apply({ [name]: value || undefined });

  const isDirty =
    month !== toMonthInput(params.get("month") ?? "") ||
    company !== (params.get("company") ?? "") ||
    skill !== (params.get("skill") ?? "");
  const applyIfDirty = () => isDirty && apply();

  return (
    <div className="space-y-1.5">
      {/* 입력이 여럿인 폼은 제출 버튼 없이는 Enter 가 제출하지 않아 Enter 를 직접 받는다. */}
      <form
        aria-label="공고 조회 조건"
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => e.preventDefault()}
        onKeyDown={(e) => {
          if (e.key === "Enter" && e.target instanceof HTMLInputElement) {
            e.preventDefault();
            apply();
          }
        }}
      >
        <Field label="월">
          <Input
            type="month"
            aria-label="월"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            onBlur={applyIfDirty}
            className="h-8 text-xs bg-carix-surface text-carix-text md:text-xs"
          />
        </Field>
        <Select
          label="원천"
          name="source"
          value={params.get("source") ?? ""}
          onPick={pick}
          options={[
            ["", "전체 원천"],
            ["JOBKOREA", "잡코리아"],
            ["SARAMIN", "사람인"],
            ["WANTED", "원티드"],
            ["PROGRAMMERS", "프로그래머스"],
          ]}
        />
        <Select
          label="표본 여부"
          name="sampled"
          value={params.get("sampled") ?? ""}
          onPick={pick}
          options={[
            ["", "표본·정기 모두"],
            ["true", "표본"],
            ["false", "정기 수집"],
          ]}
        />
        <Select
          label="직무"
          name="family"
          value={params.get("family") ?? ""}
          onPick={pick}
          options={[
            ["", "전체 직무"],
            ["DEVELOPER", "개발"],
            ["DATA", "데이터"],
            ["AI", "AI"],
            ["OTHER", "기타"],
          ]}
        />
        <Field label="회사">
          <Input
            aria-label="회사"
            value={company}
            onChange={(e) => setCompany(e.target.value)}
            onBlur={applyIfDirty}
            placeholder="회사명 일부"
            className="h-8 text-xs bg-carix-surface text-carix-text md:text-xs"
          />
        </Field>
        <Field label="스킬">
          <Input
            aria-label="스킬"
            value={skill}
            onChange={(e) => setSkill(e.target.value)}
            onBlur={applyIfDirty}
            placeholder="정확히 일치"
            className="h-8 text-xs bg-carix-surface text-carix-text md:text-xs"
          />
        </Field>
        <Select
          label="공개 구분"
          name="disclosure"
          value={params.get("disclosure") ?? ""}
          onPick={pick}
          options={[
            ["", "전체 공개 구분"],
            ["PUBLIC", "공개"],
            ["BAND", "비공개 구간"],
            ["NONE", "없음"],
            ["UNKNOWN", "판정 전"],
          ]}
        />
        <Select
          label="정렬"
          name="sort"
          value={parsePostingSort(params.get("sort"))}
          onPick={pick}
          options={POSTING_SORTS.map((o) => [o.id, o.label])}
        />
        <Button type="button" variant="ghost" size="sm" onClick={onReset}>
          조건 초기화
        </Button>
      </form>
      <p className="text-[11px] text-carix-text/50">
        선택 상자는 바로, 월·회사·스킬은 Enter 를 누르거나 칸을 벗어나면
        적용합니다
      </p>
    </div>
  );
}
