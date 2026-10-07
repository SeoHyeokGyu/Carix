package com.carix.jobposting.wanted;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

/** 원티드 목록 API·공고 페이지·상세 API 를 연다. 테스트는 가짜 구현을 넣는다. */
public interface WantedClient {

  int LIST_LIMIT = 100;

  Optional<String> fetchList(int offset);

  Optional<String> fetchPage(long id);

  Optional<String> fetchDetailApi(long id);

  @Slf4j
  final class Http implements WantedClient {

    private static final String LIST_URL =
        "https://www.wanted.co.kr/api/v4/jobs?country=kr&tag_type_ids=518&job_sort=job.latest_order"
            + "&years=-1&locations=all&limit="
            + LIST_LIMIT
            + "&offset=";
    private static final String PAGE_URL = "https://www.wanted.co.kr/wd/";
    private static final String DETAIL_URL = "https://www.wanted.co.kr/api/v4/jobs/";
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
    public Optional<String> fetchList(int offset) {
      return get(LIST_URL + offset);
    }

    @Override
    public Optional<String> fetchPage(long id) {
      return get(PAGE_URL + id);
    }

    @Override
    public Optional<String> fetchDetailApi(long id) {
      return get(DETAIL_URL + id);
    }

    private Optional<String> get(String url) {
      try {
        // maxBodySize(0): 공고 페이지 HTML 이 Jsoup 기본 상한(2MB)을 넘을 수 있다.
        Connection.Response response =
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .timeout(TIMEOUT_MS)
                .execute();
        int status = response.statusCode();
        if (status == HTTP_OK) {
          return Optional.of(response.body());
        }
        if (status != HTTP_NOT_FOUND) {
          log.warn("[원티드] {} 응답 {}", url, status);
        }
        return Optional.empty();
      } catch (Exception e) {
        log.warn("[원티드] {} 요청 실패: {}", url, e.getMessage());
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
