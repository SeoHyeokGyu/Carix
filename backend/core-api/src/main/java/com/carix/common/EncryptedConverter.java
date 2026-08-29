package com.carix.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 민감 컬럼을 AES-GCM으로 암호화해 저장한다.
 *
 * <p>현재는 금액(Long) 전용이다 — {@code income_history.gross_salary_encrypted}, {@code
 * net_salary_encrypted}, {@code expense_item.amount_encrypted}. 다른 타입 암호화가 필요해지면 그때 확장한다.
 */
@Converter
@Component
public class EncryptedConverter implements AttributeConverter<Long, String> {

  private static final String ALGO = "AES/GCM/NoPadding";
  private static final int TAG_BITS = 128;
  private static final int IV_LEN = 12;

  private final SecretKeySpec key;

  public EncryptedConverter(
      @Value("${carix.encryption-key:${CARIX_ENCRYPTION_KEY:}}") String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("carix.encryption-key (또는 CARIX_ENCRYPTION_KEY) 값이 없습니다.");
    }
    this.key = new SecretKeySpec(Base64.getDecoder().decode(secret.trim()), "AES");
  }

  @Override
  public String convertToDatabaseColumn(Long attribute) {
    if (attribute == null) return null;
    try {
      byte[] iv = new byte[IV_LEN];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGO);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText =
          cipher.doFinal(String.valueOf(attribute).getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("암호화 실패", e);
    }
  }

  @Override
  public Long convertToEntityAttribute(String dbData) {
    if (dbData == null) return null;
    try {
      byte[] combined = Base64.getDecoder().decode(dbData);
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
