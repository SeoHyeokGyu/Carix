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

class EncryptedConverterTest {

  private String testKey;
  private EncryptedConverter converter;

  @BeforeEach
  void setUp() {
    testKey = Base64.getEncoder().encodeToString(new byte[32]);
    converter = new EncryptedConverter(testKey);
  }

  @Test
  @DisplayName("키가 없으면 IllegalStateException 발생")
  void constructor_noKey() {
    assertThatThrownBy(() -> new EncryptedConverter((String) null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EncryptedConverter("")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EncryptedConverter((KeyRing) null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("null 변환 시 null 반환")
  void convert_null() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("암호화 및 복호화 라운드트립 성공 (v1 접두사 포함)")
  void roundTrip() {
    Long original = 65000000L;
    String encrypted = converter.convertToDatabaseColumn(original);

    assertThat(encrypted).isNotNull().startsWith("v1:");

    Long decrypted = converter.convertToEntityAttribute(encrypted);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  @DisplayName("접두사가 없는 레거시 암호문도 정상 복호화(하위 호환)")
  void decrypt_legacyCiphertextWithoutPrefix() throws Exception {
    Long original = 72000000L;

    // 접두사 없이 기존 형식으로 직접 암호화
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

    // 콜론(:) 접두사가 없음을 확인
    assertThat(legacyCiphertext).doesNotContain(":");

    // 복호화 성공 검증
    Long decrypted = converter.convertToEntityAttribute(legacyCiphertext);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  @DisplayName("다중 키 링: v1으로 암호화된 데이터를 v2 활성 키 상태에서도 복호화 가능")
  void multiKeyRotation_decryptsOlderVersions() {
    String keyV1 = testKey;
    byte[] rawV2 = new byte[32];
    rawV2[0] = 1;
    String keyV2 = Base64.getEncoder().encodeToString(rawV2);

    // 1단계: v1 컨버터로 데이터 암호화
    String encryptedV1 = converter.convertToDatabaseColumn(55000000L);
    assertThat(encryptedV1).startsWith("v1:");

    // 2단계: v2 가 active 인 키 링 생성 및 컨버터 구성
    KeyRing keyRing = KeyRing.of("v2", Map.of("v1", keyV1, "v2", keyV2));
    EncryptedConverter rotatedConverter = new EncryptedConverter(keyRing);

    // 3단계: v2 컨버터가 v1 암호문을 정상 복호화
    Long decryptedOld = rotatedConverter.convertToEntityAttribute(encryptedV1);
    assertThat(decryptedOld).isEqualTo(55000000L);

    // 4단계: 새 데이터 암호화 시 v2 접두사 생성 및 복호화 확인
    String encryptedV2 = rotatedConverter.convertToDatabaseColumn(80000000L);
    assertThat(encryptedV2).startsWith("v2:");
    Long decryptedNew = rotatedConverter.convertToEntityAttribute(encryptedV2);
    assertThat(decryptedNew).isEqualTo(80000000L);
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
