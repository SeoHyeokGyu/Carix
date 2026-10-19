package com.carix.dashboard.service;

import com.carix.dashboard.dto.DashboardSummaryResponse.NegotiationTargets;
import com.carix.dashboard.dto.DashboardSummaryResponse.RecoveryTarget;
import com.carix.dashboard.dto.DashboardSummaryResponse.Target;
import com.carix.datasource.ReadOnlyRoutingContext;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.PersonalPriceIndexCalculator;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.snapshot.YearMonths;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협상 목표 인상률의 재료를 모은다 (기능 명세 US-D7·§4.8). 요청 시점 계산이고 <b>어디에도 저장하지 않으며 로그를 남기지 않는다</b> — 목표와 기준선 금액이
 * 함께 있으면 연봉이 복원된다.
 *
 * <p>연봉과 마지막 변경일은 {@link CurrentSalaryProvider} 로만 얻는다. {@code income_history} 를 직접 읽으면 복호화 경계가 소득
 * 도메인 밖으로 샌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationTargetService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final CurrentSalaryProvider currentSalaryProvider;
  private final CareerProfileRepository careerProfileRepository;
  private final WageBaselineResolver wageBaselineResolver;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  private final CategoryCpiRateService categoryCpiRateService;
  private final ExpenseWeightResolver expenseWeightResolver;

  /**
   * 대시보드 요약의 선택 블록이라 <b>어느 재료가 실패해도 예외를 던지지 않고</b> 그 줄만 {@code null} 로 강등한다. 바깥 요약 트랜잭션에 참여하면 안쪽
   * 실패가 rollback-only 를 찍어 요약 전체가 커밋에서 깨지므로 트랜잭션을 잇지 않는다({@code NOT_SUPPORTED}) — 각 조회는 자기 트랜잭션에서
   * 돈다.
   *
   * @param storedGapBucket 스냅샷 판정 때 저장된 격차 구간. 임금통계 줄을 판정과 대조한다
   * @param storedWageSource 스냅샷 판정 때 임금통계 기준선 출처. 화면 출처 표기와 맞춘다
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public NegotiationTargets resolve(
      UUID userId, String baseYearMonth, String storedGapBucket, String storedWageSource) {
    Optional<CurrentSalary> salary;
    Optional<CareerProfile> profile;
    try {
      salary = currentSalaryProvider.currentGrossSalary(userId, baseYearMonth);
      profile = careerProfileRepository.findByUserId(userId);
    } catch (RuntimeException e) {
      logFailure("salary", userId, e);
      return new NegotiationTargets(null, null, null);
    }
    if (salary.isEmpty()) {
      return new NegotiationTargets(null, null, null);
    }
    long gross = salary.get().grossSalary();

    Target wage = null;
    Target peer = null;
    // 연봉 분포 화면(SalaryDistributionService)과 같이 읽기 전용 풀에서 병렬로 — 요청 스레드에서 부르면 쓰기 풀을 잡는다(b17).
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      // 임금통계는 MVI 와 같은 기본 프로필로 — 판정이 그 기준선으로 났다
      CareerProfile baselineProfile = profile.orElseGet(() -> new CareerProfile(userId));
      Future<WageBaseline> baselineFuture =
          executor.submit(readOnly(() -> wageBaselineResolver.resolveBaseline(baselineProfile)));
      // p75 는 연봉 분포 화면처럼 온보딩한 프로필에만 — 같은 화면의 p75 와 같은 값이어야 한다
      Future<PeerSalaryDistribution> distributionFuture =
          profile.isPresent()
              ? executor.submit(
                  readOnly(() -> peerSalaryDistributionResolver.resolve(profile.get())))
              : null;
      Optional<WageBaseline> baseline = await(baselineFuture, "wage", userId);
      if (baseline != null) {
        wage =
            NegotiationTargetCalculator.wageLine(
                gross, baseline.orElse(null), storedGapBucket, storedWageSource);
      }
      if (distributionFuture != null) {
        Optional<PeerSalaryDistribution> distribution = await(distributionFuture, "peer", userId);
        if (distribution != null) {
          peer = NegotiationTargetCalculator.peerLine(gross, distribution.orElse(null));
        }
      }
    }

    RecoveryTarget recovery;
    try {
      recovery = recovery(userId, baseYearMonth, salary.get());
    } catch (RuntimeException e) {
      logFailure("recovery", userId, e);
      recovery = null;
    }
    return new NegotiationTargets(wage, peer, recovery);
  }

  /** 결과(값이 null 이면 empty), 실패면 {@code null}. */
  private static <T> Optional<T> await(Future<T> future, String line, UUID userId) {
    try {
      return Optional.ofNullable(future.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logFailure(line, userId, e);
      return null;
    } catch (ExecutionException e) {
      logFailure(line, userId, e.getCause());
      return null;
    }
  }

  /** 예외 종류만 남긴다 — 메시지·스택에 금액이 실릴 수 있다(완료 기준 10). */
  private static void logFailure(String line, UUID userId, Throwable e) {
    log.warn(
        "negotiation-target line={} userId={} failed: {}",
        line,
        userId,
        e == null ? "unknown" : e.getClass().getName());
  }

  /** 마지막 연봉 변경 달부터 기준월(또는 허용 시차 안 마지막 CPI 월)까지 개인물가 누적. 변경이 CPI 수집 전이면 CPI 첫 달로 당기고 그 달을 밝힌다. */
  private RecoveryTarget recovery(UUID userId, String baseYearMonth, CurrentSalary salary) {
    Optional<String> firstCpi = categoryCpiRateService.firstCpiMonth();
    Optional<String> latestCpi = categoryCpiRateService.latestCpiMonth(baseYearMonth);
    if (firstCpi.isEmpty()
        || latestCpi.isEmpty()
        || YearMonth.parse(latestCpi.get(), YM)
            .isBefore(
                YearMonth.parse(baseYearMonth, YM)
                    .minusMonths(CategoryCpiRateService.MAX_LAG_MONTHS))) {
      return null;
    }
    String since = max(YearMonths.of(salary.effectiveDate()), firstCpi.get());
    String end = min(latestCpi.get(), baseYearMonth);
    if (end.compareTo(since) <= 0) {
      return NegotiationTargetCalculator.recoveryLine(Optional.of(BigDecimal.ONE), since);
    }
    Optional<BigDecimal> priceIndex =
        PersonalPriceIndexCalculator.accumulate(
            since,
            end,
            categoryCpiRateService.monthOverMonth(since, end),
            expenseWeightResolver.resolveMonths(userId, monthsBetween(since, end)));
    return NegotiationTargetCalculator.recoveryLine(priceIndex, since);
  }

  private static <T> Callable<T> readOnly(Callable<T> callable) {
    return ReadOnlyRoutingContext.withReadOnlyCallable(callable);
  }

  private static List<String> monthsBetween(String from, String to) {
    List<String> months = new ArrayList<>();
    YearMonth end = YearMonth.parse(to, YM);
    for (YearMonth m = YearMonth.parse(from, YM); !m.isAfter(end); m = m.plusMonths(1)) {
      months.add(YM.format(m));
    }
    return months;
  }

  private static String min(String a, String b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static String max(String a, String b) {
    return a.compareTo(b) >= 0 ? a : b;
  }
}
