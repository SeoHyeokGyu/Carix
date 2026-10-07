package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 관리자 데이터 상태 점검. 읽기 → 그룹별 판정 → 요약. 한 그룹이 실패해도 그 그룹만 판정 불가로 내고 응답은 낸다. */
@Slf4j
@Service
public class DataHealthService {

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final DataHealthReader reader;
  private final EngineVersionClient engineVersionClient;
  private final HealthThresholds thresholds;
  private final Clock clock;

  @Autowired
  public DataHealthService(
      DataHealthReader reader,
      EngineVersionClient engineVersionClient,
      HealthThresholds thresholds) {
    this(reader, engineVersionClient, thresholds, Clock.system(SEOUL));
  }

  DataHealthService(
      DataHealthReader reader,
      EngineVersionClient engineVersionClient,
      HealthThresholds thresholds,
      Clock clock) {
    this.reader = reader;
    this.engineVersionClient = engineVersionClient;
    this.thresholds = thresholds;
    this.clock = clock;
  }

  static YearMonth referenceMonth(Clock clock) {
    return YearMonth.from(clock.instant().atZone(SEOUL));
  }

  public DataHealthReport evaluate() {
    YearMonth reference = referenceMonth(clock);
    YearMonth previous = reference.minusMonths(1);
    String engine = engineVersionClient.ruleVersion().orElse(null);

    DataHealthSnapshot data;
    try {
      data = reader.read(previous);
    } catch (Exception e) {
      log.error("[데이터 상태] 데이터 읽기 실패", e);
      data = null;
    }
    DataHealthSnapshot read = data;
    List<DataHealthReport.Group> groups = new ArrayList<>();
    groups.add(
        safe(
            "PPI",
            "구매력 지수",
            "내 실질 구매력은?",
            read,
            () -> PpiChecks.group(read, reference, thresholds)));
    groups.add(
        safe(
            "MVI",
            "시장가치 지수",
            "내 스킬 가치는?",
            read,
            () -> MviChecks.group(read, previous, thresholds)));
    groups.add(
        safe(
            "DECISION",
            "결정 신호",
            "지금 무엇을 해야 하나?",
            read,
            () -> DecisionChecks.group(read, reference, engine)));
    groups.add(
        safe(
            "SUPPORT",
            "보조 화면",
            "연봉 분포·개방창",
            read,
            () -> SupportChecks.group(read, clock.instant(), thresholds)));
    groups.add(
        safe(
            WantedChecks.GROUP_ID,
            "원티드(도입 중)",
            "원티드 표본이 쌓이고 있나?",
            read,
            () -> WantedChecks.group(read, previous, thresholds)));

    DataHealthSnapshot forSummary =
        read != null
            ? read
            : new DataHealthSnapshot(null, null, null, null, null, null, null, null, null, null);
    String previousMonth = YM.format(previous);
    List<DataHealthReport.Group> linked =
        groups.stream()
            .map(
                g ->
                    new DataHealthReport.Group(
                        g.id(),
                        g.title(),
                        g.question(),
                        g.checks().stream()
                            .map(
                                c ->
                                    new DataHealthReport.Check(
                                        c.id(),
                                        c.title(),
                                        c.verdict(),
                                        c.message(),
                                        c.impactUsers(),
                                        c.action(),
                                        c.actionLink(),
                                        c.evidence(),
                                        drillDown(c.id(), previousMonth)))
                            .toList()))
            .toList();
    return new DataHealthReport(
        OffsetDateTime.now(clock.withZone(SEOUL)),
        YM.format(reference),
        previousMonth,
        engine,
        SummaryCalculator.summarize(
            forSummary,
            linked.stream().filter(g -> !WantedChecks.GROUP_ID.equals(g.id())).toList()),
        linked);
  }

  private static final int MONTHLY_DRILL_MONTHS = 36;
  private static final int CPI_DRILL_MONTHS = 120;

  /** 직전 달 포함 {@code months} 개월 범위를 붙인 링크. */
  private static String rangedLink(String tab, String previousMonth, int months) {
    YearMonth to = YearMonth.parse(previousMonth, YM);
    return "/admin/data?tab="
        + tab
        + "&from="
        + YM.format(to.minusMonths(months - 1L))
        + "&to="
        + previousMonth;
  }

  /** 점검 → 관리자 「수집 데이터」 원본 화면. 판정과 무관하게 채운다(설계 §7). */
  static String drillDown(String checkId, String previousMonth) {
    return switch (checkId) {
      case "SAMPLE_LAST_MONTH" ->
          "/admin/data?tab=postings&month=" + previousMonth + "&sampled=true";
      case "SAMPLE_GAPS", "AGGREGATE_STALE", "PEER_SAMPLE_BY_FAMILY", "CRAWL_FRESHNESS" ->
          rangedLink("monthly", previousMonth, MONTHLY_DRILL_MONTHS);
      case "CPI_LATEST", "CPI_GAP" -> rangedLink("cpi", previousMonth, CPI_DRILL_MONTHS);
      case "WANTED_SAMPLE_LAST_MONTH" ->
          "/admin/data?tab=postings&month=" + previousMonth + "&sampled=true&source=WANTED";
      case "WANTED_SAMPLE_GAPS", "WANTED_AGGREGATE_STALE" ->
          rangedLink("monthly", previousMonth, MONTHLY_DRILL_MONTHS) + "&sampleSource=WANTED";
      case "WAGE_BASELINE" -> "/admin/data?tab=wage";
      default -> null;
    };
  }

  private static DataHealthReport.Group safe(
      String id,
      String title,
      String question,
      DataHealthSnapshot data,
      Supplier<DataHealthReport.Group> build) {
    if (data == null) {
      return failed(id, title, question, "데이터를 읽지 못했습니다");
    }
    try {
      return build.get();
    } catch (Exception e) {
      log.error("[데이터 상태] {} 점검 실패", id, e);
      return failed(id, title, question, "점검 중 오류: " + e.getMessage());
    }
  }

  private static DataHealthReport.Group failed(
      String id, String title, String question, String message) {
    return new DataHealthReport.Group(
        id,
        title,
        question,
        List.of(
            new DataHealthReport.Check(
                id + "_FAILED",
                title,
                HealthVerdict.UNKNOWN,
                message,
                null,
                null,
                null,
                Map.of())));
  }
}
