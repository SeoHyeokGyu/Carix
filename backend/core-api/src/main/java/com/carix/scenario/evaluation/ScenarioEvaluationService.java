package com.carix.scenario.evaluation;

import com.carix.profile.domain.RetirementPensionType;
import com.carix.scenario.ScenarioCalculator;
import com.carix.scenario.ScenarioService;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.EvaluateRequest.Move;
import com.carix.scenario.dto.EvaluateRequest.Stay;
import com.carix.scenario.dto.EvaluationResponse;
import com.carix.scenario.dto.EvaluationResponse.Assumption;
import com.carix.scenario.dto.EvaluationResponse.MoveView;
import com.carix.scenario.dto.EvaluationResponse.Range;
import com.carix.scenario.dto.EvaluationResponse.SeveranceView;
import com.carix.scenario.dto.EvaluationResponse.StayView;
import com.carix.scenario.dto.EvaluationResponse.StockLine;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.dto.ScenarioContextResponse.BonusRange;
import com.carix.scenario.inflation.InflationAssumptions;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.projection.ProjectionInput;
import com.carix.scenario.projection.ProjectionResult;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import com.carix.scenario.projection.SeveranceTerms;
import com.carix.scenario.projection.StayMoveProjector;
import com.carix.scenario.severance.SeveranceOutlookService;
import com.carix.scenario.tax.TakeHomeEstimator;
import com.carix.scenario.tax.TakeHomeEstimator.Calibration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잔류·이직의 세후·실질 평가. 결론 숫자와 계단은 확정층(연봉·별도 고정상여·공백·비용·실업급여)만이고, 기대층(성과급·사이닝)은 범위로, 불확실층(주식)은 별도 줄로만
 * 내보낸다. 어디에도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ScenarioEvaluationService {

  private static final int MONTHS_PER_YEAR = 12;
  private static final double PERCENT = 100.0;

  private final ScenarioContextService contextService;
  private final ScenarioService scenarioService;
  private final InflationAssumptions inflationAssumptions;
  private final TakeHomeEstimator estimator;
  private final StayMoveProjector projector;
  private final SeveranceOutlookService severanceOutlookService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public EvaluationResponse evaluate(UUID userId, EvaluateRequest request) {
    ScenarioContextResponse context = contextService.context(userId);
    Stay stay = request.stay();
    Move move = request.move();

    long currentGross = currentGross(stay, context);
    if (estimator.annualNet(currentGross) <= 0) {
      throw new IllegalArgumentException("현재 연봉으로는 세후 소득을 계산할 수 없습니다. 연봉을 확인해 주세요.");
    }
    boolean overridden = stay.currentGrossOverride() != null;
    Calibration calibration =
        estimator.calibrate(currentGross, overridden ? null : context.monthlyNet());
    InflationBasis basis = context.inflation();
    double inflation = inflationAssumptions.effective(basis).doubleValue() / PERCENT;
    double governmentInflation = basis.governmentPercent().doubleValue() / PERCENT;
    double raise = stay.raisePercent() / PERCENT;

    // 블록과 비교가 같은 퇴직금 판정을 쓴다: DC형·입사일 없음이면 비교에도 퇴직금을 넣지 않는다
    SeveranceView severance =
        severanceOutlookService.outlook(userId, context, raise, stay.currentGrossOverride());
    SeveranceTerms severanceTerms =
        SeveranceOutlookService.STATUS_OK.equals(severance.status())
            ? new SeveranceTerms(context.hireDate(), LocalDate.now(clock))
            : null;
    Long moveGross = move == null ? null : moveAnnualGross(move);
    ProjectionResult result =
        projector.project(
            input(currentGross, raise, moveGross, move, inflation, calibration, severanceTerms));
    double stayGovernmentChange =
        basis.personalPercent() == null
            ? result.stayRealChangePercent()
            : projector
                .project(
                    input(currentGross, raise, null, null, governmentInflation, calibration, null))
                .stayRealChangePercent();

    Double marketGap =
        overridden
            ? contextService.marketGapPercent(userId, currentGross)
            : context.marketGapPercent();
    // 개인물가가 없으면 내 기준 손익분기는 한국은행 전망 값의 복사본이라 "개인물가 기준" 으로 보이지 않게 비운다
    StayView stayView =
        new StayView(
            result.stayRealChangePercent(),
            stayGovernmentChange,
            basis.personalPercent() == null
                ? null
                : projector.breakevenRaisePercent(currentGross, inflation),
            projector.breakevenRaisePercent(currentGross, governmentInflation),
            marketGap);

    return new EvaluationResponse(
        stayView,
        move == null
            ? null
            : moveView(
                userId,
                stay,
                move,
                moveGross,
                currentGross,
                context,
                result,
                inflation,
                calibration),
        basis,
        assumptions(basis, calibration, severance, move, stay),
        unreflected(stay, move, context),
        severance,
        move == null ? null : typeLabel(result.firstYear()));
  }

  /** 라벨은 오퍼의 성격을 말하므로 지금 회사 근속 길이에 따라 바뀌는 퇴직금 차이를 빼고 정한다. */
  private static String typeLabel(FirstYear firstYear) {
    return ScenarioCalculator.typeLabelOf(firstYear.total() - firstYear.severance());
  }

  private static long currentGross(Stay stay, ScenarioContextResponse context) {
    if (stay.currentGrossOverride() != null) {
      return stay.currentGrossOverride();
    }
    if (context.currentAnnualGross() == null) {
      throw new IllegalArgumentException("소득 이력이 없습니다. currentGrossOverride 를 직접 입력해 주세요.");
    }
    return context.currentAnnualGross();
  }

  /** 따로 받는 고정상여는 이직 연봉에 더한다. 연봉에 포함돼 있으면 더하지 않는다. */
  private static long moveAnnualGross(Move move) {
    long separateBonus =
        move.bonusIncluded() || move.fixedBonusAnnual() == null ? 0 : move.fixedBonusAnnual();
    return move.grossSalary() + separateBonus;
  }

  private static ProjectionInput input(
      long currentGross,
      double raise,
      Long moveGross,
      Move move,
      double inflation,
      Calibration calibration,
      SeveranceTerms severance) {
    return new ProjectionInput(
        currentGross,
        raise,
        moveGross,
        move == null ? 0 : move.unemploymentMonths(),
        move == null ? 0 : move.oneTimeCost(),
        move != null && move.benefitEligible(),
        inflation,
        calibration.ratio(),
        severance);
  }

  private MoveView moveView(
      UUID userId,
      Stay stay,
      Move move,
      long moveGross,
      long currentGross,
      ScenarioContextResponse context,
      ProjectionResult result,
      double inflation,
      Calibration calibration) {
    return new MoveView(
        result.stayCumulative(),
        result.moveCumulative(),
        result.crossoverMonth(),
        result.firstYear(),
        expectedRange(
            stay,
            move,
            moveGross,
            currentGross,
            context.performanceBonusRange(),
            inflation,
            calibration),
        stockLine(stay, move),
        result.equalizingRaisePercent(),
        scenarioService.feasibilityDetail(userId, move.grossSalary()));
  }

  /**
   * 기대층 첫해 차이 범위. 성과급·사이닝의 세후 증분(연봉 위에 얹었을 때 세금 증가분 제외)을 실질로 환산해 min = 이직min − 잔류max + 사이닝, max =
   * 이직max − 잔류min + 사이닝.
   */
  private Range expectedRange(
      Stay stay,
      Move move,
      long moveGross,
      long currentGross,
      BonusRange recorded,
      double inflation,
      Calibration calibration) {
    long stayBase = Math.round(currentGross * (1 + stay.raisePercent() / PERCENT));
    long stayMin = recorded != null ? recorded.min() : 0;
    long stayMax = recorded != null ? recorded.average() : orZero(stay.performanceBonusEstimate());

    double stayLow = realNetIncrement(stayBase, stayMin, MONTHS_PER_YEAR, inflation, calibration);
    double stayHigh = realNetIncrement(stayBase, stayMax, MONTHS_PER_YEAR, inflation, calibration);
    double moveHigh =
        realNetIncrement(
            moveGross,
            orZero(move.performanceBonusExpected()),
            MONTHS_PER_YEAR,
            inflation,
            calibration);
    double signing =
        realNetIncrement(moveGross, orZero(move.signingBonus()), 1, inflation, calibration);
    return new Range(Math.round(0 - stayHigh + signing), Math.round(moveHigh - stayLow + signing));
  }

  private double realNetIncrement(
      long baseGross, long extra, int month, double inflation, Calibration calibration) {
    double net = estimator.annualNet(baseGross + extra) - estimator.annualNet(baseGross);
    return net * calibration.ratio() / Math.pow(1 + inflation, (double) month / MONTHS_PER_YEAR);
  }

  private static StockLine stockLine(Stay stay, Move move) {
    if (stay.stockVesting12m() == null
        && stay.stockUnvested() == null
        && move.stockVesting12m() == null) {
      return null;
    }
    return new StockLine(stay.stockVesting12m(), stay.stockUnvested(), move.stockVesting12m());
  }

  private List<Assumption> assumptions(
      InflationBasis basis,
      Calibration calibration,
      SeveranceView severance,
      Move move,
      Stay stay) {
    List<Assumption> list = new ArrayList<>();
    list.add(new Assumption("TAX_HOUSEHOLD", "본인 1인, 부양가족 없음 기준으로 세금을 계산했습니다."));
    list.add(new Assumption("TAX_SCOPE", "비과세 수당과 연말정산 공제는 반영하지 않았습니다."));
    list.add(
        new Assumption("TAX_RATES_AS_OF", "%s년 세율·보험요율 기준입니다.".formatted(estimator.ratesAsOf())));
    list.add(calibrationAssumption(calibration));
    list.add(
        new Assumption(
            "INFLATION_SOURCE",
            "물가는 %s(%s 발표) 전망 %s%% 기준입니다."
                .formatted(
                    basis.source(), basis.asOf(), basis.governmentPercent().toPlainString())));
    if (basis.personalPercent() == null) {
      list.add(
          new Assumption(
              InflationAssumptions.PERSONAL_INFLATION_UNAVAILABLE,
              "개인물가를 계산할 수 없어 한국은행 전망을 사용했습니다."));
    }
    list.add(new Assumption("HORIZON_HOLD", "4년 동안 인상률과 물가가 지금 입력한 값으로 유지된다고 가정했습니다."));
    boolean severanceOk = SeveranceOutlookService.STATUS_OK.equals(severance.status());
    if (severanceOk) {
      list.addAll(severanceAssumptions(severance));
    }
    if (move != null) {
      // 퇴직금을 계산하지 못하면 비교에도 넣지 않으므로 옛 안내가 여전히 맞다
      list.add(
          new Assumption(
              "NEW_JOB_SEVERANCE",
              severanceOk ? "퇴직금은 양쪽 모두 그 달에 나간다고 보고 넣었습니다" : "새 회사를 1년 안에 나가면 그곳 퇴직금도 없습니다"));
      if (move.signingBonus() != null && move.signingBonus() > 0) {
        list.add(new Assumption("SIGNING_CLAWBACK", "조기 퇴사 시 사이닝 보너스를 돌려줘야 할 수 있습니다"));
      }
    }
    if (hasStock(stay, move)) {
      list.add(new Assumption("STOCK_VALUE", "주식은 현재 평가액 그대로이며 세금은 반영하지 않았습니다."));
    }
    return list;
  }

  private List<Assumption> severanceAssumptions(SeveranceView severance) {
    List<Assumption> list = new ArrayList<>();
    list.add(new Assumption("SEVERANCE_BASIS", "퇴직금은 주 15시간 이상 정규 근무를 가정했고 성과급·연차수당은 넣지 않았습니다."));
    list.add(
        new Assumption(
            "SEVERANCE_IRP",
            "퇴직금은 IRP 계좌로 들어가며, 일시금으로 찾을 때 기준입니다. 55세 이후 연금으로 받으면 퇴직소득세가 30~40% 줄어듭니다."));
    // 퇴직소득세도 TakeHomeEstimator 와 같은 소득세법 기본세율 표를 쓰므로 기준 연도가 같다
    list.add(
        new Assumption(
            "SEVERANCE_TAX_AS_OF", "퇴직소득세는 %s년 세법 기준입니다.".formatted(estimator.ratesAsOf())));
    if (RetirementPensionType.UNKNOWN.name().equals(severance.pensionType())) {
      list.add(new Assumption("SEVERANCE_ASSUMED_DB", "퇴직금·DB형으로 계산했습니다."));
    }
    return list;
  }

  private static Assumption calibrationAssumption(Calibration calibration) {
    if (calibration.applied()) {
      return new Assumption(
          "NET_CALIBRATED", "입력한 실수령액에 맞춰 세후 추정을 %.2f배로 보정했습니다.".formatted(calibration.ratio()));
    }
    if (TakeHomeEstimator.NET_CALIBRATION_SKIPPED.equals(calibration.reasonCode())) {
      return new Assumption(calibration.reasonCode(), "입력한 실수령액이 추정과 크게 달라 보정하지 않았습니다.");
    }
    return new Assumption(calibration.reasonCode(), "실수령액이 없어 세후를 공식으로만 추정했습니다.");
  }

  private static boolean hasStock(Stay stay, Move move) {
    return stay.stockVesting12m() != null
        || stay.stockUnvested() != null
        || (move != null && move.stockVesting12m() != null);
  }

  private static List<String> unreflected(Stay stay, Move move, ScenarioContextResponse context) {
    List<String> codes = new ArrayList<>();
    if (isEmpty(stay.performanceBonusEstimate()) && context.performanceBonusRange() == null) {
      codes.add("PERFORMANCE_BONUS");
    }
    if (isEmpty(stay.stockVesting12m()) && isEmpty(stay.stockUnvested())) {
      codes.add("STOCK");
    }
    if (context.hireDate() == null) {
      codes.add("HIRE_DATE");
    }
    if (move != null) {
      if (isEmpty(move.performanceBonusExpected())) {
        codes.add("MOVE_PERFORMANCE_BONUS");
      }
      if (isEmpty(move.signingBonus())) {
        codes.add("SIGNING_BONUS");
      }
      if (isEmpty(move.stockVesting12m())) {
        codes.add("MOVE_STOCK");
      }
    }
    return codes;
  }

  private static boolean isEmpty(Long value) {
    return value == null || value == 0;
  }

  private static long orZero(Long value) {
    return value == null ? 0 : value;
  }
}
