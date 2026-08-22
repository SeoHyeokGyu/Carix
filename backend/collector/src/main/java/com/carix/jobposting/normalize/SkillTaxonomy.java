package com.carix.jobposting.normalize;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 원천이 스킬 태그로 주는 문자열을 표준 표기와 분류로 정리한다.
 *
 * <p>두 가지를 고친다. 하나는 표기 흔들림 — 같은 기술이 `JAVA`/`Java` 처럼 원천마다 다르게 적혀 별개 스킬로 집계됐다. 다른 하나는 분류 오염 — 사람인 직무
 * 카테고리 태그(`웹개발`, `백엔드/서버개발`)가 스킬로 섞여 들어와 수요 상위를 차지했다.
 *
 * <p>여기서 하는 정규화는 대소문자·표기 통합까지다. 별칭 사전(`자바` -> `Java`)은 표준 스킬 사전 도입 시 `skill_alias` 로 다룬다.
 */
public class SkillTaxonomy {

  public static final String SKILL = "SKILL";
  public static final String JOB_CATEGORY = "JOB_CATEGORY";

  /** 표준 표기. 키는 소문자 정규화한 관측 표기다. */
  private static final Map<String, String> STANDARD_NAMES =
      toLookup(
          List.of(
              "Java",
              "Spring Boot",
              "Python",
              "Go",
              "Node.js",
              "TypeScript",
              "React",
              "Next.js",
              "PostgreSQL",
              "MySQL",
              "AWS",
              "Kubernetes",
              "Apache Airflow",
              "PyTorch",
              "TensorFlow",
              "LLM",
              "jQuery",
              ".NET",
              "C#",
              "C++",
              "JavaScript",
              "Docker",
              "Linux",
              "Git"));

  /** 기술이 아니라 직무를 가리키는 태그. 수요 집계에서 스킬로 세면 안 된다. */
  private static final Set<String> JOB_TITLES =
      Set.of(
          "백엔드/서버개발",
          "웹개발",
          "프론트엔드",
          "앱개발",
          "si개발",
          "퍼블리셔",
          "풀스택",
          "웹마스터",
          "qa/테스터",
          "유지보수",
          "기술지원",
          "솔루션",
          "데이터엔지니어",
          "데이터분석가",
          "se(시스템엔지니어)",
          "아키텍쳐",
          "게임개발",
          "웹기획");

  public String canonicalName(String rawName) {
    if (rawName == null) {
      return null;
    }
    String trimmed = rawName.strip();
    return STANDARD_NAMES.getOrDefault(key(trimmed), trimmed);
  }

  public String category(String rawName) {
    return JOB_TITLES.contains(key(canonicalName(rawName))) ? JOB_CATEGORY : SKILL;
  }

  private static String key(String name) {
    return name == null ? "" : name.strip().toLowerCase();
  }

  private static Map<String, String> toLookup(List<String> standardNames) {
    return standardNames.stream().collect(Collectors.toMap(SkillTaxonomy::key, name -> name));
  }
}
