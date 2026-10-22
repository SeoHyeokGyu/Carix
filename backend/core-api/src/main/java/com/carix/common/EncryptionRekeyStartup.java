package com.carix.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 키 로테이션 절차(SECURITY §3.2)의 운영 실행 경로다. 재암호화 서비스는 엔드포인트가 없어 운영에서 부를 길이 없었다.
 *
 * <p>기동할 때마다 활성 키와 테이블별 미전환 행 수를 {@code rekey-status} 한 줄로 남긴다 — 구 키를 폐기해도 되는지 서버 로그만으로 판단한다.
 * {@code carix.encryption.rekey-on-startup}({@code CARIX_ENCRYPTION_REKEY_ON_STARTUP}) 이 켜져 있으면 그
 * 전에 재암호화한다. 이미 활성 키로 된 행은 조건에서 빠지므로 켜 둔 채 재기동해도 비용이 거의 없다.
 *
 * <p>어느 단계가 실패해도 기동을 막지 않는다. 키 하나를 빠뜨린 설정 실수로 배포 헬스체크가 실패해 서비스가 내려가는 것보다, 실패를 남기고 미전환 행 수로 드러내는 편이
 * 낫다. 남기는 것은 키 ID·건수·예외 문구뿐이다 — 금액과 키 값은 남기지 않는다(완료 기준 10).
 */
@Slf4j
@Component
public class EncryptionRekeyStartup {

  private final SensitiveDataRekeyingService rekeyingService;
  private final boolean rekeyOnStartup;

  public EncryptionRekeyStartup(
      SensitiveDataRekeyingService rekeyingService,
      @Value("${carix.encryption.rekey-on-startup:${CARIX_ENCRYPTION_REKEY_ON_STARTUP:false}}")
          boolean rekeyOnStartup) {
    this.rekeyingService = rekeyingService;
    this.rekeyOnStartup = rekeyOnStartup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    if (rekeyOnStartup) {
      rekey();
    }
    reportStatus();
  }

  private void rekey() {
    try {
      rekeyingService.rekeyAll();
    } catch (RuntimeException e) {
      log.error("rekey-failed reason={}", e.getMessage());
    }
  }

  private void reportStatus() {
    try {
      SensitiveDataRekeyingService.RekeyingStatus status = rekeyingService.checkStatus();
      log.info(
          "rekey-status activeKeyId={} incomeHistory={} expenseItem={} careerScenario={} performanceBonus={} fullyMigrated={}",
          status.activeKeyId(),
          status.outdatedIncomeHistory(),
          status.outdatedExpenseItem(),
          status.outdatedCareerScenario(),
          status.outdatedPerformanceBonus(),
          status.isFullyMigrated());
    } catch (RuntimeException e) {
      log.error("rekey-status-failed reason={}", e.getMessage());
    }
  }
}
