package com.carix.jobposting.wanted;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class WantedConfig {

  /** 요청 간격 초당 1건. */
  private static final long REQUEST_DELAY_MS = 1_000L;

  @Bean
  WantedIngestService wantedIngestService(
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      PostingSkillService skillService,
      SkillTaxonomy taxonomy,
      PlatformTransactionManager transactionManager) {
    return new WantedIngestService(
        new WantedClient.Http(REQUEST_DELAY_MS),
        new WantedParser(),
        postings,
        rawIngests,
        skillService,
        taxonomy,
        new TransactionTemplate(transactionManager));
  }

  @Bean
  WantedBackfillService wantedBackfillService(
      JobPostingRepository postings,
      PostingSkillService skillService,
      SkillTaxonomy taxonomy,
      PlatformTransactionManager transactionManager) {
    return new WantedBackfillService(
        WantedClient.Http::new,
        new WantedParser(),
        postings,
        skillService,
        taxonomy,
        new TransactionTemplate(transactionManager));
  }
}
