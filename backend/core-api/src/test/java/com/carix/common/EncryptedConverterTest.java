package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncryptedConverterTest {

  private EncryptedConverter converter;

  @BeforeEach
  void setUp() {
    String testKey = Base64.getEncoder().encodeToString(new byte[32]);
    converter = new EncryptedConverter(testKey);
  }

  @Test
  @DisplayName("키가 없으면 IllegalStateException 발생")
  void constructor_noKey() {
    assertThatThrownBy(() -> new EncryptedConverter(null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EncryptedConverter("")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("null 변환 시 null 반환")
  void convert_null() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("암호화 및 복호화 라운드트립 성공")
  void roundTrip() {
    Long original = 65000000L;
    String encrypted = converter.convertToDatabaseColumn(original);

    assertThat(encrypted).isNotNull().isNotEqualTo(String.valueOf(original));

    Long decrypted = converter.convertToEntityAttribute(encrypted);
    assertThat(decrypted).isEqualTo(original);
  }

  @Test
  @DisplayName("잘못된 암호문 복호화 시 IllegalStateException 발생")
  void decrypt_invalid() {
    assertThatThrownBy(() -> converter.convertToEntityAttribute("invalid-data"))
        .isInstanceOf(IllegalStateException.class);
  }
}
