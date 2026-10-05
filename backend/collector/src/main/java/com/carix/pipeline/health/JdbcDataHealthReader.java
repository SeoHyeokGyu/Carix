package com.carix.pipeline.health;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import com.carix.pipeline.health.DataHealthSnapshot.WageCell;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 데이터를 읽는다. 모두 읽기 전용이고 금액 컬럼을 읽지 않는다. {@code index_snapshot} 은 core-api 소관이라 엔티티를 두지 않고 필요한 컬럼만
 * 읽는다.
 */
@Component
@RequiredArgsConstructor
public class JdbcDataHealthReader implements DataHealthReader {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateService marketAggregateService;

  @Override
  @Transactional(readOnly = true)
  public DataHealthSnapshot read(YearMonth previousMonth) {
    Map<String, Integer> sampled =
        monthCounts(
            "SELECT sampled_month, COUNT(*) FROM core.job_posting WHERE sampled_month IS NOT NULL GROUP BY sampled_month");
    List<JobPosting> all = jobPostingRepository.findAll();
    Map<String, Integer> capped = new LinkedHashMap<>();
    sampled
        .keySet()
        .forEach(m -> capped.put(m, marketAggregateService.sampledPostingsForMonth(all, m).size()));

    return new DataHealthSnapshot(
        monthCounts(
            "SELECT base_year_month, COUNT(DISTINCT category) FROM core.cpi_observation GROUP BY base_year_month"),
        sampled,
        capped,
        monthCounts(
            "SELECT base_year_month, MAX(posting_count) FROM core.market_aggregate WHERE source = 'JOBKOREA'"
                + " AND job_family = 'ALL' AND experience_bracket = 'ALL' AND region = 'ALL'"
                + " AND employment_type = 'ALL' AND company_size = 'ALL' AND skill_id IS NULL GROUP BY base_year_month"),
        familyTotals(YM.format(previousMonth)),
        wageCells(),
        crawlLatest(),
        snapshots(),
        families());
  }

  private Map<String, Integer> monthCounts(String sql) {
    Map<String, Integer> out = new HashMap<>();
    jdbc.query(
        sql,
        rs -> {
          out.put(rs.getString(1), rs.getInt(2));
        });
    return out;
  }

  private Map<String, Integer> familyTotals(String month) {
    Map<String, Integer> out = new HashMap<>();
    jdbc.query(
        "SELECT job_family, MAX(posting_count) FROM core.market_aggregate WHERE source = 'JOBKOREA'"
            + " AND base_year_month = ? AND experience_bracket = 'ALL' AND region = 'ALL'"
            + " AND employment_type = 'ALL' AND company_size = 'ALL' AND skill_id IS NULL GROUP BY job_family",
        rs -> {
          out.put(rs.getString(1), rs.getInt(2));
        },
        month);
    return out;
  }

  private List<WageCell> wageCells() {
    return jdbc.query(
        "SELECT DISTINCT base_period, job_family, experience_bracket, source FROM core.wage_stat_observation",
        (rs, i) ->
            new WageCell(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
  }

  private Map<String, Instant> crawlLatest() {
    Map<String, Instant> out = new HashMap<>();
    jdbc.query(
        "SELECT source, MAX(collected_at) FROM core.job_posting WHERE sampled_month IS NULL GROUP BY source",
        rs -> {
          Timestamp t = rs.getTimestamp(2);
          if (t != null) {
            out.put(rs.getString(1), t.toInstant());
          }
        });
    return out;
  }

  private List<UserSnapshot> snapshots() {
    List<UserSnapshot> out = new ArrayList<>();
    jdbc.query(
        "SELECT s.user_id, s.base_year_month, s.rule_version, s.decision_signal, CAST(s.rationale AS VARCHAR)"
            + " FROM core.index_snapshot s"
            + " WHERE NOT EXISTS (SELECT 1 FROM core.index_snapshot t WHERE t.user_id = s.user_id"
            + "   AND (t.base_year_month > s.base_year_month"
            + "     OR (t.base_year_month = s.base_year_month AND t.created_at > s.created_at)))",
        rs -> {
          out.add(
              parseSnapshot(
                  rs.getString(1),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getString(5),
                  MAPPER));
        });
    return out;
  }

  private Map<String, String> families() {
    Map<String, String> out = new HashMap<>();
    jdbc.query(
        "SELECT user_id, job_family FROM core.career_profile",
        rs -> {
          out.put(rs.getString(1), rs.getString(2));
        });
    return out;
  }

  /** rationale 에서 지수 상태와 규칙 문구만 꺼낸다. 깨졌거나 비어 있으면 상태 null. */
  static UserSnapshot parseSnapshot(
      String userId,
      String month,
      String version,
      String signal,
      String rationaleJson,
      ObjectMapper mapper) {
    String ppi = null;
    String mvi = null;
    List<String> rules = new ArrayList<>();
    try {
      JsonNode root = rationaleJson == null ? null : mapper.readTree(rationaleJson);
      if (root != null) {
        JsonNode st = root.path("indexStatuses");
        ppi = st.path("ppi").isTextual() ? st.path("ppi").asText() : null;
        mvi = st.path("mvi").isTextual() ? st.path("mvi").asText() : null;
        root.path("rulesApplied").forEach(n -> rules.add(n.asText()));
      }
    } catch (Exception e) {
      // 깨진 rationale 은 상태 없음으로 둔다 — 요약이 UNKNOWN_RATIONALE 로 센다.
    }
    return new UserSnapshot(userId, month, version, signal, ppi, mvi, rules);
  }
}
