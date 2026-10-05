package com.carix.pipeline.explore;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 관리자 수집 데이터 열람 쿼리. 읽기 전용이고 금액 컬럼을 고르지 않는다. H2(테스트)와 PostgreSQL(운영) 공통 문법만 쓴다. */
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
}
