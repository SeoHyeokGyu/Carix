package com.carix.pipeline.health;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.health.DataHealthSnapshot.SampleSeries;
import com.carix.pipeline.health.DataHealthSnapshot.UserSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 데이터를 읽는다. 모두 읽기 전용이다. 표본 상한 건수를 집계 서비스와 같은 규칙으로 세려고 공고 엔티티를 {@code findAll()} 로 통째로 올리므로 공개 공고
 * 필드(공시 연봉 범위 포함)가 메모리에 실리지만, 건수만 쓰고 금액은 반환하거나 노출하지 않는다. {@code index_snapshot} 은 core-api 소관이라
 * 엔티티를 두지 않고, rationale 에서도 지수 상태와 규칙 문구 조각만 읽는다.
 */
@Component
@RequiredArgsConstructor
public class JdbcDataHealthReader implements DataHealthReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateService marketAggregateService;

  @Override
  @Transactional(readOnly = true)
  public DataHealthSnapshot read() {
    List<JobPosting> all = jobPostingRepository.findAll();
    SampleSeries jobKorea = sampleSeries("JOBKOREA", all);
    SampleSeries wanted = sampleSeries("WANTED", all);

    return new DataHealthSnapshot(
        strings("SELECT DISTINCT base_year_month FROM core.cpi_observation"),
        jobKorea,
        wanted,
        crawlLatest(),
        snapshots(),
        strings("SELECT user_id FROM core.career_profile"));
  }

  SampleSeries sampleSeries(String source, List<JobPosting> all) {
    Map<String, Integer> sampled =
        monthCounts(
            "SELECT sampled_month, COUNT(*) FROM core.job_posting WHERE sampled_month IS NOT NULL"
                + " AND source = ? GROUP BY sampled_month",
            source);
    Map<String, Integer> capped = new LinkedHashMap<>();
    sampled
        .keySet()
        .forEach(
            m ->
                capped.put(
                    m, marketAggregateService.sampledPostingsForMonth(all, m, source).size()));
    Map<String, Integer> aggregated =
        monthCounts(
            "SELECT base_year_month, MAX(posting_count) FROM core.market_aggregate WHERE source = ?"
                + " AND job_family = 'ALL' AND experience_bracket = 'ALL' AND region = 'ALL'"
                + " AND employment_type = 'ALL' AND company_size = 'ALL' AND skill_id IS NULL GROUP BY base_year_month",
            source);
    return new SampleSeries(sampled, capped, aggregated);
  }

  private Map<String, Integer> monthCounts(String sql, Object... args) {
    Map<String, Integer> out = new HashMap<>();
    jdbc.query(
        sql,
        rs -> {
          out.put(rs.getString(1), rs.getInt(2));
        },
        args);
    return out;
  }

  private Set<String> strings(String sql) {
    Set<String> out = new HashSet<>();
    jdbc.query(
        sql,
        rs -> {
          out.add(rs.getString(1));
        });
    return out;
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
        "SELECT s.user_id, s.base_year_month, s.rule_version, s.decision_signal, CAST(s.rationale->'indexStatuses' AS VARCHAR),"
            + " CAST(s.rationale->'rulesApplied' AS VARCHAR)"
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
                  rs.getString(6),
                  MAPPER));
        });
    return out;
  }

  /**
   * rationale 의 {@code indexStatuses}·{@code rulesApplied} 조각을 해석한다. 조각이 깨졌거나 비어 있으면 상태는 null, 규칙은
   * 빈 목록이다.
   */
  static UserSnapshot parseSnapshot(
      String userId,
      String month,
      String version,
      String signal,
      String statusesJson,
      String rulesJson,
      ObjectMapper mapper) {
    JsonNode statuses = readOrNull(statusesJson, mapper);
    JsonNode rulesNode = readOrNull(rulesJson, mapper);
    String ppi = textOrNull(statuses, "ppi");
    String mvi = textOrNull(statuses, "mvi");
    List<String> rules = new ArrayList<>();
    if (rulesNode != null && rulesNode.isArray()) {
      rulesNode.forEach(n -> rules.add(n.asText()));
    }
    return new UserSnapshot(userId, month, version, signal, ppi, mvi, rules);
  }

  private static JsonNode readOrNull(String json, ObjectMapper mapper) {
    if (json == null) {
      return null;
    }
    try {
      return mapper.readTree(json);
    } catch (Exception e) {
      // 깨진 조각은 없음으로 둔다 — 요약이 UNKNOWN_RATIONALE 로 센다.
      return null;
    }
  }

  private static String textOrNull(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode v = node.path(field);
    return v.isTextual() ? v.asText() : null;
  }
}
