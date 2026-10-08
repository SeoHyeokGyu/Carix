package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 관리자 데이터 상태 점검. 읽기 → 점검 다섯 → 요약. 읽기나 판정이 실패하면 점검을 모두 판정 불가로 내고 응답은 낸다. */
@Slf4j
@Service
public class DataHealthService {

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int DEFAULT_DRILL_MONTHS = 17;
  private static final int MAX_DRILL_MONTHS = 35;
  private static final Map<String, String> CHECK_TITLES = checkTitles();
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

  private static Map<String, String> checkTitles() {
    Map<String, String> titles = new LinkedHashMap<>();
    titles.put("AGGREGATE_STALE", "재집계 누락");
    titles.put("RULE_VERSION", "규칙 버전");
    titles.put("SNAPSHOT_FRESHNESS", "스냅샷 최신성");
    titles.put("CPI_LATEST", "CPI 최신월");
    titles.put("CRAWL_FRESHNESS", "정기 수집 최신성");
    return titles;
  }

  /** 서버 시계(UTC)로 센 이번 달. 수집기가 관측월을 UTC 로 매기므로 같은 기준을 쓴다. */
  static YearMonth referenceMonth(Clock clock) {
    return YearMonth.from(clock.instant().atZone(ZoneOffset.UTC));
  }

  public DataHealthReport evaluate() {
    YearMonth reference = referenceMonth(clock);
    YearMonth previous = reference.minusMonths(1);
    String engine = engineVersionClient.ruleVersion().orElse(null);

    DataHealthSnapshot data;
    try {
      data = reader.read();
    } catch (Exception e) {
      log.error("[데이터 상태] 데이터 읽기 실패", e);
      data = null;
    }
    List<DataHealthReport.Check> checks = checks(data, reference, engine);
    DataHealthSnapshot forSummary =
        data != null ? data : new DataHealthSnapshot(null, null, null, null, null, null);
    return new DataHealthReport(
        OffsetDateTime.now(clock.withZone(SEOUL)),
        YM.format(reference),
        YM.format(previous),
        engine,
        SummaryCalculator.summarize(forSummary),
        checks.stream().map(c -> withDrillDown(c, reference)).toList());
  }

  private List<DataHealthReport.Check> checks(
      DataHealthSnapshot data, YearMonth reference, String engine) {
    if (data == null) {
      return allUnknown("데이터를 읽지 못했습니다");
    }
    try {
      return HealthChecks.all(data, reference, engine, thresholds, clock.instant());
    } catch (Exception e) {
      log.error("[데이터 상태] 점검 실패", e);
      return allUnknown("점검 중 오류: " + e.getMessage());
    }
  }

  private static List<DataHealthReport.Check> allUnknown(String message) {
    return CHECK_TITLES.entrySet().stream()
        .map(e -> HealthChecks.unknown(e.getKey(), e.getValue(), message))
        .toList();
  }

  private static DataHealthReport.Check withDrillDown(
      DataHealthReport.Check c, YearMonth reference) {
    return new DataHealthReport.Check(
        c.id(),
        c.title(),
        c.verdict(),
        c.message(),
        c.impactUsers(),
        c.action(),
        c.actionLink(),
        c.evidence(),
        drillDown(c, reference));
  }

  /** 점검 → 관리자 「수집 데이터」 원본 화면. 판정과 무관하게 채운다(설계 §2.2). */
  static String drillDown(DataHealthReport.Check c, YearMonth reference) {
    return switch (c.id()) {
      case "AGGREGATE_STALE" -> monthlyLink(c, reference);
      case "CPI_LATEST" -> "/admin/data?tab=cpi";
      case "CRAWL_FRESHNESS" -> crawlLink(c);
      default -> null;
    };
  }

  /** 불일치 달 중 가장 이른 달과 기준월-17개월 중 이른 쪽부터, 단 기준월-35개월보다 이르면 그 달부터. */
  private static String monthlyLink(DataHealthReport.Check c, YearMonth reference) {
    YearMonth from = reference.minusMonths(DEFAULT_DRILL_MONTHS);
    for (Object months : c.evidence().values()) {
      if (months instanceof List<?> list) {
        for (Object m : list) {
          YearMonth stale = parseOrNull(m);
          if (stale != null && stale.isBefore(from)) {
            from = stale;
          }
        }
      }
    }
    YearMonth floor = reference.minusMonths(MAX_DRILL_MONTHS);
    from = from.isBefore(floor) ? floor : from;
    return "/admin/data?tab=monthly&from=" + YM.format(from) + "&to=" + YM.format(reference);
  }

  private static YearMonth parseOrNull(Object month) {
    try {
      return YearMonth.parse(String.valueOf(month), YM);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** 가장 오래된 원천의 마지막 수집 달로 간다. 원천이 없으면 링크도 없다. */
  private static String crawlLink(DataHealthReport.Check c) {
    if (!(c.evidence().get("daysSinceBySource") instanceof Map<?, ?> days)
        || !(c.evidence().get("lastMonthBySource") instanceof Map<?, ?> months)) {
      return null;
    }
    return days.entrySet().stream()
        .filter(e -> e.getValue() instanceof Number && months.get(e.getKey()) != null)
        .max(Comparator.comparingLong(e -> ((Number) e.getValue()).longValue()))
        .map(
            e ->
                "/admin/data?tab=postings&month="
                    + months.get(e.getKey())
                    + "&sort=recent&source="
                    + e.getKey())
        .orElse(null);
  }
}
