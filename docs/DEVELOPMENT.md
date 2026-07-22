# Carix 로컬 개발 가이드

로컬은 순수 코드 개발이다. **Docker와 PostgreSQL은 OCI 서버에서만 기동**하고, 로컬
백엔드는 OCI dev DB에 원격 연결한다. 배경과 결정은 [루트 README 개발 환경](../README.md#개발-환경)을 참조한다.

> 프런트엔드·백엔드 프로젝트는 아직 스캐폴드 전이다. 아래 명령 중 앱 실행·테스트
> 부분은 각 프로젝트 초기화(`frontend/` Vite, `backend/` Spring Boot) 이후 활성화된다.

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

### 3.1 SSH 터널 (권장)

```bash
# 로컬 5432 → OCI 서버의 Postgres 5432
ssh -N -L 5432:localhost:5432 opc@<oci-host> -i ~/.ssh/<oci-key>
```

터널이 열린 동안 `.env`의 `SPRING_DATASOURCE_URL`을
`jdbc:postgresql://localhost:5432/carix_dev`로 둔다.

### 3.2 방화벽 직접 허용 (대안)

OCI 보안 목록에서 개발자 IP에 5432를 허용한 경우, 터널 없이 직접 주소를 사용한다.
운영 DB가 아닌 dev DB에만 허용한다.

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
./gradlew bootRun     # http://localhost:8080
```

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
- **통합 테스트(Testcontainers)**: CI(Docker 가능 러너)에서 실행한다. CI 워크플로우는
  [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 참조.
- 급히 로컬에서 통합 테스트가 필요하면 OCI dev DB를 대상으로 실행하되, 스키마
  드리프트에 주의한다.

## 7. 커밋·브랜치

- `main` 직접 커밋 금지. 기능 브랜치(`feature/`·`fix/`·`docs/`·`chore/`) + PR.
- API 키·개인정보·`.env`는 커밋하지 않는다.
- 상세: [루트 README 협업 방식](../README.md#협업-방식).
