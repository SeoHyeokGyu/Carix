import { InlineAlert } from "@/components/ui/inline-alert";
import { LoadingState } from "@/components/ui/loading-state";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useState, useEffect, useCallback, useRef } from "react";
import { Link, useNavigate } from "react-router";
import {
  fetchUserSkills,
  searchSkills,
  createUserSkill,
  updateUserSkill,
  deleteUserSkill,
  type UserSkill,
  type SkillSearchItem,
} from "../../lib/skillApi";
import { SKILL_FIELD_MESSAGES, userSkillSchema } from "../../lib/schemas/skill";
import { toUserMessage } from "../../lib/apiError";
import { UnauthorizedError } from "../../lib/coreApi";
import { LOGIN_PATH } from "../../lib/routeGuards";

const CATEGORY_OPTIONS = [
  { value: "BACKEND", label: "백엔드 (Backend)" },
  { value: "FRONTEND", label: "프론트엔드 (Frontend)" },
  { value: "DEVOPS", label: "인프라/데브옵스 (DevOps)" },
  { value: "DATA", label: "데이터 엔지니어링 (Data)" },
  { value: "AI_ML", label: "인공지능/머신러닝 (AI/ML)" },
  { value: "ETC", label: "기타 (ETC)" },
];

function getCurrentYearMonthDay(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function meta() {
  return [
    { title: "온보딩: 보유 스킬 | Carix" },
    {
      name: "description",
      content: "표준 스킬 사전 검색 및 실무 경력 등록",
    },
  ];
}

export default function Skills() {
  const navigate = useNavigate();

  // 스킬 목록 및 로딩 상태
  const [userSkills, setUserSkills] = useState<UserSkill[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  // 목록 불러오기 실패는 동작 실패 문장(errorMessage)과 갈라 목록 자리에서 다시 시도한다.
  const [loadError, setLoadError] = useState(false);

  // 검색 및 선택 상태
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<SkillSearchItem[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);

  // 선택된 스킬 및 입력 폼 상태
  const [selectedSkill, setSelectedSkill] = useState<{
    standardName: string;
    isCustom: boolean;
  } | null>(null);
  const [months, setMonths] = useState<number | "">("");
  const [lastUsedDate, setLastUsedDate] = useState<string>(
    getCurrentYearMonthDay(),
  );
  const [category, setCategory] = useState<string>("BACKEND");

  // 인라인 수정 상태
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editMonths, setEditMonths] = useState<number | "">("");
  const [editDate, setEditDate] = useState<string>("");

  // 피드백 알림
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const searchContainerRef = useRef<HTMLDivElement>(null);

  // 1. 초기 사용자 스킬 목록 로드
  const loadSkills = useCallback(async () => {
    try {
      setIsLoading(true);
      setLoadError(false);
      const data = await fetchUserSkills();
      setUserSkills(data);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadSkills();
  }, [loadSkills]);

  // 2. 검색창 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        searchContainerRef.current &&
        !searchContainerRef.current.contains(event.target as Node)
      ) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 3. 디바운스 실시간 검색
  useEffect(() => {
    if (!searchQuery.trim()) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSearchResults([]);
      setIsSearching(false);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setIsSearching(true);
        const results = await searchSkills(searchQuery);
        setSearchResults(results);
        setShowDropdown(true);
      } catch (err) {
        if (err instanceof UnauthorizedError) {
          navigate(LOGIN_PATH);
          return;
        }
        // 검색 실패 무시
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery, navigate]);

  // 알림 메시지 자동 제거
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 3500);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  // 표준 스킬 선택
  const handleSelectSkill = (item: SkillSearchItem) => {
    setSelectedSkill({
      standardName: item.standardName,
      isCustom: item.isCustom,
    });
    setSearchQuery(item.standardName);
    setShowDropdown(false);
    setErrorMessage(null);
  };

  // 사전에 없는 커스텀 스킬 직접 추가 시작
  const handleCustomSkillStart = () => {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;
    setSelectedSkill({
      standardName: trimmed,
      isCustom: true,
    });
    setShowDropdown(false);
    setErrorMessage(null);
  };

  // 스킬 추가 핸들러
  const handleAddSkill = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!selectedSkill) {
      setErrorMessage("스킬을 검색하여 선택하거나 직접 추가해 주세요.");
      return;
    }

    // 1~30개 개수 검증
    if (userSkills.length >= 30) {
      setErrorMessage("보유 스킬은 최대 30개까지 등록 가능합니다.");
      return;
    }

    // 중복 검증
    const isDuplicate = userSkills.some(
      (s) =>
        s.standardName.trim().toLowerCase() ===
        selectedSkill.standardName.trim().toLowerCase(),
    );
    if (isDuplicate) {
      setErrorMessage("이미 등록되어 있는 스킬입니다.");
      return;
    }

    const numMonths =
      typeof months === "number" ? months : parseInt(months, 10);
    const parsed = userSkillSchema.safeParse({
      monthsOfExperience: numMonths,
      lastUsedDate,
    });
    if (!parsed.success) {
      setErrorMessage(
        parsed.error.issues[0]?.path[0] === "lastUsedDate"
          ? SKILL_FIELD_MESSAGES.lastUsedDate
          : SKILL_FIELD_MESSAGES.monthsOfExperience,
      );
      return;
    }

    try {
      setActionLoading(true);
      const created = await createUserSkill({
        standardName: selectedSkill.standardName,
        monthsOfExperience: numMonths,
        lastUsedDate,
        category: selectedSkill.isCustom ? category : undefined,
      });

      setUserSkills((prev) => [...prev, created]);
      setSuccessMessage(`'${created.standardName}' 스킬이 등록되었습니다.`);
      // 폼 초기화
      setSelectedSkill(null);
      setSearchQuery("");
      setMonths("");
      setLastUsedDate(getCurrentYearMonthDay());
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setErrorMessage(toUserMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // 스킬 삭제 핸들러
  const handleDelete = async (id: string, name: string) => {
    try {
      setActionLoading(true);
      await deleteUserSkill(id);
      setUserSkills((prev) => prev.filter((s) => s.id !== id));
      setSuccessMessage(`'${name}' 스킬이 삭제되었습니다.`);
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setErrorMessage(toUserMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // 인라인 수정 시작
  const startEditing = (skill: UserSkill) => {
    setEditingId(skill.id);
    setEditMonths(skill.monthsOfExperience);
    setEditDate(skill.lastUsedDate);
    setErrorMessage(null);
  };

  // 인라인 수정 저장
  const handleSaveEdit = async (id: string) => {
    const numMonths =
      typeof editMonths === "number" ? editMonths : parseInt(editMonths, 10);
    const parsed = userSkillSchema.safeParse({
      monthsOfExperience: numMonths,
      lastUsedDate: editDate,
    });
    if (!parsed.success) {
      setErrorMessage(
        parsed.error.issues[0]?.path[0] === "lastUsedDate"
          ? SKILL_FIELD_MESSAGES.lastUsedDate
          : SKILL_FIELD_MESSAGES.monthsOfExperience,
      );
      return;
    }

    try {
      setActionLoading(true);
      const updated = await updateUserSkill(id, {
        monthsOfExperience: numMonths,
        lastUsedDate: editDate,
      });

      setUserSkills((prev) => prev.map((s) => (s.id === id ? updated : s)));
      setEditingId(null);
      setSuccessMessage("스킬 정보가 수정되었습니다.");
    } catch (err) {
      if (err instanceof UnauthorizedError) {
        navigate(LOGIN_PATH);
        return;
      }
      setErrorMessage(toUserMessage(err));
    } finally {
      setActionLoading(false);
    }
  };

  // 다음 단계 진행 검증
  const handleNextStep = (e: React.MouseEvent) => {
    if (userSkills.length === 0) {
      e.preventDefault();
      setErrorMessage("최소 1개 이상의 보유 스킬을 등록해야 합니다.");
    } else {
      navigate("/onboarding/review");
    }
  };

  return (
    <PageFrame width="narrow">
      <PageHeader
        title="스킬"
        description="표준 스킬 사전에서 검색해 실무 사용 기간과 마지막 사용일을 기록합니다."
        actions={
          <span className="inline-flex items-center rounded-full bg-carix-surface border border-carix-border px-3 py-1 text-xs font-medium text-carix-text">
            등록: {userSkills.length} / 30
          </span>
        }
      />

      {/* 3. 피드백 메시지 */}
      {errorMessage && (
        <div className="mb-6">
          <InlineAlert
            message={errorMessage}
            onDismiss={() => setErrorMessage(null)}
          />
        </div>
      )}
      {successMessage && (
        <div
          role="status"
          className="mb-6 rounded-lg border border-carix-positive/30 bg-carix-positive/10 px-4 py-3 text-sm text-carix-positive"
        >
          {successMessage}
        </div>
      )}

      {/* 4. 스킬 검색 및 추가 카드 */}
      <PageSection
        title="새 스킬 추가"
        className="rounded-xl border border-carix-border bg-carix-surface p-5 shadow-sm"
      >
        {/* 검색창 */}
        <div ref={searchContainerRef} className="relative">
          <label
            htmlFor="skill-search-input"
            className="block text-xs font-medium text-carix-text/80 mb-1"
          >
            스킬 검색
          </label>
          <div className="relative">
            <input
              id="skill-search-input"
              type="search"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setSelectedSkill(null);
              }}
              placeholder="스킬 이름을 검색하세요 (예: Spring Boot, TypeScript, Docker)..."
              autoComplete="off"
              className="w-full rounded-lg border border-carix-border bg-carix-bg px-4 py-2.5 text-sm text-carix-text placeholder:text-carix-text/40 focus:border-carix-info focus:outline-none focus:ring-1 focus:ring-carix-info"
            />
            {isSearching && (
              <span className="absolute right-3 top-2.5 text-xs text-carix-text/50">
                검색 중...
              </span>
            )}
          </div>

          {/* 자동완성 드롭다운 */}
          {showDropdown && searchQuery.trim().length > 0 && (
            <div className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-carix-border bg-carix-surface py-1 shadow-lg">
              {searchResults.length > 0 ? (
                searchResults.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleSelectSkill(item)}
                    className="w-full px-4 py-2 text-left text-sm hover:bg-carix-bg flex items-center justify-between text-carix-text"
                  >
                    <span>{item.standardName}</span>
                    {item.isCustom && (
                      <span className="text-[11px] rounded bg-carix-warning/10 text-carix-warning px-1.5 py-0.5">
                        사용자 정의
                      </span>
                    )}
                  </button>
                ))
              ) : (
                <div className="px-4 py-3 text-sm text-carix-text/70">
                  <p>&apos;{searchQuery}&apos;에 대한 검색 결과가 없습니다.</p>
                  <button
                    type="button"
                    onClick={handleCustomSkillStart}
                    className="mt-2 text-xs font-medium text-carix-info hover:underline"
                  >
                    + &apos;{searchQuery}&apos;를 사전에 없는 스킬로 직접
                    추가하기
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 선택된 스킬 상세 입력 폼 */}
        {selectedSkill && (
          <form
            onSubmit={handleAddSkill}
            className="rounded-lg border border-carix-border/60 bg-carix-bg/60 p-4"
          >
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm font-semibold text-carix-text">
                선택된 스킬:{" "}
                <span className="text-carix-info">
                  {selectedSkill.standardName}
                </span>
              </span>
              {selectedSkill.isCustom ? (
                <span className="rounded bg-carix-warning/10 px-2 py-0.5 text-xs font-medium text-carix-warning">
                  사용자 정의 스킬
                </span>
              ) : (
                <span className="rounded bg-carix-positive/10 px-2 py-0.5 text-xs font-medium text-carix-positive">
                  표준 스킬
                </span>
              )}
            </div>

            {selectedSkill.isCustom && (
              <div className="mb-4 rounded border border-carix-warning/30 bg-carix-warning/5 p-2.5 text-xs text-carix-warning">
                ⚠️ 사전에 없는 스킬은 시장 수요 통계가 제공되지 않을 수
                있습니다. 직무 카테고리를 선택해 주세요.
                <div className="mt-2">
                  <label
                    htmlFor="custom-category-select"
                    className="block text-[11px] font-semibold mb-1"
                  >
                    직무 카테고리
                  </label>
                  <select
                    id="custom-category-select"
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="rounded border border-carix-border bg-carix-surface px-2.5 py-1.5 text-xs text-carix-text focus:outline-none focus:border-carix-info"
                  >
                    {CATEGORY_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label
                  htmlFor="months-input"
                  className="block text-xs font-medium text-carix-text/80 mb-1"
                >
                  실무 사용 기간 (개월, 1~600)
                </label>
                <input
                  id="months-input"
                  type="number"
                  min="1"
                  max="600"
                  required
                  placeholder="예: 24"
                  value={months}
                  onChange={(e) =>
                    setMonths(
                      e.target.value === "" ? "" : Number(e.target.value),
                    )
                  }
                  className="w-full rounded-lg border border-carix-border bg-carix-surface px-3 py-2 text-sm text-carix-text focus:outline-none focus:border-carix-info"
                />
              </div>
              <div>
                <label
                  htmlFor="last-used-date-input"
                  className="block text-xs font-medium text-carix-text/80 mb-1"
                >
                  마지막 사용일
                </label>
                <input
                  id="last-used-date-input"
                  type="date"
                  max={getCurrentYearMonthDay()}
                  required
                  value={lastUsedDate}
                  onChange={(e) => setLastUsedDate(e.target.value)}
                  className="w-full rounded-lg border border-carix-border bg-carix-surface px-3 py-2 text-sm text-carix-text focus:outline-none focus:border-carix-info"
                />
              </div>
            </div>

            <p className="mt-3 text-[11px] text-carix-text/50">
              * 자기평가 숙련도(상/중/하)는 받지 않으며, 실무 사용 기간과
              최근성을 바탕으로 객관적 가중치를 산출합니다.
            </p>

            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSelectedSkill(null)}
                className="rounded-lg border border-carix-border bg-carix-surface px-3 py-1.5 text-xs text-carix-text hover:bg-carix-bg"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={actionLoading}
                className="rounded-lg bg-carix-info px-4 py-1.5 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50"
              >
                {actionLoading ? "추가 중..." : "+ 스킬 추가"}
              </button>
            </div>
          </form>
        )}
      </PageSection>

      {/* 5. 등록된 스킬 테이블 */}
      <PageSection
        title={`등록된 스킬 (${userSkills.length}개)`}
        className="mb-10"
      >
        {isLoading ? (
          <LoadingState message="스킬 목록을 불러오는 중입니다..." />
        ) : loadError ? (
          <EmptyState
            variant="plain"
            tone="danger"
            icon="⚠️"
            title="스킬 목록을 불러오지 못했습니다"
            description="잠시 후 다시 시도해 주세요."
          >
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void loadSkills()}
            >
              다시 시도
            </Button>
          </EmptyState>
        ) : userSkills.length === 0 ? (
          <div className="rounded-xl border border-dashed border-carix-border bg-carix-surface p-8 text-center">
            <p className="text-sm text-carix-text/60">
              등록된 보유 스킬이 없습니다.
            </p>
            <p className="mt-1 text-xs text-carix-text/40">
              상단 검색창에서 본인의 스킬을 검색해 최소 1개 이상 추가해 주세요.
            </p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-sm">
            <table className="w-full text-left text-sm text-carix-text">
              <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-medium text-carix-text/70">
                <tr>
                  <th scope="col" className="px-4 py-3">
                    스킬
                  </th>
                  <th scope="col" className="px-4 py-3">
                    실무 사용 기간
                  </th>
                  <th scope="col" className="px-4 py-3">
                    마지막 사용일
                  </th>
                  <th scope="col" className="px-4 py-3 text-right">
                    작업
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-carix-border">
                {userSkills.map((skill) => {
                  const isEditing = editingId === skill.id;

                  return (
                    <tr key={skill.id} className="hover:bg-carix-bg/40">
                      <td className="px-4 py-3 font-medium">
                        <div className="flex items-center gap-2">
                          <span>{skill.standardName}</span>
                          {skill.isCustom && (
                            <span className="rounded bg-carix-warning/10 px-1.5 py-0.5 text-[11px] text-carix-warning">
                              통계 미제공
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {isEditing ? (
                          <div className="flex items-center gap-1">
                            <input
                              type="number"
                              min="1"
                              max="600"
                              value={editMonths}
                              onChange={(e) =>
                                setEditMonths(
                                  e.target.value === ""
                                    ? ""
                                    : Number(e.target.value),
                                )
                              }
                              className="w-20 rounded border border-carix-border bg-carix-bg px-2 py-1 text-xs text-carix-text"
                            />
                            <span className="text-xs">개월</span>
                          </div>
                        ) : (
                          <span>{skill.monthsOfExperience}개월</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-carix-text/80 text-xs">
                        {isEditing ? (
                          <input
                            type="date"
                            max={getCurrentYearMonthDay()}
                            value={editDate}
                            onChange={(e) => setEditDate(e.target.value)}
                            className="rounded border border-carix-border bg-carix-bg px-2 py-1 text-xs text-carix-text"
                          />
                        ) : (
                          <span>{skill.lastUsedDate}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {isEditing ? (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleSaveEdit(skill.id)}
                              disabled={actionLoading}
                              className="rounded bg-carix-positive px-2 py-1 text-xs text-white hover:opacity-90 disabled:opacity-50"
                            >
                              저장
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditingId(null)}
                              className="rounded border border-carix-border px-2 py-1 text-xs text-carix-text hover:bg-carix-bg"
                            >
                              취소
                            </button>
                          </div>
                        ) : (
                          <div className="flex items-center justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => startEditing(skill)}
                              className="text-xs font-medium text-carix-info hover:underline"
                            >
                              수정
                            </button>
                            <ConfirmDialog
                              trigger={
                                <button
                                  type="button"
                                  disabled={actionLoading}
                                  className="text-xs font-medium text-carix-danger hover:underline"
                                >
                                  삭제
                                </button>
                              }
                              title="스킬을 삭제할까요?"
                              description={`'${skill.standardName}' 스킬이 보유 목록에서 지워집니다. 되돌릴 수 없습니다.`}
                              confirmLabel="삭제"
                              onConfirm={() =>
                                void handleDelete(skill.id, skill.standardName)
                              }
                            />
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageSection>

      {/* 6. 이전 / 다음 네비게이션 버튼 */}
      <div className="flex items-center justify-between border-t border-carix-border pt-6">
        <Link
          to="/onboarding/expense"
          className="rounded-lg border border-carix-border bg-carix-surface px-5 py-2.5 text-sm font-medium text-carix-text hover:bg-carix-bg"
        >
          ← 이전 (소비 입력)
        </Link>
        <button
          type="button"
          onClick={handleNextStep}
          className={`rounded-lg px-6 py-2.5 text-sm font-medium text-white transition-opacity ${
            userSkills.length === 0
              ? "cursor-not-allowed bg-carix-text/30"
              : "bg-carix-info hover:opacity-90"
          }`}
        >
          다음 (검토) →
        </button>
      </div>
    </PageFrame>
  );
}
