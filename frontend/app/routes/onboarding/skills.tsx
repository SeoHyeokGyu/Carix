import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSection } from "@/components/layout/PageSection";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { InlineAlert } from "@/components/ui/inline-alert";
import { Input } from "@/components/ui/input";
import { LoadingState } from "@/components/ui/loading-state";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToneBadge } from "@/components/ui/tone-badge";

import { onboardingStepLabel } from "../../components/onboarding/StepIndicator";
import { useAlertMessage } from "../../hooks/useAlertMessage";
import { toUserMessage } from "../../lib/apiError";
import { UnauthorizedError } from "../../lib/coreApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { SKILL_FIELD_MESSAGES, userSkillSchema } from "../../lib/schemas/skill";
import {
  createUserSkill,
  deleteUserSkill,
  fetchUserSkills,
  searchSkills,
  type SkillSearchItem,
  updateUserSkill,
  type UserSkill,
} from "../../lib/skillApi";

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

/** 개월 수를 사람이 기억하는 단위로 읽는다 — 30개월보다 2년 6개월이 떠올리기 쉽다. */
function formatMonthsAsYears(months: number): string {
  const years = Math.floor(months / 12);
  const rest = months % 12;
  if (years === 0) return `${rest}개월`;
  return rest === 0 ? `${years}년` : `${years}년 ${rest}개월`;
}

