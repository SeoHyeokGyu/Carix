package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EncryptedConverter 단위 테스트.
 *
 * <p>검증 항목:
 *
 * <ul>
 *   <li>1. 암호화 시 최신 활성 키 식별자({@code v1:}) 접두사 부여 및 복호화 라운드트립
 *   <li>2. 접두사가 없는 과거 레거시 암호문의 하위 호환 복호화 보장
 *   <li>3. 다중 키 로테이션 시나리오: 구버전({@code v1}) 암호문을 신규({@code v2}) 컨버터가 정상 복호화
 *   <li>4. 등록되지 않은 키 ID({@code v99}) 및 변조된 암호문 복호화 실패 시 방어 예외 검증
 * </ul>
 */
class EncryptedConverterTest {

  private String testKey;
  private EncryptedConverter converter;

  @BeforeEach
  void setUp() {
    testKey = Base64.getEncoder().encodeToString(new byte[32]);
    converter = new EncryptedConverter(testKey);
  }

  @Test
  @DisplayName("암호화 및 복호화 라운드트립 성공 (v1: 접두사 자동 부여 확인)")
  void roundTrip() {
    Long original = 65_000_000L;
    String encrypted = converter.convertToDatabaseColumn(original);

    // 암호문에 v1: 접두사가 포함되어야 함
    assertThat(encrypted).isNotNull().startsWith("v1:");
    assertThat(converter.getKeyRing()).isNotNull();
    assertThat(converter.getKeyRing().getActiveKeyId()).isEqualTo("v1");

    // 복호화 결과가 원본 금액과 정확히 일치해야 함
    Long decrypted = converter.convertToEntityAttribute(encrypted);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  @DisplayName("접두사가 없는 레거시 암호문도 정상 복호화(과거 데이터 무중단 호환)")
  void decrypt_legacyCiphertextWithoutPrefix() throws Exception {
    Long original = 72_000_000L;

    // 접두사 없이 기존 형식으로 직접 암호화하여 DB 기존 데이터 모사
    byte[] keyBytes = Base64.getDecoder().decode(testKey);
    SecretKeySpec spec = new SecretKeySpec(keyBytes, "AES");
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, spec, new GCMParameterSpec(128, iv));
    byte[] cipherText = cipher.doFinal(String.valueOf(original).getBytes(StandardCharsets.UTF_8));
    byte[] combined = new byte[iv.length + cipherText.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
    String legacyCiphertext = Base64.getEncoder().encodeToString(combined);

    // 콜론(:) 접두사가 없는 레거시 포맷 확인
    assertThat(legacyCiphertext).doesNotContain(":");

    // 접두사가 없어도 레거시 기본 키로 정상 복호화되는지 검증
    Long decrypted = converter.convertToEntityAttribute(legacyCiphertext);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  @DisplayName("다중 키 링 로테이션: v1으로 암호화된 데이터를 v2 활성 키 상태에서도 복호화 가능")
  void multiKeyRotation_decryptsOlderVersions() {
    String keyV1 = testKey;
    byte[] rawV2 = new byte[32];
    rawV2[0] = 1; // v1과 다른 새로운 키 생성
    String keyV2 = Base64.getEncoder().encodeToString(rawV2);

    // 1단계: v1 컨버터로 데이터 암호화 (기존 DB 데이터 발생)
    String encryptedV1 = converter.convertToDatabaseColumn(55_000_000L);
    assertThat(encryptedV1).startsWith("v1:");

    // 2단계: v2 가 active 로 설정된 새 키 링 생성 및 컨버터 구성 (키 로테이션 배포 상황)
    KeyRing keyRing = KeyRing.of("v2", Map.of("v1", keyV1, "v2", keyV2));
    EncryptedConverter rotatedConverter = new EncryptedConverter(keyRing);

    // 3단계: v2 컨버터가 기존 v1 접두사를 인식하고 v1 키로 복호화 성공
    Long decryptedOld = rotatedConverter.convertToEntityAttribute(encryptedV1);
    assertThat(decryptedOld).isEqualTo(55_000_000L);

    // 4단계: 이후 신규 암호화 시 v2: 접두사로 암호화되고 자체 복호화도 성공
    String encryptedV2 = rotatedConverter.convertToDatabaseColumn(80_000_000L);
    assertThat(encryptedV2).startsWith("v2:");
    Long decryptedNew = rotatedConverter.convertToEntityAttribute(encryptedV2);
    assertThat(decryptedNew).isEqualTo(80_000_000L);
  }

  @Test
  @DisplayName("등록되지 않은 keyId 암호문 복호화 시 IllegalStateException 발생")
  void decrypt_unknownKeyId() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("v99:some-invalid-data"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("잘못된 암호문 복호화 시 IllegalStateException 발생")
  void decrypt_invalid() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("invalid-data"))
        .isInstanceOf(IllegalStateException.class);
  }
}
