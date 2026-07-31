# Backend 모듈 재배치 — platform/services 분리 설계

> 상태: 설계 확정, 구현 대기
> 날짜: 2026-07-31
> 관련: [Spring Cloud 아키텍처](../../specs/spring-cloud-architecture.md), [전환 계획 A](../../plans/spring-cloud-a-infra.md)

## 1. 목적

`backend/`는 현재 `core-api`·`eureka`·`config-server` 3개 모듈이 평평하게 놓여 있다. 목표는
**실무에 가까운 아키텍처** — 인프라 모듈(config-server·eureka, 향후 gateway)과 도메인 모듈
(core-api, 향후 auth-service·batch-worker)의 소유 경계를 디렉토리 구조로 드러낸다.

**진짜 근거는 조직도 비유가 아니다.** 재배치 과정에서 `backend/build.gradle`의 블랭킷
`subprojects { }` 설정이 Gradle 컨테이너 프로젝트(`:platform`, `:services`) 자동 생성과 충돌해
`./gradlew build`를 깨뜨리는 게 확인됐다(§3.2). 이 블랭킷 `subprojects` 패턴은 중첩 시 깨지는
것으로 잘 알려진 안티패턴이다 — 지금 독립적으로 고치지 않으면 계획 C(auth-service·batch-worker
분리) 때 "이름을 바꿔서 깨진 건지 새 서비스 설정이 잘못된 건지" 헷갈리는 상태로 만나게 된다.
재배치가 이 수정을 강제한다는 것 자체가 지금 하는 것의 실질적 이유다.

## 2. 목표 구조

```
backend/
├── build.gradle              (leaf 모듈만 Boot 플러그인 적용하도록 수정 — §3.2)
├── settings.gradle           (include 경로 변경)
├── platform/                 인프라 — 도메인 로직 없음
│   ├── config-server/
│   └── eureka/               (계획 B의 gateway도 향후 여기로)
└── services/                 도메인 — 비즈니스 로직
    └── core-api/             (계획 C의 auth-service·batch-worker도 향후 여기로)
```

지금 없는 서비스(gateway·auth-service·batch-worker)의 빈 디렉토리는 미리 만들지 않는다(YAGNI).

**이름 관련 참고:** `platform`이 Gradle의 `platform()` BOM 표기(`dependencyManagement`의
`mavenBom`)와 개념적으로 겹쳐 보일 수 있으나 실제 충돌은 없다 — 디렉토리/서브프로젝트 이름과
Gradle DSL 함수는 별개 네임스페이스다.

## 3. 변경 사항

### 3.1 디렉토리 이동

```bash
git mv backend/config-server backend/platform/config-server
git mv backend/eureka backend/platform/eureka
git mv backend/core-api backend/services/core-api
```

`src/`, `Dockerfile`, `.dockerignore`, `build.gradle` 등 내부 파일은 변경 없이 디렉토리째 이동한다.
Gradle 서브프로젝트 **이름**(`core-api`, `eureka`, `config-server`)은 경로 nesting과 무관하게
그대로 유지되므로 — 빌드 산출물 경로만 `services/core-api/build/libs/core-api-0.0.1-SNAPSHOT.jar`
식으로 바뀌고 jar 파일명 자체는 그대로다. Dockerfile의 `COPY build/libs/*.jar`는 빌드 컨텍스트
상대경로라 무수정.

### 3.2 `backend/settings.gradle`

```gradle
include 'platform:config-server'
include 'platform:eureka'
include 'services:core-api'
```

### 3.3 `backend/build.gradle` — 블로킹 수정 (필수)

현재 블랭킷 `subprojects { apply plugin: 'org.springframework.boot' ... }`는 재귀적이라, 위
include로 자동 생성되는 무소스 컨테이너 프로젝트 `:platform`·`:services`에도 Boot 플러그인이
걸린다. 컨테이너 프로젝트엔 mainClass가 없어 `bootJar` task가 다음과 같이 깨진다.

```
> Task :platform:bootJar FAILED
Main class name has not been configured and it could not be resolved from classpath
```

개별 모듈 지정 빌드(`:services:core-api:build`)는 안 걸리므로 CI는 그대로 통과하지만
`docs/README.md`가 검증 명령으로 안내하는 `./gradlew build`(전체 빌드)는 깨진다 — 눈에 안
띄는 채로 남을 뻔한 결함.

**수정:**

