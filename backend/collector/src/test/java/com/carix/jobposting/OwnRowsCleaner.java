package com.carix.jobposting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 없이 도는 @SpringBootTest 가 만든 행만 지운다. 다른 테스트가 만든 행을 건드리지 않도록 시작 시점의 스킬·원본 수집 이력 id 를 기억해 두었다가 그
 * 뒤에 생긴 것만 지운다.
 */
public final class OwnRowsCleaner {

  private final JobPostingRepository postings;
  private final SkillRepository skills;
  private final RawIngestRepository rawIngests;
  private final TransactionTemplate tx;
  private final Set<UUID> skillIdsBefore = new HashSet<>();
  private final Set<UUID> rawIdsBefore = new HashSet<>();

  public OwnRowsCleaner(
      JobPostingRepository postings,
      SkillRepository skills,
      RawIngestRepository rawIngests,
      PlatformTransactionManager transactionManager) {
    this.postings = postings;
    this.skills = skills;
    this.rawIngests = rawIngests;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public void snapshot() {
    skillIdsBefore.clear();
    rawIdsBefore.clear();
    skills.findAll().forEach(s -> skillIdsBefore.add(s.getId()));
    rawIngests.findAll().forEach(r -> rawIdsBefore.add(r.getId()));
  }

  /** 공고는 원천·외부 id 로 지우고, 스킬과 수집 이력은 snapshot 뒤에 생긴 것만 지운다. */
  public void cleanUp(String source, String externalId) {
    tx.executeWithoutResult(
        s -> postings.findBySourceAndExternalId(source, externalId).ifPresent(postings::delete));
    tx.executeWithoutResult(
        s -> {
          rawIngests.deleteAll(
              rawIngests.findAll().stream()
                  .filter(r -> !rawIdsBefore.contains(r.getId()))
                  .toList());
          skills.deleteAll(
              skills.findAll().stream().filter(k -> !skillIdsBefore.contains(k.getId())).toList());
        });
  }
}
