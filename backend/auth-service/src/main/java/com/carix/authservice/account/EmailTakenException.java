package com.carix.authservice.account;

/** 가입 시 이미 쓰이는 이메일. 존재 여부를 숨기지 않는다 — 숨기면 사용자가 실패 원인을 알 수 없다 (SECURITY.md §2). */
public class EmailTakenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmailTakenException() {
    super("이미 사용 중인 이메일입니다");
  }
}
