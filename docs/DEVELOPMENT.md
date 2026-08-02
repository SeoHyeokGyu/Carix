# Carix 로컬 개발 가이드

로컬은 순수 코드 개발이다. **Docker와 PostgreSQL은 OCI 서버에서만 기동**하고, 로컬
백엔드는 OCI의 `carix` DB(운영과 같은 DB)에 원격 연결한다. 접근은 TLS + OCI Security
List IP 허용목록이다(2026-08-02 완료, Headscale tailnet 대체 — §3 참조). 배경과 결정은
[루트 README 개발 환경](../README.md#개발-환경)을 참조한다.

> **현재 스캐폴드 상태 (2026-08-01).** 이 문서는 목표 상태를 기술한다. 지금 실제로 되는 것과
> 다음 스캐폴드를 기다리는 것을 구분한다.
>
> | 항목 | 지금 (2026-08-01) |
> | --- | --- |
> | 백엔드 core-api | Spring Boot 4.1 + springdoc. `./gradlew build`·`bootRun` 동작. **JPA·Flyway·Security·Batch 없음** |
> | 백엔드 auth-service | JPA·Flyway·초대·매직링크·세션·rate limit 구현 완료. 이메일 발송은 로깅 스텁. 배포 배선(Dockerfile·compose·CI) 미연결 |
> | 프런트엔드 | 배포 검증용 스텁(`server.js`)만. **Vite·React 없음.** `pnpm dev`·`test:e2e` 스크립트 없음 |
> | DB | `auth` 스키마는 auth-service가 쓴다. `core` 스키마는 core-api가 아직 안 씀 — §3 접근 설정은 JPA 도입 후부터 필요 |
>
> 따라서 §4(프런트엔드)와 §5의 Flyway·배치 항목, §6의 Playwright는 각 프로젝트 초기화 이후
> 활성화된다.

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

> **접근 경로 완료 (2026-08-02).** Headscale(자체 호스팅 Tailscale) tailnet 을 제거했다 —
> 로컬 클라이언트의 DNS 해석 실패(사설망에 IPv6 라우팅 부재)로 팀원 온보딩이 반복 실패해,
> 팀 판단으로 tailnet 자체를 접었다(폐기 경위·이전 근거는 §3.2). 대체 경로는 **Postgres
> TLS 활성화 + OCI Security List IP 허용목록**이며, 서버에서 검증 완료됐다: TLS `on`
> 확인, 평문 외부 접속 시도는 `no pg_hba.conf entry ... no encryption`로 명시 거부,
> 허용 IP에서 정상 접속. Security List는 화이트리스트 방식(등록된 IP `/32`만 통과, 나머지
> 전부 default-deny)이라 팀원마다 IP를 등록해야 한다(§3.0).

> **여기서 실행하는 쿼리와 마이그레이션은 곧 운영에 적용된다.** 개발용 사본이 아니다.
> 파괴적 변경 전에는 팀에 알리고, 마이그레이션은 §3.3 규약을 지킨다.

### 왜 같은 DB를 쓰는가

초기 설계는 같은 Postgres 인스턴스 안에 `carix`(운영)와 `carix_dev`(개발) 두 DB를 뒀다.
팀 프로젝트로 전환하면서 팀원들이 로컬에서 같은 실데이터를 보며 개발할 필요가 생겨
DB를 하나로 합쳤다 — `carix_dev` DB·롤과 initdb 스크립트를 제거하고, 팀원은 개인별 DB
롤을 발급받는다(공용 계정은 이탈 시 회수가 안 돼서 안 씀).

**검토한 대안:**

- **DB 2개 분리 (이전 구성)** — 운영 데이터가 개발과 격리된다. 다만 개발 중 확인하고 싶은
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
| 마이그레이션 사고 | 브랜치에서 잘못 실행한 Flyway 마이그레이션이 곧바로 운영 스키마에 적용된다 |
| 백업 부재 | 현재 DB 백업이 구현돼 있지 않다. 위 사고가 나면 복구 수단이 없다 |
| 테스트 데이터 혼입 | 로컬에서 만든 데이터가 운영 DB에 남는다 |
| 자격증명 확산 | 운영 DB 접근 정보가 팀원 노트북마다 존재한다 |
| 게이트 부재 | PR 단계 CI 와 자동 롤백이 없어 사고를 사전에 막거나 되돌릴 장치가 없다(사용자 결정으로 두지 않음) |

**DB 백업이 최우선 후속 과제다** — 위 위험표에서 유일하게 즉시 완화 가능한 항목이다.
서버에 남아 있던 기존 `carix_dev` DB·롤은 이 전환으로 자동 삭제되지 않았다(initdb 는 빈
볼륨에서만 실행된다) — 수동 정리가 필요했다.

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

   # <새-IP>/32, TCP 5432 규칙을 추가해 /tmp/new_ingress.json 으로 저장한 뒤
   oci network security-list update --security-list-id <SL_OCID> \
     --ingress-security-rules file:///tmp/new_ingress.json --force
   ```

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

4. §2의 `.env` 작성 (`sslmode=require` 필수 — §3.4)

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

### 3.2 왜 이 구성이었는가 (폐기, 2026-08-02)

> **이 절은 역사적 기록이다.** 아래는 Headscale 채택 당시(2026-08-01~02) 근거다. 실제
> 운영 중 로컬 tailscale 클라이언트의 DNS 해석이 반복 실패했다(사설망에 IPv6 라우팅이
> 없어 Tailscale bootstrap DNS 폴백까지 막힘 — 서버·Caddy·방화벽 문제가 아니라 팀원
> 로컬 네트워크 환경 문제로 확인됨). 대안 비교 자체가 잘못됐던 것은 아니고, 이 비교에서
> 이겼던 선택지가 실제 팀원 환경에서 반복적으로 온보딩에 실패해 운용 가치가 없어졌다.
> 대체 경로는 §3 배너 참조.

**5432를 인터넷에 직접 열지 않는다** *(당시 결정 — 이후 IP 허용목록 + TLS로 대체 진행 중,
§3 참조)*. 당시 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
직접 노출하면 비밀번호와 모든 쿼리·결과가 평문으로 오간다는 점이 직접 노출을 접은 이유였다.
운영 DB이므로 더 그렇다. tailnet 구간은 WireGuard로 암호화된다.

`docker-compose.yml`의 postgres는 `127.0.0.1:5432:5432`로 **루프백에만** 바인딩하고,
호스트의 `tailscale serve`가 그 루프백 포트를 tailnet에 중계한다. Headscale 컨테이너 자체도
호스트 포트를 발행하지 않고 라우터 Caddy 만 닿는다.

- `expose`만 두면 호스트에 포트가 없어 `serve`가 중계할 대상이 없다.
- `"5432:5432"`로 쓰면 `0.0.0.0`에 붙어 인터넷에 노출된다. Docker 발행 포트는 firewalld를
  우회하므로 호스트 방화벽으로 막을 수 없고, OCI Security List에서 걸러야 한다.
- `127.0.0.1:`을 명시하면 외부 경로가 없고 tailnet 안에서만 닿는다.

**왜 Headscale인가 — 검토한 대안:**

| 방식 | 서버 부담 | 자격증명 배포 | 회수 | 인원 | 외부 의존 |
| --- | --- | --- | --- | --- | --- |
| **Headscale (채택)** | Go 바이너리 + SQLite | 없음 — preauth key | CLI에서 사용자 삭제 | 무제한 | 없음 |
| Tailscale SaaS | 없음 | 없음 — SSO | 콘솔 1클릭 | **무료 6명** | 계정 |
| SSH 터널 | 없음 | **개인키 파일** + `authorized_keys` 등록 | 수동 | 무제한 | 없음 |
| 직접 노출 + TLS | 없음 | 비밀번호 | 롤 삭제 | 무제한 | 없음 |
| NetBird 자체 호스팅 | **컨테이너 5개, 2GB RAM, 80/443, 공개 도메인** | 없음 | 대시보드 | 무제한 | 없음 |

**Tailscale SaaS 대신 Headscale을 택한 이유**: 무료 플랜이 사용자 6명이고, 외부 계정에
팀 전체의 DB 접근이 묶인다. Headscale은 클라이언트가 공식 그대로라 팀원 사용 경험이 같으면서
그 두 제약이 사라진다. `tailscale serve`도 지원한다.

**NetBird를 접은 이유**: 자체 호스팅이 80/443과 공개 도메인을 요구한다. 이 호스트에서
80/443은 라우터 Caddy가 소유하고 `sslip.io`는 소유 도메인이 아니다. 컨테이너 5개와 2GB RAM
요구도 이미 여러 컨테이너가 도는 이 서버엔 과하다.

**SSH 터널을 접은 이유**: 사람마다 키를 만들어 서버 `authorized_keys`에 등록하고 이탈 시
회수해야 한다. 팀 규모에 비례해 수동 서버 작업이 늘고 개인키 파일이 노트북마다 남는다.

**직접 노출을 접은 이유**: 운영 DB다. 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
자격증명과 쿼리·결과가 평문으로 오간다. 팀원 IP가 유동이라 허용목록도 계속 깨진다.

**대가로 받아들인 것:**

- **DB 접근의 단일 장애점이 하나 늘었다.** Headscale이 죽으면 신규 인증과 키 갱신이 막힌다.
  기존 연결은 잠시 유지되고, 긴급 시 SSH 포워딩이 우회로로 남는다.
- **`headscale-data` 볼륨이 백업 대상이다.** 노드 키와 ACL을 담고 있어 잃으면 팀원 전원이
  재인증해야 한다. 현재 백업 체계가 없다.
- Headscale 문서는 대상 사용자를 "self-hosters and hobbyists"로 명시한다. 팀 운영 DB 접근의
  단일 경로로 쓰는 이상 그 문구를 감안한다.
- Carix compose에 함께 두므로 **Carix 배포가 headscale 컨테이너를 재생성**한다. 인프라 성격의
  서비스가 애플리케이션 배포 주기에 묶인다. 별도 인프라 저장소가 버전 관리·백업 밖에 있어
  그쪽에 두는 것보다는 낫다는 판단이다.
- DERP는 Tailscale 공개 릴레이를 쓴다. 계정은 필요 없지만 NAT 통과 경로가 외부 인프라에
  의존한다. 내장 DERP로 바꾸면 STUN 포트를 추가로 열어야 한다.

**"되돌릴 조건"(당시 기록):** 팀이 6명 이하로 유지되고 자체 운영 부담이 문제가 되면
Tailscale SaaS로 되돌릴 수 있다고 봤다. 실제로는 SaaS로 전환하지 않고 Headscale 자체를
접었다 — 위 배너 참조.

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

`config-server`(8890)·`eureka`(8761) 는 서버에서 이미 돈다 — 로컬에는 작업 대상 하나만
띄운다(Gateway 작업은 예외 — 아래 spring-cloud-architecture.md §6 참조). 서버 것을
원격으로 쓴다 — OCI Security List IP 허용목록으로 열었다(2026-08-02, Postgres 5432와
같은 바인딩 방식). **TLS는 없다** — 두 서비스 다 시크릿을 다루지 않아(라우트·임계값·
기능 플래그, 서비스 레지스트리뿐) Security List 단독 방어로 충분하다고 판단했다
(Postgres는 실제 자격증명이 오가서 TLS를 추가했다). 서버 쪽 포트·볼륨 설정은
`docker-compose.yml`의 config-server·eureka 서비스 주석을 참조한다.

**가장 큰 위험 — 로컬 인스턴스가 운영 트래픽을 받는다.** 로컬 `core-api`가 서버 Eureka에
등록되면 서버 쪽 라우팅이 그리로 실사용자 요청을 보낼 수 있다. 노트북을 닫으면 서비스가 죽는다.
그래서 **`local` 프로파일을 반드시 켠다** — `application-local.yml`이 `register-with-eureka:
false`를 강제한다.

```bash
cd backend
CONFIG_SERVER_URL=http://144.24.90.88:8890 EUREKA_URL=http://144.24.90.88:8761/eureka/ \
  ./gradlew :core-api:bootRun --args='--server.port=8080 --spring.profiles.active=local'
```

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
