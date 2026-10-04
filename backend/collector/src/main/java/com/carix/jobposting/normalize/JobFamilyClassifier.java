package com.carix.jobposting.normalize;

import java.util.Arrays;
import java.util.List;

/**
 * 원천이 주는 직무명(잡코리아 {@code jobTypeName}, 사람인 직무 카테고리 태그)을 MVP 직군 코드로 정규화한다.
 *
 * <p>한 공고에 직무가 여러 개 붙는 경우가 흔해서(실측 400건 중 다직무 공고 다수) 토큰 전체를 훑고 우선순위가 가장 높은 직군을 채택한다.
 */
public class JobFamilyClassifier {

  public static final String AI = "AI";
  public static final String DATA = "DATA";
  public static final String DEVELOPER = "DEVELOPER";
  public static final String OTHER = "OTHER";

  /** 직무명에 `엔지니어`가 들어가도 소프트웨어 직군이 아닌 것들. */
  private static final List<String> NON_SOFTWARE =
      List.of("전기", "전자", "설비", "설치", "수리", "기계", "화학", "토목", "건축", "생산관리");

  private static final List<String> AI_KEYWORDS = List.of("AI", "인공지능", "ML엔지니어", "머신러닝", "딥러닝");

  private static final List<String> DATA_KEYWORDS = List.of("데이터", "빅데이터", "DBA");

  private static final List<String> DEVELOPER_KEYWORDS =
      List.of(
          "개발", "엔지니어", "프론트엔드", "백엔드", "퍼블리셔", "풀스택", "웹마스터", "임베디드", "펌웨어", "아키텍", "정보보안", "네트워크",
          "서버", "기술지원", "유지보수", "솔루션", "QA", "테스터", "SI", "SE(");

  /** 콤마로 이어진 직무명 문자열을 판정한다. `외 12건` 같은 꼬리표는 무시된다. */
  public String classify(String jobTypeName) {
    if (jobTypeName == null || jobTypeName.isBlank()) {
      return OTHER;
    }
    return classifyTags(Arrays.asList(jobTypeName.split(",")));
  }

  /** 원천이 태그 목록으로 주는 경우(사람인 직무 카테고리)를 판정한다. */
  public String classifyTags(List<String> tags) {
    boolean developer = false;
    boolean data = false;
    for (String tag : tags) {
      String token = tag == null ? "" : tag.strip();
      if (token.isEmpty() || containsAny(token, NON_SOFTWARE)) {
        continue;
      }
      if (containsAny(token, AI_KEYWORDS)) {
        return AI;
      }
      data |= containsAny(token, DATA_KEYWORDS);
      developer |= containsAny(token, DEVELOPER_KEYWORDS);
    }
    if (data) {
      return DATA;
    }
    return developer ? DEVELOPER : OTHER;
  }

  /**
   * 원천이 이미 IT 직무로 분류한 공고의 제목을 판정한다. 제목에 직무명이 없는 경우(`Software Engineer`, `JAVA SM`)가 흔해 기본값은
   * DEVELOPER 이고, 제목은 AI·DATA 로 올리거나 소프트웨어가 아닌 공학 직무를 걸러내는 데만 쓴다.
   */
  public String classifyItCategoryTitle(String title) {
    String token = title == null ? "" : title.strip();
    if (containsAny(token, NON_SOFTWARE)) {
      return OTHER;
    }
    if (containsAny(token, AI_KEYWORDS)) {
      return AI;
    }
    return containsAny(token, DATA_KEYWORDS) ? DATA : DEVELOPER;
  }

  private boolean containsAny(String token, List<String> keywords) {
    return keywords.stream().anyMatch(token::contains);
  }
}