```gradle
configure(subprojects.findAll { it.childProjects.isEmpty() }) {
	apply plugin: 'java'
	apply plugin: 'org.springframework.boot'
	// ... 기존 subprojects 블록 내용을 여기로 옮긴다
}
```

leaf(자식 없는) 프로젝트에만 적용되도록 필터링한다. 향후 누군가 `services/core-api/` 아래에
모듈을 더 중첩하면 core-api가 조용히 Boot 플러그인을 잃는다는 게 이 방식의 함정이다 — 지금
저장소 규모(3~6개 모듈, 팀 2~3명)에는 `buildSrc` 컨벤션 플러그인보다 leaf 필터링이 적절한
선택이라 판단했다. 이 판단 자체를 `build.gradle` 주석으로 남긴다.

### 3.4 `.github/workflows/deploy.yml`

3개 서비스 빌드 잡(core-api·eureka·config-server) 각각에서 3곳씩, 총 9곳:

| 항목 | 예 (core-api) |
| --- | --- |
| 태그 계산 pathspec | `git log -1 -- backend/core-api/ ...` → `backend/services/core-api/ ...` |
| Docker 빌드 컨텍스트 | `context: ./backend/core-api` → `./backend/services/core-api` |
| Gradle task | `./gradlew :core-api:build` → `./gradlew :services:core-api:build` |

`on.push.paths`의 `backend/**` 패턴은 `backend/platform/**`·`backend/services/**`를 그대로
포함하므로 무수정.

**주의:** `git log -- <path>`는 rename을 따라가지 않는다. 이동 직후 pathspec은 이동 커밋
자체에 걸려 non-empty가 되므로 태그가 새로 계산되고 3개 이미지 전부 재빌드된다 — 즉 이
커밋이 config-server·eureka·core-api를 **동시에** 재생성한다(지금까지는 항상 하나씩만
바뀌었다). §5 리스크 참조.

### 3.5 문서

**살아있는 문서 — 실제 경로로 수정:**
- `backend/README.md` — `./gradlew :core-api:bootRun` 등 예시 명령
- `docs/DEVELOPMENT.md` — `:core-api:bootRun` IntelliJ 실행 구성 언급
- `README.md` §저장소 구성 — `backend/ — config-server, eureka, core-api` 트리를
  `backend/platform/{config-server,eureka}`, `backend/services/core-api`로 갱신
- `docs/README.md` — `./gradlew build`를 검증 명령으로 안내하는 문구는 §3.3 수정 후에도
  유효하지만, "backend 잡" 서술이 있다면 모듈 경로 언급 확인

**살아있는 문서 — 이번 참조점만 갱신:**
- `docs/README.md:74` — Spring Cloud 전환 A 인덱스 설명에 이 재배치 스펙 문서를 후속으로
  가리키는 문구 추가

**역사 기록 — 본문 유지, 주석만 추가:**
- `docs/plans/spring-cloud-a-infra.md` — 이 문서는 as-executed 실행 기록(과거형 서술,
  실측 트러블슈팅 포함)이다. ADR 0006 방식(상태 줄 갱신)이 아니라
  [`oci-server-provisioning-plan.md:6-9`](../../plans/oci-server-provisioning-plan.md)의
  "역사 기록이다" 블록쿼트 패턴을 그대로 따른다 — 같은 성격(as-executed 계획 문서 안의 stale
  경로)의 더 가까운 선례이기 때문. 본문의 `backend/core-api/` 등 경로는 원문 그대로 두고,
  최상단에 "이 문서는 재배치 이전 경로 기준. 지금 서버에서 실행할 명령은 바뀐 경로를 쓴다.
  재배치 내역은 이 설계서 참조" 주석만 추가한다.

**변경 없음:**
- `docker-compose.yml` — 이미지는 GHCR pull이라 소스 경로 참조 자체가 없음(§5의 healthcheck
  추가는 경로와 무관한 별개 변경)
- `.superpowers/sdd/*` — git 추적 제외 예정(사용자 결정, 이 세션 확인됨)

## 4. `docker-compose.yml` — 헬스게이트 추가 (이번 재배치와 함께 진행)

**배경:** core-api의 Config Server 연동은 `spring.config.import: optional:configserver:...` +
`fail-fast: true`다. 지금 `depends_on`은 조건 없는 목록(`postgres`, `config-server`, `eureka`)이라
컨테이너 **시작**만 보장하고 앱이 요청을 받을 준비가 됐는지는 보장하지 않는다. config-server에
헬스체크가 없다. 지금까지는 배포마다 서비스 하나씩만 재생성돼 드러나지 않았지만, 이번 재배치는
§3.4에서 본 대로 3개를 **동시에** 재생성한다 — core-api가 config-server보다 먼저 뜨면 빈 설정을
받아 임계값 0.0/0.0으로 **조용히**(크래시 없이, `/api/health`도 200) 뜰 수 있다.

