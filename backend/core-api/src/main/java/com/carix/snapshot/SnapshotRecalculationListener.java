package com.carix.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저장 커밋 뒤에 스냅샷을 다시 계산한다.
 *
 * <p>산출기는 읽기 전용 풀의 별도 커넥션으로 읽으므로 저장 트랜잭션 안에서 돌리면 아직 커밋되지 않은 쓰기를 보지 못한다. 같은 스레드에서 동기로 돌고, 스레드풀·재시도는
 * 없다. 재계산이 실패해도 이미 커밋된 저장은 되돌리지 않고 원인만 남긴다.
 */
@Component
public class SnapshotRecalculationListener {

  private static final Logger log = LoggerFactory.getLogger(SnapshotRecalculationListener.class);

  private final SnapshotRecalculationService recalculationService;
  private final TransactionTemplate newTransaction;

  public SnapshotRecalculationListener(
      SnapshotRecalculationService recalculationService,
      PlatformTransactionManager transactionManager) {
    this.recalculationService = recalculationService;
    // AFTER_COMMIT 단계에서는 끝난 트랜잭션의 리소스가 아직 스레드에 묶여 있다. 새 트랜잭션을 열지 않으면 그 트랜잭션에 참여하고,
    // 버전 발급기(MANDATORY)도 쓸 트랜잭션을 못 얻는다.
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void on(SnapshotRecalculationRequested event) {
    // try 를 트랜잭션 바깥에 둔다. 안쪽에서 잡으면 서비스가 rollback-only 로 표시한 트랜잭션이 커밋을 시도하다
    // UnexpectedRollbackException 을 밖으로 던진다.
    try {
      newTransaction.executeWithoutResult(
          status ->
              recalculationService.recalculateSnapshot(event.userId(), event.fromYearMonth()));
    } catch (RuntimeException e) {
      log.warn("스냅샷 재계산 실패: {}", e.toString());
    }
  }
}
