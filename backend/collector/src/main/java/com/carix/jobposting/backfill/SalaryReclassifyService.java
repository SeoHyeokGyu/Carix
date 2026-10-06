package com.carix.jobposting.backfill;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.PostingSalaryClassifier;
import com.carix.jobposting.normalize.SalaryDisclosure;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 저장된 잡코리아·사람인 공고를 상세페이지로 다시 받아 급여를 판정한다(설계 §5.2). 측정 모드는 쓰지 않고 판정 분포와 서울·정규직 동료집단의 공개 연봉 수를 전후로 낸다
 * — 사용자 화면의 연봉 표본이 얼마나 남는지 보고 적용을 정하기 위해서다.
 */
@Slf4j
@Service
public class SalaryReclassifyService {

  static final long DEFAULT_DELAY_MS = 1_000L;
  private static final List<String> SOURCES = List.of("JOBKOREA", "SARAMIN");
  private static final List<String> FAMILIES = List.of("DEVELOPER", "DATA", "AI");
  // core-api 동료집단 경력 3구간과 같은 경계(PeerSalaryDistributionResolver.bracketYearRange).
  private static final Map<String, int[]> BRACKETS = new LinkedHashMap<>();

  static {
    BRACKETS.put("JUNIOR", new int[] {0, 3});
    BRACKETS.put("MID", new int[] {4, 7});
    BRACKETS.put("SENIOR", new int[] {8, 99});
  }

  /** 원천·공고 번호로 상세페이지를 연다. 테스트는 가짜를 넣는다. */
  interface DetailPages {
    Optional<String> fetch(String source, String externalId);
  }

  private final JobPostingRepository repository;
  private final DetailPages pages;
  private final PostingSalaryClassifier classifier = new PostingSalaryClassifier();

  @Autowired
  public SalaryReclassifyService(JobPostingRepository repository) {
    this(repository, null);
  }

  SalaryReclassifyService(JobPostingRepository repository, DetailPages pages) {
    this.repository = repository;
    this.pages = pages;
  }

  public Map<String, Object> run(boolean dryRun, Long delayMs) {
    DetailPages fetcher =
        pages != null
            ? pages
            : realPages(delayMs != null ? Math.max(0L, delayMs) : DEFAULT_DELAY_MS);
    Map<String, Map<String, Integer>> bySource = new TreeMap<>();
    Map<String, Map<String, Integer>> peers = new TreeMap<>();
    int total = 0;
    int unreachable = 0;
    int errors = 0;
    List<JobPosting> postings = new ArrayList<>();
    SOURCES.forEach(s -> postings.addAll(repository.findBySource(s)));
    for (JobPosting posting : postings) {
      total++;
      try {
        boolean hadSalary = posting.getSalaryMin() != null && posting.getSalaryMax() != null;
        Optional<String> html = fetcher.fetch(posting.getSource(), posting.getExternalId());
        PostingSalary salary =
            html.map(h -> classify(posting.getSource(), h)).orElse(PostingSalary.unknown());
        // 받았어도 판정 불가(차단·캡차 등)면 받지 못한 것과 같다 — 진짜 판정을 UNKNOWN 으로 덮으며 값을 잃지 않는다.
        boolean isUnreachable = salary.disclosure() == SalaryDisclosure.UNKNOWN;
        if (isUnreachable) {
          unreachable++;
        }
        bySource
            .computeIfAbsent(posting.getSource(), k -> new TreeMap<>())
            .merge(salary.disclosure().name(), 1, Integer::sum);
        String peerKey = peerKey(posting);
        if (peerKey != null) {
          Map<String, Integer> counts =
              peers.computeIfAbsent(peerKey, k -> new TreeMap<>(Map.of("before", 0, "after", 0)));
          if (hadSalary) {
            counts.merge("before", 1, Integer::sum);
          }
          if (salary.disclosure() == SalaryDisclosure.PUBLIC) {
            counts.merge("after", 1, Integer::sum);
          }
        }
        if (!dryRun) {
          if (!isUnreachable) {
            posting.applySalary(salary);
            repository.save(posting);
          } else if (isUnjudged(posting)) {
            posting.markSalaryUnreachable();
            repository.save(posting);
          }
          // 이미 판정된 공고는 일시 실패로 UNKNOWN 으로 내리지 않고 그대로 둔다.
        }

      } catch (RuntimeException e) {
        errors++;
        log.warn(
            "[급여 재판정] {} {} 처리 실패 — 건너뜀: {}",
            posting.getSource(),
            posting.getExternalId(),
            e.toString());
      }
    }
    log.info(
        "[급여 재판정] dryRun={} 대상 {} / 받지 못함 {} / 오류 {} / {}",
        dryRun,
        total,
        unreachable,
        errors,
        bySource);
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("dryRun", dryRun);
    summary.put("total", total);
    summary.put("unreachable", unreachable);
    summary.put("errors", errors);
    summary.put("bySource", bySource);
    summary.put("publicPeers", peers);
    return summary;
  }

  private static boolean isUnjudged(JobPosting p) {
    return p.getSalaryDisclosure() == null || "UNKNOWN".equals(p.getSalaryDisclosure());
  }

  private PostingSalary classify(String source, String html) {
    return "SARAMIN".equals(source)
        ? classifier.classifySaramin(html)
        : classifier.classifyJobKorea(html);
  }

  /** 서울·정규직·세 직무에 한해 「직무/경력구간」 키. 경력 범위 겹침은 core-api 동료집단 조회와 같은 규칙. 여러 구간에 걸치면 처음 맞는 구간 하나. */
  private static String peerKey(JobPosting p) {
    if (p.getJobFamily() == null
        || !FAMILIES.contains(p.getJobFamily())
        || p.getRegion() == null
        || !p.getRegion().startsWith("서울")
        || !"FULL_TIME".equals(p.getEmploymentType())) {
      return null;
    }
    for (Map.Entry<String, int[]> b : BRACKETS.entrySet()) {
      int lo = b.getValue()[0];
      int hi = b.getValue()[1];
      boolean minOk = p.getExperienceMin() == null || p.getExperienceMin() <= hi;
      boolean maxOk = p.getExperienceMax() == null || p.getExperienceMax() >= lo;
      if (minOk && maxOk) {
        return p.getJobFamily() + "/" + b.getKey();
      }
    }
    return null;
  }

  private static DetailPages realPages(long delayMs) {
    JobKoreaPageFetcher jobKorea = new JobKoreaPageFetcher(delayMs);
    SaraminDetailFetcher saramin = new SaraminDetailFetcher(delayMs);
    return (source, id) ->
        "SARAMIN".equals(source) ? saramin.fetch(id) : jobKorea.fetch(Long.parseLong(id));
  }
}
