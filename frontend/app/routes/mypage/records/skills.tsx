import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useEffect, useState } from "react";
import { redirect, useLoaderData, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Field } from "../../../components/onboarding/Field";
import { toUserMessage } from "../../../lib/apiError";
import { UnauthorizedError } from "../../../lib/coreApi";
import { LOGIN_PATH } from "../../../lib/routeGuards";
import {
  SKILL_FIELD_MESSAGES,
  userSkillSchema,
} from "../../../lib/schemas/skill";
import {
  createUserSkill,
  deleteUserSkill,
  fetchUserSkills,
  searchSkills,
  updateUserSkill,
  type SkillSearchItem,
  type UserSkill,
} from "../../../lib/skillApi";

const CATEGORY_OPTIONS = [
  { value: "BACKEND", label: "백엔드 (Backend)" },
  { value: "FRONTEND", label: "프론트엔드 (Frontend)" },
  { value: "DEVOPS", label: "인프라/데브옵스 (DevOps)" },
  { value: "DATA", label: "데이터 엔지니어링 (Data)" },
  { value: "AI_ML", label: "인공지능/머신러닝 (AI/ML)" },
  { value: "ETC", label: "기타 (ETC)" },
];

export async function clientLoader() {
  try {
    return { skills: await fetchUserSkills() };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/** 추가·수정 두 흐름이 같은 `userSkillSchema` 로 검증하고 같은 문구를 뽑는다 — 검증 경로를 나눠 적지 않는다. */
function parseSkillInput(months: string, lastUsedDate: string) {
  return userSkillSchema.safeParse({
    monthsOfExperience: Number(months),
    lastUsedDate,
  });
}

function skillFieldMessage(issue: { path: PropertyKey[] } | undefined): string {
  return issue?.path[0] === "lastUsedDate"
    ? SKILL_FIELD_MESSAGES.lastUsedDate
    : SKILL_FIELD_MESSAGES.monthsOfExperience;
}

interface SkillRowProps {
  skill: UserSkill;
  busy: boolean;
  isEditing: boolean;
  editMonths: string;
  editDate: string;
  editError: string | null;
  deleteError: string | null;
  onStartEdit: (skill: UserSkill) => void;
  onCancelEdit: () => void;
  onEditMonthsChange: (value: string) => void;
  onEditDateChange: (value: string) => void;
  onSaveEdit: (skill: UserSkill) => void;
  onDelete: (skill: UserSkill) => void;
}

function SkillRow({
  skill,
  busy,
  isEditing,
  editMonths,
  editDate,
  editError,
  deleteError,
  onStartEdit,
  onCancelEdit,
  onEditMonthsChange,
  onEditDateChange,
  onSaveEdit,
  onDelete,
}: SkillRowProps) {
  if (isEditing) {
    return (
      <TableRow>
        <TableCell>{skill.standardName}</TableCell>
        <TableCell>
          <Field
            id={`editMonths-${skill.id}`}
            label={`${skill.standardName} 실무 사용 기간`}
            error={editError ?? undefined}
          >
            {(aria) => (
              <Input
                {...aria}
                type="number"
                min="1"
                max="600"
                value={editMonths}
                onChange={(e) => onEditMonthsChange(e.target.value)}
              />
            )}
          </Field>
        </TableCell>
        <TableCell>
          <Field
            id={`editDate-${skill.id}`}
            label={`${skill.standardName} 마지막 사용일`}
          >
            {(aria) => (
              <Input
                {...aria}
                type="date"
                value={editDate}
                onChange={(e) => onEditDateChange(e.target.value)}
              />
            )}
          </Field>
        </TableCell>
        <TableCell>
          <div className="flex gap-2">
            <Button
              type="button"
              size="sm"
              disabled={busy}
              aria-label={`${skill.standardName} 저장`}
              onClick={() => onSaveEdit(skill)}
            >
              저장
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy}
              onClick={onCancelEdit}
            >
              취소
            </Button>
          </div>
        </TableCell>
      </TableRow>
    );
  }

  return (
    <TableRow>
      <TableCell>{skill.standardName}</TableCell>
      <TableCell>{skill.monthsOfExperience}개월</TableCell>
      <TableCell>{skill.lastUsedDate}</TableCell>
      <TableCell>
        <div className="flex flex-col gap-1">
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy}
              aria-label={`${skill.standardName} 수정`}
              onClick={() => onStartEdit(skill)}
            >
              수정
            </Button>
            <ConfirmDialog
              trigger={
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={busy}
                  aria-label={`${skill.standardName} 삭제`}
                >
                  삭제
                </Button>
              }
              title="스킬을 삭제할까요?"
              description={`'${skill.standardName}' 스킬이 보유 목록에서 지워집니다. 되돌릴 수 없습니다.`}
              confirmLabel="삭제"
              onConfirm={() => onDelete(skill)}
            />
          </div>
          {deleteError && (
            <p role="alert" className="text-xs text-destructive">
              {deleteError}
            </p>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}

export default function RecordsSkills() {
  const { skills: initial } = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const [skills, setSkills] = useState<UserSkill[]>(initial);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillSearchItem[]>([]);
  // I2: 검색 결과에서 고른 표준 스킬과, "사전에 없는 스킬로 직접 추가하기"로
  // 만든 커스텀 스킬을 같은 상태로 다룬다. 커스텀 쪽은 서버 id 가 없으므로
  // (검색 결과가 아니다) SkillSearchItem 대신 onboarding/skills.tsx 의
  // selectedSkill 과 같은 최소 모양을 쓴다 — 여기서 쓰지 않는 id 를 지어내지 않는다.
  const [selected, setSelected] = useState<{
    standardName: string;
    isCustom: boolean;
  } | null>(null);
  const [category, setCategory] = useState("BACKEND");
  const [months, setMonths] = useState("");
  const [lastUsedDate, setLastUsedDate] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // 검색이 실제로 끝난 뒤에만 "결과 없음" 을 판단한다 — 디바운스 대기 중에는
  // results 가 이전 상태(빈 배열 포함)라 그 사이에 직접 추가 컨트롤이 잠깐
  // 깜빡이며 나타난다(onboarding/skills.tsx 의 showDropdown 과 같은 이유).
  const [hasSearched, setHasSearched] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editMonths, setEditMonths] = useState("");
  const [editDate, setEditDate] = useState("");
  const [editError, setEditError] = useState<string | null>(null);
  const [deleteErrors, setDeleteErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults([]);
      setHasSearched(false);
      return;
    }
    // 입력마다 즉시 부르면 글자 수만큼 요청이 나간다 — 250ms 쉬면 한 번으로 줄어든다.
    const timer = setTimeout(() => {
      searchSkills(trimmed)
        .then((items) => {
          setResults(items);
          setHasSearched(true);
          setSearchError(null);
        })
        .catch((error) => {
          if (error instanceof UnauthorizedError) {
            navigate(LOGIN_PATH);
            return;
          }
          // M6: 실패를 조용히 삼키면 드롭다운이 낡은 결과를 최신인 것처럼 계속
          // 보여준다. 결과를 비우고 오류를 보여준다 — 단, hasSearched 는 그대로
          // 두어 "사전에 없는 스킬" 직접 추가 컨트롤이 검색 실패를 사전 미등록으로
          // 오해해 잘못 뜨지 않게 한다.
          setResults([]);
          setSearchError(toUserMessage(error));
        });
    }, 250);
    return () => clearTimeout(timer);
  }, [query, navigate]);

  // 사전에 없는 검색어를 그대로 커스텀 스킬로 선택한다 — 표준화 사전 검색이
  // 빈 결과를 줬을 때만 노출되는 경로다(명세 §3.4: "사전에 없으면 커스텀
  // 생성이며 이때 category 가 필수다"). onboarding/skills.tsx 의
  // handleCustomSkillStart 와 같은 흐름이다.
  const handleCustomSkillStart = () => {
    const trimmed = query.trim();
    if (!trimmed) return;
    setSelected({ standardName: trimmed, isCustom: true });
    setResults([]);
    setSearchError(null);
  };

  const handleCreate = async () => {
    if (!selected) {
      setSearchError("추가할 스킬을 먼저 고르세요.");
      return;
    }
    const parsed = parseSkillInput(months, lastUsedDate);
    if (!parsed.success) {
      setFormError(skillFieldMessage(parsed.error.issues[0]));
      return;
    }

    setBusy(true);
    setFormError(null);
    setSearchError(null);
    try {
      const created = await createUserSkill({
        standardName: selected.standardName,
        monthsOfExperience: parsed.data.monthsOfExperience,
        lastUsedDate: parsed.data.lastUsedDate,
        category: selected.isCustom ? category : undefined,
      });
      setSkills((prev) => [...prev, created]);
      setSelected(null);
      setQuery("");
      setMonths("");
      setLastUsedDate("");
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      // 409(중복 정규화 스킬)는 폼 전체가 아니라 검색 필드 문제다 — 사용자가 고쳐야
      // 하는 것이 고른 스킬이기 때문이다.
      setSearchError(toUserMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (skill: UserSkill) => {
    setBusy(true);
    // M4: 실패해도 다른 행의 오류를 지우지 않는다 — 행별로 붙인다.
    setDeleteErrors((prev) => {
      const next = { ...prev };
      delete next[skill.id];
      return next;
    });
    try {
      await deleteUserSkill(skill.id);
      setSkills((prev) => prev.filter((s) => s.id !== skill.id));
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      // M4: 추가 폼의 formError 가 아니라 실패한 행 옆에 붙인다 — income.tsx
      // IncomeRow 의 행별 오류와 같은 자리다.
      setDeleteErrors((prev) => ({
        ...prev,
        [skill.id]: toUserMessage(error),
      }));
    } finally {
      setBusy(false);
    }
  };

  const startEdit = (skill: UserSkill) => {
    setEditingId(skill.id);
    setEditMonths(String(skill.monthsOfExperience));
    setEditDate(skill.lastUsedDate);
    setEditError(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditError(null);
  };

  const handleSaveEdit = async (skill: UserSkill) => {
    const parsed = parseSkillInput(editMonths, editDate);
    if (!parsed.success) {
      setEditError(skillFieldMessage(parsed.error.issues[0]));
      return;
    }

    setBusy(true);
    setEditError(null);
    try {
      const updated = await updateUserSkill(skill.id, parsed.data);
      setSkills((prev) => prev.map((s) => (s.id === skill.id ? updated : s)));
      setEditingId(null);
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setEditError(toUserMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {skills.length === 0 ? (
        <EmptyState
          icon="📋"
          title="등록된 스킬이 없습니다"
          description="아래에서 스킬을 검색해 추가하면 시장가치 지수 계산에 반영됩니다."
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>스킬</TableHead>
              <TableHead>실무 사용 기간</TableHead>
              <TableHead>마지막 사용일</TableHead>
              <TableHead>작업</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {skills.map((skill) => (
              <SkillRow
                key={skill.id}
                skill={skill}
                busy={busy}
                isEditing={editingId === skill.id}
                editMonths={editMonths}
                editDate={editDate}
                editError={editError}
                onStartEdit={startEdit}
                onCancelEdit={cancelEdit}
                onEditMonthsChange={setEditMonths}
                onEditDateChange={setEditDate}
                onSaveEdit={handleSaveEdit}
                onDelete={handleDelete}
                deleteError={deleteErrors[skill.id] ?? null}
              />
            ))}
          </TableBody>
        </Table>
      )}

      <div className="flex flex-col gap-4 rounded-xl border border-carix-border bg-carix-surface p-6">
        <Field
          id="skillSearch"
          label="스킬 검색"
          hint="사전에 없는 스킬은 직접 등록할 수 있습니다."
          error={searchError ?? undefined}
        >
          {(aria) => (
            <Input
              {...aria}
              type="text"
              placeholder="예: Spring Boot"
              value={selected?.standardName ?? query}
              onChange={(e) => {
                setSelected(null);
                setHasSearched(false);
                setQuery(e.target.value);
              }}
            />
          )}
        </Field>

        {results.length > 0 && !selected && (
          <ul
            role="listbox"
            aria-label="검색 결과"
            className="flex flex-col gap-1"
          >
            {results.map((item) => (
              <li key={item.id}>
                <Button
                  type="button"
                  variant="ghost"
                  role="option"
                  aria-selected="false"
                  className="h-auto min-h-8 w-full justify-start py-1.5 text-left whitespace-normal"
                  onClick={() => setSelected(item)}
                >
                  {item.standardName}
                </Button>
              </li>
            ))}
          </ul>
        )}

        {/* I2: 사전 검색이 끝났는데 결과가 없으면 직접 추가할 길이 없었다 — 이
          카테고리 셀렉트(selected?.isCustom 아래)는 이 경로로만 열린다. */}
        {hasSearched && results.length === 0 && query.trim() && !selected && (
          <div className="rounded-md border border-carix-border/60 px-3 py-2 text-sm text-carix-text/70">
            <p>&apos;{query.trim()}&apos;에 대한 검색 결과가 없습니다.</p>
            <Button
              type="button"
              variant="link"
              size="xs"
              className="mt-2 h-auto px-0 text-left whitespace-normal"
              onClick={handleCustomSkillStart}
            >
              + &apos;{query.trim()}&apos;를 사전에 없는 스킬로 직접 추가하기
            </Button>
          </div>
        )}

        {selected?.isCustom && (
          <Field id="skillCategory" label="스킬 분류">
            {(aria) => (
              <select
                {...aria}
                className="h-9 rounded-md border border-carix-border bg-carix-surface px-3 text-sm"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                {CATEGORY_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
        )}

        <Field
          id="monthsOfExperience"
          label="실무 사용 기간 (개월, 1~600)"
          error={formError ?? undefined}
        >
          {(aria) => (
            <Input
              {...aria}
              type="number"
              min="1"
              max="600"
              value={months}
              onChange={(e) => setMonths(e.target.value)}
            />
          )}
        </Field>

        <Field id="lastUsedDate" label="마지막 사용일">
          {(aria) => (
            <Input
              {...aria}
              type="date"
              value={lastUsedDate}
              onChange={(e) => setLastUsedDate(e.target.value)}
            />
          )}
        </Field>

        <div className="flex items-center gap-2">
          <Button type="button" disabled={busy} onClick={handleCreate}>
            스킬 추가
          </Button>
          <p className="text-xs text-carix-text/60">
            추가하면 다음 계산부터 시장가치 지수에 반영됩니다.
          </p>
        </div>
      </div>
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <EmptyState
      variant="plain"
      tone="danger"
      icon="⚠️"
      title="스킬 이력을 불러오지 못했습니다"
      description="잠시 후 다시 시도해 주세요. 다른 탭은 그대로 열립니다."
    />
  );
}
