package com.carix.jobposting.normalize;

import com.carix.jobposting.backfill.JobKoreaItCodes;
import com.carix.jobposting.backfill.JobKoreaOwnPostingParser;

/**
 * 잡코리아 공고의 직무군. 정기 수집·시드·백필이 같은 규칙을 써야 MVI 의 최근 창과 직전 창 모집단이 같아진다.
 *
 * <p>상세페이지의 {@code jobTypeName} 은 추천 공고 목록의 값이라 쓰지 않는다. 자기 공고의 직무 분류 코드가 IT 블록이면 제목으로
 * AI·DATA·DEVELOPER 를 가르고, 아니면 OTHER 다.
 */
public class JobKoreaFamilyResolver {

  private final JobKoreaItCodes itCodes;
  private final JobKoreaOwnPostingParser ownPostingParser = new JobKoreaOwnPostingParser();
  private final JobFamilyClassifier jobFamilyClassifier = new JobFamilyClassifier();

  public JobKoreaFamilyResolver(JobKoreaItCodes itCodes) {
    this.itCodes = itCodes;
  }

  public static JobKoreaFamilyResolver bundled() {
    return new JobKoreaFamilyResolver(JobKoreaItCodes.bundled());
  }

  public String resolve(String detailHtml, String title) {
    if (detailHtml == null
        || !itCodes.isIt(ownPostingParser.extractClassificationCodes(detailHtml))) {
      return JobFamilyClassifier.OTHER;
    }
    return jobFamilyClassifier.classifyItCategoryTitle(title);
  }
}
