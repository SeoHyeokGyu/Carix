package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KeyRing 단위 테스트.
 *
 * <p>검증 항목:
 *
 * <ul>
 *   <li>1. 단일 레거시 키({@code CARIX_ENCRYPTION_KEY})만 주어진 경우 자동으로 {@code v1} 활성 키 등록
 *   <li>2. 다중 키 링({@code CARIX_ENCRYPTION_KEYS}) 파싱 및 지정된 활성 키({@code activeKeyId}) 바인딩
 *   <li>3. 활성 키 미지정 시 {@code v1} 기본 폴백
 *   <li>4. 존재하지 않는 활성 키 ID 지정 시 예외 발생 검증
 *   <li>5. 32바이트(256비트)가 아닌 비정상 키 길이에 대한 방어
 *   <li>6. 키 설정 부재 시 Fail-Fast 예외 발생 검증
 * </ul>
 */
class KeyRingTest {

  /** 테스트용 N바이트 더미 AES 키 생성 헬퍼 */
  private String generateKey(byte fill) {
    byte[] key = new byte[32];
    java.util.Arrays.fill(key, fill);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  @DisplayName("단일 레거시 키 주입 시 v1 활성 키로 자동 설정 (하위 호환)")
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

    // v2 가 활성 키로 승격되었는지 확인
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
  @DisplayName("존재하지 않는 active-key-id 지정 시 예외 발생 (잘못된 배포 방지)")
  void nonExistentActiveKeyId_throwsException() {
    String key1 = generateKey((byte) 1);
    String config = "v1:" + key1;

    assertThatThrownBy(() -> new KeyRing(config, "v99", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("v99");
  }

  @Test
  @DisplayName("키 길이가 32바이트(256비트)가 아니면 IllegalArgumentException 발생")
  void invalidKeyLength_throwsException() {
    // 16바이트 (128비트) - AES-256 규격 미달
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> KeyRing.ofLegacySingleKey(shortKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32바이트");
  }

  @Test
  @DisplayName("키 설정이 전혀 없으면 Fail-Fast로 IllegalStateException 발생")
  void noKeyConfig_throwsException() {
    assertThatThrownBy(() -> new KeyRing(null, null, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new KeyRing("", "", "")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("정적 팩토리 메서드 of 동작 검증 (단위 테스트 배선용)")
  void staticFactoryOf() {
    String key1 = generateKey((byte) 1);
    String key2 = generateKey((byte) 2);

    KeyRing ring = KeyRing.of("v2", Map.of("v1", key1, "v2", key2));

    assertThat(ring.getActiveKeyId()).isEqualTo("v2");
    assertThat(ring.getActiveKey()).isNotNull();
    assertThat(ring.getKey("v1")).isNotNull();
  }
}
