package com.carix.jobposting.jumpit;

import com.carix.jobposting.normalize.JobFamilyClassifier;
import java.util.List;
import java.util.Set;

/**
 * 점핏 직무 분류를 대표 직무 하나로 고른다(설계 §5.2). 분류가 여럿이면 AI > DATA > DEVELOPER > OTHER. 대응표에 없는 분류는 개발자 전용 서비스라
 * DEVELOPER 로 본다.
 */
public class JumpitFamilyResolver {

  private static final Set<String> AI = Set.of("인공지능/머신러닝");
  private static final Set<String> DATA = Set.of("빅데이터 엔지니어", "DBA");
  private static final Set<String> OTHER = Set.of("기술지원", "개발 PM");

  private final JobFamilyClassifier titleRule = new JobFamilyClassifier();

  public String resolve(List<String> jobCategories, String title) {
    if (jobCategories == null || jobCategories.isEmpty()) {
      return titleRule.classifyItCategoryTitle(title);
    }
    if (jobCategories.stream().anyMatch(AI::contains)) {
      return JobFamilyClassifier.AI;
    }
    if (jobCategories.stream().anyMatch(DATA::contains)) {
      return JobFamilyClassifier.DATA;
    }
    if (jobCategories.stream().anyMatch(c -> !OTHER.contains(c))) {
      return JobFamilyClassifier.DEVELOPER;
    }
    return JobFamilyClassifier.OTHER;
  }
}
