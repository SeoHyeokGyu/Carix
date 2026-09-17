package com.carix.common;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 대칭키 다중 버전 관리자(Key Ring).
 *
 * <p><b>배경 및 목적 (SECURITY §3.2):</b> Carix는 연봉·실수령액·소비액 등 최고 수준의 개인 재무 데이터를 다루므로, 정기적인 키 로테이션 및 비상
 * 키 교체(침해 사고, 개발팀 이탈 등)를 지원해야 한다. 단일 고정 키 환경에서는 키 교체 시 기존 DB 데이터의 복호화가 불가능해지는 문제가 발생하므로, 복수의 대칭키를
 * 식별자({@code keyId})와 함께 관리하는 키 링 구조를 도입한다.
 *
 * <p><b>무중단 키 로테이션 4단계 절차:</b>
 *
 * <ol>
 *   <li><b>신규 키 주입:</b> {@code CARIX_ENCRYPTION_KEYS}에 {@code v2} 키를 추가 (Active: v1).
 *   <li><b>활성 키 승격:</b> {@code CARIX_ENCRYPTION_ACTIVE_KEY_ID=v2}로 변경하여 배포. 이후 신규 데이터는 {@code v2:}
 *       접두사로 암호화되며, 기존 {@code v1} 데이터는 구 키로 계속 정상 복호화된다.
 *   <li><b>백그라운드 재암호화:</b> {@code SensitiveDataRekeyingService}를 통해 기존 데이터를 {@code v2}로 일괄 재암호화.
 *   <li><b>구 키 폐기:</b> 잔여 구 암호문이 0건임을 확인한 후 환경변수에서 {@code v1} 키를 안전하게 제거.
 * </ol>
 *
 * <p><b>설정 우선순위 및 바인딩:</b>
 *
 * <ol>
 *   <li>{@code carix.encryption.keys} (또는 {@code CARIX_ENCRYPTION_KEYS}): {@code
 *       v1:base64Key1,v2:base64Key2} 형태의 다중 키 목록.
 *   <li>{@code carix.encryption.active-key-id} (또는 {@code CARIX_ENCRYPTION_ACTIVE_KEY_ID}): 신규 암호화에
 *       사용할 활성 키 ID.
 *   <li>{@code carix.encryption-key} (또는 {@code CARIX_ENCRYPTION_KEY}): 단일 레거시 키 환경변수 (하위 호환성을 위해
 *       자동으로 {@code v1} 할당).
 * </ol>
 */
@Component
public class KeyRing {

  /** 기본 레거시 키 식별자 */
  public static final String DEFAULT_LEGACY_KEY_ID = "v1";

  /** AES-256 표준 대칭키 바이트 길이 (256비트 = 32바이트) */
  private static final int AES_256_KEY_BYTES = 32;

  /** 현재 신규 암호화에 사용하는 활성 키 식별자 */
  private final String activeKeyId;

  /** 콜론(:) 접두사가 없는 레거시 암호문 해독에 사용하는 기본 키 식별자 */
  private final String legacyKeyId;

  /** 등록된 모든 키의 맵 (Key ID -> SecretKeySpec) */
  private final Map<String, SecretKeySpec> keys;

  /**
   * 스프링 환경 설정으로부터 키 링을 구성하는 메인 생성자.
   *
   * @param keysConfig 콤마로 구분된 {@code keyId:base64Key} 쌍 목록
   * @param activeKeyIdConfig 신규 암호화에 사용할 활성 키 ID (생략 시 v1 또는 첫 번째 키)
   * @param legacyKeyConfig 단일 레거시 Base64 키 (keysConfig 가 비어있거나 v1 이 누락된 경우 폴백)
   * @throws IllegalStateException 유효한 암호화 키 설정이 전혀 없거나 활성 키 ID가 목록에 없는 경우
   * @throws IllegalArgumentException 키 형식이 잘못되었거나 키 길이가 32바이트가 아닌 경우
   */
  @Autowired
  public KeyRing(
      @Value("${carix.encryption.keys:${CARIX_ENCRYPTION_KEYS:}}") String keysConfig,
      @Value("${carix.encryption.active-key-id:${CARIX_ENCRYPTION_ACTIVE_KEY_ID:}}")
          String activeKeyIdConfig,
      @Value("${carix.encryption-key:${CARIX_ENCRYPTION_KEY:}}") String legacyKeyConfig) {

    Map<String, SecretKeySpec> parsedKeys = new LinkedHashMap<>();

    // 1. 다중 키 링 설정 파싱 (콤마 구분: v1:KEY1,v2:KEY2)
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

    // 2. 단일 레거시 키 환경변수가 존재하고 v1이 아직 파싱되지 않았다면 v1으로 자동 등록
    if (legacyKeyConfig != null && !legacyKeyConfig.isBlank()) {
      parsedKeys.putIfAbsent(
          DEFAULT_LEGACY_KEY_ID, createKeySpec(legacyKeyConfig.trim(), DEFAULT_LEGACY_KEY_ID));
    }

    // 3. 등록된 키가 전혀 없으면 애플리케이션 시작 불가 (Fail-Fast)
    if (parsedKeys.isEmpty()) {
      throw new IllegalStateException("carix.encryption.keys 또는 CARIX_ENCRYPTION_KEY 설정이 없습니다.");
    }

    this.keys = Collections.unmodifiableMap(parsedKeys);

    // 4. activeKeyId 결정: 명시적 지정값 우선 검증, 미지정 시 v1 또는 첫 번째 키를 사용
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

    // 5. legacyKeyId 결정: 버전 프리픽스가 없는 과거 암호문 해독에 사용할 키 지정
    if (this.keys.containsKey(DEFAULT_LEGACY_KEY_ID)) {
      this.legacyKeyId = DEFAULT_LEGACY_KEY_ID;
    } else {
      this.legacyKeyId = this.keys.keySet().iterator().next();
    }
  }

