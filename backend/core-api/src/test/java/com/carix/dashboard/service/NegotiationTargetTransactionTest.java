package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.cpi.CpiObservationRecordRepository;
import com.carix.index.dto.CurrentSalary;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.YearMonths;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.carix.support.H2DataVersionIssuerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 협상 목표(c8)는 대시보드 요약의 선택 블록이다 — 재료 조회가 실패해도 요약 전체가 깨지면 안 된다.
 *
 * <p>안쪽 트랜잭션 빈({@code CategoryCpiRateService})이 바깥 요약 트랜잭션에 참여한 채 예외를 던지면 rollback-only 가 찍혀, 서비스가
 * 예외를 잡아도 바깥 커밋에서 {@code UnexpectedRollbackException} 이 난다. 테스트 메서드는 트랜잭션으로 감싸지 않는다 — 그래야 실제 커밋 경계가
 * 드러난다({@code SnapshotTopPriceDriverTransactionTest} 와 같은 방식).
 */
@SpringBootTest
@Import(H2DataVersionIssuerConfiguration.class)
class NegotiationTargetTransactionTest {

  private static final String CURRENT_YM = YearMonths.current();

  @Autowired private DashboardSummaryService summaryService;
  @Autowired private IndexSnapshotRepository indexSnapshotRepository;

  @MockitoBean private CurrentSalaryProvider currentSalaryProvider;
  @MockitoBean private CpiObservationRecordRepository cpiObservationRecordRepository;

  private UUID userId;

  @AfterEach
  void cleanUp() {
    if (userId != null) {
      indexSnapshotRepository.deleteAll(
          indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId));
    }
  }

  @Test
  @DisplayName("CPI 조회가 실패해도 대시보드 요약은 정상 반환하고 복구선만 비운다")
  void CPI조회실패해도_요약은_커밋된다() {
    userId = UUID.randomUUID();
    indexSnapshotRepository.save(negotiateSnapshot(userId));
    given(currentSalaryProvider.currentGrossSalary(any(), anyString()))
        .willReturn(Optional.of(new CurrentSalary(50_000_000L, LocalDate.of(2025, 3, 15))));
    given(cpiObservationRecordRepository.findFirstByOrderByBaseYearMonthAsc())
        .willThrow(new RuntimeException("CPI 조회 실패"));

    DashboardSummaryResponse response = summaryService.resolve(userId, CURRENT_YM);

    assertThat(response.decisionSignal().code()).isEqualTo("NEGOTIATE");
    assertThat(response.negotiationTargets()).isNotNull();
    assertThat(response.negotiationTargets().toPurchasingPowerRecovery()).isNull();
  }

  private static IndexSnapshot negotiateSnapshot(UUID userId) {
    return IndexSnapshot.builder()
        .userId(userId)
        .baseYearMonth(CURRENT_YM)
        .dataVersion("20260701093000-000")
        .ruleVersion("1.5.0")
        .purchasingPowerIndex(new BigDecimal("94.20"))
        .decisionSignal(DecisionSignalCode.NEGOTIATE)
        .rationale(
            new SnapshotRationale(
                List.of("연봉 격차 5~15% 구간"),
                "BELOW_MARKET_5_TO_15",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                "202606",
                "20260701093000-000",
                null,
                null,
                "KOSIS"))
        .build();
  }
}
