package com.carix.pipeline.explore;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.dto.explore.MonthlyOverview;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 「수집 데이터」 열람. 기본 범위·검증·조립. 읽기 전용. */
@Service
public class DataExploreService {

  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  static final List<String> FAMILIES = List.of("DEVELOPER", "DATA", "AI", "OTHER");
  private static final int MONTHLY_DEFAULT = 13;
  private static final int MONTHLY_MAX = 36;

  private final ExploreQueries queries;
  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateService marketAggregateService;
  private final Clock clock;

  @Autowired
  public DataExploreService(
      ExploreQueries queries,
      JobPostingRepository jobPostingRepository,
      MarketAggregateService marketAggregateService) {
    this(queries, jobPostingRepository, marketAggregateService, Clock.system(SEOUL));
  }

  DataExploreService(
      ExploreQueries queries,
      JobPostingRepository jobPostingRepository,
      MarketAggregateService marketAggregateService,
      Clock clock) {
    this.queries = queries;
    this.jobPostingRepository = jobPostingRepository;
    this.marketAggregateService = marketAggregateService;
    this.clock = clock;
  }

  static YearMonth currentMonth(Clock clock) {
    return YearMonth.from(clock.instant().atZone(SEOUL));
  }

  @Transactional(readOnly = true)
  public MonthlyOverview monthly(String from, String to) {
    YearMonth[] range = range(from, to, MONTHLY_DEFAULT, MONTHLY_MAX);
    String f = YM.format(range[0]);
    String t = YM.format(range[1]);
    Map<String, Integer> sampled = queries.sampledCounts(f, t);
    Map<String, Map<String, Integer>> crawled = queries.crawledCounts(f, t);
    List<JobPosting> all = jobPostingRepository.findAll();

    List<MonthlyOverview.Month> months = new ArrayList<>();
    for (YearMonth m = range[0]; !m.isAfter(range[1]); m = m.plusMonths(1)) {
      String key = YM.format(m);
      List<JobPosting> capped = marketAggregateService.sampledPostingsForMonth(all, key);
      Map<String, Integer> byFamily = new LinkedHashMap<>();
      FAMILIES.forEach(fam -> byFamily.put(fam, 0));
      capped.forEach(p -> byFamily.computeIfPresent(familyOf(p), (k, v) -> v + 1));
      Map<String, Integer> crawledMonth = crawled.getOrDefault(key, Map.of());
      months.add(
          new MonthlyOverview.Month(
              key,
              sampled.getOrDefault(key, 0),
              capped.size(),
              crawledMonth.getOrDefault("JOBKOREA", 0),
              crawledMonth.getOrDefault("SARAMIN", 0),
              byFamily));
    }
    return new MonthlyOverview(f, t, months);
  }

  private static String familyOf(JobPosting p) {
    return FAMILIES.contains(p.getJobFamily()) ? p.getJobFamily() : "OTHER";
  }

  /** {@code [from, to]}. 둘 다 없으면 이번 달까지 {@code defaultMonths} 개월. */
  YearMonth[] range(String from, String to, int defaultMonths, int maxMonths) {
    YearMonth end = to == null ? currentMonth(clock) : parse(to);
    YearMonth start = from == null ? end.minusMonths(defaultMonths - 1L) : parse(from);
    if (start.isAfter(end)) {
      throw badRequest("from 이 to 보다 늦습니다");
    }
    if (ChronoUnit.MONTHS.between(start, end) + 1 > maxMonths) {
      throw badRequest("범위는 최대 " + maxMonths + "개월입니다");
    }
    return new YearMonth[] {start, end};
  }

  static YearMonth parse(String month) {
    try {
      return YearMonth.parse(month, YM);
    } catch (DateTimeParseException e) {
      throw badRequest("월은 YYYYMM 형식입니다: " + month);
    }
  }

  static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
