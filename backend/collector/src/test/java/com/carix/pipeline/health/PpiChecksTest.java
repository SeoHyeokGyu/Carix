package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.pipeline.dto.DataHealthReport;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PpiChecksTest {

  private static final YearMonth NOW = YearMonth.of(2026, 10);

  private static Map<String, Integer> fullMonths(String from, String to) {
    Map<String, Integer> m = new LinkedHashMap<>();
    for (YearMonth y = YearMonth.parse(from.substring(0, 4) + "-" + from.substring(4));
        !y.isAfter(YearMonth.parse(to.substring(0, 4) + "-" + to.substring(4)));
        y = y.plusMonths(1)) {
      m.put(String.format("%d%02d", y.getYear(), y.getMonthValue()), 12);
    }
    return m;
  }

  private static DataHealthReport.Check check(
      Map<String, Integer> cpi, String id, List<UserSnapshot> snaps) {
    var data = new DataHealthSnapshot(cpi, null, null, null, null, null, null, snaps, null);
    return PpiChecks.group(data, NOW, HealthThresholds.defaults()).checks().stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("CPI 지연 1개월 정상, 2개월 주의, 3개월 문제")
  void cpiLatest_lagBoundaries() {
    assertThat(check(fullMonths("202001", "202609"), "CPI_LATEST", List.of()).verdict())
        .isEqualTo(HealthVerdict.OK);
    assertThat(check(fullMonths("202001", "202608"), "CPI_LATEST", List.of()).verdict())
        .isEqualTo(HealthVerdict.WARN);
    assertThat(check(fullMonths("202001", "202607"), "CPI_LATEST", List.of()).verdict())
        .isEqualTo(HealthVerdict.ERROR);
  }

  @Test
  @DisplayName("업데이트 지연 사용자를 영향으로 센다")
  void cpiLatest_impactUsers() {
    var snaps =
        List.of(
            new UserSnapshot("u1", "202610", "1.3.0", "STAY", "UPDATE_DELAYED", "OK", List.of()));
    assertThat(check(fullMonths("202001", "202607"), "CPI_LATEST", snaps).impactUsers())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("중간에 12개 품목이 다 있지 않은 달이 있으면 문제")
  void cpiGap_partialMonth() {
    Map<String, Integer> cpi = fullMonths("202001", "202609");
    cpi.put("202403", 11);
    var c = check(cpi, "CPI_GAP", List.of());
    assertThat(c.verdict()).isEqualTo(HealthVerdict.ERROR);
    assertThat(c.evidence()).containsEntry("incompleteMonths", List.of("202403"));
  }

  @Test
  @DisplayName("중간에 통째로 빠진 달도 결측이다")
  void cpiGap_missingMonth() {
    Map<String, Integer> cpi = fullMonths("202001", "202609");
    cpi.remove("202405");
    assertThat(check(cpi, "CPI_GAP", List.of()).verdict()).isEqualTo(HealthVerdict.ERROR);
  }

  @Test
  @DisplayName("CPI 가 하나도 없으면 판정 불가")
  void cpi_unknownWhenEmpty() {
    assertThat(check(Map.of(), "CPI_LATEST", List.of()).verdict()).isEqualTo(HealthVerdict.UNKNOWN);
    assertThat(check(Map.of(), "CPI_GAP", List.of()).verdict()).isEqualTo(HealthVerdict.UNKNOWN);
  }
}
