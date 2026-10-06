package com.carix.pipeline.explore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관리자 수집 데이터 열람 쿼리. 읽기 전용이고 사용자 소득은 고르지 않는다. H2(테스트)와 PostgreSQL(운영) 공통 문법만 쓴다. */
@Repository
@RequiredArgsConstructor
public class ExploreQueries {

  private final JdbcTemplate jdbc;

  public Map<String, Integer> sampledCounts(String from, String to) {
    Map<String, Integer> out = new HashMap<>();
    jdbc.query(
        "SELECT sampled_month, COUNT(*) FROM core.job_posting"
            + " WHERE source = 'JOBKOREA' AND sampled_month BETWEEN ? AND ? GROUP BY sampled_month",
        rs -> {
          out.put(rs.getString(1), rs.getInt(2));
        },
        from,
        to);
    return out;
  }

  public Map<String, Map<String, Integer>> crawledCounts(String from, String to) {
    Map<String, Map<String, Integer>> out = new HashMap<>();
    jdbc.query(
        "SELECT first_seen_month, source, COUNT(*) FROM core.job_posting"
            + " WHERE sampled_month IS NULL AND first_seen_month BETWEEN ? AND ?"
            + " GROUP BY first_seen_month, source",
        rs -> {
          out.computeIfAbsent(rs.getString(1), k -> new HashMap<>())
              .put(rs.getString(2), rs.getInt(3));
        },
        from,
        to);
    return out;
  }

  public Map<String, Map<String, BigDecimal>> cpiValues(String from, String to) {
    Map<String, Map<String, BigDecimal>> out = new TreeMap<>();
    jdbc.query(
        "SELECT base_year_month, category, cpi_value FROM core.cpi_observation WHERE base_year_month BETWEEN ? AND ?",
        rs -> {
          out.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
              .put(rs.getString(2), rs.getBigDecimal(3));
        },
        from,
        to);
    return out;
  }

  /** {첫 달, 마지막 달}. CPI 행이 없으면 둘 다 null. */
  public String[] cpiBounds() {
    return jdbc.queryForObject(
        "SELECT MIN(base_year_month), MAX(base_year_month) FROM core.cpi_observation",
        (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
  }

  public String latestWagePeriod() {
    return jdbc.queryForObject(
        "SELECT MAX(base_period) FROM core.wage_stat_observation", String.class);
  }

  /** 임금통계 한 칸의 정의·출처별 최신 회차 행(전국). */
  public record WageRow(
      String jobFamily,
      String experienceBracket,
      String wageDefinition,
      String source,
      String statVintage,
      long wageMedian) {}

  /** 기준 연도의 전국 행 중 (직무, 경력, 정의, 출처)마다 가장 늦은 회차만. */
  public List<WageRow> wageCells(String basePeriod) {
    return jdbc.query(
        "SELECT w.job_family, w.experience_bracket, w.wage_definition, w.source, w.stat_vintage,"
            + " w.wage_median FROM core.wage_stat_observation w"
            + " WHERE w.base_period = ? AND w.region = 'ALL' AND w.stat_vintage = ("
            + " SELECT MAX(v.stat_vintage) FROM core.wage_stat_observation v"
            + " WHERE v.base_period = w.base_period AND v.region = w.region"
            + " AND v.job_family = w.job_family AND v.experience_bracket = w.experience_bracket"
            + " AND v.wage_definition = w.wage_definition AND v.source = w.source)",
        (rs, i) ->
            new WageRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getLong(6)),
        basePeriod);
  }

  /** 기준선 후보 한 행(전 연도, {@code 전국}·{@code ALL}). */
  public record BaselineRow(
      String jobFamily,
      String experienceBracket,
      String region,
      String basePeriod,
      String wageDefinition,
      String source,
      String statVintage,
      long wageMedian) {}

  /** 엔진 기준선 후보. 고르는 순서는 서비스가 core-api 와 같게 맞춘다. */
  public List<BaselineRow> wageBaselineCandidates() {
    return jdbc.query(
        "SELECT job_family, experience_bracket, region, base_period, wage_definition, source,"
            + " stat_vintage, wage_median FROM core.wage_stat_observation"
            + " WHERE region IN ('전국', 'ALL')",
        (rs, i) ->
            new BaselineRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getLong(8)));
  }

