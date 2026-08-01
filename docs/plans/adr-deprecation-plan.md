# ADR 폐지 — 내용 통합 계획

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans 또는
> superpowers:subagent-driven-development 로 태스크별 실행한다. Opus 최종 리뷰(2026-07-31)
> 반영 완료 — 초안의 인용 검색 누락·매핑 오류·대안 손실 위험을 이 버전에서 고쳤다.

**Goal:** `docs/adr/` 8개 문서의 결정 근거를 각자 해당하는 살아있는 문서(README·spec·plan)로
옮기고, ADR 형식과 디렉토리 자체를 폐지한다. 내용은 사라지지 않고 위치만 바뀐다.

**왜 지금 하는가:** 원래 ADR은 "되돌리기 어렵거나 재논의된 결정"을 결정 시점 맥락과 함께
보존하는 용도였다. 실제로는 README.md 채택 사유 표, DEVELOPMENT.md, SECURITY.md,
spring-cloud-architecture.md 등 여러 문서가 ADR을 인용하고 있어 — 결정 근거가 전용 문서와
그걸 쓰는 문서 양쪽에 분산돼 있다. 인용하는 문서 안에 근거를 직접 담으면 그 분산이 없어진다.

**알아둘 것 — Opus 리뷰가 지적한 진짜 손실 위험.** ADR 몇 개는 "요약이 topic 문서에 이미
있고 ADR엔 근거만 있는" 구도가 아니라 **반대**다. 0006 비교표, 0007 위험 감수 표, 0008 대안
표는 저장소 다른 어디에도 없다 — 잘못 옮기면 진짜로 없어진다. 그리고 0003·0004·0006 셋은
지금 "대체됨/재논의 중" 상태라 supersession 메타데이터를 갖고 있는데, topic 문서엔 이걸
받을 자리가 없다. 사용자가 그래도 삭제하기로 결정했으므로 이 계획은 **손실 없이 옮기는 것**을
최우선으로 한다 — 대충 링크만 떼면 안 된다.

## 원칙

- **내용 보존, 형식만 폐지.** 결정뿐 아니라 **대안(왜 다른 안을 버렸는지)과 결과의 재검토
  트리거**까지 옮긴다. "상태: 채택/대체됨" 같은 ADR 전용 메타데이터만 없어진다.
- **가장 적합한 곳이 새 집.** "인용하는 문서"가 아니라 "그 주제를 실무적으로 가장 깊이
  다루는 곳" — 인용 사이트 수와 무관하게 구조적으로 맞는 자리를 고른다(§2 매핑표 참조,
  Opus 리뷰로 세 곳 재배치함).
- **인용부는 링크 제거, 텍스트로 대체.** 단 ADR을 "문서 충돌 시 최종 권위"로 지목하는
  문장(§5)과 "새 ADR을 작성한다"는 미래 공정 약속(§6)은 단순 링크가 아니라 문장 자체를
  다시 써야 한다.
- **md 파일만 보지 않는다.** `docker-compose.yml`, `.env.example`,
  `deploy/headscale/config.yaml`에도 ADR 인용이 있다 — Task 6 최종 검색에서 `--include="*.md"`
  쓰지 않는다.

## 이동 매핑

| ADR | 옮길 곳 | 인용 사이트 |
| --- | --- | --- |
| 0001 하이브리드 설계 계보 | `docs/specs/functional-specification.md` §6.1 (이미 설계 계보를 산문으로 풀어놓은 곳 — README:456보다 실질적인 집) | README.md:456, functional-specification.md:20,255,257 |
| 0002 Vite+React+Node SSR | `docs/specs/frontend-design.md` (프런트엔드 기술 근거 섹션 신설) | README.md:286 |
| 0003 Docker·PostgreSQL 서버 전용 | `README.md` 채택 사유 표 (297행 — DEVELOPMENT.md §1 아님. §1 앞 도입부는 "배경은 README 참조"로 이미 위임 중이라 거기 없음) | README.md:297, spring-cloud-architecture.md:262, SECURITY.md:56 |
| 0004 연봉 기준선 공식 임금통계 | **이식 대상 아님 — 특수 케이스.** `external-api-integration.md:326-343` "미결" 절이 이미 이 결정의 전제가 실측으로 흔들렸다고 기록 중이고 열린 체크박스가 있다 | external-api-integration.md:332,334,342, README.md:309 |
| 0005 서버 세션 단일 인증 | `docs/SECURITY.md` §1 인증 — **대안이 이 ADR의 사실상 유일한 고유 내용**(왜 JWT 아닌지: 즉시 폐기 어려움·토큰 유출 대응 복잡). §1은 지금 결정만 있고 근거가 전혀 없다 | README.md:308, SECURITY.md:7,58 |
| 0006 엣지 프록시 Caddy vs nginx | `docs/plans/oci-server-provisioning-plan.md` **Phase 3**(엣지: 라우터+프로젝트별 Caddy, ~382행) — 기존 Phase 5(~704행, 배포/GHCR 문단 사이)에 있던 블록은 **여기로 재배치**, 확장이 아니라 이동 | README.md:280,298, oci-server-provisioning-plan.md:704, spring-cloud-architecture.md:317,321,324 |
| 0007 단일 DB (운영=개발) | `docs/DEVELOPMENT.md` §3 OCI DB 원격 연결 | backend/README.md:71, oci-server-provisioning-plan.md:275, spring-cloud-architecture.md:6,58,311,354, docs/README.md:53, DEVELOPMENT.md:69, SECURITY.md:57, README.md:425, docker-compose.yml, .env.example |
| 0008 Headscale (Tailscale 자체 호스팅) | `docs/DEVELOPMENT.md` §3.2 (158-171행) — **지금 그 절엔 0008 고유 내용이 하나도 없다.** 대안 5행 표, "왜 접었는지" 4문단, 대가 절(SPOF, `headscale-data` 백업 필요, 배포 주기 결합, DERP 외부 의존) 전부를 새로 써넣는 작업이지 "합류"가 아니다 | backend/README.md:70, oci-server-provisioning-plan.md:127, spring-cloud-architecture.md:7, DEVELOPMENT.md:70,77, SECURITY.md:60, README.md:306,425, deploy/headscale/config.yaml |

