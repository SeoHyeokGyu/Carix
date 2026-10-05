package com.carix.pipeline.health;

import com.carix.pipeline.dto.DataHealthReport;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ④ 보조 화면 — 연봉 분포·개방창 지수는 정기 수집 공고를 직접 읽는다. */
public final class SupportChecks {

  private SupportChecks() {}

  public static DataHealthReport.Group group(
      DataHealthSnapshot data, Instant now, HealthThresholds t) {
    return new DataHealthReport.Group(
        "SUPPORT", "보조 화면", "연봉 분포·개방창", List.of(crawl(data, now, t)));
  }

  private static DataHealthReport.Check crawl(
      DataHealthSnapshot data, Instant now, HealthThresholds t) {
    if (data.crawlLatestBySource().isEmpty()) {
      return PpiChecks.unknown("CRAWL_FRESHNESS", "정기 수집 최신성", "정기 수집 기록이 없습니다");
    }
    Map<String, Object> days = new LinkedHashMap<>();
    long worst = 0;
    for (Map.Entry<String, Instant> e : data.crawlLatestBySource().entrySet()) {
      long d = Duration.between(e.getValue(), now).toDays();
      days.put(e.getKey(), d);
      worst = Math.max(worst, d);
    }
    HealthVerdict v = worst > t.crawlStaleDays() ? HealthVerdict.WARN : HealthVerdict.OK;
    return new DataHealthReport.Check(
        "CRAWL_FRESHNESS",
        "정기 수집 최신성",
        v,
        "가장 오래된 원천 " + worst + "일 전",
        null,
        v == HealthVerdict.OK ? null : "정기 수집 실행",
        v == HealthVerdict.OK ? null : "/admin/collector",
        Map.of("daysSinceBySource", days));
  }
}
