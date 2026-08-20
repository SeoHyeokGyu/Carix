package com.carix.jobposting.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 백필을 애플리케이션 기동 시 1회 실행한다. 운영 기동에서 우발적으로 돌지 않도록
 * 속성으로 명시해야만 빈이 만들어지고, 기본 동작은 쓰지 않는 dry-run 이다.
 *
 * <pre>
 * ./gradlew :collector:bootRun --args='\
 *   --spring.profiles.active=local \
 *   --carix.backfill.job-posting.enabled=true \
 *   --carix.backfill.job-posting.dry-run=false'
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "carix.backfill.job-posting.enabled", havingValue = "true")
public class JobPostingBackfillRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(JobPostingBackfillRunner.class);

  private final JobPostingBackfill backfill;
  private final boolean dryRun;

  public JobPostingBackfillRunner(JobPostingBackfill backfill,
      @Value("${carix.backfill.job-posting.dry-run:true}") boolean dryRun) {
    this.backfill = backfill;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("job_posting 백필 시작 (dryRun={})", dryRun);
    BackfillReport report = backfill.run(dryRun);
    log.info("job_posting 백필 완료 {}", report);
  }
}
