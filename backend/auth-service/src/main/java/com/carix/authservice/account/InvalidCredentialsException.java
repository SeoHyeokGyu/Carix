package com.carix.authservice.account;

/** 로그인 실패. 없는 이메일과 비밀번호 불일치를 구분하지 않는다 — 구분하면 이메일 존재 여부가 응답으로 드러난다 (SECURITY.md §2). */
public class InvalidCredentialsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super("이메일 또는 비밀번호가 올바르지 않습니다");
  }
}
