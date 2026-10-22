package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompanySizeClassifierTest {

  private final CompanySizeClassifier classifier = new CompanySizeClassifier();

  @Test
  void classifiesByEmployeeCount() {
    assertEquals("LARGE", classifier.classify(1270, "중견기업"));
    assertEquals("LARGE", classifier.classify(300, "중소기업"));
    assertEquals("MEDIUM", classifier.classify(299, "중소기업"));
    assertEquals("MEDIUM", classifier.classify(50, "벤처기업"));
    assertEquals("STARTUP", classifier.classify(49, "중소기업"));
    assertEquals("STARTUP", classifier.classify(1, null));
  }

  @Test
  void prefersEmployeeCountOverCompanyType() {
    // 사원수가 있으면 그 값이 판정 기준이다. 기업 구분은 사원수가 없을 때만 쓴다.
    assertEquals("STARTUP", classifier.classify(12, "중견기업"));
    assertEquals("LARGE", classifier.classify(5970, "중소기업"));
  }

  @Test
  void fallsBackToCompanyTypeWhenEmployeeCountIsMissing() {
    assertEquals("LARGE", classifier.classify(null, "대기업"));
    assertEquals("LARGE", classifier.classify(null, "대기업 계열사·자회사"));
    assertEquals("LARGE", classifier.classify(null, "중견기업"));
    assertEquals("LARGE", classifier.classify(null, "국내 공공기관·공기업"));
    assertEquals("MEDIUM", classifier.classify(null, "중소기업"));
    assertEquals("STARTUP", classifier.classify(null, "벤처기업"));
  }

  @Test
  void treatsNonPositiveEmployeeCountAsMissing() {
    assertEquals("MEDIUM", classifier.classify(0, "중소기업"));
    assertEquals("UNKNOWN", classifier.classify(-1, null));
  }
}
