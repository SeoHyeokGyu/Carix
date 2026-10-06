package com.carix.jobposting.backfill;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

/** 잡코리아 상세페이지를 ID 로 연다. 200 이 아니면(삭제·비공개) 비운다. 요청마다 간격을 둬 원천에 부담을 주지 않는다. */
@Slf4j
public class JobKoreaPageFetcher {

  private static final String DETAIL_URL = "https://www.jobkorea.co.kr/Recruit/GI_Read/";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final int TIMEOUT_MS = 10_000;
  private static final int HTTP_OK = 200;

  private final long delayMs;

  public JobKoreaPageFetcher(long delayMs) {
    this.delayMs = Math.max(0L, delayMs);
  }

  public Optional<String> fetch(long id) {
    try {
      Connection.Response response =
          Jsoup.connect(DETAIL_URL + id)
              .userAgent(USER_AGENT)
              .timeout(TIMEOUT_MS)
              .ignoreHttpErrors(true)
              .execute();
      return response.statusCode() == HTTP_OK ? Optional.of(response.body()) : Optional.empty();
    } catch (Exception e) {
      log.warn("[백필] 상세페이지 {} 요청 실패: {}", id, e.getMessage());
      return Optional.empty();
    } finally {
      pause();
    }
  }

  private void pause() {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