## 별도 처리 — 단순 링크 제거가 아닌 것

**"ADR = 문서 충돌 시 최종 권위" 역할 (3곳) — 확정: 최신 커밋 우선.**
`functional-specification.md:20`("문서 간 충돌 시 ... ADR을 우선한다"), `docs/README.md:78`
(동일), ADR 0001 자체의 결과절("충돌 시 이 ADR과 기능 분해 §6.1을 따른다") — 이 셋을 전부
"문서 간 충돌 시 각 주제의 소관 문서를 따르고, 소관 문서끼리도 내용이 어긋나면 최신 커밋을
우선한다"로 통일한다. ADR 0001의 결과절은 Task 5에서 functional-specification.md §6.1에
합류하는 시점에 자기 자신을 가리키는 순환 참조가 되므로 통째로 삭제한다(그 문서 자체가
근거를 담고 있으니 "충돌 시 이걸 따른다"는 문장이 불필요해짐).

**`spring-cloud-architecture.md:324` — 미래 공정 약속.** "구현 시 ADR 0006의 상태를 갱신하고
이 변경을 기록하는 **새 ADR을 작성한다**"는 ADR 인용이 아니라 앞으로 할 일에 대한 절차
약속이다. ADR 체계가 없어지므로 이 문장 자체를 다시 쓴다(예: "Gateway 도입 커밋에 이
변경 사유를 기록한다").

**`README.md:368` — 이미 부정확한 인용, 텍스트화가 아니라 삭제.** PR 단계 CI가 없는 이유를
ADR 인덱스로 돌리는데, 정작 ADR 0007의 위험 감수 표는 그 항목을 "게이트 부재 … (ADR 없음,
사용자 결정)"이라고 스스로 적어놨다 — 애초에 인용이 틀렸다. 괄호째 삭제.

**Stale "dev DB" 텍스트 — 0007 이식 전에 청산.** `DEVELOPMENT.md:4,291`, `README.md:426`,
`backend/README.md:79`가 아직 "dev DB" 개념을 전제로 쓰여 있다. 0007 전체 근거가 §3에
들어가면 이 네 곳과 바로 모순되므로 Task 1에서 같이 정리한다.

## 실행 순서

### Task 0: 대체 권위 문구 적용 (확정: 최신 커밋 우선)
- `functional-specification.md:20`, `docs/README.md:78`를 "문서 간 충돌 시 각 주제의 소관
  문서를 따르고, 소관 문서끼리도 내용이 어긋나면 최신 커밋을 우선한다"로 통일
- ADR 0001 결과절의 "충돌 시 이 ADR과 §6.1을 따른다" 문장은 Task 5에서 삭제(순환 참조)
- `spring-cloud-architecture.md:324`의 "새 ADR을 작성한다" 문장을 "Gateway 도입 커밋에
  이 변경 사유를 기록한다"로 다시 쓴다

### Task 1: ADR 0007·0008 → DEVELOPMENT.md 통합 (가장 큰 덩어리)
- 0007: `docs/DEVELOPMENT.md` §3에 대안 비교·감수하는 위험 표 이식
- 0008: §3.2에 대안 표·"왜 접었는지" 4문단·대가 절(SPOF, 백업, 배포 주기 결합, DERP 의존)
  **새로 작성** — 기존 §3.2엔 없는 내용임을 유의
- stale "dev DB" 문구 4곳(`DEVELOPMENT.md:4,291`, `README.md:426`, `backend/README.md:79`) 정리
- 인용 사이트 정리: backend/README.md, oci-server-provisioning-plan.md,
  spring-cloud-architecture.md, docs/README.md, SECURITY.md, README.md,
  `docker-compose.yml`, `.env.example`, `deploy/headscale/config.yaml`
