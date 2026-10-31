package com.carix.jobposting.programmers;

import com.carix.jobposting.normalize.JobFamilyClassifier;
import java.util.List;
import java.util.Set;

/** 프로그래머스 직무 카테고리 및 공고 제목을 대표 직무 하나로 판정한다. 우선순위: AI > DATA > DEVELOPER > OTHER. */
public class ProgrammersFamilyResolver {

  private static final Set<String> AI_CATEGORIES =
      Set.of("인공지능", "머신러닝", "딥러닝", "인공지능/머신러닝", "AI", "ML");

  private static final Set<String> DATA_CATEGORIES =
      Set.of("데이터 엔지니어", "빅데이터 엔지니어", "DBA", "데이터 분석가");

  private static final Set<String> OTHER_CATEGORIES = Set.of("기술지원", "개발 PM", "기획", "디자인", "QA");

  private static final List<String> NON_SOFTWARE_TITLE_KEYWORDS =
      List.of("기획", "디자이너", "디자인", "마케팅", "영업", "HR", "인사", "총무");

  private final JobFamilyClassifier titleRule = new JobFamilyClassifier();

  public String resolve(List<String> jobCategories, String title) {
    // 1. 카테고리가 있으면 카테고리 우선 매핑
    if (jobCategories != null && !jobCategories.isEmpty()) {
      if (jobCategories.stream().anyMatch(AI_CATEGORIES::contains)) {
        return JobFamilyClassifier.AI;
      }
      if (jobCategories.stream().anyMatch(DATA_CATEGORIES::contains)) {
        return JobFamilyClassifier.DATA;
      }
      if (jobCategories.stream().anyMatch(c -> !OTHER_CATEGORIES.contains(c))) {
        return JobFamilyClassifier.DEVELOPER;
      }
      if (jobCategories.stream().allMatch(OTHER_CATEGORIES::contains)) {
        return JobFamilyClassifier.OTHER;
      }
    }

    // 2. 제목 비개발 키워드 판정
    if (title != null && NON_SOFTWARE_TITLE_KEYWORDS.stream().anyMatch(title::contains)) {
      return JobFamilyClassifier.OTHER;
    }

    // 3. 제목 기반 IT 카테고리 룰 폴백
    String fromTitle = titleRule.classifyItCategoryTitle(title);
    if (fromTitle != null && !fromTitle.isBlank()) {
      return fromTitle;
    }

    // 4. 프로그래머스는 개발자 특화 플랫폼이므로 기본 DEVELOPER
    return JobFamilyClassifier.DEVELOPER;
  }
}
