package com.carix.jobposting.backfill;

import com.carix.crawler.JobKoreaDetailParser;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.normalize.JobFamilyClassifier;
import com.carix.jobposting.normalize.JobKoreaPayParser;
import com.carix.jobposting.normalize.SalaryRange;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.seed.SaraminListingEnricher;
import com.carix.jobposting.seed.SaraminListingFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 적재된 `job_posting` 을 보존된 원문(`raw_ingest`)으로 다시 채운다.
 *
 * <p>첫 적재가 원천이 주는 정보를 다 쓰지 못해 세 가지가 비어 있거나 오염됐다. 연봉은 목록페이지 셀에서만 읽어 400건 중 36건만 찼고, `job_family` 는
 * 아예 null 이며, 스킬에는 인성 역량과 직무 카테고리가 섞여 수요 상위를 차지했다. 크롤을 다시 돌리지 않는 이유는 관측 시점이 바뀌기 때문이다 — 원문이 남아 있으니
 * 재파싱으로 족하다.
 */
@Component
public class JobPostingBackfill {

  private static final String JOBKOREA = "JOBKOREA";
  private static final String SARAMIN = "SARAMIN";
  private static final String INDUSTRY_CATEGORY = "INDUSTRY";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final JobPostingRepository jobPostingRepository;
  private final RawIngestRepository rawIngestRepository;
  private final SkillRepository skillRepository;

  private final JobKoreaDetailParser detailParser = new JobKoreaDetailParser();
  private final SaraminListingEnricher listingEnricher = new SaraminListingEnricher();
  private final JobKoreaPayParser payParser = new JobKoreaPayParser();
  private final JobFamilyClassifier jobFamilyClassifier = new JobFamilyClassifier();
  private final CompanySizeClassifier companySizeClassifier = new CompanySizeClassifier();
  private final EmploymentTypeClassifier employmentTypeClassifier = new EmploymentTypeClassifier();
  private final SkillTaxonomy skillTaxonomy = new SkillTaxonomy();

  public JobPostingBackfill(
      JobPostingRepository jobPostingRepository,
      RawIngestRepository rawIngestRepository,
      SkillRepository skillRepository) {
    this.jobPostingRepository = jobPostingRepository;
    this.rawIngestRepository = rawIngestRepository;
    this.skillRepository = skillRepository;
  }

  @Transactional
  public BackfillReport run(boolean dryRun) {
    Counters counters = new Counters();
    Set<Skill> touchedSkills = new HashSet<>();

    backfillJobKorea(counters, touchedSkills, dryRun);
    backfillSaramin(counters, touchedSkills, dryRun);
    counters.orphanSkillsRemoved = removeOrphanSkills(dryRun);

    return counters.toReport(dryRun);
  }

  private void backfillJobKorea(Counters counters, Set<Skill> touchedSkills, boolean dryRun) {
    for (JobPosting posting : jobPostingRepository.findBySource(JOBKOREA)) {
      counters.postingsScanned++;
      String html = rawHtml(JOBKOREA, "detail-" + posting.getExternalId());
      if (html == null) {
        counters.rawIngestMissing++;
        continue;
      }

      // 원문 급여 블록이 판정 권위다. 비공개(`COMPANY_POLICY`)나 연봉 외 급여형태면
      // 목록페이지에서 잘못 긁어 둔 값이 남지 않도록 지운다.
      SalaryRange salary = payParser.parse(detailParser.extractSalaryRaw(html));
      if (salary != null) {
        counters.salaryFilled++;
      } else if (posting.getSalaryMin() != null) {
        counters.salaryCleared++;
      }
      if (!dryRun) {
        posting.applyDisclosedSalary(
            salary == null ? null : salary.min(), salary == null ? null : salary.max());
      }

      String jobFamily = jobFamilyClassifier.classify(detailParser.extractJobTypeName(html));
      counters.jobFamilyFilled++;
      if (!dryRun) {
        posting.classifyAs(jobFamily);
      }

      String companySize =
          companySizeClassifier.classify(
              detailParser.extractEmployeeCount(html), detailParser.extractCompanyTypeName(html));
      if (!CompanySizeClassifier.UNKNOWN.equals(companySize)) {
        counters.companySizeFilled++;
      }
      if (!dryRun) {
        posting.classifyCompanySizeAs(companySize);
      }

      applyEmploymentType(posting, counters, dryRun);

      applySkills(
          posting, detailParser.extractSkills(html), List.of(), counters, touchedSkills, dryRun);
    }
  }

  private void backfillSaramin(Counters counters, Set<Skill> touchedSkills, boolean dryRun) {
    List<Document> listpages = new ArrayList<>();
    for (RawIngest rawIngest : rawIngestRepository.findBySource(SARAMIN)) {
      String html = html(rawIngest);
      if (html != null) {
        listpages.add(Jsoup.parse(html));
      }
    }

    for (JobPosting posting : jobPostingRepository.findBySource(SARAMIN)) {
      counters.postingsScanned++;
      SaraminListingFields fields = findInListpages(listpages, posting.getExternalId());
      if (fields == null) {
        counters.rawIngestMissing++;
        continue;
      }

      // 종전 파서가 `경력 3~10년` 에서 상한을 하한 자리에 넣었다. 목록 원문에서 다시 가른다.
      ExperienceRange experience = ExperienceRange.parse(fields.careerRaw());
      if (!java.util.Objects.equals(experience.min(), posting.getExperienceMin())
          || !java.util.Objects.equals(experience.max(), posting.getExperienceMax())) {
        counters.experienceCorrected++;
      }
      if (!dryRun) {
        posting.applyExperienceRange(experience.min(), experience.max());
      }

      String jobFamily = jobFamilyClassifier.classifyTags(fields.skillTags());
      counters.jobFamilyFilled++;
      if (!dryRun) {
        posting.classifyAs(jobFamily);
        // 사람인 목록페이지에는 기업 규모가 없다. NULL(전체 롤업)과 구분되게 UNKNOWN 을 넣는다.
        posting.classifyCompanySizeAs(CompanySizeClassifier.UNKNOWN);
      }

      applyEmploymentType(posting, counters, dryRun);

      applySkills(
          posting, fields.skillTags(), fields.industryTags(), counters, touchedSkills, dryRun);
    }
  }

