package com.carix.jobposting.backfill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 잡코리아 IT 카테고리의 직무 분류 코드. 원천은 코드 이름을 상세페이지에 싣지 않아, 목록 크롤러가 IT 카테고리(dutyCtgr=10031)에서 모은 공고들에 자주 붙은
 * 코드를 모아 만든 목록이다({@code backfill/jobkorea-it-codes.txt}). 직무군(AI·DATA·DEVELOPER)은 이 목록이 아니라 제목으로
 * 가른다.
 */
public class JobKoreaItCodes {

  private static final String RESOURCE = "/backfill/jobkorea-it-codes.txt";

  private final Set<String> codes;

  public JobKoreaItCodes(Set<String> codes) {
    this.codes = Set.copyOf(codes);
  }

  public static JobKoreaItCodes bundled() {
    try (InputStream in = JobKoreaItCodes.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " 가 없습니다");
      }
      Set<String> codes = new HashSet<>();
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        String code = line.strip();
        if (!code.isEmpty() && !code.startsWith("#")) {
          codes.add(code);
        }
      }
      return new JobKoreaItCodes(codes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public int size() {
    return codes.size();
  }

  public boolean isIt(List<String> postingCodes) {
    return postingCodes.stream().anyMatch(codes::contains);
  }
}
