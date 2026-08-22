package com.carix.authservice.magiclink;

public class MagicLinkInvalidException extends RuntimeException {

  public MagicLinkInvalidException() {
    super("링크가 만료되었습니다");
  }
}
