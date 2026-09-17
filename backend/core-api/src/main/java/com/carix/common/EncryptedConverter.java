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
 * 민감 컬럼을 AES-GCM으로 암호화해 저장한다.
 *
 * <p>KeyRing을 통해 다중 키 및 키 로테이션을 지원한다 (SECURITY §3.2). 암호문 포맷: {@code {keyId}:{Base64(IV +
 * CipherText + Tag)}} 콜론({@code :}) 접두사가 없는 레거시 암호문은 레거시 키(기본 v1)로 복호화한다.
 *
 * <p>현재는 금액(Long) 전용이다 — {@code income_history.gross_salary_encrypted}, {@code
 * net_salary_encrypted}, {@code expense_item.amount_encrypted}, {@code career_scenario.*}.
 */
@Converter
@Component
public class EncryptedConverter implements AttributeConverter<Long, String> {

  private static final String ALGO = "AES/GCM/NoPadding";
  private static final int TAG_BITS = 128;
  private static final int IV_LEN = 12;

  private final KeyRing keyRing;

  @Autowired
  public EncryptedConverter(
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

  /** KeyRing 직접 주입 생성자 (테스트 및 수동 배선용). */
  public EncryptedConverter(KeyRing keyRing) {
    if (keyRing == null) {
      throw new IllegalStateException("KeyRing 은 null 일 수 없습니다.");
    }
    this.keyRing = keyRing;
  }

  /** 단일 키(레거시 또는 테스트용) 생성자. */
  public EncryptedConverter(String singleKey) {
    this(KeyRing.ofLegacySingleKey(singleKey));
  }

  public KeyRing getKeyRing() {
    return keyRing;
  }

  @Override
  public String convertToDatabaseColumn(Long attribute) {
    if (attribute == null) return null;
    try {
      byte[] iv = new byte[IV_LEN];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, keyRing.getActiveKey(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText =
          cipher.doFinal(String.valueOf(attribute).getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return keyRing.getActiveKeyId() + ":" + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("암호화 실패", e);
    }
  }

  @Override
  public Long convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    try {
      SecretKeySpec key;
      String cipherBase64;

      int colonIdx = dbData.indexOf(':');
      if (colonIdx > 0) {
        String keyId = dbData.substring(0, colonIdx);
        cipherBase64 = dbData.substring(colonIdx + 1);
        key = keyRing.getKey(keyId);
      } else {
        key = keyRing.getLegacyKey();
        cipherBase64 = dbData;
      }

      byte[] combined = Base64.getDecoder().decode(cipherBase64);
      byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_LEN);
      byte[] cipherText = java.util.Arrays.copyOfRange(combined, IV_LEN, combined.length);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return Long.parseLong(new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("복호화 실패", e);
    }
  }
}
