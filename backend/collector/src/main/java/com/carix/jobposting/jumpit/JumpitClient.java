package com.carix.jobposting.jumpit;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

/** 점핏 목록·상세 API 를 연다. 테스트는 가짜 구현을 넣는다. */
public interface JumpitClient {

  Optional<String> fetchListPage(int page);

  Optional<String> fetchDetail(String id);

  @Slf4j
  final class Http implements JumpitClient {

    private static final String LIST_URL =
        "https://jumpit-api.saramin.co.kr/api/positions?sort=reg_dt&highlight=false&page=";
    private static final String DETAIL_URL = "https://jumpit-api.saramin.co.kr/api/position/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;

    private final long delayMs;

    public Http(long delayMs) {
      this.delayMs = Math.max(0L, delayMs);
    }

    @Override
    public Optional<String> fetchListPage(int page) {
      return get(LIST_URL + page);
    }

    @Override
    public Optional<String> fetchDetail(String id) {
      return get(DETAIL_URL + id);
    }

    private Optional<String> get(String url) {
      try {
        Connection.Response response =
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(TIMEOUT_MS)
                .execute();
        int status = response.statusCode();
        if (status == HTTP_OK) {
          return Optional.of(response.body());
        }
        if (status != HTTP_NOT_FOUND) {
          log.warn("[점핏] {} 응답 {}", url, status);
        }
        return Optional.empty();
      } catch (Exception e) {
        log.warn("[점핏] {} 요청 실패: {}", url, e.getMessage());
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
}
