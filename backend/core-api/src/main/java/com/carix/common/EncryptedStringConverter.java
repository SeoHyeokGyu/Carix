package com.carix.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 문자열 컬럼을 AES-256-GCM 으로 암호화하는 JPA AttributeConverter. 암호문 포맷·키·IV·하위 호환은 {@link
 * EncryptedConverter} 와 같다 ({@code {keyId}:{Base64(IV + CipherText + Tag)}}). {@link
 * EncryptedConverter} 도 이 클래스로 암호화한다.
 *
 * <p>적용 대상 컬럼: {@code core.career_scenario.payload_encrypted} (엔진 2 시나리오의 요청·결과 JSON, SECURITY
 * §3.1).
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  /** AES-GCM 암호화 변환 알고리즘 */
  private static final String ALGO = "AES/GCM/NoPadding";

  /** GCM 인증 태그 비트 길이 (128비트 표준) */
  private static final int TAG_BITS = 128;

  /** GCM 권장 IV 바이트 길이 (12바이트 = 96비트) */
  private static final int IV_LEN = 12;

  /** 다중 대칭키 관리자 */
  private final KeyRing keyRing;

  /**
   * 스프링 의존성 주입용 메인 생성자.
   *
   * <p>{@code keyRing} 빈이 컨텍스트에 존재하면 주입받아 사용하고, 슬라이스 테스트({@code @DataJpaTest}) 등 빈이 스캔되지 않는 격리
   * 환경에서는 주입된 환경변수 값으로 자체 KeyRing을 생성한다.
   *
   * @param keyRing 스프링 관리 KeyRing 빈 (선택적)
   * @param keysConfig 다중 키 링 환경변수 문자열
   * @param activeKeyIdConfig 활성 키 ID 환경변수 문자열
   * @param legacyKeyConfig 단일 레거시 키 환경변수 문자열
   */
  @Autowired
  public EncryptedStringConverter(
      @Autowired(required = false) KeyRing keyRing,
      @Value("${carix.encryption.keys:${CARIX_ENCRYPTION_KEYS:}}") String keysConfig,
      @Value("${carix.encryption.active-key-id:${CARIX_ENCRYPTION_ACTIVE_KEY_ID:}}")
          String activeKeyIdConfig,
      @Value("${carix.encryption-key:${CARIX_ENCRYPTION_KEY:}}") String legacyKeyConfig) {
    if (keyRing != null) {
      this.keyRing = keyRing;
    } else {
      this.keyRing = new KeyRing(keysConfig, activeKeyIdConfig, legacyKeyConfig);
    }
  }

  /**
   * KeyRing 인스턴스를 직접 주입하는 생성자 (단위 테스트 및 명시적 배선용).
   *
   * @param keyRing 다중 키 관리자 객체
   */
  public EncryptedStringConverter(KeyRing keyRing) {
    if (keyRing == null) {
      throw new IllegalStateException("KeyRing 은 null 일 수 없습니다.");
    }
    this.keyRing = keyRing;
  }

  /**
   * 단일 Base64 키 문자열을 사용하는 생성자 (레거시 호환 및 빠른 테스트용).
   *
   * @param singleKey 32바이트 Base64 인코딩 대칭키
   */
  public EncryptedStringConverter(String singleKey) {
    this(KeyRing.ofLegacySingleKey(singleKey));
  }

  /** 컨버터에 바인딩된 KeyRing 인스턴스를 반환한다. */
  public KeyRing getKeyRing() {
    return keyRing;
  }

  /**
   * 엔티티의 평문 문자열을 데이터베이스 저장용 암호문 문자열({@code {keyId}:{Base64}})로 암호화한다.
   *
   * <p>현재 활성화된 키({@link KeyRing#getActiveKey()})를 사용하며, 매 호출마다 새로운 랜덤 IV를 발급한다.
   *
   * @param attribute 평문 (null 이면 null 반환)
   * @return {@code {keyId}:{Base64(IV + Ciphertext + Tag)}} 형태의 문자열
   * @throws IllegalStateException 암호화 Cipher 초기화 또는 수행 중 실패한 경우
   */
  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) return null;
    try {
      // 1. 매 암호화마다 고유한 12바이트 암호학적 난수 IV 생성 (IV 재사용 절대 금지)
      byte[] iv = new byte[IV_LEN];
      new SecureRandom().nextBytes(iv);

      // 2. AES-GCM 암호화 초기화 및 실행
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, keyRing.getActiveKey(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

      // 3. [IV(12바이트)][암호문 + 인증태그(16바이트)] 결합
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      // 4. 활성 키 식별자 접두사와 함께 Base64 문자열로 직렬화하여 반환
      return keyRing.getActiveKeyId() + ":" + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("암호화 실패", e);
    }
  }

  /**
   * 데이터베이스의 암호문 문자열을 복호화하여 엔티티의 평문 문자열로 변환한다.
   *
   * <p>문자열에 콜론({@code :})이 있으면 앞부분의 {@code keyId}에 매핑된 키로 복호화하고, 접두사가 없는 레거시 암호문은 기본 레거시 키({@link
   * KeyRing#getLegacyKey()})로 복호화한다.
   *
   * @param dbData 데이터베이스에 저장된 암호문 문자열 (null 이면 null 반환)
   * @return 복호화된 평문
   * @throws IllegalStateException 복호화 키 미존재, 데이터 변조, 포맷 불일치 등 복호화 실패 시
   */
  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    try {
      SecretKeySpec key;
      String cipherBase64;

      // 1. 버전 식별자 접두사({keyId}:) 유무 판별
      int colonIdx = dbData.indexOf(':');
      if (colonIdx > 0) {
        String keyId = dbData.substring(0, colonIdx);
        cipherBase64 = dbData.substring(colonIdx + 1);
        key = keyRing.getKey(keyId);
      } else {
        // 콜론이 없는 과거 레거시 암호문 (v1 키로 폴백)
        key = keyRing.getLegacyKey();
        cipherBase64 = dbData;
      }

      // 2. Base64 디코딩 후 [IV(12바이트)] 와 [암호문+태그] 분리
      byte[] combined = Base64.getDecoder().decode(cipherBase64);
      byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_LEN);
      byte[] cipherText = java.util.Arrays.copyOfRange(combined, IV_LEN, combined.length);

      // 3. AES-GCM 복호화 및 무결성 검증 (태그 불일치 시 복호화 실패)
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("복호화 실패", e);
    }
  }
}
