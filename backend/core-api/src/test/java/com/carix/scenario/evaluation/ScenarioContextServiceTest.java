package com.carix.scenario.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.PerformanceBonusService;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.inflation.InflationAssumptions;
import com.carix.scenario.inflation.InflationBasis;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioContextServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private IncomeHistoryRepository incomeRepository;
  private CareerProfileRepository profileRepository;
  private PerformanceBonusService bonusService;
  private ScenarioContextService service;

  @BeforeEach
  void setUp() {
    incomeRepository = mock(IncomeHistoryRepository.class);
    profileRepository = mock(CareerProfileRepository.class);
    bonusService = mock(PerformanceBonusService.class);
    InflationAssumptions inflation = mock(InflationAssumptions.class);
    given(inflation.resolve(any(), any()))
        .willReturn(
            new InflationBasis(new BigDecimal("2.0"), null, null, null, false, "2026-08", "한국은행"));
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), SEOUL);
    service =
        new ScenarioContextService(
            incomeRepository,
            profileRepository,
            bonusService,
            inflation,
            mock(WageBaselineResolver.class),
            clock);
    given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
  }

  private static IncomeHistory row(long gross, String effective, IncomeChangeReasonEnum reason) {
    return new IncomeHistory(USER_ID, gross, null, LocalDate.parse(effective), reason);
  }

  private static PerformanceBonusResponse bonus(String paidMonth, long amount) {
    return new PerformanceBonusResponse(UUID.randomUUID(), paidMonth, amount);
  }

  @Test
  void JOB_CHANGE_직후면_기본_인상률_null() {
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(
            List.of(
                row(66_000_000L, "2026-03-01", IncomeChangeReasonEnum.JOB_CHANGE),
                row(60_000_000L, "2025-03-01", IncomeChangeReasonEnum.RAISE)));

    assertThat(service.context(USER_ID).defaultRaisePercent()).isNull();

    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(
            List.of(
                row(66_000_000L, "2026-03-01", IncomeChangeReasonEnum.RAISE),
                row(60_000_000L, "2025-03-01", IncomeChangeReasonEnum.RAISE)));

    assertThat(service.context(USER_ID).defaultRaisePercent()).isEqualTo(10.0);
  }

  @Test
  void 입사_연도는_성과급_범위에서_제외() {
    CareerProfile profile = new CareerProfile(USER_ID);
    profile.patch(null, null, null, null, null, LocalDate.parse("2024-05-01"));
    given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(row(60_000_000L, "2024-05-01", null)));
    given(bonusService.list(USER_ID))
        .willReturn(
            List.of(
                bonus("202512", 4_000_000L),
                bonus("202506", 2_000_000L),
                bonus("202412", 3_000_000L),
                bonus("202312", 9_000_000L),
                bonus("202610", 7_000_000L)));

    ScenarioContextResponse.BonusRange range = service.context(USER_ID).performanceBonusRange();

    // 대상은 2025 (6,000,000) 만 — 2024 는 입사 연도, 2023 은 입사 전, 2026 은 올해
    assertThat(range).isEqualTo(new ScenarioContextResponse.BonusRange(6_000_000L, 6_000_000L, 1));
  }
}
