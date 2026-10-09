package com.carix.snapshot.repository;

import com.carix.snapshot.RuleVersion;
import com.carix.snapshot.domain.IndexSnapshot;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 월의 여러 스냅샷 행에서 유효한 하나를 고른다 (기술 명세 §1.3).
 *
 * <p>순서가 규정돼 있다 — 먼저 최신 {@code data_version}(사전순), 그 안에서 최신 {@code rule_version}. 규칙 버전 비교는 사전순이
 * 아니라 숫자 세그먼트 비교다({@link RuleVersion}). 단일 {@code ORDER BY} 문자열로 정렬하면 {@code 1.9.0} 이 {@code
 * 1.10.0} 보다 크다고 나온다.
 */
public final class IndexSnapshotSelector {

  private IndexSnapshotSelector() {}

  public static Optional<IndexSnapshot> latest(List<IndexSnapshot> sameMonthSnapshots) {
    return sameMonthSnapshots.stream()
        .max(
            Comparator.comparing(IndexSnapshot::getDataVersion)
                .thenComparing(snapshot -> RuleVersion.of(snapshot.getRuleVersion())));
  }

  /**
   * 기준월의 당시 판정 — 그 달이 끝나기 전(다음 달 1일 00:00 UTC 전)에 만든 행 중 {@link #latest} 와 같은 규칙으로 고른 한 행. 그 달 끝에
   * 대시보드가 보여 줬을 판정이다. {@code created_at} 이 없는 행은 후보가 아니다.
   */
  public static Optional<IndexSnapshot> recordedAtMonthEnd(
      List<IndexSnapshot> sameMonthSnapshots, String baseYearMonth) {
    Instant monthEnd =
        YearMonth.parse(baseYearMonth, DateTimeFormatter.ofPattern("yyyyMM"))
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
    return latest(
        sameMonthSnapshots.stream()
            .filter(
                s -> s.getCreatedAt() != null && s.getCreatedAt().toInstant().isBefore(monthEnd))
            .toList());
  }
}