  /** 테스트 및 팩토리 메서드용 내부 생성자. */
  private KeyRing(String activeKeyId, String legacyKeyId, Map<String, SecretKeySpec> keys) {
    this.activeKeyId = activeKeyId;
    this.legacyKeyId = legacyKeyId;
    this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
  }

  /**
   * 단일 레거시 키로부터 KeyRing 인스턴스를 생성하는 팩토리 메서드 (테스트 및 단일 키 환경용).
   *
   * @param base64Key 32바이트 AES 키의 Base64 인코딩 문자열
   * @return v1 단일 키가 등록된 KeyRing 객체
   */
  public static KeyRing ofLegacySingleKey(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException("암호화 키가 비어있습니다.");
    }
    SecretKeySpec spec = createKeySpec(base64Key.trim(), DEFAULT_LEGACY_KEY_ID);
    return new KeyRing(
        DEFAULT_LEGACY_KEY_ID, DEFAULT_LEGACY_KEY_ID, Map.of(DEFAULT_LEGACY_KEY_ID, spec));
  }

  /**
   * 다중 키 맵으로부터 KeyRing 인스턴스를 생성하는 팩토리 메서드 (테스트용).
   *
   * @param activeKeyId 신규 암호화에 사용할 키 식별자
   * @param keyIdToBase64Map 키 ID 와 Base64 인코딩된 비밀키의 매핑
   * @return 다중 키가 구성된 KeyRing 객체
   */
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

  /**
   * Base64 문자열을 디코딩하여 AES-256 키 객체(SecretKeySpec)로 변환하고 길이를 검증한다.
   *
   * @param base64Key Base64 인코딩된 대칭키
   * @param keyId 오류 메시지용 키 식별자
   * @return 검증 완료된 SecretKeySpec 객체
   */
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

  /** 현재 신규 암호화에 사용하는 활성 키 객체를 반환한다. */
  public SecretKeySpec getActiveKey() {
    return keys.get(activeKeyId);
  }

  /** 현재 신규 암호화에 사용하는 활성 키 식별자를 반환한다 (예: "v1", "v2"). */
  public String getActiveKeyId() {
    return activeKeyId;
  }

  /**
   * 지정된 키 식별자에 해당하는 키 객체를 조회한다.
   *
   * @param keyId 조회할 키 식별자
   * @return 해당 키의 SecretKeySpec 객체
   * @throws IllegalStateException 키 링에 등록되지 않은 식별자인 경우
   */
  public SecretKeySpec getKey(String keyId) {
    SecretKeySpec key = keys.get(keyId);
    if (key == null) {
      throw new IllegalStateException("키 링에서 키를 찾을 수 없습니다: " + keyId);
    }
    return key;
  }

  /** 접두사가 없는 과거 레거시 암호문 해독용 기본 키 객체를 반환한다. */
  public SecretKeySpec getLegacyKey() {
    return keys.get(legacyKeyId);
  }

  /** 접두사가 없는 과거 레거시 암호문 해독용 기본 키 식별자를 반환한다. */
  public String getLegacyKeyId() {
    return legacyKeyId;
  }

  /** 지정된 키 식별자가 키 링에 존재하는지 확인한다. */
  public boolean hasKey(String keyId) {
    return keys.containsKey(keyId);
  }

  /** 키 링에 등록된 모든 키 식별자 목록을 반환한다. */
  public Set<String> getKeyIds() {
    return keys.keySet();
  }
}
