package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmploymentTypeClassifierTest {

  private final EmploymentTypeClassifier classifier = new EmploymentTypeClassifier();

  @Test
  void classifiesSingleLabels() {
    assertEquals("FULL_TIME", classifier.classify("정규직"));
    assertEquals("CONTRACT", classifier.classify("계약직"));
    assertEquals("CONTRACT", classifier.classify("파견직"));
    assertEquals("FREELANCE", classifier.classify("프리랜서"));
    assertEquals("INTERN", classifier.classify("인턴"));
    assertEquals("INTERN", classifier.classify("인턴직"));
    assertEquals("INTERN", classifier.classify("교육생"));
  }

  /** 원천은 `정규직 외` 처럼 "그 밖에도 있음"을 꼬리표로 붙인다. 대표 형태는 앞의 값이다. */
  @Test
  void stripsTheOtherSuffix() {
    assertEquals("FULL_TIME", classifier.classify("정규직 외"));
    assertEquals("CONTRACT", classifier.classify("계약직 외"));
  }

  /** 복합 표기는 원천이 대표 고용형태를 앞에 둔다. 첫 항목으로 판정한다. */
  @Test
  void takesTheFirstLabelOfACompoundValue() {
    assertEquals("FULL_TIME", classifier.classify("정규직·계약직"));
    assertEquals("FULL_TIME", classifier.classify("정규직·인턴직"));
    assertEquals("FULL_TIME", classifier.classify("정규직·프리랜서"));
    assertEquals("FULL_TIME", classifier.classify("정규직·전문연구요원"));
    assertEquals("FULL_TIME", classifier.classify("정규직·기간제"));
    assertEquals("CONTRACT", classifier.classify("계약직·프리랜서"));
    assertEquals("CONTRACT", classifier.classify("계약직·인턴직"));
    assertEquals("CONTRACT", classifier.classify("기간제·계약직"));
  }
}
