package com.carix.jobposting.jumpit;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class JumpitConfig {

  /** 설계 §5.1: 요청 간격 초당 1건. */
  private static final long REQUEST_DELAY_MS = 1_000L;

  @Bean
  JumpitIngestService jumpitIngestService(
      JobPostingRepository postings,
      RawIngestRepository rawIngests,
      PostingSkillService skillService,
      SkillTaxonomy taxonomy,
      PlatformTransactionManager transactionManager) {
    return new JumpitIngestService(
        new JumpitClient.Http(REQUEST_DELAY_MS),
        new JumpitParser(),
        new JumpitFamilyResolver(),
        postings,
        rawIngests,
        skillService,
        taxonomy,
        new SaraminDetailFetcher(REQUEST_DELAY_MS),
        new TransactionTemplate(transactionManager));
  }
}
