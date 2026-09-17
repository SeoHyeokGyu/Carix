package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyRingTest {

  private String generateKey(byte fill) {
    byte[] key = new byte[32];
    java.util.Arrays.fill(key, fill);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  @DisplayName("단일 레거시 키 주입 시 v1 활성 키로 자동 설정")
  void legacySingleKeyConfig() {
    String secret = generateKey((byte) 1);
    KeyRing keyRing = new KeyRing(null, null, secret);

    assertThat(keyRing.getActiveKeyId()).isEqualTo("v1");
    assertThat(keyRing.getLegacyKeyId()).isEqualTo("v1");
    assertThat(keyRing.hasKey("v1")).isTrue();
    assertThat(keyRing.getKey("v1")).isNotNull();
    assertThat(keyRing.getActiveKey()).isNotNull();
  }

  @Test
  @DisplayName("다중 키 링 설정 및 active-key-id 지정 성공")
  void multiKeyConfig() {
    String key1 = generateKey((byte) 1);
    String key2 = generateKey((byte) 2);
    String config = "v1:" + key1 + ",v2:" + key2;

    KeyRing keyRing = new KeyRing(config, "v2", null);

    assertThat(keyRing.getActiveKeyId()).isEqualTo("v2");
    assertThat(keyRing.getLegacyKeyId()).isEqualTo("v1");
    assertThat(keyRing.getKeyIds()).containsExactlyInAnyOrder("v1", "v2");
    assertThat(keyRing.getKey("v1")).isNotNull();
    assertThat(keyRing.getKey("v2")).isNotNull();
  }

  @Test
  @DisplayName("active-key-id 미지정 시 v1 이 있으면 v1을 기본값으로 사용")
  void multiKeyConfig_defaultsToV1() {
    String key1 = generateKey((byte) 1);
    String key2 = generateKey((byte) 2);
    String config = "v1:" + key1 + ",v2:" + key2;

    KeyRing keyRing = new KeyRing(config, null, null);

    assertThat(keyRing.getActiveKeyId()).isEqualTo("v1");
  }

  @Test
  @DisplayName("존재하지 않는 active-key-id 지정 시 예외 발생")
  void nonExistentActiveKeyId_throwsException() {
    String key1 = generateKey((byte) 1);
    String config = "v1:" + key1;

    assertThatThrownBy(() -> new KeyRing(config, "v99", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("v99");
  }

  @Test
  @DisplayName("키 길이가 32바이트가 아니면 IllegalArgumentException 발생")
  void invalidKeyLength_throwsException() {
    // 16바이트 (128비트)
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> KeyRing.ofLegacySingleKey(shortKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32바이트");
  }

  @Test
  @DisplayName("키 설정이 전혀 없으면 IllegalStateException 발생")
  void noKeyConfig_throwsException() {
    assertThatThrownBy(() -> new KeyRing(null, null, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new KeyRing("", "", "")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("정적 팩토리 메서드 of 동작 검증")
  void staticFactoryOf() {
    String key1 = generateKey((byte) 1);
    String key2 = generateKey((byte) 2);

    KeyRing ring = KeyRing.of("v2", Map.of("v1", key1, "v2", key2));

    assertThat(ring.getActiveKeyId()).isEqualTo("v2");
    assertThat(ring.getActiveKey()).isNotNull();
    assertThat(ring.getKey("v1")).isNotNull();
  }
}
