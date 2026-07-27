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
| `SPRING_DATASOURCE_URL` | 백엔드 DB 연결 | OCI dev DB (§3 터널 사용 시 `localhost:5432`) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | dev DB 인증 | 운영 계정과 분리 |
| `CARIX_ENCRYPTION_KEY` | 컬럼 암호화(AES-256-GCM) | `openssl rand -base64 32` |
| `KOSIS_API_KEY` 외 3종 | 배치 원천 API | 선행 검증 후 발급 |

## 3. OCI dev DB 원격 연결

로컬엔 Postgres를 띄우지 않는다. OCI의 dev 스키마에 연결한다.

### 3.1 SSH 터널

`~/.ssh/config`에 별칭과 포워딩을 함께 둔다. 그러면 접속만으로 터널이 붙어 매번 옵션을
칠 필요가 없다.

```
Host carix-oci
  HostName 144.24.90.88
  User opc
  IdentityFile ~/.ssh/carix-oci.key
  IdentitiesOnly yes
  LocalForward 5432 localhost:5432
```

백그라운드로 띄운다.

```bash
ssh -fN carix-oci          # 셸 없이 포워딩만
```

끊김이 잦으면 자동 재연결을 쓴다.

```bash
autossh -M 0 -fN carix-oci
```

터널이 열린 동안 `.env`의 `SPRING_DATASOURCE_URL`을
`jdbc:postgresql://localhost:5432/carix_dev`로 둔다.

### 3.2 왜 터널인가

**5432를 인터넷에 직접 열지 않는다.** 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
직접 노출하면 비밀번호와 모든 쿼리·결과가 평문으로 오간다. 같은 컨테이너에 운영 DB도 있다.

`docker-compose.yml`의 postgres는 `127.0.0.1:5432:5432`로 **루프백에만** 바인딩한다.

- `expose`만 두면 호스트에 포트가 없어 터널이 갈 곳이 없다.
- `"5432:5432"`로 쓰면 `0.0.0.0`에 붙어 인터넷에 노출된다. Docker 발행 포트는 firewalld를
  우회하므로 호스트 방화벽으로 막을 수 없고, OCI Security List에서 걸러야 한다.
- `127.0.0.1:`을 명시하면 외부 경로가 없고 SSH 터널만 닿는다.

### 3.3 IntelliJ 설정

**애플리케이션 실행**(`bootRun`, 테스트)은 위 터널이 떠 있어야 한다. 실행 구성마다 붙이려면
Run Configuration → *Before launch* → *Run External tool* 에 `ssh -fN carix-oci`를 등록한다.
이미 떠 있으면 포트 충돌로 실패하지만 무해하다 — 기존 터널이 그대로 쓰인다.

**Database 도구 창**은 자체 SSH 터널을 지원하므로 셸 터널 없이도 붙는다.

1. Data Source → PostgreSQL
2. *SSH/SSL* 탭 → *Use SSH tunnel* → `~/.ssh/config`의 `carix-oci` 선택
3. Host `localhost`, Port `5432`, Database `carix_dev`, User `carix_dev`

운영 DB(`carix`)에는 붙지 않는다. dev 스키마만 사용한다.

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