**추가 (config-server, eureka 두 서비스만 — postgres 헬스게이트는 이번 변경 범위 밖):**

```yaml
config-server:
  # ...
  healthcheck:
    test: ["CMD-SHELL", "bash -c '</dev/tcp/127.0.0.1/8888'"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 15s

eureka:
  # ...
  healthcheck:
    test: ["CMD-SHELL", "bash -c '</dev/tcp/127.0.0.1/8761'"]
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 15s

core-api:
  depends_on:
    postgres:
      condition: service_started
    config-server:
      condition: service_healthy
    eureka:
      condition: service_healthy
```

**TCP 체크(`/dev/tcp`)를 고른 이유:** 베이스 이미지가 `eclipse-temurin:21-jre`(Ubuntu 계열,
bash 내장, curl·wget 없음)라 HTTP 헬스체크를 하려면 이미지에 패키지를 추가해야 한다. 지금
잡으려는 실패 모드(JVM이 아직 포트를 안 열었는데 컨테이너는 "시작"으로 잡히는 것)는 TCP 레벨
체크로 충분히 구분된다 — Actuator `/actuator/health`만큼 정교하진 않지만 이 프로젝트 규모에
패키지 추가 없이 딱 필요한 만큼만 고치는 선택이다.

## 5. 리스크

| 리스크 | 대응 |
| --- | --- |
| PR 단계 CI 없음(`docs/README.md`) — 새 Gradle 경로의 첫 실제 실행은 머지 후 배포뿐 | §6 검증 계획으로 로컬에서 최대한 미리 잡는다 |
| **`workflow_dispatch`로 브랜치 테스트 금지** — `deploy.yml`이 `github.sha`로 `git reset --hard` 하므로 feature 브랜치 대상 dispatch는 그 브랜치를 그대로 운영에 배포한다 | PR 머지 전에는 어떤 방식으로도 `deploy.yml`을 이 브랜치에 대해 실행하지 않는다 |
| 3개 서비스 동시 재생성 — 지금까지 없던 상황 | §4 헬스게이트로 완화. 그래도 머지 직후 `/api/v1/demo/thresholds`가 실제 값(0.0 아님)인지 수동 확인 |
| `git log -- <path>` rename 미추적 → 이동 커밋에서 태그 재계산, 3 이미지 전부 리빌드 | 의도된 동작으로 받아들인다(§3.4) — 실패 아님 |

## 6. 검증 계획

1. `git mv` 완료 후 `backend/`에서 `./gradlew build` — 3모듈 컴파일·테스트 통과 + §3.3 컨테이너
   프로젝트 버그가 실제로 고쳐졌는지 확인 (이게 핵심 검증 — 나머지는 부차적)
2. 옮긴 컨텍스트로 로컬 `docker build -f backend/services/core-api/Dockerfile backend/services/core-api` 1회 — `.dockerignore`·상대경로 COPY가 실제로 문제없는지 확인 (그냥 존재 확인 아니라 실행해서 증거로 남김)
3. `git log -1 --format=%H -- backend/services/core-api/ backend/build.gradle backend/settings.gradle ':!*.md' .github/workflows/deploy.yml` 실행해 non-empty 확인 — 태그가 실제로 계산되는지
4. PR 머지 후: `deploy.yml`이 3개 이미지 재빌드·재게시하는지 확인, 서버 배포 후 `curl .../api/v1/demo/thresholds`가 실제 값 반환하는지 확인 (헬스게이트가 실제로 작동하는 첫 실전 사례)

## 7. 브랜치 전략

`feature/backend-module-restructure` 브랜치 + PR. main 직접 커밋 안 함 — CI/CD 배포 워크플로를
건드리는 변경이라 리뷰 후 머지 원칙을 따른다.

## 8. 범위 밖

- 계획 B(Gateway)·계획 C(auth-service·batch-worker 분리) 구현 — 이 스펙은 디렉토리 구조만 준비한다
- postgres 헬스게이트 — 이번에 노출된 리스크는 config-server·eureka 타이밍 문제이지 postgres가 아니다
- `buildSrc` 컨벤션 플러그인으로의 전환 — 지금 규모에는 과함(§3.3)
