package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobFamilyClassifierTest {

  private final JobFamilyClassifier classifier = new JobFamilyClassifier();

  @Test
  void classifiesDeveloperJobTitles() {
    assertEquals("DEVELOPER", classifier.classify("백엔드개발자"));
    assertEquals("DEVELOPER", classifier.classify("웹개발자"));
    assertEquals("DEVELOPER", classifier.classify("시스템엔지니어"));
    assertEquals("DEVELOPER", classifier.classify("QA"));
  }

  @Test
  void classifiesDataAndAiAboveDeveloper() {
    assertEquals("AI", classifier.classify("AI/ML엔지니어"));
    assertEquals("DATA", classifier.classify("데이터엔지니어"));
    // 여러 직무가 붙은 공고는 우선순위가 높은 직군으로 판정한다.
    assertEquals("AI", classifier.classify("AI/ML엔지니어,백엔드개발자,프론트엔드개발자"));
    assertEquals("DATA", classifier.classify("데이터분석가,백엔드개발자"));
  }

  @Test
  void classifiesNonTechnicalJobsAsOther() {
    assertEquals("OTHER", classifier.classify("마케팅기획,법무담당자,변호사 외 12건"));
    assertEquals("OTHER", classifier.classify("경영·비즈니스기획,경영지원,인사담당자 외 17건"));
  }

  @Test
  void doesNotTreatNonSoftwareEngineersAsDevelopers() {
    assertEquals("OTHER", classifier.classify("전기·전자엔지니어"));
    assertEquals("OTHER", classifier.classify("설비엔지니어"));
    assertEquals("OTHER", classifier.classify("설치·수리기사"));
    // 제외 대상은 그 직무명 하나뿐이다. 같이 붙은 IT 직무까지 버리지 않는다.
    assertEquals("DEVELOPER", classifier.classify("네트워크엔지니어,설치·수리기사,설비엔지니어"));
  }

  @Test
  void picksHighestPriorityFamilyAmongTags() {
    assertEquals("DEVELOPER", classifier.classifyTags(List.of("웹개발", "유지보수")));
    assertEquals("AI", classifier.classifyTags(List.of("웹개발", "머신러닝")));
    assertEquals("DATA", classifier.classifyTags(List.of("백엔드/서버개발", "데이터엔지니어")));
  }

  @Test
  void returnsOtherForMissingInput() {
    assertEquals("OTHER", classifier.classify(null));
    assertEquals("OTHER", classifier.classify("  "));
    assertEquals("OTHER", classifier.classifyTags(List.of()));
  }
}
