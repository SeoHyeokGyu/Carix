package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carix.index.cpi.CpiObservationRecordRepository;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.carix.support.H2DataVersionIssuerConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 물가 기여도 산출기(c12) 내부 예외가 스냅샷 저장 트랜잭션을 물귀도로 끌고 들어가지 않는지 검증한다 (전역 제약 11).
 *
 * <p>Mockito 스텁이 직접 예외를 던지는 것만으로는 이 회귀를 재현할 수 없다 — 예외가 실제 트랜잭션 프록시를 통과해야 참여 트랜잭션이 rollback-only 로
 * 표시된다. 그래서 {@code CategoryCpiRateService}(실빈, {@code @Transactional(readOnly = true)}) 는 그대로 두고 그
 * 밑의 {@link CpiObservationRecordRepository} 만 Mockito 로 갈아끼워, 예외가 {@code yearOverYear()} 의 실제 프록시를
 * 통과하게 한다.
 *
 * <p>테스트 메서드 자체는 트랜잭션으로 감싸지 않는다(클래스 레벨 {@code @Transactional} 없음) — 그래야 {@code recalculateSnapshot}
 * 의 {@code @Transactional} 경계가 실제 커밋(또는 실제 rollback-only 로 인한 {@code UnexpectedRollbackException})을
 * 그대로 드러낸다.
 */
@SpringBootTest
@Import(H2DataVersionIssuerConfiguration.class)
class SnapshotTopPriceDriverTransactionTest {

  private static final String CURRENT_YM = YearMonths.current();

  @Autowired private SnapshotRecalculationService service;
  @Autowired private IndexSnapshotRepository indexSnapshotRepository;

  @MockitoBean private CpiObservationRecordRepository cpiObservationRecordRepository;

  private UUID userId;

  @AfterEach
  void cleanUp() {
    if (userId != null) {
      // deleteByUserId 파생 삭제 쿼리는 트랜잭션이 있어야 entityManager.remove() 를 태운다 — 테스트
      // 메서드 자체가 트랜잭션 밖이라 그대로 부르면 TransactionRequiredException 이 난다. deleteAll
      // 은 SimpleJpaRepository 가 자체 @Transactional 경계를 갖고 있어 독립 호출이 안전하다.
      indexSnapshotRepository.deleteAll(
          indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId));
    }
  }

  @Test
  @DisplayName("물가 기여도 산출 중 하위 리포지토리가 던진 예외는 스냅샷 저장을 막지 않는다")
  void 물가기여도_산출_실패해도_스냅샷은_저장된다() {
    userId = UUID.randomUUID();
    given(
            cpiObservationRecordRepository
                .findFirstByBaseYearMonthLessThanEqualOrderByBaseYearMonthDesc(anyString()))
        .willThrow(new RuntimeException("CPI 조회 실패"));

    int written = service.recalculateSnapshot(userId, CURRENT_YM);

    assertThat(written).isEqualTo(1);
    List<IndexSnapshot> saved =
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId);
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getRationale().topPriceDriver()).isNull();
  }
}