  /** 원천 표기를 코드로 바꾼다. 원천 값 자체는 raw_ingest 에 남아 있으므로 덮어써도 된다. */
  private void applyEmploymentType(JobPosting posting, Counters counters, boolean dryRun) {
    String employmentType = employmentTypeClassifier.classify(posting.getEmploymentType());
    if (!EmploymentTypeClassifier.UNKNOWN.equals(employmentType)) {
      counters.employmentTypeFilled++;
    }
    if (!dryRun) {
      posting.classifyEmploymentTypeAs(employmentType);
    }
  }

  /**
   * 원천 태그를 표준 표기로 합치고 분류를 다시 매긴 뒤 공고의 스킬 연결을 통째로 교체한다. 직무 카테고리 태그는 지우지 않고 `JOB_CATEGORY` 로 분리해 둔다 —
   * 관측 사실은 남기되 스킬 수요 집계에서만 빠지게 하기 위해서다.
   */
  private void applySkills(
      JobPosting posting,
      List<String> skillTags,
      List<String> industryTags,
      Counters counters,
      Set<Skill> touchedSkills,
      boolean dryRun) {
    Map<String, Skill> resolved = new LinkedHashMap<>();
    for (String rawName : skillTags) {
      String canonicalName = skillTaxonomy.canonicalName(rawName);
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }
      String category = skillTaxonomy.category(canonicalName);
      resolved.computeIfAbsent(
          canonicalName + " " + category,
          key -> resolveSkill(canonicalName, category, counters, touchedSkills, dryRun));
    }
    for (String rawName : industryTags) {
      String canonicalName = skillTaxonomy.canonicalName(rawName);
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }
      resolved.computeIfAbsent(
          canonicalName + " " + INDUSTRY_CATEGORY,
          key -> resolveSkill(canonicalName, INDUSTRY_CATEGORY, counters, touchedSkills, dryRun));
    }

    List<Skill> skills = resolved.values().stream().filter(Objects::nonNull).toList();
    counters.skillLinksRewritten += skills.size();
    if (!dryRun) {
      posting.replaceSkills(skills);
    }
  }

  private Skill resolveSkill(
      String canonicalName,
      String category,
      Counters counters,
      Set<Skill> touchedSkills,
      boolean dryRun) {
    Skill existing =
        skillRepository.findByStandardNameAndCategory(canonicalName, category).orElse(null);
    if (existing == null && dryRun) {
      return null;
    }
    if (existing == null) {
      existing = skillRepository.save(new Skill(canonicalName, category, false));
    }
    if (touchedSkills.add(existing) && existing.isCustom()) {
      counters.skillsRecategorized++;
      if (!dryRun) {
        existing.markAsMarketObserved();
      }
    }
    return existing;
  }

  /** 표기 통합·분류 변경으로 아무 공고도 참조하지 않게 된 스킬 행을 지운다. 사용자가 만든 스킬(`created_by` 가 채워진 행)은 제외한다. */
  private int removeOrphanSkills(boolean dryRun) {
    List<Skill> orphans = skillRepository.findUnreferencedCrawledSkills();
    if (!dryRun) {
      skillRepository.deleteAll(orphans);
    }
    return orphans.size();
  }

  private SaraminListingFields findInListpages(List<Document> listpages, String externalId) {
    for (Document listpage : listpages) {
      SaraminListingFields fields = listingEnricher.extract(listpage, externalId);
      if (!fields.skillTags().isEmpty() || !fields.industryTags().isEmpty()) {
        return fields;
      }
    }
    return null;
  }

  private String rawHtml(String source, String sourceItemId) {
    return rawIngestRepository
        .findBySourceAndSourceItemId(source, sourceItemId)
        .map(this::html)
        .orElse(null);
  }

  private String html(RawIngest rawIngest) {
    try {
      return OBJECT_MAPPER.readTree(rawIngest.getPayload()).path("html").asText(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static final class Counters {

    private int postingsScanned;
    private int salaryFilled;
    private int salaryCleared;
    private int jobFamilyFilled;
    private int companySizeFilled;
    private int employmentTypeFilled;
    private int experienceCorrected;
    private int skillLinksRewritten;
    private int skillsRecategorized;
    private int orphanSkillsRemoved;
    private int rawIngestMissing;

    private BackfillReport toReport(boolean dryRun) {
      return new BackfillReport(
          dryRun,
          postingsScanned,
          salaryFilled,
          salaryCleared,
          jobFamilyFilled,
          companySizeFilled,
          employmentTypeFilled,
          experienceCorrected,
          skillLinksRewritten,
          skillsRecategorized,
          orphanSkillsRemoved,
          rawIngestMissing);
    }
  }
}
