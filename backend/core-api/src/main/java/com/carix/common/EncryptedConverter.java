package com.carix.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 민감 재무 금액 컬럼을 AES-256-GCM 알고리즘으로 암호화하여 데이터베이스에 저장하는 JPA AttributeConverter.
 *
 * <p><b>적용 대상 컬럼 (SECURITY §3.1, 기술 명세 §1.1):</b>
 *
 * <ul>
 *   <li>{@code core.income_history.gross_salary_encrypted} (세전 연봉)
 *   <li>{@code core.income_history.net_salary_encrypted} (월 실수령액)
 *   <li>{@code core.expense_item.amount_encrypted} (월 소비액)
 *   <li>{@code core.career_scenario.*_encrypted} (엔진 1 시뮬레이터 금액 컬럼 6종)
 * </ul>
 *
 * <p><b>암호화 및 보안 설계 특성:</b>
 *
 * <ul>
 *   <li><b>알고리즘:</b> {@code AES/GCM/NoPadding} (AEAD 모드로 기밀성과 무결성을 동시 보장).
 *   <li><b>초기화 벡터 (IV):</b> 12바이트(96비트) 암호학적 난수({@code SecureRandom})를 매 암호화마다 생성하여 암호문 앞에 결합.
 *   <li><b>인증 태그:</b> 128비트 태그가 암호문 뒤에 결합되어 변조 시 복호화 실패({@code AEADBadTagException}) 발생.
 *   <li><b>포맷 규격:</b> {@code {keyId}:{Base64(IV + CipherText + Tag)}} (예: {@code v2:ab12...}).
 *   <li><b>하위 호환성 (Backward Compatibility):</b> 콜론({@code :}) 접두사가 없는 과거 레거시 데이터는 {@link
 *       KeyRing#getLegacyKey()} (기본 {@code v1})를 적용하여 무중단 복호화 지원.
 * </ul>
 *
 * <p><b>스프링 빈 및 JPA 수명주기:</b> Spring Data JPA의 {@code @Converter}이자 {@code @Component}로 등록되어 Spring
 * 컨텍스트에서 {@link KeyRing} 의존성을 주입받는다. 슬라이스 테스트({@code @DataJpaTest}) 환경처럼 {@link KeyRing} 빈이 등록되지 않은
 * 경우에도 자체 환경변수 폴백을 통해 독립적으로 구동될 수 있도록 유연한 생성자 구성을 갖춘다.
 */
@Converter
@Component
public class EncryptedConverter implements AttributeConverter<Long, String> {

  /** 다중 대칭키 관리자 */
  private final KeyRing keyRing;

  /** 암호화 본체. 문자열 컬럼용 {@link EncryptedStringConverter} 와 같은 알고리즘·포맷이다 */
  private final EncryptedStringConverter delegate;

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
    this.delegate = new EncryptedStringConverter(this.keyRing);
  }

  /**
   * KeyRing 인스턴스를 직접 주입하는 생성자 (단위 테스트 및 명시적 배선용).
   *
   * @param keyRing 다중 키 관리자 객체
   */
  public EncryptedConverter(KeyRing keyRing) {
    if (keyRing == null) {
      throw new IllegalStateException("KeyRing 은 null 일 수 없습니다.");
    }
    this.keyRing = keyRing;
    this.delegate = new EncryptedStringConverter(keyRing);
  }

  /**
   * 단일 Base64 키 문자열을 사용하는 생성자 (레거시 호환 및 빠른 테스트용).
   *
   * @param singleKey 32바이트 Base64 인코딩 대칭키
   */
  public EncryptedConverter(String singleKey) {
    this(KeyRing.ofLegacySingleKey(singleKey));
  }

  /** 컨버터에 바인딩된 KeyRing 인스턴스를 반환한다. */
  public KeyRing getKeyRing() {
    return keyRing;
  }

  /**
   * 엔티티의 평문 금액(Long)을 데이터베이스 저장용 암호문 문자열({@code {keyId}:{Base64}})로 암호화한다.
   *
   * <p>현재 활성화된 키({@link KeyRing#getActiveKey()})를 사용하며, 매 호출마다 새로운 랜덤 IV를 발급한다.
   *
   * @param attribute 평문 금액 (null 이면 null 반환)
   * @return {@code {keyId}:{Base64(IV + Ciphertext + Tag)}} 형태의 문자열
   * @throws IllegalStateException 암호화 Cipher 초기화 또는 수행 중 실패한 경우
   */
  @Override
  public String convertToDatabaseColumn(Long attribute) {
    return attribute == null ? null : delegate.convertToDatabaseColumn(String.valueOf(attribute));
  }

  /**
   * 데이터베이스의 암호문 문자열을 복호화하여 엔티티의 평문 금액(Long)으로 변환한다.
   *
   * <p>문자열에 콜론({@code :})이 있으면 앞부분의 {@code keyId}에 매핑된 키로 복호화하고, 접두사가 없는 레거시 암호문은 기본 레거시 키({@link
   * KeyRing#getLegacyKey()})로 복호화한다.
   *
   * @param dbData 데이터베이스에 저장된 암호문 문자열 (null 이면 null 반환)
   * @return 복호화된 평문 금액 (Long)
   * @throws IllegalStateException 복호화 키 미존재, 데이터 변조, 포맷 불일치 등 복호화 실패 시
   */
  @Override
  public Long convertToEntityAttribute(String dbData) {
    return dbData == null ? null : Long.parseLong(delegate.convertToEntityAttribute(dbData));
  }
}
