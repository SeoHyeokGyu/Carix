package com.carix.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.expense.service.ExpenseService;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.IncomeService;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.dto.ProfileRequest;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.profile.service.ProfileService;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.repository.SkillAliasRepository;
import com.carix.skill.repository.SkillRepository;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.skill.service.SkillService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스냅샷 재계산 트리거 배선 검증 (기술 명세 §1.3).
 *
 * <p>엔진은 1~2주차에 착지했으나 호출자가 없어 {@code core.index_snapshot} 에 행이 생기지 않았다. 계약(기술 명세 §1.3)이 규정한 네 경로 —
 * 소득·소비(A), 스킬(B), 프로필(C) — 이 변경 시 {@code recalculateSnapshot(userId, fromYm)} 을 부르는지 확인한다.
 *
 * <p>{@code fromYm} 규약: 값에 유효월이 있으면(소득·소비) 그 달, 없으면(스킬·프로필) 변경 시점의 달이다. 기술 명세 §1.3 의 "해당 시점 이후의
 * 스냅샷을 갱신"을 그대로 읽은 것이다.
 */
class SnapshotTriggerWiringTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private SnapshotRecalculationService recalculation;

  @BeforeEach
  void setUp() {
    recalculation = mock(SnapshotRecalculationService.class);
  }

  @Test
  @DisplayName("소득 등록 - 유효 시작월부터 재계산한다")
  void 소득_등록() {
    IncomeHistoryRepository repository = mock(IncomeHistoryRepository.class);
    given(repository.existsByUserIdAndEffectiveDate(any(), any())).willReturn(false);
    given(repository.save(any(IncomeHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    IncomeService service = new IncomeService(repository, recalculation);

    service.create(
        USER_ID, new IncomeRequest(60_000_000L, 4_000_000L, LocalDate.of(2026, 3, 15), null));

    verify(recalculation).recalculateSnapshot(USER_ID, "202603");
  }

  @Test
  @DisplayName("소득 수정 - 옛 유효월과 새 유효월 중 이른 달부터 재계산한다")
  void 소득_수정() {
    IncomeHistoryRepository repository = mock(IncomeHistoryRepository.class);
    IncomeHistory entity =
        new IncomeHistory(USER_ID, 60_000_000L, 4_000_000L, LocalDate.of(2026, 5, 1), null);
    given(repository.findById(any())).willReturn(Optional.of(entity));
    given(repository.existsByUserIdAndEffectiveDateAndIdNot(any(), any(), any())).willReturn(false);
    IncomeService service = new IncomeService(repository, recalculation);

    service.update(
        USER_ID,
        entity.getId(),
        new IncomeRequest(70_000_000L, 4_600_000L, LocalDate.of(2026, 2, 1), null));

    // 5월 → 2월로 당겼으면 2월부터 다시 계산해야 한다. 새 값만 보면 3~4월이 옛 연봉으로 남는다.
    verify(recalculation).recalculateSnapshot(USER_ID, "202602");
  }

  @Test
  @DisplayName("소득 삭제 - 지운 행의 유효월부터 재계산한다")
  void 소득_삭제() {
    IncomeHistoryRepository repository = mock(IncomeHistoryRepository.class);
    IncomeHistory entity =
        new IncomeHistory(USER_ID, 60_000_000L, 4_000_000L, LocalDate.of(2026, 4, 1), null);
    given(repository.findById(any())).willReturn(Optional.of(entity));
    IncomeService service = new IncomeService(repository, recalculation);

    service.delete(USER_ID, entity.getId());

    verify(recalculation).recalculateSnapshot(USER_ID, "202604");
  }

  @Test
  @DisplayName("소비 저장 - 그 기준월부터 재계산한다")
  void 소비_저장() {
    ExpenseHistoryRepository repository = mock(ExpenseHistoryRepository.class);
    given(repository.findByUserIdAndBaseYearMonth(any(), any())).willReturn(Optional.empty());
    given(repository.save(any(ExpenseHistory.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    ExpenseService service =
        new ExpenseService(repository, recalculation, mock(ExpenseWeightResolver.class));
    service.upsert(USER_ID, "202605", new ExpenseUpdateRequest(List.of()));

    verify(recalculation).recalculateSnapshot(USER_ID, "202605");
  }

  @Test
  @DisplayName("스킬 삭제 - 시장가치가 바뀌므로 변경 시점의 달부터 재계산한다")
  void 스킬_삭제() {
    UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
    Skill skill = Skill.builder().standardName("Spring Boot").isCustom(false).build();
    UserSkill userSkill =
        UserSkill.builder()
            .userId(USER_ID)
            .skill(skill)
            .monthsOfExperience(24)
            .lastUsedDate(LocalDate.of(2026, 1, 1))
            .build();
    given(userSkillRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(userSkill));
    SkillService service =
        new SkillService(
            mock(SkillRepository.class),
            mock(SkillAliasRepository.class),
            userSkillRepository,
            recalculation);

    service.deleteUserSkill(USER_ID, userSkill.getId());

    verify(recalculation).recalculateSnapshot(eq(USER_ID), eq(YearMonths.current()));
  }

  @Test
  @DisplayName("프로필 변경 - 동료집단이 바뀌므로 변경 시점의 달부터 재계산한다")
  void 프로필_변경() {
    CareerProfileRepository repository = mock(CareerProfileRepository.class);
    given(repository.findByUserId(USER_ID))
        .willReturn(Optional.of(CareerProfile.builder().userId(USER_ID).build()));
    ProfileService service = new ProfileService(repository, recalculation, Clock.systemUTC());

    service.patch(
        USER_ID, new ProfileRequest("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null));

    verify(recalculation).recalculateSnapshot(eq(USER_ID), eq(YearMonths.current()));
  }
}