  /** 공고 한 행. */
  public record PostingRow(
      UUID id,
      String source,
      String externalId,
      String title,
      String companyName,
      String jobFamily,
      Integer experienceMin,
      Integer experienceMax,
      String employmentType,
      String region,
      LocalDate postedStartDate,
      LocalDate postedEndDate,
      String firstSeenMonth,
      String sampledMonth,
      Long salaryMin,
      Long salaryMax,
      Long salaryBandMin,
      Long salaryBandMax,
      String salaryDisclosure) {}

  private static final String POSTING_COLUMNS =
      "p.id, p.source, p.external_id, p.title, p.company_name, p.job_family, p.experience_min,"
          + " p.experience_max, p.employment_type, p.region, p.posted_start_date, p.posted_end_date,"
          + " p.first_seen_month, p.sampled_month, p.salary_min, p.salary_max,"
          + " p.salary_band_min, p.salary_band_max, p.salary_disclosure";

  public long countPostings(PostingFilter f) {
    List<Object> args = new ArrayList<>();
    String where = where(f, args);
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM core.job_posting p" + where, Long.class, args.toArray());
    return n == null ? 0 : n;
  }

  public List<PostingRow> findPostings(PostingFilter f) {
    List<Object> args = new ArrayList<>();
    String where = where(f, args);
    args.add(f.size());
    args.add((long) f.page() * f.size());
    return jdbc.query(
        "SELECT "
            + POSTING_COLUMNS
            + " FROM core.job_posting p"
            + where
            + " ORDER BY p.source, p.external_id LIMIT ? OFFSET ?",
        (rs, i) ->
            new PostingRow(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                (Integer) rs.getObject(7),
                (Integer) rs.getObject(8),
                rs.getString(9),
                rs.getString(10),
                toLocalDate(rs.getDate(11)),
                toLocalDate(rs.getDate(12)),
                rs.getString(13),
                rs.getString(14),
                (Long) rs.getObject(15),
                (Long) rs.getObject(16),
                (Long) rs.getObject(17),
                (Long) rs.getObject(18),
                rs.getString(19)),
        args.toArray());
  }

  public Map<UUID, List<String>> skillsByPosting(List<UUID> ids) {
    Map<UUID, List<String>> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
    jdbc.query(
        "SELECT jps.job_posting_id, s.standard_name FROM core.job_posting_skill jps"
            + " JOIN core.skill s ON s.id = jps.skill_id WHERE jps.job_posting_id IN ("
            + placeholders
            + ")"
            + " ORDER BY s.standard_name",
        rs -> {
          out.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
              .add(rs.getString(2));
        },
        ids.toArray());
    return out;
  }

  private static String where(PostingFilter f, List<Object> args) {
    StringBuilder sb = new StringBuilder(" WHERE ");
    if (Boolean.TRUE.equals(f.sampled())) {
      sb.append("p.sampled_month = ?");
      args.add(f.month());
    } else if (Boolean.FALSE.equals(f.sampled())) {
      sb.append("p.sampled_month IS NULL AND p.first_seen_month = ?");
      args.add(f.month());
    } else {
      sb.append("(p.sampled_month = ? OR (p.sampled_month IS NULL AND p.first_seen_month = ?))");
      args.add(f.month());
      args.add(f.month());
    }
    if (f.source() != null) {
      sb.append(" AND p.source = ?");
      args.add(f.source());
    }
    if (f.family() != null) {
      if ("OTHER".equals(f.family())) {
        sb.append(" AND (p.job_family IS NULL OR p.job_family NOT IN ('DEVELOPER','DATA','AI'))");
      } else {
        sb.append(" AND p.job_family = ?");
        args.add(f.family());
      }
    }
    if (f.company() != null) {
      sb.append(" AND LOWER(p.company_name) LIKE ? ESCAPE '\\'");
      args.add("%" + escapeLike(f.company().toLowerCase()) + "%");
    }
    if (f.disclosure() != null) {
      sb.append(" AND p.salary_disclosure = ?");
      args.add(f.disclosure());
    }
    if (f.skill() != null) {
      sb.append(
          " AND EXISTS (SELECT 1 FROM core.job_posting_skill jps JOIN core.skill s ON s.id = jps.skill_id"
              + " WHERE jps.job_posting_id = p.id AND s.standard_name = ?)");
      args.add(f.skill());
    }
    return sb.toString();
  }

  static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static LocalDate toLocalDate(java.sql.Date d) {
    return d == null ? null : d.toLocalDate();
  }
}
