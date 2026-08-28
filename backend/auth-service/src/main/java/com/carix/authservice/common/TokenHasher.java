package com.carix.authservice.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 토큰을 DB 에 넣기 전에 거치는 단방향 해시.
 *
 * <p>원본은 쿠키·링크에만 있고 DB 에는 해시만 남긴다. 세션 토큰은 30일짜리 자격증명이라 DB 유출 시 피해가 가장 크다.
 *
 * <p>솔트를 쓰지 않는다. 입력이 32바이트 난수라 사전 공격 대상이 아니고, 조회가 해시 일치로 이뤄져야 하므로 같은 입력이 같은 해시를 내야 한다.
 */
public final class TokenHasher {

  private TokenHasher() {}

  public static String sha256Hex(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
