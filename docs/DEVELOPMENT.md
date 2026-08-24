# Carix 로컬 개발 가이드

로컬은 순수 코드 개발이다. **Docker와 PostgreSQL은 OCI 서버에서만 기동**하고, 로컬
백엔드는 OCI의 `carix` DB(운영과 같은 DB)에 원격 연결한다. 접근은 TLS + OCI Security
List IP 허용목록이다(§3 참조). 배경과 결정은
[루트 README 개발 환경](../README.md#개발-환경)을 참조한다.

## 1. 사전 준비

### 1.1 런타임 버전

컨테이너는 Java 25, Node.js 24 LTS로 고정한다. 로컬도 동일 버전으로 맞춘다
(개발 머신의 상위 버전과 별개).

```bash
# Node 24 (nvm)
nvm install 24 && nvm use 24
corepack enable            # pnpm 활성화

# Java 25 (sdkman)
sdk install java 25-tem && sdk use java 25-tem
```

버전 확인:

```bash
node --version   # v24.x
java --version   # 25.x
pnpm --version
```

### 1.2 저장소

```bash
git clone https://github.com/SeoHyeokGyu/Carix.git
cd Carix
```

## 2. 환경 변수

`.env.example`을 복사해 `.env`를 채운다. `.env`는 커밋하지 않는다 (`.gitignore` 처리됨).

```bash
cp .env.example .env
```

채워야 할 값:

| 변수 | 용도 | 비고 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 백엔드 DB 연결 | `jdbc:postgresql://144.24.90.88:5432/carix?sslmode=require` (§3) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 개인별 DB 롤 | §3.0 에서 발급받는다 |
| `CARIX_ENCRYPTION_KEY` | 컬럼 암호화(AES-256-GCM) | `openssl rand -base64 32` |
| `KOSIS_API_KEY` 외 3종 | 배치 원천 API | 선행 검증 후 발급 |

## 3. OCI DB 원격 연결

로컬엔 Postgres를 띄우지 않는다. **운영과 개발이 같은 DB(`carix`)를 쓴다.**

> **접근 경로 완료.** **Postgres TLS 활성화 + OCI Security List IP 허용목록**으로
> 접속하며, 서버에서 검증 완료됐다: TLS `on` 확인, 평문 외부 접속 시도는
> `no pg_hba.conf entry ... no encryption`로 명시 거부, 허용 IP에서 정상 접속.
> Security List는 화이트리스트 방식(등록된 IP `/32`만 통과, 나머지 전부 default-deny)
> 이라 팀원마다 IP를 등록해야 한다(§3.0).

> **여기서 실행하는 쿼리와 마이그레이션은 곧 운영에 적용된다.** 개발용 사본이 아니다.
> 파괴적 변경 전에는 팀에 알리고, 마이그레이션은 §3.2 규약을 지킨다.

### 왜 같은 DB를 쓰는가

운영과 개발이 `carix` DB 하나를 공유한다. 팀원이 로컬에서 실데이터를 보며 개발해야 하기
때문이다. 팀원은 개인별 DB 롤을 발급받는다(공용 계정은 이탈 시 회수가 안 돼서 안 씀).

**검토한 대안:**

- **DB 2개 분리** — 운영 데이터가 개발과 격리된다. 다만 개발 중 확인하고 싶은
  실제 데이터가 dev DB에 없어, 같은 데이터를 보려면 별도 동기화가 필요하다.
- **개발자별 로컬 DB** — 임베디드 Postgres 등으로 자격증명·충돌 문제가 사라지지만,
  팀이 같은 데이터를 공유하지 못한다.
- **관리형 dev DB(Neon 등) 브랜칭** — 브랜치별 격리를 얻지만 외부 서비스가 늘고
  운영과 버전 드리프트가 생긴다.
- **DBLab 자체 호스팅** — thin clone 으로 공유 데이터 + 격리를 동시에 얻는 정석이나,
  ZFS 가 필요하고 서버 디스크 여유가 25GB 뿐이라 이 호스트에는 과하다.

**감수하는 위험** — 지금은 사용자와 데이터가 없어 실질 피해가 없으나, **실데이터가
들어오는 시점 전에 재검토해야 한다.**

| 위험 | 내용 |
| --- | --- |
| 개인정보 접근 | 팀원 전원이 실사용자의 연봉·지출·경력 데이터에 직접 접근한다. `SECURITY.md`가 컬럼 암호화를 요구하는 그 데이터다 |
| 스키마 사고 | `auth`는 브랜치에서 잘못 실행한 Flyway 마이그레이션이, `core`는 손으로 친 DDL이 곧바로 운영 스키마에 적용된다 |
| 백업 부재 | 현재 DB 백업이 구현돼 있지 않다. 위 사고가 나면 복구 수단이 없다 |
| 테스트 데이터 혼입 | 로컬에서 만든 데이터가 운영 DB에 남는다 |
| 자격증명 확산 | 운영 DB 접근 정보가 팀원 노트북마다 존재한다 |
| 게이트 부재 | PR 단계 CI(pr-check.yml)와 배포 실패 시 자동 롤백 및 수동 롤백 워크플로(rollback.yml)를 구축하여 운영 안정성을 확보 |

**DB 백업이 최우선 후속 과제다** — 위 위험표에서 유일하게 즉시 완화 가능한 항목이다.

### 3.0 팀원 온보딩

1. **IP 확인** (신규 팀원) — 고정 IP인지 먼저 확인한다. 유동 IP(카페·모바일 핫스팟 등)면
   접속이 끊길 때마다 관리자가 규칙을 다시 등록해야 한다.

   ```bash
   curl -s https://ifconfig.me
   ```

2. **Security List에 IP 등록** (관리자, OCI CLI)

   ```bash
   # 현재 규칙 조회
   oci network security-list get --security-list-id <SL_OCID> \
     --query 'data."ingress-security-rules"' > /tmp/current_ingress.json

   # <새-IP>/32 로 TCP 5432(DB) · 8890(config-server) · 8761(eureka) 세 규칙을 추가해
   # /tmp/new_ingress.json 으로 저장한 뒤
   oci network security-list update --security-list-id <SL_OCID> \
     --ingress-security-rules file:///tmp/new_ingress.json --force
   ```

   세 포트 모두 등록해야 한다 — 하나만 등록하면 그 포트만 열리고 나머지는 계속 막힌다.
   `<SL_OCID>`는 인스턴스 서브넷에 붙은 Security List OCID다
   (`oci compute instance list-vnics` → `oci network subnet get` 으로 확인).
   기존 규칙(다른 포트·다른 IP)은 절대 지우지 않는다 — 조회한 배열에 새 규칙만 추가한다.

3. **개인별 DB 롤 발급** (관리자) — 공용 계정을 돌려쓰지 않는다. 이탈 시 회수가 안 되기
   때문이다.

   ```bash
   docker exec -i carix-postgres-1 psql -U carix -d carix <<SQL
   CREATE ROLE "<이름>" LOGIN PASSWORD '<생성한-비밀번호>';
   GRANT ALL ON DATABASE carix TO "<이름>";
   GRANT ALL ON SCHEMA public TO "<이름>";
   SQL
   ```

4. §2의 `.env` 작성 (`sslmode=require` 필수 — §3.3)

**팀원이 빠지면** Security List 규칙과 DB 롤을 함께 제거한다.

```bash
docker exec -i carix-postgres-1 psql -U carix -d carix -c 'DROP ROLE "<이름>"'
```

(Security List에서 해당 IP 규칙 제거는 2번과 같은 방식 — 배열에서 그 항목만 빼고 update)

### 3.1 서버 설정 (완료, 관리자 참고용)

TLS 인증서·pg_hba 설정은 `deploy/postgres/`(git 관리) + 서버 전용 비밀 경로
(`/home/opc/carix-secrets/postgres/`, git 밖)로 이미 구성 완료됐다. 재설치가 필요하면:

```bash
# 자체 서명 인증서 생성 (10년 유효)
sudo mkdir -p /home/opc/carix-secrets/postgres
sudo openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout /home/opc/carix-secrets/postgres/server.key \
  -out /home/opc/carix-secrets/postgres/server.crt \
  -subj "/CN=144.24.90.88"
sudo chown 70:70 /home/opc/carix-secrets/postgres/server.key /home/opc/carix-secrets/postgres/server.crt
sudo chmod 600 /home/opc/carix-secrets/postgres/server.key
sudo chmod 644 /home/opc/carix-secrets/postgres/server.crt
sudo chmod 700 /home/opc/carix-secrets/postgres
```

uid:gid `70:70`는 `postgres:16-alpine` 이미지의 postgres 프로세스 실행 사용자다.
소유자가 다르거나 개인키 권한이 600보다 넓으면 컨테이너가 기동 시 거부한다.
나머지(hba_file, ssl 플래그)는 `docker-compose.yml`의 postgres `command`를 참조한다.

### 3.2 스키마 관리 규약

스키마마다 관리 방식이 다르다. 붙어 있는 모듈을 먼저 확인한다.

| 스키마 | 모듈 | 관리 방식 |
| --- | --- | --- |
| `auth` | auth-service | Flyway 마이그레이션 (`src/main/resources/db/migration`) |
| `core` | core-api, collector | **DDL 직접 적용** (Flyway 미사용) |

#### core 스키마 — 직접 적용

`core-api`와 `collector` 모두 Flyway를 쓰지 않는다. **실행 정본은
[`deploy/postgres/schema/`](../deploy/postgres/schema/)의 `.sql` 파일들**이고, 설계 배경은
[`specs/technical-specification.md` §1](specs/technical-specification.md)에 있다.

```
deploy/postgres/schema/
├── 01_job_posting.sql             채용공고 수집 5개 테이블
├── 02_user_domain.sql             프로필·소득·소비·보유스킬 5개
├── 03_reference_and_aggregate.sql CPI·임금통계·시장집계 3개
└── 04_snapshot_and_scenario.sql   지수 스냅샷·시나리오 2개
```

파일은 번호 순서대로 적용한다 — 뒤 파일이 앞 파일의 테이블을 참조한다.

```bash
psql "postgresql://<user>@144.24.90.88:5432/carix?sslmode=require" \
  -v ON_ERROR_STOP=1 -f deploy/postgres/schema/02_user_domain.sql
```

`ON_ERROR_STOP=1`을 붙인다. 없으면 중간 문장이 실패해도 나머지가 계속 실행돼 절반만 적용된
스키마가 남는다.

스키마를 바꿀 때는 해당 `.sql` 파일을 고치고, 그 변경분만 운영에 적용한다. 파일 전체를 다시
돌리면 이미 있는 테이블에서 막힌다 — 파일은 빈 DB 재현용이고, 변경 적용은 `ALTER` 문을 직접
친다.

지켜야 할 것:

- **배포보다 DDL이 먼저다.** 새 컬럼·테이블을 쓰는 코드가 먼저 뜨면 런타임에
  `relation ... does not exist` 예외로 애플리케이션 기동에 실패합니다. 스키마를 적용하고 나서 머지한다.
- **적용 이력이 자동으로 남지 않는다.** 무엇을 언제 적용했는지 PR 본문이나
  `docs/specs/erd.md` 운영 현황 절에 직접 남긴다.
- **`DROP`은 데이터가 사라진다.** 운영과 개발이 같은 DB다(§3 왜 같은 DB를 쓰는가).
  컬럼 삭제·타입 변경은 되돌릴 수 없다고 보고 진행한다.
- 스키마가 스펙과 어긋났는지는 `\d core.<table>`로 직접 대조한다. 자동 검증 수단은 없다.

새 환경(빈 DB)에 `core`를 재현하려면 정본 DDL을 순서대로 실행한다 — 앱을 띄운다고
테이블이 생기지 않는다.

#### auth 스키마 — Flyway

DB가 하나뿐이므로 마이그레이션 파일 이름이 충돌하면 **운영 스키마에서** 막힌다.

```
A 브랜치: V3__add_income.sql   → 공유 dev DB에 먼저 적용됨
B 브랜치: V3__add_skill.sql    → 같은 버전, 다른 체크섬 → checksum mismatch
```

**버전은 순번이 아니라 타임스탬프로 짓는다.** 같은 초에 두 명이 파일을 만들 일은 사실상 없다.

```
V20260727_143000__add_income_history.sql
V20260727_151200__add_skill_catalog.sql
```

dev 프로파일에서만 순서 뒤바뀜을 허용한다. 운영은 엄격하게 둔다.

```yaml
# application-dev.yml
spring:
  flyway:
    out-of-order: true
```

그래도 막히면 본인 브랜치 작업분만 되돌린다. **DB를 통째로 초기화하지 않는다** — 운영
데이터가 사라진다. `flyway clean`은 절대 실행하지 않는다.

### 3.3 IntelliJ 설정

노트북에 컨테이너를 띄우지 않는다 — 원격 DB에 바로 붙는다. 접속 전에 IP가
Security List에 등록돼 있어야 한다(§3.0).

**Database 도구 창**

1. Data Source → PostgreSQL
2. Host `144.24.90.88`, Port `5432`, Database `carix`, User `<본인 롤>`
3. **SSL 탭 → Use SSL 체크**, Mode `require` (자체 서명 인증서라 `verify-full`은 실패한다 —
   `require`면 충분하다. 인증서 체인 검증 없이 암호화만 강제)
4. Test Connection으로 확인

**이 연결은 운영 DB다.** 데이터 편집·삭제 전에 무엇을 지우는지 확인한다.

**core-api 실행 구성**

Run/Debug Configurations → `core-api` → Active profiles 에 `local` 을 고정한다(§5).
매번 커맨드에 `--spring.profiles.active=local` 을 타이핑하지 않기 위해서다 — 빠뜨리면
서버 Eureka 에 등록된다.

## 4. 프론트엔드 개발

```bash
cd frontend
pnpm install
pnpm dev              # Vite dev server (기본 5173)
```

- `/api` 프록시는 아직 구성돼 있지 않다 — 프론트가 백엔드를 호출하는 시점에
  `vite.config.ts`에 추가한다.
- Docker 불필요.

## 5. 백엔드 개발

`config-server`(8890)·`eureka`(8761) 는 서버에서 이미 돈다 — 로컬에는 작업 대상 하나만
띄운다(Gateway 작업은 예외 — 아래 spring-cloud-architecture.md §6 참조). 서버 것을
원격으로 쓴다 — OCI Security List IP 허용목록으로 열었다(Postgres 5432와
같은 바인딩 방식). **TLS는 없다** — 두 서비스 다 시크릿을 다루지 않아(라우트·임계값·
기능 플래그, 서비스 레지스트리뿐) Security List 단독 방어로 충분하다고 판단했다
(Postgres는 실제 자격증명이 오가서 TLS를 추가했다). 서버 쪽 포트·볼륨 설정은
`docker-compose.yml`의 config-server·eureka 서비스 주석을 참조한다.

**가장 큰 위험 — 로컬 인스턴스가 운영 트래픽을 받는다.** 로컬 `core-api`가 서버 Eureka에
등록되면 서버 쪽 라우팅이 그리로 실사용자 요청을 보낼 수 있습니다. 노트북 환경 종료 시 서비스 요청이 실패할 수 있으므로,
**`local` 프로파일을 반드시 활성화**해야 합니다 (`application-local.yml`의 `register-with-eureka: false` 적용).

```bash
cd backend
CONFIG_SERVER_URL=http://144.24.90.88:8890 EUREKA_URL=http://144.24.90.88:8761/eureka/ \
  ./gradlew :core-api:bootRun --args='--server.port=8080 --spring.profiles.active=local'
```

`--spring.profiles.active=local` 을 빠뜨리면 서버 Eureka에 등록된다. 커맨드에서 매번
타이핑하는 대신 IntelliJ 실행 구성의 Active profiles 에 `local`을 고정해 둔다(§3.3).

컨텍스트 패스가 `/api`다. 기동 후 확인 경로:

| URL | 내용 |
| --- | --- |
| `http://localhost:8080/api/health` | 헬스체크 |
| `http://localhost:8080/api/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8080/api/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/api/v1/demo/thresholds` | Config Server 연동 확인. 서버 `config/spring/core-api.yml` 값을 읽는다 |

- **core-api 는 DB 를 쓰지 않는다** — 채용공고 도메인이 `collector` 로 이동해
  JPA·Postgres 의존성이 없다. `spring.datasource.*` 없이 기동한다.
  `market_aggregate` 를 읽기 시작하면 다시 필요해지지만, 그때는 JPA 가 아니라
  `JdbcClient` 읽기 전용으로 들인다(`specs/data-architecture.md` §3.1).
- core-api 에는 Flyway 의존성이 없고 JPA 도 없다.
  `core` 스키마 DDL 은 `collector` 가 쓰는 대상이다 — 적용 절차는 §3.2 를 따른다.
- 수집·정규화는 `collector` 모듈이다. `application.yml`이 `spring.cloud.config.fail-fast:
  true`를 켜 두므로 위 core-api 예시와 같은 방식으로 환경 변수와 `local` 프로파일을 줘야
  기동한다. 포트는 `application.yml` 기본값(8083)을 그대로 쓰므로 `--server.port`는
  붙이지 않는다.

```bash
cd backend
CONFIG_SERVER_URL=http://144.24.90.88:8890 EUREKA_URL=http://144.24.90.88:8761/eureka/ \
  ./gradlew :collector:bootRun --args='--spring.profiles.active=local'
```

#### Collector 내부 파이프라인 API (`/internal/pipeline/*`)

`collector`는 Gateway에 노출되지 않는 관리 전용 내부 API(`http://localhost:8083/internal/pipeline/**`)를 제공합니다:

| 메서드 & 경로 | 설명 | 호출 예시 |
| --- | --- | --- |
| `POST /internal/pipeline/crawl` | 잡코리아/사람인 웹 스크래핑 & DB 실시간 직접 적재 | `curl -X POST http://localhost:8083/internal/pipeline/crawl -H "Content-Type: application/json" -d '{"source": "ALL", "pageLimit": 5}'` |
| `POST /internal/pipeline/seed` | `verification/out` 디렉토리 시드 파일 적재 | `curl -X POST http://localhost:8083/internal/pipeline/seed -H "Content-Type: application/json" -d '{}'` |
| `POST /internal/pipeline/reprocess` | DB `raw_ingest` 원본 데이터 기반 재파싱·재가공 | `curl -X POST http://localhost:8083/internal/pipeline/reprocess -H "Content-Type: application/json" -d '{"dryRun": false}'` |
| `GET /internal/pipeline/jobs/{jobId}` | 특정 비동기 파이프라인 작업 상태 조회 | `curl http://localhost:8083/internal/pipeline/jobs/{jobId}` |
| `GET /internal/pipeline/jobs` | 최근 실행된 파이프라인 작업 히스토리 목록 조회 | `curl http://localhost:8083/internal/pipeline/jobs` |
| `GET /internal/pipeline/stats` | 수집 DB(`raw_ingest`, `job_posting`, `skill`) 테이블 건수 현황 통계 | `curl http://localhost:8083/internal/pipeline/stats` |

프로젝트 진행 상태는 [`README.md` 진행 상태](README.md#진행-상태)를 참조.

## 6. 테스트

```bash
# 프론트엔드
cd frontend && pnpm test          # 서버 스모크 테스트 — pretest 가 먼저 pnpm build 를 돈다
pnpm test:unit                    # Vitest (컴포넌트 단위)
pnpm test:e2e                     # Playwright

# 백엔드 단위
cd backend && ./gradlew test
```

> **PR 자동 검증 & 스마트 Skip CI:** `.github/workflows/pr-check.yml` 워크플로가 PR 단계에서 변경 사항을 자동으로 검증합니다:
> - **스마트 영역 분기 (`dorny/paths-filter`)**: 백엔드만 수정 시 Frontend Job 자동 스킵, 프론트엔드만 수정 시 Backend Job 자동 스킵.
> - **문서 자동 스킵**: `docs/**`, `*.md`, `.githooks/**` 수정 시 CI 워크플로가 트리거되지 않습니다.
> - **작업 중 스킵 (Draft PR)**: PR이 Draft 상태일 때는 CI가 돌지 않으며, "Ready for review" 전환 시 검증이 실행됩니다.
> - **수동 스킵 (`[skip ci]` / 라벨)**: 커밋 메시지에 `[skip ci]`를 포함하거나 PR에 `skip-ci` 라벨 부착 시 검증을 건너뜁니다.
> - **빌드 캐시 가속**: `ubuntu-latest` 러너와 Gradle 빌드 캐시, pnpm 캐시를 통해 수십 초 내로 빠르게 완료됩니다.

### DB를 쓰는 테스트는 두지 않는다

테스트용 DB를 쓰지 않기로 했다. 로컬에 Docker가 없고 운영 DB를 테스트 대상으로 삼을 수도
없으므로, DB 연결이 필요한 테스트는 실행할 곳이 없다.

원래는 Testcontainers 설계였고 auth-service에 해당 테스트가 있었으나, 로컬 개발 환경의 Docker 부재로 실행이 어려웠고 CI에서도 실질적인 검증 지점이 없어 의존성과 함께 제거했습니다.

대신 컨텍스트를 띄우는 테스트는 **연결하지 않는 DataSource**로 돌린다
(`backend/collector/build.gradle`의 `test` 블록):

```gradle
systemProperty 'spring.datasource.url', 'jdbc:postgresql://127.0.0.1:1/unused'
systemProperty 'spring.datasource.hikari.initialization-fail-timeout', '-1'
systemProperty 'spring.jpa.database-platform', 'org.hibernate.dialect.PostgreSQLDialect'
systemProperty 'spring.jpa.hibernate.ddl-auto', 'none'
```

커넥션은 잡지 않고 방언만 고정해 `EntityManagerFactory`를 만든다. 그래서 **엔티티 매핑과
`@Query` JPQL은 컨텍스트 로드 시점에 검증된다** — 잘못된 필드명을 쓰면 `contextLoads`가 깨진다.
DB를 실제로 읽고 쓰는 테스트를 새로 쓰면 연결 거부로 실패한다. 조용히 통과하지 않는다.

DB 동작 확인이 필요하면 테스트가 아니라 앱을 OCI DB에 붙여 직접 확인한다(§3).

## 7. 코드 스타일 및 포맷팅 (Code Formatting)

### 백엔드 (Google Java Format)
백엔드는 **Google Java Format (1.36.1)** 최신 표준 스타일을 사용하며, **Spotless** 플러그인으로 관리합니다.

* **수동 포맷팅 적용**:
  ```bash
  cd backend && ./gradlew spotlessApply
  ```
* **포맷 준수 검사**:
  ```bash
  cd backend && ./gradlew spotlessCheck
  ```
* **커밋 전 자동 포맷팅 (Git Hook)**:
  저장소 루트에서 다음 명령어로 공유 pre-commit 훅을 1회 활성화하면, `git commit` 시 변경된 `.java` 파일에 Google Java Format이 자동으로 적용되어 스테이징됩니다.
  ```bash
  git config core.hooksPath .githooks
  ```

### 프론트엔드 (ESLint & Prettier)
* `cd frontend && pnpm lint` (ESLint 정적 검사)
* `cd frontend && pnpm format` (Prettier 포맷팅)

## 8. 커밋·브랜치

- `main` 직접 커밋 금지. 기능 브랜치(`feature/`·`fix/`·`docs/`·`chore/`) + PR.
- API 키·개인정보·`.env`는 커밋하지 않는다.
- 상세: [루트 README 협업 방식](../README.md#협업-방식).
