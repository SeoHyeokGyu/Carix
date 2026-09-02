package com.carix.snapshot;

import java.util.UUID;

/**
 * 스냅샷 계산 실행 단위 버전을 발급한다 (기술 명세 §1.3). 형식은 {@code YYYYMMDDHHMMSS-NNN} (UTC) 이고 "최신 버전" 판정은 사전순 비교를
 * 따른다.
 *
 * <p>인터페이스로 두는 이유는 테스트다. 매 실행이 새 버전을 발급하면 같은 키의 재실행이 일어나지 않아 대체(upsert) 경로가 한 번도 실행되지 않는다. 발급기를
 * 고정해야 그 경로를 찌를 수 있다.
 */
public interface DataVersionIssuer {

  String issue(UUID userId);
}
