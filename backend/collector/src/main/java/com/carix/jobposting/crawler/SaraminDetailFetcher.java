package com.carix.jobposting.crawler;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

/** 사람인 상세페이지를 공고 번호로 연다. 200 이 아니면 비운다. 요청마다 간격을 둔다. */
@Slf4j
public class SaraminDetailFetcher {

  private static final String DETAIL_URL =
      "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final int TIMEOUT_MS = 10_000;
  private static final int HTTP_OK = 200;

  private final long delayMs;

  public SaraminDetailFetcher(long delayMs) {
    this.delayMs = Math.max(0L, delayMs);
  }

  public Optional<String> fetch(String externalId) {
    try {
      Connection.Response response =
          Jsoup.connect(DETAIL_URL + externalId)
              .userAgent(USER_AGENT)
              .timeout(TIMEOUT_MS)
              .ignoreHttpErrors(true)
              .execute();
      return response.statusCode() == HTTP_OK ? Optional.of(response.body()) : Optional.empty();
    } catch (Exception e) {
      log.warn("[사람인] 상세페이지 {} 요청 실패: {}", externalId, e.getMessage());
      return Optional.empty();
    } finally {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
