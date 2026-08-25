package com.carix.jobposting.dedup;

import com.google.re2j.Pattern;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 공고 제목 정규화 및 단어 토큰 기반 유사도 계산 유틸리티. */
@Component
public class TitleNormalizer {

  private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9가-힣\\s]");
  private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

  private static final Set<String> STOP_WORDS =
      Set.of(
          "채용", "모집", "공고", "부문", "영입", "사원", "직무", "담당자", "전문가", "신입", "경력", "대규모", "정규직", "계약직");

  /**
   * 공고 제목을 핵심 키워드 토큰 집합으로 정규화합니다.
   *
   * @param title 원본 공고 제목
   * @return 정제된 핵심 토큰 집합
   */
  public Set<String> extractKeywords(String title) {
    if (title == null || title.isBlank()) {
      return Set.of();
    }

    String cleaned = SPECIAL_CHARS.matcher(title).replaceAll(" ");
    cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim().toLowerCase();

    if (cleaned.isBlank()) {
      return Set.of();
    }

    return Arrays.stream(cleaned.split("\\s+"))
        .filter(word -> word.length() >= 2 && !STOP_WORDS.contains(word))
        .collect(Collectors.toSet());
  }

  /** 두 공고 제목의 자카드(Jaccard) 키워드 유사도를 계산합니다. (0.0 ~ 1.0) */
  public double calculateJaccardSimilarity(Set<String> tokens1, Set<String> tokens2) {
    if (tokens1.isEmpty() && tokens2.isEmpty()) {
      return 1.0;
    }
    if (tokens1.isEmpty() || tokens2.isEmpty()) {
      return 0.0;
    }

    Set<String> intersection = new HashSet<>(tokens1);
    intersection.retainAll(tokens2);

    Set<String> union = new HashSet<>(tokens1);
    union.addAll(tokens2);

    return (double) intersection.size() / union.size();
  }
}
