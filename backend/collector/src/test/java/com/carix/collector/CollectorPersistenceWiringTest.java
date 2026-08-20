package com.carix.collector;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 옮겨 온 코드가 com.carix.jobposting 패키지명을 유지하므로 CollectorApplication 의
 * 기본 스캔 범위(com.carix.collector) 밖에 있다. 스캔 대상을 명시하지 않으면 엔티티와
 * 리포지터리가 조용히 빠진 채 컨텍스트가 뜬다 — 이 테스트가 그것을 잡는다.
 */
@SpringBootTest
class CollectorPersistenceWiringTest {

  @Autowired(required = false)
  private JobPostingRepository jobPostingRepository;

  @Autowired(required = false)
  private RawIngestRepository rawIngestRepository;

  @Autowired(required = false)
  private SkillRepository skillRepository;

  @Test
  void registersJobPostingRepositories() {
    assertNotNull(jobPostingRepository);
    assertNotNull(rawIngestRepository);
    assertNotNull(skillRepository);
  }
}
