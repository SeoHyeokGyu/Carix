package com.carix.meta;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.snapshot.DecisionSignalEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetaControllerTest {

  @Test
  @DisplayName("엔진 규칙 버전을 그대로 돌려준다 — 관리자 데이터 상태가 사용자 스냅샷 버전과 비교한다")
  void returnsRuleVersion() {
    assertThat(new MetaController().versions())
        .containsEntry("ruleVersion", DecisionSignalEngine.RULE_VERSION);
  }
}
