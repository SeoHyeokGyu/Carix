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
> | DB | 백엔드가 아직 DB를 쓰지 않는다. §3 터널은 JPA 도입 후부터 필요 |
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
| `SPRING_DATASOURCE_URL` | 백엔드 DB 연결 | §3 터널 사용 시 `localhost:5432/carix` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | 개인별 DB 롤 | §3.0 에서 발급받는다 |
| `CARIX_ENCRYPTION_KEY` | 컬럼 암호화(AES-256-GCM) | `openssl rand -base64 32` |
| `KOSIS_API_KEY` 외 3종 | 배치 원천 API | 선행 검증 후 발급 |

## 3. OCI DB 원격 연결

로컬엔 Postgres를 띄우지 않는다. **운영과 개발이 같은 DB(`carix`)를 쓴다**
([ADR 0007](adr/0007-single-database-for-prod-and-dev.md)). 팀원은 SSH 터널로 여기에 붙는다.

> **여기서 실행하는 쿼리와 마이그레이션은 곧 운영에 적용된다.** 개발용 사본이 아니다.
> 파괴적 변경 전에는 팀에 알리고, 마이그레이션은 §3.3 규약을 지킨다.

### 3.0 팀원 온보딩

접근 경로는 **Tailscale**이다 ([ADR 0008](adr/0008-db-access-via-tailscale.md)).
SSH 키를 배포하지 않고 SSO 로그인으로 신원을 확인한다.

1. **Tailscale 초대** (관리자) — 관리 콘솔에서 팀원을 tailnet에 초대한다.
   무료 Personal 플랜은 사용자 6명까지다.

2. **클라이언트 설치·로그인** (신규 팀원)

   ```bash
   brew install --cask tailscale     # macOS
   tailscale up                      # 브라우저 SSO 로그인
   ```

3. **개인별 DB 롤 발급** — 공용 계정을 돌려쓰지 않는다. 이탈 시 회수가 안 되기 때문이다.

   ```bash
   docker exec -i carix-postgres-1 psql -U carix -d carix <<SQL
   CREATE ROLE "<이름>" LOGIN PASSWORD '<생성한-비밀번호>';
   GRANT ALL ON DATABASE carix TO "<이름>";
   GRANT ALL ON SCHEMA public TO "<이름>";
   SQL
   ```

   비밀번호는 비밀번호 관리자 등 안전한 경로로 전달한다. 채팅·이슈에 붙여넣지 않는다.

4. §2의 `.env` 작성

**팀원이 빠지면** Tailscale 콘솔에서 사용자를 제거하고 DB 롤도 함께 삭제한다.

```bash
docker exec -i carix-postgres-1 psql -U carix -d carix -c 'DROP ROLE "<이름>"'
```

### 3.1 Tailscale 연결

서버는 tailnet에 참여해 DB 포트를 tailnet 안에만 노출한다. **서버 1회 설정**(관리자):

```bash
sudo tailscale up                                          # 브라우저 인증 필요
sudo tailscale serve --bg --tcp=5432 tcp://localhost:5432
```

`--bg`는 백그라운드 상시 실행이며 재부팅 후에도 유지된다. `serve`는 tailnet 내부에만
공개한다 — 인터넷에 여는 것은 `funnel`이며 쓰지 않는다.

팀원은 클라이언트가 켜져 있으면 그냥 붙는다. 터널 명령이 없다.

```
SPRING_DATASOURCE_URL=jdbc:postgresql://<서버-tailnet-이름>:5432/carix
```

서버의 tailnet 이름은 `tailscale status`로 확인한다.

### 3.2 왜 이 구성인가

**5432를 인터넷에 직접 열지 않는다.** 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
직접 노출하면 비밀번호와 모든 쿼리·결과가 평문으로 오간다. 운영 DB이므로 더 그렇다.
Tailscale 구간은 WireGuard로 암호화된다.

`docker-compose.yml`의 postgres는 `127.0.0.1:5432:5432`로 **루프백에만** 바인딩하고,
`tailscale serve`가 그 루프백 포트를 tailnet에 중계한다.

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
Database 도구의 SSH 터널 설정도 필요 없다.

**Database 도구 창**

1. Data Source → PostgreSQL
2. Host `<서버-tailnet-이름>`, Port `5432`, Database `carix`, User `<본인 롤>`

**이 연결은 운영 DB다.** 데이터 편집·삭제 전에 무엇을 지우는지 확인한다.

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

터널(§3)이 열린 상태에서:

```bash
cd backend
./gradlew bootRun
```

컨텍스트 패스가 `/api`다. 기동 후 확인 경로:

| URL | 내용 |
| --- | --- |
| `http://localhost:8080/api/health` | 헬스체크 |
| `http://localhost:8080/api/swagger-ui/index.html` | Swagger UI |
| `http://localhost:8080/api/v3/api-docs` | OpenAPI JSON |

- **DB 없이 뜬다.** 아직 DataSource 자동설정이 없으므로 §3 터널 없이도 기동된다.
  아래 두 항목은 JPA·Batch 도입 이후 적용된다.
- Flyway가 기동 시 마이그레이션을 적용한다. dev DB에만 적용되도록 프로파일을 확인한다.
- 배치 워커는 별도 프로파일(`--spring.profiles.active=dev,batch`)로 실행한다.

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
