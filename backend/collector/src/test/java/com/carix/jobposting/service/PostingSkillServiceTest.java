package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.JobPostingSkillRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.UserSkillRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostingSkillServiceTest {

  private JobPostingRepository jobPostingRepository;
  private SkillRepository skillRepository;
  private PostingSkillService service;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    skillRepository = mock(SkillRepository.class);

    given(skillRepository.findByStandardNameAndCategory(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(skillRepository.save(any(Skill.class))).willAnswer(i -> i.getArgument(0));
    given(jobPostingRepository.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));

    service =
        new PostingSkillService(
            jobPostingRepository,
            skillRepository,
            null,
            null,
            null,
            new SkillTaxonomy(skillRepository, null));
  }

  @Test
  @DisplayName("deduplicateSkills - 벌크 Repository가 제공될 때 중복 스킬 링크 재할당 및 삭제 정상 수행")
  void deduplicateSkills_withBulkRepositories() {
    JobPostingSkillRepository jpsRepo = mock(JobPostingSkillRepository.class);
    SkillAliasRepository saRepo = mock(SkillAliasRepository.class);
    UserSkillRepository usRepo = mock(UserSkillRepository.class);

    Skill canonical = new Skill("React", "FRAMEWORK", false);
    Skill duplicate = new Skill("React", "SKILL", false);

    given(skillRepository.findAll()).willReturn(List.of(canonical, duplicate));

    PostingSkillService bulkService =
        new PostingSkillService(
            jobPostingRepository,
            skillRepository,
            jpsRepo,
            saRepo,
            usRepo,
            new SkillTaxonomy(skillRepository, saRepo));

    int deduped = bulkService.deduplicateSkills();

    assertThat(deduped).isEqualTo(1);
    verify(jpsRepo).deleteDuplicateLinks(duplicate.getId(), canonical.getId());
    verify(jpsRepo).reassignSkill(duplicate.getId(), canonical);
    verify(saRepo).reassignSkill(duplicate.getId(), canonical);
    verify(usRepo).deleteDuplicateLinks(duplicate.getId(), canonical.getId());
    verify(usRepo).reassignSkill(duplicate.getId(), canonical);
    verify(skillRepository).deleteSkillDirectly(duplicate.getId());
  }
}
