package com.carix.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 기동 시 재암호화 상태 보고와 선택적 재암호화 (SECURITY §3.2). */
@ExtendWith(OutputCaptureExtension.class)
class EncryptionRekeyStartupTest {

  private SensitiveDataRekeyingService service;

  @BeforeEach
  void setUp() {
    service = mock(SensitiveDataRekeyingService.class);
    given(service.checkStatus())
        .willReturn(new SensitiveDataRekeyingService.RekeyingStatus(2, 0, 1, 3, "v1"));
  }

  @Test
  @DisplayName("플래그가 꺼져 있으면 재암호화하지 않고 남은 행 수만 한 줄로 남긴다")
  void 상태만(CapturedOutput output) {
    new EncryptionRekeyStartup(service, false).run();

    verify(service, never()).rekeyAll();
    assertThat(output)
        .contains(
            "rekey-status activeKeyId=v1 incomeHistory=2 expenseItem=0 careerScenario=1 performanceBonus=3 fullyMigrated=false");
  }

  @Test
  @DisplayName("플래그가 켜져 있으면 재암호화한 뒤 상태를 남긴다")
  void 재암호화() {
    given(service.rekeyAll())
        .willReturn(new SensitiveDataRekeyingService.RekeyingResult(2, 0, 1, 3, "v1"));

    new EncryptionRekeyStartup(service, true).run();

    InOrder order = inOrder(service);
    order.verify(service).rekeyAll();
    order.verify(service).checkStatus();
  }

  @Test
  @DisplayName("재암호화가 실패해도 기동을 막지 않는다 — 실패를 남기고 상태는 그대로 보고한다")
  void 실패해도_기동(CapturedOutput output) {
    given(service.rekeyAll()).willThrow(new IllegalStateException("복호화 실패"));

    assertThatCode(() -> new EncryptionRekeyStartup(service, true).run())
        .doesNotThrowAnyException();

    assertThat(output).contains("rekey-failed").contains("복호화 실패");
    assertThat(output).contains("rekey-status activeKeyId=v1");
  }

  @Test
  @DisplayName("상태 조회까지 실패해도 기동을 막지 않는다")
  void 상태조회_실패(CapturedOutput output) {
    given(service.checkStatus()).willThrow(new IllegalStateException("테이블 없음"));

    assertThatCode(() -> new EncryptionRekeyStartup(service, false).run())
        .doesNotThrowAnyException();
    assertThat(output).contains("rekey-status-failed");
  }
}
