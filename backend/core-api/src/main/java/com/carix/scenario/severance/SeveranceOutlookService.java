package com.carix.scenario.severance;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.EffectiveIncome;
import com.carix.profile.domain.RetirementPensionType;
import com.carix.scenario.dto.EvaluationResponse.SeverancePoint;
import com.carix.scenario.dto.EvaluationResponse.SeveranceView;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.tax.RetirementIncomeTax;
import com.carix.scenario.tax.RetirementIncomeTax.RetirementTax;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지금 회사를 세 시점(오늘·3개월 뒤·다음 인상 뒤 또는 1주년)에 나가면 받는 세후 퇴직금(명목). IRP 에서 일시금으로 찾을 때 기준이다. 어디에도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SeveranceOutlookService {

  public static final String STATUS_OK = "OK";
  public static final String STATUS_DC = "DC";
  public static final String STATUS_NO_HIRE_DATE = "NO_HIRE_DATE";

  static final String TODAY = "TODAY";
  static final String IN_3_MONTHS = "IN_3_MONTHS";
  static final String AFTER_NEXT_RAISE = "AFTER_NEXT_RAISE";
  static final String AFTER_ANNIVERSARY = "AFTER_ANNIVERSARY";

  private static final int MONTHS_AHEAD = 3;
  private static final int RAISE_CYCLE_MONTHS = 12;

  private final IncomeHistoryRepository incomeRepository;
  private final Clock clock;

  /**
   * raiseRate 는 비율(0.03 = 3%)이다. currentGrossOverride 가 있으면 과거·현재 모두 그 연봉으로 보고(§4.1) 인상만 같은 규칙으로
   * 얹는다.
   */
  @Transactional(readOnly = true)
  public SeveranceView outlook(
      UUID userId, ScenarioContextResponse context, double raiseRate, Long currentGrossOverride) {
    RetirementPensionType pensionType =
        context.retirementPensionType() == null
            ? RetirementPensionType.UNKNOWN
            : context.retirementPensionType();
    // DC 형은 입사일이 없어도 금액이 퇴사 시점과 무관하다는 안내가 먼저다
    if (pensionType == RetirementPensionType.DC) {
      return new SeveranceView(pensionType.name(), STATUS_DC, List.of());
    }
    LocalDate hireDate = context.hireDate();
    if (hireDate == null) {
      return new SeveranceView(pensionType.name(), STATUS_NO_HIRE_DATE, List.of());
    }

    LocalDate today = LocalDate.now(clock);
    List<IncomeHistory> history =
        incomeRepository.findByUserIdOrderByEffectiveDateDesc(userId).stream()
            .filter(h -> !h.getEffectiveDate().isAfter(today))
            .toList();
    if (history.isEmpty() && currentGrossOverride == null) {
      throw new IllegalArgumentException("소득 이력이 없습니다. currentGrossOverride 를 직접 입력해 주세요.");
    }
    LocalDate raiseDate =
        nextRaiseDate(history.isEmpty() ? hireDate : history.get(0).getEffectiveDate(), today);
    ToLongFunction<LocalDate> annualGrossOn =
        annualGrossOn(history, currentGrossOverride, today, raiseDate, raiseRate);

    LocalDate thirdDay;
    String thirdKind;
    if (SeveranceCalculator.isEligible(hireDate, today)) {
      thirdKind = AFTER_NEXT_RAISE;
      thirdDay = YearMonth.from(raiseDate.plusMonths(1)).atEndOfMonth();
    } else {
      thirdKind = AFTER_ANNIVERSARY;
      thirdDay = SeveranceCalculator.anniversaryLastWorkDay(hireDate);
    }

    SeverancePoint todayPoint = point(TODAY, hireDate, today, annualGrossOn, null);
    long todayNet = todayPoint.net();
    List<SeverancePoint> points =
        Stream.of(
                todayPoint,
                point(
                    IN_3_MONTHS, hireDate, today.plusMonths(MONTHS_AHEAD), annualGrossOn, todayNet),
                point(thirdKind, hireDate, thirdDay, annualGrossOn, todayNet))
            .sorted(Comparator.comparing(SeverancePoint::lastWorkDay))
            .toList();
    return new SeveranceView(pensionType.name(), STATUS_OK, points);
  }

  /** 마지막 연봉 변경 적용 시작일 + 12개월. 이미 지났으면(오늘 포함) 다음 해로 민다. */
  private static LocalDate nextRaiseDate(LocalDate lastChange, LocalDate today) {
    LocalDate raiseDate = lastChange.plusMonths(RAISE_CYCLE_MONTHS);
    while (!raiseDate.isAfter(today)) {
      raiseDate = raiseDate.plusYears(1);
    }
    return raiseDate;
  }

  /**
   * 인상일 이후는 현재 연봉 × (1 + 인상률), 오늘 이후는 현재 연봉, 과거는 그날 유효한 이력의 상여 포함 연봉. 첫 이력보다 이른 날은 첫 이력 연봉으로 본다 — 0
   * 으로 채우면 평균임금이 근거 없이 깎인다.
   */
  private static ToLongFunction<LocalDate> annualGrossOn(
      List<IncomeHistory> history,
      Long override,
      LocalDate today,
      LocalDate raiseDate,
      double raiseRate) {
    long current = override != null ? override : EffectiveIncome.annualGross(history.get(0));
    long raised = Math.round(current * (1 + raiseRate));
    return date -> {
      if (!date.isBefore(raiseDate)) {
        return raised;
      }
      if (override != null || date.isAfter(today)) {
        return current;
      }
      IncomeHistory effective =
          history.stream()
              .filter(h -> !h.getEffectiveDate().isAfter(date))
              .findFirst()
              .orElse(history.get(history.size() - 1));
      return EffectiveIncome.annualGross(effective);
    };
  }

  private static SeverancePoint point(
      String kind,
      LocalDate hireDate,
      LocalDate lastWorkDay,
      ToLongFunction<LocalDate> annualGrossOn,
      Long todayNet) {
    long gross = SeveranceCalculator.grossSeverance(hireDate, lastWorkDay, annualGrossOn);
    RetirementTax tax = RetirementIncomeTax.of(gross, hireDate, lastWorkDay);
    long net = tax.net(gross);
    // todayNet 이 null 이면 오늘 점 자신이다
    long delta = todayNet == null ? 0 : net - todayNet;
    return new SeverancePoint(kind, lastWorkDay, gross, tax.total(), net, delta);
  }
}
