package com.carix.jobposting.wanted;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 원티드 공고 행을 만들거나 갱신한다(정기 수집·표본 백필 공용). 지연 로딩 스킬을 만지므로 호출자가 공고 하나를 한 트랜잭션으로 감싸 부른다. HTTP 는 하지 않는다.
 * 미등록 스킬은 호출자가 준 공고별 맵에 세고, 총계에 합치는 시점은 호출자가 정한다.
 */
public class WantedPostingWriter {

  static final String SOURCE = "WANTED";
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final JobPostingRepository postings;
  private final PostingSkillService skills;
  private final SkillTaxonomy taxonomy;
  private final WantedFamilyResolver families = new WantedFamilyResolver();

  public WantedPostingWriter(
      JobPostingRepository postings, PostingSkillService skills, SkillTaxonomy taxonomy) {
    this.postings = postings;
    this.skills = skills;
    this.taxonomy = taxonomy;
  }

  public boolean isSampled(String id) {
    return postings
        .findBySourceAndExternalId(SOURCE, id)
        .map(p -> p.getSampledMonth() != null)
        .orElse(false);
  }

  /** 이 ID 의 원티드 행이 이미 있는가. 상세 API 를 부르기 전에 불필요한 호출을 거르는 데 쓴다. */
  public boolean exists(String id) {
    return postings.findBySourceAndExternalId(SOURCE, id).isPresent();
  }

  /** 있으면 관측월·확인 경로만 갱신하고 true. */
  public boolean touchListed(String id, String month) {
    return postings
        .findBySourceAndExternalId(SOURCE, id)
        .map(
            p -> {
              p.updateLastSeenMonth(month);
              p.addCollectedVia(CollectedVia.WANTED_LIST);
              postings.save(p);
              return true;
            })
        .orElse(false);
  }

  /** 정기 수집 신규 공고. 관측월은 수집 월. 새로 만들었으면 true. */
  public boolean upsertListed(
      WantedPosting p, List<String> skillTags, String month, Map<String, Integer> droppedOut) {
    if (touchListed(p.id(), month)) {
      return false;
    }
    postings.save(newPosting(p, skillTags, month, CollectedVia.WANTED_LIST, droppedOut));
    return true;
  }

  /** 표본 공고. 관측월은 게시월. 이미 있으면 표본 월만 단다. 새로 만들었으면 true. */
  public boolean upsertSampled(
      WantedPosting p,
      List<String> skillTags,
      YearMonth sampledMonth,
      Map<String, Integer> droppedOut) {
    String month = sampledMonth.format(YM);
    return postings
        .findBySourceAndExternalId(SOURCE, p.id())
        .map(
            existing -> {
              existing.markSampled(month);
              existing.addCollectedVia(CollectedVia.WANTED_BACKFILL);
              postings.save(existing);
              return false;
            })
        .orElseGet(
            () -> {
              JobPosting created =
                  newPosting(p, skillTags, month, CollectedVia.WANTED_BACKFILL, droppedOut);
              created.markSampled(month);
              postings.save(created);
              return true;
            });
  }

  private JobPosting newPosting(
      WantedPosting p,
      List<String> skillTags,
      String month,
      String via,
      Map<String, Integer> droppedOut) {
    Integer experienceMin =
        p.newbie() && p.annualFrom() == null ? Integer.valueOf(0) : p.annualFrom();
    JobPosting posting =
        new JobPosting(
            SOURCE,
            p.id(),
            p.title(),
            p.company(),
            null,
            null,
            p.region(),
            experienceMin,
            p.annualTo(),
            p.employmentType(),
            CompanySizeClassifier.UNKNOWN,
            null,
            null,
            families.resolve(p.childTagIds(), p.title()),
            null,
            null,
            month,
            month,
            Instant.now());
    posting.applyPostedDates(p.postedDate(), p.endDate());
    posting.applySalary(PostingSalary.none());
    posting.recordSourceJobTags(sourceTags(p));
    posting.addCollectedVia(via);
    posting.addSkillsIfAbsent(resolveSkills(p, skillTags, droppedOut));
    return posting;
  }

  private static String sourceTags(WantedPosting p) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < p.childTagIds().size(); i++) {
      String name = i < p.childTagNames().size() ? p.childTagNames().get(i) : "";
      out.add(p.childTagIds().get(i) + ":" + name);
    }
    return String.join(",", out);
  }

  private List<Skill> resolveSkills(
      WantedPosting p, List<String> skillTags, Map<String, Integer> droppedOut) {
    Set<String> names = new LinkedHashSet<>();
    for (String raw : skillTags) {
      List<String> known = taxonomy.matchKnownSkills(raw);
      if (known.isEmpty()) {
        droppedOut.merge(raw, 1, Integer::sum);
      }
      names.addAll(known);
    }
    names.addAll(taxonomy.extractSkillsFromSentence(p.title() + "\n" + p.bodyText()));
    Map<String, Skill> out = new LinkedHashMap<>();
    names.forEach(n -> out.computeIfAbsent(n, skills::upsertKnownSkill));
    return List.copyOf(out.values());
  }
}
