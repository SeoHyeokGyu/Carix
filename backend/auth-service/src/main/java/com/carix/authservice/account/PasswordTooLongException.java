package com.carix.authservice.account;

/**
 * BCrypt 입력 한계(72바이트)를 넘은 비밀번호.
 *
 * <p>그냥 넘기면 73바이트째부터가 조용히 잘려 나가 사용자가 입력한 것과 다른 비밀번호가 저장된다. 잘린 줄 모르고 전체를 입력하면 로그인은 되지만, 보안 강도는
 * 72바이트짜리다. 명시적으로 거부한다.
 */
public class PasswordTooLongException extends RuntimeException {

  public PasswordTooLongException() {
    super("비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다");
  }
}
