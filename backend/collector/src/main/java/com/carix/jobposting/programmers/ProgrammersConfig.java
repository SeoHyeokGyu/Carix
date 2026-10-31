package com.carix.jobposting.programmers;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ProgrammersConfig {

  /** 초당 1건 요청 간격. */
  private static final long REQUEST_DELAY_MS = 1_000L;

  @Bean
  ProgrammersIngestService programmersIngestService(
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      PostingSkillService skillService,
      SkillTaxonomy taxonomy,
      PlatformTransactionManager transactionManager) {
    return new ProgrammersIngestService(
        new ProgrammersClient.Http(REQUEST_DELAY_MS),
        new ProgrammersParser(),
        postings,
        rawIngests,
        skillService,
        taxonomy,
        new TransactionTemplate(transactionManager));
  }
}
