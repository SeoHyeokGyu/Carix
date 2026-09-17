package com.carix.common;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대칭키 다중 버전 관리(Key Ring).
 *
 * <p>연봉 암호화 키 로테이션(SECURITY §3.2)을 지원하기 위해 복수의 대칭키를 식별자({@code keyId})와 함께 관리한다. 신규 암호화에는 {@link
 * #getActiveKey()} 를 사용하고, 복호화 시에는 암호문에 기록된 {@code keyId} 에 해당하는 키를 찾아 사용한다.
 *
 * <p>설정 우선순위:
 *
 * <ol>
 *   <li>{@code carix.encryption.keys} (또는 {@code CARIX_ENCRYPTION_KEYS}): {@code
 *       v1:base64Key1,v2:base64Key2}
 *   <li>{@code carix.encryption-key} (또는 {@code CARIX_ENCRYPTION_KEY}): 단일 레거시 키 (자동으로 {@code v1}
 *       할당)
 * </ol>
 */
@Component
public class KeyRing {

  public static final String DEFAULT_LEGACY_KEY_ID = "v1";
  private static final int AES_256_KEY_BYTES = 32;

  private final String activeKeyId;
  private final String legacyKeyId;
  private final Map<String, SecretKeySpec> keys;

  @org.springframework.beans.factory.annotation.Autowired
  public KeyRing(
      @Value("${carix.encryption.keys:${CARIX_ENCRYPTION_KEYS:}}") String keysConfig,
      @Value("${carix.encryption.active-key-id:${CARIX_ENCRYPTION_ACTIVE_KEY_ID:}}")
          String activeKeyIdConfig,
      @Value("${carix.encryption-key:${CARIX_ENCRYPTION_KEY:}}") String legacyKeyConfig) {

    Map<String, SecretKeySpec> parsedKeys = new LinkedHashMap<>();

    if (keysConfig != null && !keysConfig.isBlank()) {
      String[] pairs = keysConfig.split(",");
      for (String pair : pairs) {
        String trimmed = pair.trim();
        if (trimmed.isEmpty()) continue;
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon >= trimmed.length() - 1) {
          throw new IllegalArgumentException(
              "올바르지 않은 키 링 형식입니다. 'keyId:base64Key' 형식이어야 합니다: " + trimmed);
        }
        String keyId = trimmed.substring(0, colon).trim();
        String secret = trimmed.substring(colon + 1).trim();
        parsedKeys.put(keyId, createKeySpec(secret, keyId));
      }
    }

    if (legacyKeyConfig != null && !legacyKeyConfig.isBlank()) {
      parsedKeys.putIfAbsent(
          DEFAULT_LEGACY_KEY_ID, createKeySpec(legacyKeyConfig.trim(), DEFAULT_LEGACY_KEY_ID));
    }

    if (parsedKeys.isEmpty()) {
      throw new IllegalStateException("carix.encryption.keys 또는 CARIX_ENCRYPTION_KEY 설정이 없습니다.");
    }

    this.keys = Collections.unmodifiableMap(parsedKeys);

    if (activeKeyIdConfig != null && !activeKeyIdConfig.isBlank()) {
      String trimmedActive = activeKeyIdConfig.trim();
      if (!this.keys.containsKey(trimmedActive)) {
        throw new IllegalStateException(
            "지정된 active-key-id '%s' 가 키 링 목록에 존재하지 않습니다. (등록된 키: %s)"
                .formatted(trimmedActive, this.keys.keySet()));
      }
      this.activeKeyId = trimmedActive;
    } else {
      if (this.keys.containsKey(DEFAULT_LEGACY_KEY_ID)) {
        this.activeKeyId = DEFAULT_LEGACY_KEY_ID;
      } else {
        this.activeKeyId = this.keys.keySet().iterator().next();
      }
    }

    if (this.keys.containsKey(DEFAULT_LEGACY_KEY_ID)) {
      this.legacyKeyId = DEFAULT_LEGACY_KEY_ID;
    } else {
      this.legacyKeyId = this.keys.keySet().iterator().next();
    }
  }

  private KeyRing(String activeKeyId, String legacyKeyId, Map<String, SecretKeySpec> keys) {
    this.activeKeyId = activeKeyId;
    this.legacyKeyId = legacyKeyId;
    this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
  }

  public static KeyRing ofLegacySingleKey(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException("암호화 키가 비어있습니다.");
    }
    SecretKeySpec spec = createKeySpec(base64Key.trim(), DEFAULT_LEGACY_KEY_ID);
    return new KeyRing(
        DEFAULT_LEGACY_KEY_ID, DEFAULT_LEGACY_KEY_ID, Map.of(DEFAULT_LEGACY_KEY_ID, spec));
  }

  public static KeyRing of(String activeKeyId, Map<String, String> keyIdToBase64Map) {
    if (keyIdToBase64Map == null || keyIdToBase64Map.isEmpty()) {
      throw new IllegalStateException("키 링 목록이 비어있습니다.");
    }
    Map<String, SecretKeySpec> map = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : keyIdToBase64Map.entrySet()) {
      map.put(entry.getKey(), createKeySpec(entry.getValue(), entry.getKey()));
    }
    if (!map.containsKey(activeKeyId)) {
      throw new IllegalStateException("activeKeyId '%s' 가 키 링에 존재하지 않습니다.".formatted(activeKeyId));
    }
    String legacy =
        map.containsKey(DEFAULT_LEGACY_KEY_ID)
            ? DEFAULT_LEGACY_KEY_ID
            : map.keySet().iterator().next();
    return new KeyRing(activeKeyId, legacy, map);
  }

  private static SecretKeySpec createKeySpec(String base64Key, String keyId) {
    try {
      byte[] decoded = Base64.getDecoder().decode(base64Key);
      if (decoded.length != AES_256_KEY_BYTES) {
        throw new IllegalArgumentException(
            "AES-256 키 길이는 32바이트(256비트)여야 합니다. (keyId=%s, 길이=%d바이트)"
                .formatted(keyId, decoded.length));
      }
      return new SecretKeySpec(decoded, "AES");
    } catch (IllegalArgumentException e) {
      if (e.getMessage() != null && e.getMessage().contains("AES-256")) {
        throw e;
      }
      throw new IllegalArgumentException("올바른 Base64 인코딩이 아닙니다. (keyId=" + keyId + ")", e);
    }
  }

  public SecretKeySpec getActiveKey() {
    return keys.get(activeKeyId);
  }

  public String getActiveKeyId() {
    return activeKeyId;
  }

  public SecretKeySpec getKey(String keyId) {
    SecretKeySpec key = keys.get(keyId);
    if (key == null) {
      throw new IllegalStateException("키 링에서 키를 찾을 수 없습니다: " + keyId);
    }
    return key;
  }

  public SecretKeySpec getLegacyKey() {
    return keys.get(legacyKeyId);
  }

  public String getLegacyKeyId() {
    return legacyKeyId;
  }

  public boolean hasKey(String keyId) {
    return keys.containsKey(keyId);
  }

  public Set<String> getKeyIds() {
    return keys.keySet();
  }
}