- Verify: 원본 ADR의 대안·위험·대가 항목이 DEVELOPMENT.md에 문단 단위로 전부 있는지 대조

### Task 2: ADR 0006 → oci-server-provisioning-plan.md Phase 3로 재배치
- Phase 5(~704행)의 기존 엣지 프록시 블록을 Phase 3(~382행, 엣지 섹션)로 **이동**
- Caddy vs nginx 비교표(라우터·앱 계층 둘 다) + "앱 계층은 Gateway로 전환 예정" 단서 포함
- 대안 절(공인 IP 1개 실측; "한 프로젝트가 라우터 겸함" 시 asset-pulse 배포 끊김)도 이식
- 인용 정리: README.md 2곳, spring-cloud-architecture.md 3곳
- Verify: Phase 5 자리에 빈 구멍이나 끊긴 문맥이 안 남았는지 확인

### Task 3: ADR 0003·0005 통합
- 0003 → `README.md` 채택 사유 표의 "Docker·PostgreSQL 서버 전용" 행 확장(대안: 로컬
  Docker 허용 시 문제, 임베디드 H2 시 스키마 드리프트). **"운영 DB와 dev DB는 분리한다"
  문장은 옮기지 않는다** — ADR 0007(Task 1에서 이미 반대 내용이 DEVELOPMENT.md에 들어감)과
  모순되는, 이미 대체된 문장
- 0005 → `SECURITY.md` §1 인증에 대안 절 추가(왜 JWT 아닌지, 초기 규격의 인증 방식 혼재
  맥락) — 지금 §1은 결정만 있고 근거가 없으므로 사실상 신규 작성
- 인용 정리: README.md, spring-cloud-architecture.md, SECURITY.md 각 해당 줄

### Task 4: ADR 0002 → frontend-design.md 통합
- 대안(Next.js 유지 시 역할 경계 흐려짐) 포함해서 이식
- 인용 1곳 정리 (README.md:286)

### Task 4.5: ADR 0004 특수 처리 (이식 아님)
- `external-api-integration.md`의 ADR 0004 링크를 일반 텍스트로 바꾼다 — 이미 그 문단이
  결정 배경과 "전제가 흔들렸다"는 사실을 서술 중이라 추가로 옮길 내용 없음
- :334 "하나를 택하고 **ADR을 갱신해야 한다**"와 :342 체크박스 둘 다 "이 섹션 갱신"으로 수정
  (초안은 :342만 고쳤음 — :334도 같은 문제)
- README.md:309의 ADR 0004 링크도 텍스트로 정리

### Task 5: ADR 0001 → functional-specification.md §6.1 통합
- 대안 A/B, 결과의 충돌 해소 규칙 포함해서 이식 (Task 0에서 정한 대체 권위 문구로 교체)
- README.md:456은 functional-specification.md §6.1을 가리키는 짧은 포인터로 축소
- 인용 정리: README.md, functional-specification.md

### Task 6: adr/ 디렉토리 삭제, 인덱스 정리, 전체 재검색
- `git rm -r docs/adr/`
- `docs/README.md`의 "아키텍처 결정 (ADR)" 섹션 삭제
- 전체 재검색 (md 한정 아님, 대소문자 무시, 바이너리 스킵):
  ```bash
  grep -rniI '\bADR\b\|adr/' --exclude-dir=.git --exclude-dir=.superpowers \
    --exclude-dir=superpowers .
  ```
- Verify: 위 결과가 0곳이거나 `docs/superpowers/`(동결 아카이브) 안에서만 나와야 함

## 검증

- Task 0~5 각각 끝날 때마다 원본 ADR과 이식된 문단을 나란히 대조 — 결정뿐 아니라 **대안·
  결과·재검토 트리거**까지 빠진 게 없는지 (Task 1·2·3·5는 사실상 "요약 확장"이 아니라
  "신규 작성"에 가까운 항목이 섞여 있음 — 위 각 Task 설명 참조)
- Task 6의 grep이 0(또는 동결 아카이브만)일 때까지 반복 — 첫 실행에서 안 걸릴 것으로 예상:
  `docker-compose.yml`, `.env.example`, `deploy/headscale/config.yaml`
- 링크 깨짐 수동 확인: 이번에 수정한 모든 문서를 훑으며 문장이 자연스럽게 이어지는지,
  "위 참조" 같은 문구가 실제로 뭔가를 가리키는지

## 범위 밖

- ADR이 기록한 **결정 자체**를 재검토하지 않는다 — 텍스트만 옮긴다(단 0004는 이미
  재검토 대상으로 명시된 특수 케이스, Task 4.5)
- git 히스토리에서 ADR 삭제하지 않는다(`git rm`만, `filter-branch` 등 히스토리 재작성 안 함) —
  과거 커밋에서는 여전히 조회 가능
