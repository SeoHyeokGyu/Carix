package com.carix.jobposting.programmers;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.CompanySizeClassifier;
import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 프로그래머스 공고 행을 생성하거나 갱신한다. 지연 로딩 스킬을 다루므로 호출자가 트랜잭션 내에서 실행한다. */
public class ProgrammersPostingWriter {

  public static final String SOURCE = "PROGRAMMERS";

  private final JobPostingRepository postings;
  private final PostingSkillService skills;
  private final SkillTaxonomy taxonomy;
  private final ProgrammersFamilyResolver families = new ProgrammersFamilyResolver();

  public ProgrammersPostingWriter(
      JobPostingRepository postings, PostingSkillService skills, SkillTaxonomy taxonomy) {
    this.postings = postings;
    this.skills = skills;
    this.taxonomy = taxonomy;
  }

  public boolean exists(String id) {
    return postings.findBySourceAndExternalId(SOURCE, id).isPresent();
  }

  /** 이미 있는 공고는 관측월과 수집 경로만 갱신한다. */
  public boolean touchListed(String id, String month) {
    return postings
        .findBySourceAndExternalId(SOURCE, id)
        .map(
            p -> {
              p.updateLastSeenMonth(month);
              p.addCollectedVia(CollectedVia.PROGRAMMERS);
              postings.save(p);
              return true;
            })
        .orElse(false);
  }

  /** 정기 수집 신규 공고를 저장한다. */
  public boolean upsertListed(
      ProgrammersPosition p, String month, Map<String, Integer> droppedOut) {
    if (touchListed(p.id(), month)) {
      return false;
    }
    postings.save(newPosting(p, month, droppedOut));
    return true;
  }

  private JobPosting newPosting(
      ProgrammersPosition p, String month, Map<String, Integer> droppedOut) {
    JobPosting posting =
        new JobPosting(
            SOURCE,
            p.id(),
            p.title(),
            p.companyName(),
            null,
            null,
            p.address(),
            p.minCareer(),
            p.maxCareer(),
            EmploymentTypeClassifier.FULL_TIME,
            CompanySizeClassifier.UNKNOWN,
            null,
            null,
            families.resolve(p.jobCategories(), p.title()),
            null,
            null,
            month,
            month,
            Instant.now());

    posting.applyPostedDates(p.startAt(), p.endAt());
    posting.applySalary(PostingSalary.none());
    if (p.jobCategories() != null && !p.jobCategories().isEmpty()) {
      posting.recordSourceJobTags(String.join(",", p.jobCategories()));
    }
    posting.addCollectedVia(CollectedVia.PROGRAMMERS);
    posting.addSkillsIfAbsent(resolveSkills(p, droppedOut));
    return posting;
  }

  private List<Skill> resolveSkills(ProgrammersPosition p, Map<String, Integer> droppedOut) {
    Set<String> names = new LinkedHashSet<>();
    if (p.positionTags() != null) {
      for (String raw : p.positionTags()) {
        List<String> known = taxonomy.matchKnownSkills(raw);
        if (known.isEmpty()) {
          droppedOut.merge(raw, 1, Integer::sum);
        }
        names.addAll(known);
      }
    }
    names.addAll(taxonomy.extractSkillsFromSentence(p.title()));

    Map<String, Skill> out = new LinkedHashMap<>();
    names.forEach(n -> out.computeIfAbsent(n, skills::upsertKnownSkill));
    return List.copyOf(out.values());
  }
}
