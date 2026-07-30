# Carix 로컬 개발 가이드

로컬은 순수 코드 개발이다. **Docker와 PostgreSQL은 OCI 서버에서만 기동**하고, 로컬
백엔드는 OCI dev DB에 원격 연결한다. 배경과 결정은 [루트 README 개발 환경](../README.md#개발-환경)을 참조한다.

> **현재 스캐폴드 상태 (2026-07-26).** 이 문서는 목표 상태를 기술한다. 지금 실제로 되는 것과
> 다음 스캐폴드를 기다리는 것을 구분한다.
>
> | 항목 | 지금 |
> | --- | --- |
> | 백엔드 | Spring Boot 4.1 + springdoc. `./gradlew build`·`bootRun` 동작. **JPA·Flyway·Security·Batch 없음** |
> | 프런트엔드 | 배포 검증용 스텁(`server.js`)만. **Vite·React 없음.** `pnpm dev`·`test:e2e` 스크립트 없음 |
> | DB | 백엔드가 아직 DB를 쓰지 않는다. §3 접근 설정은 JPA 도입 후부터 필요 |
>
> 따라서 §4(프런트엔드)와 §5의 Flyway·배치 항목, §6의 Playwright는 각 프로젝트 초기화 이후
> 활성화된다.

## 1. 사전 준비

### 1.1 런타임 버전

컨테이너는 Java 21, Node.js 24 LTS로 고정한다. 로컬도 동일 버전으로 맞춘다
(개발 머신의 상위 버전과 별개).

```bash
# Node 24 (nvm)
nvm install 24 && nvm use 24
corepack enable            # pnpm 활성화

# Java 21 (sdkman)
sdk install java 21-tem && sdk use java 21-tem
```

버전 확인:

```bash
node --version   # v24.x
java --version   # 21.x
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
| `SPRING_DATASOURCE_URL` | 백엔드 DB 연결 | `<서버-tailnet-이름>:5432/carix` (§3) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 개인별 DB 롤 | §3.0 에서 발급받는다 |
| `CARIX_ENCRYPTION_KEY` | 컬럼 암호화(AES-256-GCM) | `openssl rand -base64 32` |
| `KOSIS_API_KEY` 외 3종 | 배치 원천 API | 선행 검증 후 발급 |

## 3. OCI DB 원격 연결

로컬엔 Postgres를 띄우지 않는다. **운영과 개발이 같은 DB(`carix`)를 쓴다**
([ADR 0007](adr/0007-single-database-for-prod-and-dev.md)). 팀원은 Headscale tailnet 을 통해
여기에 붙는다 ([ADR 0008](adr/0008-db-access-via-tailscale.md)).

> **여기서 실행하는 쿼리와 마이그레이션은 곧 운영에 적용된다.** 개발용 사본이 아니다.
> 파괴적 변경 전에는 팀에 알리고, 마이그레이션은 §3.3 규약을 지킨다.

### 3.0 팀원 온보딩

접근 경로는 **Headscale**(자체 호스팅 Tailscale)이다
([ADR 0008](adr/0008-db-access-via-tailscale.md)). 클라이언트는 Tailscale 공식 클라이언트를
그대로 쓴다. SSH 키를 배포하지 않는다.

1. **사용자·preauth key 발급** (관리자)

   ```bash
   ssh carix-oci 'docker exec carix_headscale headscale users create <이름>'
   ssh carix-oci 'docker exec carix_headscale headscale preauthkeys create --user <이름> --expiration 24h'
   ```

   출력된 키를 비밀번호 관리자 등 안전한 경로로 전달한다. 24시간 뒤 만료된다.

2. **클라이언트 설치·등록** (신규 팀원)

   노트북에는 **Tailscale 앱 하나만** 설치한다. Docker 도 컨테이너도 필요 없다.

   ```bash
   brew install --cask tailscale     # macOS — standalone 변형
   tailscale up --login-server https://headscale.144.24.90.88.sslip.io --authkey <preauth-key>
   ```

   > **App Store 버전을 쓰지 않는다.** 샌드박스 제약이 있고 Tailscale 문서도 standalone 을
   > 기본으로 권장한다. `brew install --cask tailscale` 이 standalone 이다.
   >
   > GUI 로 하려면 Option 키를 누른 채 메뉴 아이콘 클릭 → Debug → Custom Login Server →
   > *Add Account...* 에 위 URL 을 넣는다.

3. **개인별 DB 롤 발급** — 공용 계정을 돌려쓰지 않는다. 이탈 시 회수가 안 되기 때문이다.

   ```bash
   docker exec -i carix-postgres-1 psql -U carix -d carix <<SQL
   CREATE ROLE "<이름>" LOGIN PASSWORD '<생성한-비밀번호>';
   GRANT ALL ON DATABASE carix TO "<이름>";
   GRANT ALL ON SCHEMA public TO "<이름>";
   SQL
   ```

4. §2의 `.env` 작성

**팀원이 빠지면** Headscale 사용자와 DB 롤을 함께 제거한다.

```bash
ssh carix-oci 'docker exec carix_headscale headscale users destroy <이름>'
docker exec -i carix-postgres-1 psql -U carix -d carix -c 'DROP ROLE "<이름>"'
```

### 3.1 서버 1회 설정 (관리자)

Headscale 컨테이너는 배포로 뜬다. 서버 **호스트**가 그 tailnet에 참여해야 DB 포트를 중계할 수 있다.

```bash
# 호스트에 Tailscale 클라이언트 설치
curl -fsSL https://tailscale.com/install.sh | sh

# 자기 자신의 headscale 에 등록
docker exec carix_headscale headscale users create server
docker exec carix_headscale headscale preauthkeys create --user server --expiration 1h
sudo tailscale up --login-server https://headscale.144.24.90.88.sslip.io --authkey <키>

# DB 포트를 tailnet 안에만 중계 (재부팅 후에도 유지)
sudo tailscale serve --bg --tcp=5432 tcp://localhost:5432

# core-api 를 로컬에서 서버 Config·Eureka 에 붙이려면(§5) 이 둘도 중계한다
sudo tailscale serve --bg --tcp=8890 tcp://localhost:8890   # config-server
sudo tailscale serve --bg --tcp=8761 tcp://localhost:8761   # eureka
```

> **미완료.** 위 `tailscale up`은 아직 서버에서 실행되지 않았다 — 브라우저 인증이 필요해
> 관리자가 직접 해야 한다. 그 전까지 §5의 `CONFIG_SERVER_URL` 은 tailnet 으로 닿지 않는다.

`serve`는 tailnet 내부에만 공개한다 — 인터넷에 여는 것은 `funnel`이며 쓰지 않는다.

팀원은 클라이언트가 켜져 있으면 그냥 붙는다. 터널 명령이 없다.

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<서버-tailnet-이름>:5432/carix
```

tailnet 이름은 `tailscale status` 또는
`docker exec carix_headscale headscale nodes list`로 확인한다.

### 3.2 왜 이 구성인가

**5432를 인터넷에 직접 열지 않는다.** 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
직접 노출하면 비밀번호와 모든 쿼리·결과가 평문으로 오간다. 운영 DB이므로 더 그렇다.
tailnet 구간은 WireGuard로 암호화된다.

`docker-compose.yml`의 postgres는 `127.0.0.1:5432:5432`로 **루프백에만** 바인딩하고,
호스트의 `tailscale serve`가 그 루프백 포트를 tailnet에 중계한다. Headscale 컨테이너 자체도
호스트 포트를 발행하지 않고 라우터 Caddy 만 닿는다.

- `expose`만 두면 호스트에 포트가 없어 `serve`가 중계할 대상이 없다.
- `"5432:5432"`로 쓰면 `0.0.0.0`에 붙어 인터넷에 노출된다. Docker 발행 포트는 firewalld를
  우회하므로 호스트 방화벽으로 막을 수 없고, OCI Security List에서 걸러야 한다.
- `127.0.0.1:`을 명시하면 외부 경로가 없고 tailnet 안에서만 닿는다.

### 3.3 Flyway 규약

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

### 3.4 IntelliJ 설정

Tailscale 클라이언트가 켜져 있으면 별도 설정이 없다. 실행 구성의 before-launch 훅도,
Database 도구의 SSH 터널 설정도 필요 없다. 노트북에 컨테이너를 띄우지 않는다.

**Database 도구 창**

1. Data Source → PostgreSQL
2. Host `<서버-tailnet-이름>`, Port `5432`, Database `carix`, User `<본인 롤>`

**이 연결은 운영 DB다.** 데이터 편집·삭제 전에 무엇을 지우는지 확인한다.

**core-api 실행 구성**

Run/Debug Configurations → `core-api` → Active profiles 에 `local` 을 고정한다(§5).
매번 커맨드에 `--spring.profiles.active=local` 을 타이핑하지 않기 위해서다 — 빠뜨리면
서버 Eureka 에 등록된다.

## 4. 프런트엔드 개발

```bash
cd frontend
pnpm install
pnpm dev              # Vite dev server (기본 5173)
```

- `/api` 요청은 Vite 프록시로 백엔드(`http://localhost:8080`)에 전달한다
  (프록시 설정은 `vite.config.ts`).
- Docker 불필요.

## 5. 백엔드 개발

`config-server`·`eureka` 는 서버에서 이미 돈다 — 로컬에는 작업 대상(`core-api`) 하나만 띄운다.

**가장 큰 위험 — 로컬 인스턴스가 운영 트래픽을 받는다.** 로컬 `core-api`가 서버 Eureka에
등록되면 서버 쪽 라우팅이 그리로 실사용자 요청을 보낼 수 있다. 노트북을 닫으면 서비스가 죽는다.
그래서 **`local` 프로파일을 반드시 켠다** — `application-local.yml`이 `register-with-eureka:
false`를 강제한다.

Tailscale 클라이언트가 켜진 상태에서(§3, §3.1의 8890·8761 중계 포함):

```bash
cd backend
CONFIG_SERVER_URL=http://<서버-tailnet-주소>:8890 EUREKA_URL=http://<서버-tailnet-주소>:8761/eureka/ \
  ./gradlew :core-api:bootRun --args='--spring.profiles.active=local'
```

> §3.1의 8890·8761 중계가 아직 서버에 설정되지 않았다면(관리자의 `tailscale up` 미완료),
> 이 명령은 config-server 에 닿지 못하고 fail-fast 로 죽는다. 그때까지 로컬 검증은
> `backend/README.md`의 로컬 `config-server`·`eureka` 직접 기동 절차를 쓴다.

`--spring.profiles.active=local` 을 빠뜨리면 서버 Eureka에 등록된다. 커맨드에서 매번
타이핑하는 대신 IntelliJ 실행 구성의 Active profiles 에 `local`을 고정해 둔다(§3.4).

컨텍스트 패스가 `/api`다. 기동 후 확인 경로:

| URL | 내용 |
| --- | --- |
| `http://localhost:8080/api/health` | 헬스체크 |
| `http://localhost:8080/api/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8080/api/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/api/v1/demo/thresholds` | Config Server 연동 확인. 서버 `config/spring/core-api.yml` 값을 읽는다 |

- **DB 없이 뜬다.** 아직 DataSource 자동설정이 없으므로 §3 설정 없이도 기동된다.
  아래 두 항목은 JPA·Batch 도입 이후 적용된다.
- Flyway가 기동 시 마이그레이션을 적용한다. dev DB에만 적용되도록 프로파일을 확인한다.
- 배치 워커는 별도 프로파일(`--spring.profiles.active=local,batch`)로 실행한다.

전환 예정(Gateway 도입, `auth-service`·`batch-worker` 분리)은
[Spring Cloud 아키텍처 §6](specs/spring-cloud-architecture.md) 참조.

## 6. 테스트

```bash
# 프런트엔드 단위·컴포넌트
cd frontend && pnpm test          # Vitest
pnpm test:e2e                     # Playwright

# 백엔드 단위
cd backend && ./gradlew test
```

### 통합 테스트 주의

설계는 Testcontainers를 쓰나 로컬 Docker가 필요하다. Docker가 OCI 전용이므로:

- **로컬**: 단위 테스트 중심으로 돌린다.
- **통합 테스트(Testcontainers)**: main 푸시 시 배포 워크플로의 backend 잡(Docker 가능 러너)에서
  실행된다. [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) 참조.
  **PR 단계에서는 아무것도 돌지 않는다** — 머지 전에 확인하려면 로컬에서 직접 돌린다.
- 급히 로컬에서 통합 테스트가 필요하면 OCI dev DB를 대상으로 실행하되, 스키마
  드리프트에 주의한다.

## 7. 커밋·브랜치

- `main` 직접 커밋 금지. 기능 브랜치(`feature/`·`fix/`·`docs/`·`chore/`) + PR.
- API 키·개인정보·`.env`는 커밋하지 않는다.
- 상세: [루트 README 협업 방식](../README.md#협업-방식).