export default function Skills() {
  const navigate = useNavigate();

  // 스킬 목록 및 로딩 상태
  const [userSkills, setUserSkills] = useState<UserSkill[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  // 목록 불러오기 실패는 동작 실패 문장(errorMessage)과 갈라 목록 자리에서 다시 시도한다.
  const [loadError, setLoadError] = useState(false);
  const [nextError, setNextError] = useState<string | null>(null);

  // 검색 및 선택 상태
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<SkillSearchItem[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

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
  const {
    message: errorMessage,
    key: errorKey,
    show: setErrorMessage,
  } = useAlertMessage();
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
        setActiveIndex(-1);
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

  const isDropdownOpen = showDropdown && searchQuery.trim().length > 0;

  // 자동완성은 ARIA 콤보박스 패턴을 따른다 — 포커스는 입력칸에 두고 방향키로 고른
  // 항목을 aria-activedescendant 로 알린다.
  const handleSearchKeyDown = (
    event: React.KeyboardEvent<HTMLInputElement>,
  ) => {
    if (event.key === "Escape") {
      setShowDropdown(false);
      setActiveIndex(-1);
      return;
    }
    if (!isDropdownOpen || searchResults.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((index) => Math.min(searchResults.length - 1, index + 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((index) => Math.max(0, index - 1));
    } else if (event.key === "Enter" && activeIndex >= 0) {
      event.preventDefault();
      handleSelectSkill(searchResults[activeIndex]);
    }
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
      setNextError(null);
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
  const handleNextStep = () => {
    if (userSkills.length === 0) {
      setNextError("최소 1개 이상의 보유 스킬을 등록해야 합니다.");
    } else {
      navigate("/onboarding/review");
    }
  };

  return (
    <PageFrame width="narrow">
      <PageHeader
        title={onboardingStepLabel(4)}
        description="업무에서 쓰는 기술을 찾아 추가해 주세요."
      />

      {/* 3. 피드백 메시지 */}
      {errorMessage && (
        <div className="mb-6">
          <InlineAlert
            key={errorKey}
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
      <PageSection title="새 스킬 추가">
        <Card>
          {/* 검색창 */}
          <div ref={searchContainerRef} className="relative">
            <label
              htmlFor="skill-search-input"
              className="block text-xs font-medium text-carix-text/80 mb-1"
            >
              스킬 검색
            </label>
            <div className="relative">
              <Input
                id="skill-search-input"
                type="search"
                role="combobox"
                aria-expanded={isDropdownOpen && searchResults.length > 0}
                aria-controls="skill-search-listbox"
                aria-autocomplete="list"
                aria-activedescendant={
                  activeIndex >= 0 ? `skill-option-${activeIndex}` : undefined
                }
                onKeyDown={handleSearchKeyDown}
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setSelectedSkill(null);
                }}
                placeholder="스킬 이름을 검색하세요 (예: Spring Boot, TypeScript, Docker)..."
                autoComplete="off"
              />
              {isSearching && (
                <span className="absolute right-3 top-2 text-xs text-carix-text/70">
                  검색 중...
                </span>
              )}
            </div>

            {/* 자동완성 드롭다운 */}
            <p role="status" className="sr-only">
              {isDropdownOpen && !isSearching
                ? searchResults.length > 0
                  ? `검색 결과 ${searchResults.length}건`
                  : "검색 결과가 없습니다"
                : ""}
            </p>
            {isDropdownOpen && (
              <div className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-carix-border bg-carix-surface py-1 shadow-lg">
                {searchResults.length > 0 ? (
                  <ul
                    id="skill-search-listbox"
                    role="listbox"
                    aria-label="스킬 검색 결과"
                  >
                    {searchResults.map((item, index) => (
                      <li
                        key={item.id}
                        id={`skill-option-${index}`}
                        role="option"
                        aria-selected={index === activeIndex}
                        onClick={() => handleSelectSkill(item)}
                        onMouseEnter={() => setActiveIndex(index)}
                        className={[
                          "flex w-full cursor-pointer items-center justify-between px-4 py-2 text-left text-sm",
                          index === activeIndex
                            ? "bg-carix-info/10 text-carix-text"
                            : "text-carix-text hover:bg-carix-info/5",
                        ].join(" ")}
                      >
                        <span>{item.standardName}</span>
                        {item.isCustom && (
                          <ToneBadge tone="warning">사용자 정의</ToneBadge>
                        )}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="px-4 py-3 text-sm text-carix-text/70">
                    <p>
                      &apos;{searchQuery}&apos;에 대한 검색 결과가 없습니다.
                    </p>
                    <Button
                      type="button"
                      variant="link"
                      size="xs"
                      onClick={handleCustomSkillStart}
                      className="mt-2 h-auto px-0 text-left whitespace-normal"
                    >
                      + &apos;{searchQuery}&apos;를 사전에 없는 스킬로 직접
                      추가하기
                    </Button>
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
                  <ToneBadge tone="warning">사용자 정의 스킬</ToneBadge>
                ) : (
                  <ToneBadge tone="positive">표준 스킬</ToneBadge>
                )}
              </div>

              {selectedSkill.isCustom && (
                <div className="mb-4 rounded border border-carix-warning/30 bg-carix-warning/5 p-2.5 text-xs text-carix-warning">
                  사전에 없는 스킬은 시장 수요 통계가 제공되지 않을 수 있습니다.
                  어느 분야인지 골라 주세요.
                  <div className="mt-2">
                    <label
                      htmlFor="custom-category-select"
                      className="block text-[11px] font-semibold mb-1"
                    >
                      직무 카테고리
                    </label>
                    <NativeSelect
                      id="custom-category-select"
                      required
                      options={CATEGORY_OPTIONS}
                      placeholder="카테고리 선택"
                      value={category}
                      onChange={(e) => setCategory(e.target.value)}
                      className="bg-carix-surface text-carix-text"
                    />
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
                  <Input
                    id="months-input"
                    type="number"
                    min="1"
                    max="600"
                    required
                    placeholder="예: 24"
                    aria-describedby={
                      months === "" ? undefined : "months-input-hint"
                    }
                    value={months}
                    onChange={(e) =>
                      setMonths(
                        e.target.value === "" ? "" : Number(e.target.value),
                      )
                    }
                  />
                  {months !== "" && months > 0 && (
                    <p
                      id="months-input-hint"
                      className="mt-1 text-xs text-muted-foreground"
                    >
                      = {formatMonthsAsYears(months)}
                    </p>
                  )}
                </div>
                <div>
                  <label
                    htmlFor="last-used-date-input"
                    className="block text-xs font-medium text-carix-text/80 mb-1"
                  >
                    마지막 사용일
                  </label>
                  <Input
                    id="last-used-date-input"
                    type="date"
                    max={getCurrentYearMonthDay()}
                    required
                    value={lastUsedDate}
                    onChange={(e) => setLastUsedDate(e.target.value)}
                  />
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-3">
                <Button type="submit" disabled={actionLoading || loadError}>
                  {actionLoading ? "추가 중..." : "+ 스킬 추가"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setSelectedSkill(null)}
                >
                  취소
                </Button>
              </div>
            </form>
          )}
        </Card>
      </PageSection>

      {/* 5. 등록된 스킬 테이블 */}
      <PageSection
        title={`등록된 스킬 (${userSkills.length} / 30)`}
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
          <EmptyState
            icon="📋"
            title="등록된 보유 스킬이 없습니다."
            description="상단 검색창에서 본인의 스킬을 검색해 최소 1개 이상 추가해 주세요."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col">스킬</TableHead>
                <TableHead scope="col">실무 사용 기간</TableHead>
                <TableHead scope="col">마지막 사용일</TableHead>
                <TableHead scope="col" className="text-right">
                  작업
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {userSkills.map((skill) => {
                const isEditing = editingId === skill.id;

                return (
                  <TableRow key={skill.id} className="hover:bg-carix-bg/40">
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <span>{skill.standardName}</span>
                        {skill.isCustom && (
                          <ToneBadge tone="warning">통계 미제공</ToneBadge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {isEditing ? (
                        <div className="flex items-center gap-1">
                          <Input
                            type="number"
                            min="1"
                            max="600"
                            aria-label={`${skill.standardName} 실무 사용 기간`}
                            value={editMonths}
                            onChange={(e) =>
                              setEditMonths(
                                e.target.value === ""
                                  ? ""
                                  : Number(e.target.value),
                              )
                            }
                            className="w-20"
                          />
                          <span className="text-xs">개월</span>
                        </div>
                      ) : (
                        <span>{skill.monthsOfExperience}개월</span>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-carix-text/80">
                      {isEditing ? (
                        <Input
                          type="date"
                          max={getCurrentYearMonthDay()}
                          aria-label={`${skill.standardName} 마지막 사용일`}
                          value={editDate}
                          onChange={(e) => setEditDate(e.target.value)}
                        />
                      ) : (
                        <span>{skill.lastUsedDate}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      {isEditing ? (
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => handleSaveEdit(skill.id)}
                            disabled={actionLoading}
                          >
                            저장
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setEditingId(null)}
                          >
                            취소
                          </Button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => startEditing(skill)}
                          >
                            수정
                          </Button>
                          <ConfirmDialog
                            trigger={
                              <Button
                                type="button"
                                variant="destructive"
                                size="sm"
                                disabled={actionLoading}
                              >
                                삭제
                              </Button>
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
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </PageSection>

      {/* 6. 단계 이동 줄 — 넘어가지 못하는 이유는 누른 버튼 바로 위에 둔다 */}
      <div className="flex flex-col gap-3">
        {nextError && (
          <p role="alert" className="text-sm text-carix-danger">
            {nextError}
          </p>
        )}
        <div className="flex flex-wrap gap-3">
          <Link
            to="/onboarding/expense"
            className={buttonVariants({ variant: "outline" })}
          >
            이전
          </Link>
          {/* 목록을 모르는 채로는 넘어가지 않는다 — 실패 자리에 다시 시도가 있다. */}
          <Button type="button" disabled={loadError} onClick={handleNextStep}>
            다음
          </Button>
        </div>
      </div>
    </PageFrame>
  );
}
