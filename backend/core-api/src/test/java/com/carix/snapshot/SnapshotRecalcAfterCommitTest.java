package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.carix.datasource.ReadOnlyRoutingContext;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.income.service.IncomeService;
import com.carix.index.DefaultPurchasingPowerIndexProvider;
import com.carix.index.dto.MviResult;
import com.carix.index.dto.PpiResult;
import com.carix.index.enums.ExpenseWeightStatus;
import com.carix.index.enums.IndexStatus;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.skill.dto.UserSkillCreateRequest;
import com.carix.skill.repository.UserSkillRepository;
import com.carix.skill.service.SkillService;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import com.carix.support.H2DataVersionIssuerConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 재계산이 저장 커밋 뒤에 도는지 검증한다.
 *
 * <p>테스트 메서드를 트랜잭션으로 감싸지 않는다({@link SnapshotTopPriceDriverTransactionTest} 와 같은 이유) — 감싸면 서비스의 저장
 * 트랜잭션이 바깥 트랜잭션에 합류해 커밋이 일어나지 않고, 커밋 시점에 관한 버그가 가려진다.
 *
 * <p>두 산출기는 "계산 시점에 리포지토리로 보이는 행 수"를 값으로 돌려주는 프로브다. 산출은 읽기 전용 풀의 별도 커넥션에서 도므로, 저장이 커밋되기 전에 재계산하면 방금
 * 저장한 행이 프로브에 보이지 않는다.
 */
@SpringBootTest
@Import(H2DataVersionIssuerConfiguration.class)
class SnapshotRecalcAfterCommitTest {

  @Autowired private SkillService skillService;
  @Autowired private IncomeService incomeService;
  @Autowired private UserSkillRepository userSkillRepository;
  @Autowired private IncomeHistoryRepository incomeHistoryRepository;
  @Autowired private IndexSnapshotRepository indexSnapshotRepository;

  // 인터페이스가 아니라 구현 클래스를 바꾼다 — DashboardController 가 MarketValueIndexServiceImpl 을 구체 타입으로 주입받는다.
  @MockitoBean private DefaultPurchasingPowerIndexProvider ppiProvider;
  @MockitoBean private MarketValueIndexServiceImpl mviProvider;

  private final UUID userId = UUID.randomUUID();

  @AfterEach
  void cleanUp() {
    indexSnapshotRepository.deleteAll(
        indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId));
    userSkillRepository.deleteAll(userSkillRepository.findByUserId(userId));
    incomeHistoryRepository.deleteAll(
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId));
  }

  @Test
  @DisplayName("스킬을 차례로 추가하면 최신 스냅샷이 마지막 스킬까지 본다")
  void 마지막_입력까지_반영한다() {
    // 회귀: 저장 트랜잭션 안에서 재계산해 읽기 전용 풀이 커밋 전 스킬을 못 봤다 — 첫 대시보드 시장가치가 마지막 스킬을 빼고 나왔다.
    probeSkillCountAsMvi();

    skillService.createUserSkill(userId, request("Java"));
    skillService.createUserSkill(userId, request("Spring Boot"));
    skillService.createUserSkill(userId, request("AWS"));

    assertThat(latestSnapshot().getMarketValueIndex()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("소득 저장 직후 스냅샷이 새 소득을 본다")
  void 소득도_반영한다() {
    // 회귀: 위와 같은 원인 — 소득 행이 커밋되기 전에 읽어 마지막 소득이 빠졌다.
    given(ppiProvider.calculate(any(), any()))
        .willAnswer(
            invocation -> {
              int rows =
                  visibleFromReadOnlyPool(
                      () ->
                          incomeHistoryRepository
                              .findByUserIdOrderByEffectiveDateDesc(userId)
                              .size());
              String ym = invocation.getArgument(1);
              // 0 은 값이 아니다(지수 계약) — 행이 없으면 1 로 둔다. 행 수 2 와 구분된다.
              return new PpiResult(
                  ym,
                  BigDecimal.valueOf(Math.max(rows, 1)),
                  IndexStatus.OK,
                  ExpenseWeightStatus.DEFAULT_CPI,
                  ym,
                  LocalDate.now());
            });
    given(mviProvider.calculate(any(), any()))
        .willAnswer(invocation -> MviResult.notAvailable(invocation.getArgument(1)));

    incomeService.create(
        userId, new IncomeRequest(60_000_000L, 4_000_000L, LocalDate.now().minusMonths(2), null));
    incomeService.create(
        userId, new IncomeRequest(70_000_000L, 4_600_000L, LocalDate.now().minusMonths(1), null));

    assertThat(latestSnapshot().getPurchasingPowerIndex()).isEqualByComparingTo("2");
  }

  @Test
  @DisplayName("재계산이 실패해도 저장은 남는다")
  void 재계산이_실패해도_저장은_남는다() {
    given(ppiProvider.calculate(any(), any()))
        .willAnswer(invocation -> PpiResult.notAvailable(invocation.getArgument(1)));
    given(mviProvider.calculate(any(), any())).willThrow(new IllegalStateException("boom"));

    assertThatCode(() -> skillService.createUserSkill(userId, request("Java")))
        .doesNotThrowAnyException();

    assertThat(userSkillRepository.findByUserId(userId)).hasSize(1);
  }

  private void probeSkillCountAsMvi() {
    given(ppiProvider.calculate(any(), any()))
        .willAnswer(invocation -> PpiResult.notAvailable(invocation.getArgument(1)));
    given(mviProvider.calculate(any(), any()))
        .willAnswer(
            invocation -> {
              int rows =
                  visibleFromReadOnlyPool(() -> userSkillRepository.findByUserId(userId).size());
              String ym = invocation.getArgument(1);
              if (rows == 0) {
                return new MviResult(
                    ym,
                    null,
                    IndexStatus.INSUFFICIENT_SAMPLE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null);
              }
              return new MviResult(
                  ym,
                  BigDecimal.valueOf(rows),
                  IndexStatus.OK,
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  null,
                  null);
            });
  }

  /** 운영 산출기가 읽는 방식을 따른다 — 가상 스레드에서 읽기 전용 풀로. 호출 스레드에서 바로 읽으면 쓰기 트랜잭션의 커넥션을 공유해 커밋 전 행도 보인다. */
  private static int visibleFromReadOnlyPool(Callable<Integer> read) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      return executor.submit(ReadOnlyRoutingContext.withReadOnlyCallable(read)).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  private IndexSnapshot latestSnapshot() {
    List<IndexSnapshot> all = indexSnapshotRepository.findByUserIdOrderByBaseYearMonthDesc(userId);
    String latestVersion =
        all.stream()
            .map(IndexSnapshot::getDataVersion)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    return all.stream()
        .filter(s -> s.getDataVersion().equals(latestVersion))
        .findFirst()
        .orElseThrow();
  }

  private static UserSkillCreateRequest request(String name) {
    return new UserSkillCreateRequest(name, 12, LocalDate.now().minusDays(1), "BACKEND");
  }
}
