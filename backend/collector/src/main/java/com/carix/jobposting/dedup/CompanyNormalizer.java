package com.carix.jobposting.dedup;

import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;

/**
 * 회사명 정규화 유틸리티.
 *
 * <p>법인 표기('(주)', '주식회사', 'Inc.', 'Corp.' 등)와 특수문자를 제거하고 소문자 및 공백 압축을 수행하여 원천 간 동일 기업을 매칭합니다.
 */
@Component
public class CompanyNormalizer {

  private static final Pattern CORP_PREFIX_SUFFIX =
      Pattern.compile(
          "(?i)\\((주|유|합|사|재|특|유한|법인|주식회사|유한회사)\\)|"
              + "주식회사|유한회사|사단법인|재단법인|"
              + ",?\\s*(inc\\.?|corp\\.?|co\\.,?\\s*ltd\\.?|ltd\\.?|llc\\.?)");

  private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9가-힣]");

  /**
   * 회사명을 표준 매칭 키로 정규화합니다.
   *
   * @param companyName 원본 회사명
   * @return 정규화된 회사명 (공백 및 특수문자 제거, 소문자)
   */
  public String normalize(String companyName) {
    if (companyName == null || companyName.isBlank()) {
      return "";
    }

    String cleaned = CORP_PREFIX_SUFFIX.matcher(companyName).replaceAll("");
    cleaned = SPECIAL_CHARS.matcher(cleaned).replaceAll("");
    return cleaned.trim().toLowerCase();
  }
}
