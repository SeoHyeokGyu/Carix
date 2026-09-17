import { useEffect, useState } from "react";
import { redirect, useLoaderData, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
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
  onStartEdit,
  onCancelEdit,
  onEditMonthsChange,
  onEditDateChange,
  onSaveEdit,
  onDelete,
}: SkillRowProps) {
  if (isEditing) {
    return (
      <tr className="border-b border-carix-border/60">
        <td className="px-3 py-2">{skill.standardName}</td>
        <td className="px-3 py-2">
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
        </td>
        <td className="px-3 py-2">
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
        </td>
        <td className="px-3 py-2">
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
        </td>
      </tr>
    );
  }

  return (
    <tr className="border-b border-carix-border/60">
      <td className="px-3 py-2">{skill.standardName}</td>
      <td className="px-3 py-2">{skill.monthsOfExperience}개월</td>
      <td className="px-3 py-2">{skill.lastUsedDate}</td>
      <td className="px-3 py-2">
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
          <Button
            type="button"
            variant="destructive"
            size="sm"
            disabled={busy}
            aria-label={`${skill.standardName} 삭제`}
            onClick={() => onDelete(skill)}
          >
            삭제
          </Button>
        </div>
      </td>
    </tr>
  );
}

export default function RecordsSkills() {
  const { skills: initial } = useLoaderData<typeof clientLoader>();
  const navigate = useNavigate();
  const [skills, setSkills] = useState<UserSkill[]>(initial);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillSearchItem[]>([]);
  const [selected, setSelected] = useState<SkillSearchItem | null>(null);
  const [category, setCategory] = useState("BACKEND");
  const [months, setMonths] = useState("");
  const [lastUsedDate, setLastUsedDate] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editMonths, setEditMonths] = useState("");
  const [editDate, setEditDate] = useState("");
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setResults([]);
      return;
    }
    // 입력마다 즉시 부르면 글자 수만큼 요청이 나간다 — 250ms 쉬면 한 번으로 줄어든다.
    const timer = setTimeout(() => {
      searchSkills(trimmed)
        .then(setResults)
        .catch((error) => {
          if (error instanceof UnauthorizedError) navigate(LOGIN_PATH);
        });
    }, 250);
    return () => clearTimeout(timer);
  }, [query, navigate]);

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
    try {
      await deleteUserSkill(skill.id);
      setSkills((prev) => prev.filter((s) => s.id !== skill.id));
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setFormError(toUserMessage(error));
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
        <table className="w-full text-left text-sm">
          <thead className="text-carix-text/70">
            <tr>
              <th className="px-3 py-2 font-medium">스킬</th>
              <th className="px-3 py-2 font-medium">실무 사용 기간</th>
              <th className="px-3 py-2 font-medium">마지막 사용일</th>
              <th className="px-3 py-2 font-medium">작업</th>
            </tr>
          </thead>
          <tbody>
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
              />
            ))}
          </tbody>
        </table>
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
                <button
                  type="button"
                  role="option"
                  aria-selected="false"
                  className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-carix-border/40"
                  onClick={() => setSelected(item)}
                >
                  {item.standardName}
                </button>
              </li>
            ))}
          </ul>
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
