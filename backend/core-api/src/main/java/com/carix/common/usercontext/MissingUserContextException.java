package com.carix.common.usercontext;

/**
 * {@code X-User-Id} 헤더가 없거나 UUID 로 읽을 수 없을 때. {@code GlobalExceptionHandler} 가 401 로 변환한다.
 *
 * <p>운영에서 이 예외가 보인다면 게이트웨이를 우회한 직접 호출이거나 세션 검증 플래그가 꺼져 있는 것이다.
 */
public class MissingUserContextException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MissingUserContextException(String message) {
    super(message);
  }
}
