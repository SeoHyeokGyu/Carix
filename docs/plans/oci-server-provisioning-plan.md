# OCI 배포 환경 구성 계획 (144.24.90.88 공유 호스트)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans 로 단계별 실행한다.
> Phase 1·3·4는 사람이 서버에서 1회 수행하는 준비 작업이고, Phase 5 이후는 CI/CD 경로다.

> **역사 기록이다.** 이 문서는 단일 백엔드 컨테이너(`spring-api`) 기준으로 작성됐고 Phase는
> 전부 완료됐다. 이후 Spring Cloud 전환 계획 A로 백엔드가
> `config-server`·`eureka`·`core-api` 로 나뉘었다. 본문의 `spring-api` 는 그 시점의 원문
> 그대로 두되, 지금 서버에서 실행할 명령은 바뀐 이름을 쓴다.

**Goal:** 기존 OCI 인스턴스 `opc@144.24.90.88`에 Carix를 얹는다.
**빌드는 GitHub Actions ARM 러너에서 하고 서버는 이미지를 pull만** 한다.
엣지는 2단이다. 서버의 라우터 Caddy가 TLS를 종료하고 호스트명으로 분기하면,
프로젝트 전용 `carix-caddy`가 애플리케이션 라우팅을 맡는다. 도메인은
`carix.144.24.90.88.sslip.io`(sslip.io + 자동 ACME)다.

---

## 전제 (서버 직접 실측)

접속: `ssh <oci-server-alias>` (키는 `~/.ssh/<oci-server-alias>.key`, 권한 600. `~/.ssh/config`에 별칭 등록 완료)

| 항목 | 실측값 |
| --- | --- |
| OS / 아키텍처 | Oracle Linux 9.6 / `aarch64` |
| CPU / RAM | 4 OCPU / 22.9GB (available 11.7GB) |
| **Swap** | **4044 / 4095 MB 사용 — 이미 메모리 압박** |
| 디스크 | 133G 중 113G 사용 (**85%**), 여유 21G |
| Docker 회수 가능 | 이미지 **18.84GB** + 빌드 캐시 **5.02GB** ≈ 24GB |
| Docker | 29.0.3 / compose v2.40.3 (설치 완료, `enabled`) |
| 가동 컨테이너 | 18개 — web-archiver(11), asset-pulse(4), isa-risk-dashboard, infra(dockge 3001, dozzle 8888), flaresolverr×4, warp×3, autoheal |
| **80 / 443** | 라우터 `infra_caddy`가 소유. **어떤 프로젝트도 호스트 포트를 직접 바인딩하지 않는다** |
| TLS | `pulse.144.24.90.88.sslip.io`에 Let's Encrypt 인증서 발급 완료. 외부 `curl` 200, 검증 통과 |
| DNS | `carix.144.24.90.88.sslip.io` → `144.24.90.88` (sslip.io 와일드카드, 확인함) |
| Mukzzi | **이 호스트에서 돌던 것.** `~/mukzzi` 삭제됨, 컨테이너·볼륨 없음. 별도 인스턴스는 존재하지 않는다 |
| 저장소 | `SeoHyeokGyu/Carix` private. ARM 러너는 private도 free-tier 대상 (2 vCPU) |
| 참조 구현 | `Mukzzi/.github/workflows/backend-deploy.yml` — `ubuntu-24.04-arm` → buildx → GHCR → SSH `compose pull`. 최근 5회 전부 성공 (1m45s–1m56s) |
| `backend/`, `frontend/` | ~~`README.md`만~~ → PR #15에서 최소 스캐폴드 완료. Phase 6 게이트 해제 |

## 실행 현황

| Phase | 상태 |
| --- | --- |
| 1. 디스크 정리 | **완료** — 85% → 81% |
| 2. 저장소 변경 | **완료** — PR #11 머지됨 (`cec2ab3`) |
| 3. 엣지 구성 | **완료** — 라우터(`infra_caddy`) + 프로젝트별 Caddy(`carix_caddy`, `asset_pulse_caddy`). 양 도메인 정상 TLS |
| 4. 서버 준비 | **완료** — `/home/opc/carix`가 main(`cec2ab3`) 체크아웃, `.env` 생성(600), deploy key 등록 |
| 5. CD 워크플로 | **완료** — 워크플로·Secrets 등록. 실행 성공 |
| 6. 첫 배포 검증 | **완료** — [run 30193778838](https://github.com/SeoHyeokGyu/Carix/actions/runs/30193778838) |
| 7. 운영 | 미착수 |

검증된 상태 (2단 엣지 전환 후):

- `https://pulse.144.24.90.88.sslip.io/` → 200, TLS 검증 통과 (라우터 → `asset_pulse_caddy` → frontend)
- `infra_caddy`만 호스트 포트(80/443)를 바인딩한다. 프로젝트 Caddy는 `expose`만
- 컨테이너 비정상 0
- 5432·8080·3000·8000 외부 차단 확인

### 첫 배포 실측 (run 30193778838)

전 구간 통과. 총 2분 37초.

| 잡 | 소요 | 비고 |
| --- | --- | --- |
| Test & Build backend | 1m 44s | `ubuntu-24.04-arm`, `BUILD SUCCESSFUL in 56s` |
| Build frontend | 40s | buildx + GHA 레이어 캐시 |
| Deploy to Oracle Cloud | 40s | SSH → `compose pull` → `up -d` |
| Health check | 4s | 첫 시도에 200 |

배포 직후 공개 도메인 실측:

| 경로 | 결과 |
| --- | --- |
| `/api/health` | `{"status":"UP"}` 200, TLS 검증 통과 |
| `/api/v3/api-docs` | 200 |
| `/api/swagger-ui/index.html` | 200 |
| `/` | 200 (프런트엔드 스텁) |

**`ubuntu-24.04-arm` free-tier가 private 저장소에서 동작한다**는 것도 이 실행으로 확인됐다.
그 전까지는 Mukzzi 참조와 공지에 근거한 전제였다.

### 겪은 장애: 첫 배포가 이미지 태그·buildx로 두 번 실패

[run 30193645689](https://github.com/SeoHyeokGyu/Carix/actions/runs/30193645689)에서 `backend`·`frontend` 잡이
모두 이미지 빌드 단계에서 죽었다. Gradle 빌드 자체는 성공했으므로 애플리케이션 문제가 아니었다.

1. **태그 대문자.** `IMAGE_BASE`가 `ghcr.io/${{ github.repository }}`인데 `github.repository`는
   `SeoHyeokGyu/Carix`다. Docker는 repository 이름이 소문자여야 한다:
   `invalid tag "...": repository name must be lowercase`.
   `docker-compose.yml`은 이미 소문자를 참조하고 있었으므로 그 값으로 고정했다.
2. **buildx 드라이버 부재.** `frontend` 잡이 `cache-to: type=gha`를 쓰는데
   `docker/setup-buildx-action`이 없어 기본 `docker` 드라이버로 돌았다:
   `Cache export is not supported for the docker driver`. GHA 캐시 export에는
   `docker-container` 드라이버가 필요하다.

둘 다 PR #16에서 수정. **교훈:** `${{ github.repository }}`를 이미지 이름에 그대로 쓰지 않는다.
계정·저장소 이름에 대문자가 있으면 반드시 깨진다.

### 겪은 장애: bind mount inode 끊김

브랜치를 main으로 전환하는 순간 `carix` 도메인이 TLS 핸드셰이크 실패로 죽었다.
`git checkout`이 `deploy/caddy` 디렉터리를 지웠다 다시 만들면서 inode가 바뀌었고,
컨테이너의 bind mount는 옛 inode를 계속 가리켜 마운트 지점이 빈 디렉터리로 보였다.

**대응:** git이 건드리지 않는 `/home/opc/carix`를 마운트하고
`import /etc/carix-repo/deploy/caddy/*.caddy`로 참조한다. 배포 워크플로의
`git reset --hard`에도 같은 함정이 있었으므로 이 수정이 필수다.

### 서버에 남긴 미커밋 변경 (asset-pulse)

`/home/opc/.codex/worktrees/asset-pulse-hardening/`의 `docker-compose.yml`과 `Caddyfile`이
**커밋되지 않은 채 운영에 반영돼 있다.**

- `docker-compose.yml`: `caddy` 서비스에서 호스트 포트 바인딩(80/443) 제거, `expose: 80` +
  `networks: [default, edge]` 추가. `frontend`는 edge에서 제외
- `Caddyfile`: `{$PUBLIC_HOST}` 사이트 → `:80` + `auto_https off`

그 워크트리에서 `git checkout` / `git reset --hard` / `git stash`를 하거나 `*.bak-BEFORE-*`로
복원하면 `caddy`가 80/443 바인딩을 시도해 `infra_caddy`와 충돌하고 `pulse` 도메인이 죽는다.
경고문을 같은 디렉터리의 `CADDY-MIGRATION-NOTE.md`에 남겼다. **그 저장소에 커밋할지는 사용자 판단.**

## 결정 요약

- **빌드는 GitHub Actions `ubuntu-24.04-arm`.** 서버는 `docker compose pull`만. `build:` 지시자를 저장소에서 없앤다.
- **Carix compose에서 `nginx` 서비스를 제거**하고 `carix-caddy`를 둔다. TLS 종료와 호스트명 분기는 서버 라우터 Caddy가 맡는다.
- TLS는 sslip.io + Caddy ACME로 즉시 확보. certbot·도메인 구매 불필요.
- Postgres 5432는 외부 미개방. 로컬 개발은 Headscale tailnet 으로 접근한다 (당초 SSH 터널에서 변경, 근거는 DEVELOPMENT.md §3.2).
- 서버 직접 배포 금지 (`~/.claude/rules/common/cmux-workflow.md` §9). Phase 1·3·4의 준비 작업만이 정당한 수동 개입이다.

## 범위 밖 관찰 (조치하지 않음)

- `asset-pulse`가 `/home/opc/.codex/worktrees/asset-pulse-hardening/docker-compose.yml`에서 실서비스를 돌린다. Codex 워크트리가 운영 트래픽을 받는 상태. Phase 3에서 그쪽 Caddy의 호스트 포트 바인딩만 걷어내고 나머지는 그대로 둔다.

---

## Phase 1 — 선행 디스크 정리

디스크 85%에서 JVM·Node 이미지를 pull하면 위험하다. **Phase 6 이전에 반드시 선행한다.**

- [ ] **Step 1: 회수 대상 확인**

```bash
ssh <oci-server-alias> 'docker system df'
```

Expected: Images RECLAIMABLE ≈ 18.84GB, Build Cache ≈ 5.02GB.

- [ ] **Step 2: 미사용 이미지·빌드 캐시 정리**

> **선행 확인 필수.** 이 호스트에는 레지스트리 없이 로컬 빌드된 이미지가 여럿이다
> (`web-archiver-frontend`, `web-archiver-backend`, `asset-pulse-frontend`, `asset-pulse-backend`,
> `isa-risk-dashboard-*`). 가동 중이면 안전하지만, **중지된 컨테이너가 하나라도 있으면 그 이미지는
> 삭제되고 소스 재빌드로만 복구된다.**

```bash
ssh <oci-server-alias> 'docker ps -a --filter status=exited --filter status=created --format "{{.Names}}\t{{.Image}}"'
```

Expected: 출력 없음. 있으면 해당 이미지를 먼저 확인하고 정리 범위를 좁힌다.

볼륨은 건드리지 않는다 (`--volumes` 금지).

```bash
ssh <oci-server-alias> 'docker image prune -a -f && docker builder prune -a -f && df -h /'
```

**실행 결과:** 113G → 107G, 85% → **81%**, 6GB 회수. 빌드 캐시 7.4GB 전량 삭제.

> `docker system df`의 `RECLAIMABLE 18.85GB`는 과대계상이다. 실행 중 컨테이너가 안 쓰는 이미지를
> 모두 회수 가능으로 세지만, 실제 미사용 named 이미지는 684MB뿐이었다
> (`polymarket-alerts-*`, `node:24-alpine`, `alpine:3.20`, `dperson/torproxy`).
> 실질 회수분은 빌드 캐시다. **`prune -a`는 실행하지 않았다** — 로컬 빌드 이미지의 이전 버전을
> 지우면 소스 재빌드로만 복구된다.
>
> 남은 용량 상위: `/var/lib/docker/volumes` 44G, `/home/opc/backups` 22G. 둘 다 사용자 데이터라
> 손대지 않았다. 27G 여유면 Carix에 충분하다.

- [ ] **Step 3: 메모리 여유 재확인**

```bash
ssh <oci-server-alias> 'free -m; ps -eo rss,comm --sort=-rss | head -10'
```

Carix는 JVM 2개 + Node + Postgres ≈ 3–4GB를 추가로 쓴다. available 11.7GB면 들어가지만 스왑이
이미 가득 찼다. 부족하면 `flaresolverr`×4 / `warp`×3 중 미사용분 정리를 사용자와 별건으로 논의한다.

---

## Phase 2 — 저장소 변경 (로컬 작업, main 머지 필요)

Phase 5·6보다 **먼저 main에 들어가야 한다.**

### Task 2.1: compose를 GHCR pull 방식으로 전환하고 nginx 제거

- [ ] **Step 1: `nginx` 서비스 삭제**

80/443은 서버의 라우터 Caddy가 쓴다. `deploy/nginx/nginx.conf`의 라우팅은
`deploy/caddy/Caddyfile`로 옮긴다. `depends_on`에서 nginx 참조도 함께 제거한다.

- [ ] **Step 2: `build:` → `image:`**

태그는 변수화한다. 서버 `.env`의 `IMAGE_TAG`로 배포 커밋을 고정하고 롤백한다 (Phase 5 참조).

> **당시 원문이다.** 이후 `IMAGE_TAG` 하나를 `BACKEND_TAG`/`FRONTEND_TAG`로 나눴고 자동 롤백도
> 두지 않기로 했다. 현재 규칙은 아래 「배포 실패 시 복구와 이미지 보존」을 본다.

```yaml
  frontend-node:
    image: ghcr.io/seohyeokgyu/carix/frontend:${IMAGE_TAG:-latest}
    expose:
      - "3000"

  spring-api:
    image: ghcr.io/seohyeokgyu/carix/backend:${IMAGE_TAG:-latest}
    expose:
      - "8080"

  spring-batch:
    image: ghcr.io/seohyeokgyu/carix/backend:${IMAGE_TAG:-latest}
    command: ["--spring.batch.job.enabled=true", "--carix.role=batch"]
```

- [ ] **Step 3: Carix 전용 Caddy 서비스 추가**

라우터가 도달할 접점은 이 컨테이너 하나다. 애플리케이션 서비스는 `edge`에 노출하지 않는다.

```yaml
services:
  carix-caddy:
    image: caddy:2-alpine
    container_name: carix_caddy
    volumes:
      - ./deploy/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
    expose:
      - "80"
    networks: [default, edge]
    restart: unless-stopped

networks:
  edge:
    external: true
```

- [ ] **Step 4: JVM 힙 상한과 메모리 제한**

스왑이 이미 가득 찬 호스트다. 상한 없이 올리지 않는다. 두 JVM 서비스에 추가한다.

```yaml
      - JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50
    mem_limit: 1500m
```

- [ ] **Step 5: 로그 회전** (18개 컨테이너가 도는 호스트다)

```yaml
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
```

- [x] **Step 6: 배치 재시작 정책 재검토** — **서비스를 제거했다**

`spring-batch`가 `restart: unless-stopped`인데 원샷 잡이면 재시작 루프에 빠진다.
상주 스케줄러면 유지, 원샷이면 `restart: "no"` + 크론/`docker compose run`으로 바꾼다.

> **결론: 지금은 아예 뺀다.** 배치 코드가 없어서 `spring-api`와 같은 이미지로 뜬 두 번째
> 웹 서버가 아무 일도 하지 않으며 JVM 힙만 차지하고 있었다. 스왑이 빠듯한 공유 호스트에서
> 실질 낭비다. 수집기(`collector`) 배포 시 관리자 API 구동 형태로 되살린다.

### Task 2.2: dev DB 분리 — 폐기

> 운영과 개발이 같은 DB 를 쓰기로 결정해 폐기했다 (근거는 DEVELOPMENT.md §3).
> 저장소·서버 양쪽에서 관련 롤·DB·설정 항목을 제거했다.

### Task 2.3: 스캐폴드 담당자에게 전달할 계약

> **이행 완료 (PR #15).** 아래는 계약 원문이며, 실제 스캐폴드에서 달라진 점은 각 항목 아래
> 인용문으로 덧붙였다. 실물은 [`backend/Dockerfile`](../../backend/Dockerfile) 참조.

- [x] **`backend/Dockerfile` — 컴파일하지 않는다.** JAR은 GitHub Actions 러너가 Gradle 캐시를 써서
      만들고, Dockerfile은 그 산출물을 layered로 풀어 담기만 한다.

```dockerfile
# 1단계: 이미 빌드된 JAR을 레이어로 분해
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract \
      --layers \
      --application-filename application.jar \
      --destination extracted

# 2단계: 런타임 (자주 안 바뀌는 레이어부터)
FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "application.jar"]
```

효과: 의존성이 안 바뀌면 배포마다 애플리케이션 레이어(수 MB)만 push·pull된다.
통짜 JAR을 COPY하면 매번 60–100MB가 통째로 바뀐다 — **디스크 85%인 서버에 직결되는 차이**다.

> **스캐폴드에서 확인된 세 가지 (계약 원문과 다름):**
>
> 1. **`--application-filename` 이 필요하다.** 추출된 애플리케이션 JAR의 이름은 기본적으로
>    입력 JAR 파일명을 따라간다. 이 플래그가 없으면 `ENTRYPOINT`가 버전 문자열에 묶인다.
> 2. **`spring-boot-loader` 레이어는 비어 있다.** `--launcher`를 쓰지 않으면 `lib/` +
>    manifest Class-Path 방식이라 로더가 추출되지 않는다. 해당 `COPY` 줄은 뺐다.
> 3. **plain jar를 꺼야 한다.** `build/libs/*.jar` 글롭이 `bootJar` 산출물과 `-plain.jar`
>    두 개를 집어 `COPY`가 실패한다. `tasks.named('jar') { enabled = false }`.
>
> 채택 버전은 Spring Boot 4.1.0이며 `-Djarmode=tools ... extract --layers`가 그대로 유효하다.
> (Boot 3.2 이하라면 `-Djarmode=layertools extract`에 ENTRYPOINT도
> `org.springframework.boot.loader.launch.JarLauncher`가 된다.)
> `.dockerignore`는 `*` + 부정(`!`) 대신 명시적 제외 목록을 쓴다 — 부정 패턴은 buildkit
> 구현에 따라 부모 경로 재포함이 필요하다.

- [ ] **`frontend/Dockerfile`** — 멀티스테이지(pnpm install → build → 런타임 prod deps만),
      Node SSR 3000 노출. 프런트엔드는 Dockerfile 안에서 빌드해도 pnpm 레이어 캐시가 잘 듣는다.

> 현재는 의존성 없는 스텁이라 단일 스테이지다. 실제 프런트엔드 스캐폴드 시 이 계약대로 교체한다.
> `cache-to: type=gha`를 쓰므로 **`docker/setup-buildx-action`이 반드시 있어야 한다** —
> 기본 `docker` 드라이버는 캐시 export를 지원하지 않는다.

- [x] 두 이미지 모두 **linux/arm64**로 빌드된다 (러너가 `ubuntu-24.04-arm`, 서버가 aarch64).
      QEMU 에뮬레이션은 쓰지 않는다.
- [x] 백엔드가 **정확히 `/api/health`** 를 서빙할 것. Caddy가 `/api/*`를 경로 그대로 `spring-api:8080`에 넘긴다.
      Actuator 기본 경로(`/actuator/health`)로는 맞지 않는다. Phase 5 헬스체크가 이 경로에 의존한다.

> **채택 방식: `server.servlet.context-path=/api`.** 컨트롤러에 `/api`를 직접 박는 대신
> 컨텍스트 패스로 두면 헬스체크뿐 아니라 OpenAPI 문서(`/api/v3/api-docs`)와 Swagger UI 정적
> 자원(`/api/swagger-ui/**`)까지 전부 `/api` 아래로 들어와 `deploy/caddy/Caddyfile`을
> 건드릴 필요가 없다. springdoc의 `swagger-ui.path`만 바꾸면 리다이렉트 대상과 webjar 자원이
> `/api` 밖으로 나가 Caddy가 `frontend-node`로 보낸다.

---

## Phase 3 — 엣지: 라우터 + 프로젝트별 Caddy

> **선행 조건: Phase 4 Task 4.2(`/home/opc/carix` 클론)를 먼저 끝낸다.**
> 라우터가 `/home/opc/carix`를 마운트하므로 클론이 없으면 Docker가 빈 디렉터리를 만들고
> import가 아무것도 못 찾는다.

### 구조

```
Internet :443
   └─ infra_caddy (라우터, ~/infra)        TLS 종료 + 호스트명 분기만
        ├─ pulse.144.24.90.88.sslip.io → asset_pulse_caddy:80   (asset-pulse 저장소 소유)
        └─ carix.144.24.90.88.sslip.io → carix_caddy:80         (Carix 저장소 소유)
                                            ├─ /api/*  → spring-api:8080
                                            └─ /*      → frontend-node:3000
```

**왜 라우터가 필요한가.** 80/443은 한 컨테이너만 바인딩할 수 있다. 그리고 Let's Encrypt는
HTTP-01(80)이나 TLS-ALPN-01(443)로만 검증하러 온다 — 8443 같은 대체 포트로는 오지 않는다.
sslip.io는 소유 도메인이 아니라 DNS-01 우회도 불가능하다. 따라서 **포트만 분리하면 표준 포트를
못 쓰는 쪽이 정상 인증서를 받지 못한다.** 라우터 한 단을 두면 두 프로젝트 모두 정상 TLS를 받으면서
각자 자기 저장소에서 애플리케이션 라우팅을 소유한다.

전용 Caddy는 `auto_https off` + `:80` 으로 뜨므로 인증서를 발급하지 않는다. 인증서 관리 지점은
라우터 하나뿐이다.

### 왜 Caddy인가 (nginx와 비교)

두 계층 모두 현재 Caddy다. nginx로 바꿀지 검토하면서 두 선택지를 비교했다. 계층마다 요구가
달라 따로 본다.

**라우터 계층 — TLS를 다루는 쪽**

| 항목 | Caddy | nginx |
| --- | --- | --- |
| 인증서 발급·갱신 | 기본 동작. 설정에 관련 줄 0개 | ACME 클라이언트 없음. certbot 또는 acme-companion을 별도 컨테이너로 |
| 갱신 파이프라인 | 내장 | webroot 공유 볼륨 + 갱신 타이머 + 갱신 후 reload 훅을 직접 구성 |
| DNS-01 우회 | 가능하나 불필요 | 동일. 단 `sslip.io`는 소유 도메인이 아니라 어느 쪽도 못 씀 |
| 전환 비용 | — | 인증서 저장 형식이 달라 사실상 재발급. 전환 실패 시 두 도메인 HTTPS 중단 |
| 설정 분량 | 8줄 (도메인 2개 분기 + import) | 서버 블록 + ACME 스택 |

TLS 자동화가 필요한 계층에서는 Caddy가 명확히 적은 구성으로 같은 결과를 낸다.

**앱 계층 — TLS를 다루지 않는 쪽**

여기는 평문 `:80`만 받으므로 위 항목이 전부 무의미하다. 다른 축으로 비교한다.

| 항목 | Caddy | nginx |
| --- | --- | --- |
| 업스트림 DNS | 요청마다 재해석 | 기동 시 1회 해석 후 IP 고정. `resolver` + `proxy_pass` 변수화로 우회하되, 변수를 쓰면 URI 자동 전달이 끊겨 `$request_uri`를 직접 붙여야 함 |
| 배포 시 컨테이너 IP 변경 | 영향 없음 | 위 우회를 안 하면 낡은 IP를 찔러 502 |
| 압축 | gzip + zstd | stock 이미지는 gzip만 (zstd 모듈 별도) |
| 현재 요구 대비 설정 분량 | 25줄 | 70줄 |
| 세밀 제어 | 제한적 | `proxy_buffer*`, `proxy_cache` 존, `limit_req`, `map`·정규식 rewrite 등 훨씬 풍부 |
| 생태계·자료 | 적음 | 압도적으로 많음. 실무 채택률·트러블슈팅 사례 우위 |
| 설정 검증 | `caddy validate` | `nginx -t`. 단 로컬에 Docker가 없어 사전 검증 불가, CI 스텝 의존 |
| 이 호스트 내 일관성 | 다른 프로젝트도 Caddy | 혼재 |

**nginx가 나은 축이 분명히 있다** — 세밀 제어와 생태계. 다만 현재 앱 계층이 실제로 쓰는 것은
경로 분기, 압축, 캐시 헤더 세 가지뿐이라 그 우위가 발현되지 않는다. 반대로 업스트림 IP
고정 문제는 이 배포 방식(컨테이너 재생성)에서 바로 드러난다.

**결정 — 두 계층 모두 Caddy를 유지한다.**

- 라우터: ACME 자동화가 핵심 요구이고, 그 축에서 Caddy가 우위다. 게다가 `~/infra`는 이 저장소
  밖이며 다른 프로젝트와 공유한다.
- 앱 계층: nginx의 우위 축(세밀 제어)을 현재 쓰지 않고, 열위 축(업스트림 IP 고정)은 바로 문제가 된다.

앱 계층 nginx 설정을 실제로 작성해 동작 가능함을 확인한 뒤 폐기했다. 기술적 불가가 아니라
비용 대비 이득으로 내린 결정이다.

**검토한 대안:**

- **IP를 추가해 프로젝트별 독립 엣지** — 공인 IP가 하나뿐이라 성립하지 않는다.
  80/443은 한 컨테이너만 바인딩할 수 있고 Let's Encrypt는 그 두 포트로만 검증하러 온다.
  IP를 늘리면 물리적으로는 가능하나 인증서 관리 지점이 둘로 늘어난다.
- **한 프로젝트가 라우터를 겸함** — 공유 컴포넌트는 사라지지만 결합이 악화된다.
  Carix 배포가 다른 프로젝트의 라우팅을 끊을 위험을 안게 된다.

> **전환 예정.** 앱 계층 라우팅은 Spring Cloud Gateway로 옮길 예정이다
> ([설계서](../specs/spring-cloud-architecture.md)). **라우터 Caddy 유지 근거(ACME 자동화·
> 80/443 단일 바인딩·타 프로젝트 공유)는 그대로 유효하다** — Gateway는 TLS를 다루지 않는다.
> 앱 계층에서 `proxy_cache` 존, `limit_req`, 복잡한 rewrite 같은 nginx 고유 기능이 필요해지면
> 위 비교표의 무게추가 달라진다.

### 저장소가 소유하는 파일

| 파일 | 역할 | 마운트 대상 |
| --- | --- | --- |
| `deploy/caddy/Caddyfile` | Carix 애플리케이션 라우팅 | `carix_caddy:/etc/caddy/Caddyfile` |
| `deploy/caddy/router.caddy` | 라우터의 호스트명 분기 항목 | 라우터가 `/etc/carix-repo/deploy/caddy/*.caddy`로 import |

- [ ] **Step 1: 공유 네트워크**

```bash
ssh <oci-server-alias> 'docker network inspect edge >/dev/null 2>&1 || docker network create edge'
```

- [ ] **Step 2: 라우터 `~/infra/caddy/Caddyfile`**

```
# 라우터. TLS 종료와 호스트명 분기만 한다. 애플리케이션 라우팅은 각 프로젝트 Caddy가 맡는다.
{$PULSE_HOST} {
    encode zstd gzip
    reverse_proxy asset_pulse_caddy:80
}

import /etc/carix-repo/deploy/caddy/*.caddy
```

> **마운트 경로 주의 (두 번 데인 함정):** `/home/opc/carix/deploy/caddy`처럼 **git이 재생성하는
> 하위 디렉터리나 파일을 마운트하면 안 된다.** 브랜치 전환이나 `git reset --hard`가 디렉터리를 지웠다 다시 만들면 inode가 바뀌어
> 컨테이너의 bind mount가 옛 inode를 가리킨 채 빈 디렉터리로 보인다. 실제로 겪었고
> `carix` 도메인이 TLS 핸드셰이크 실패로 죽었다.
> `mkdir`로 만든 `/home/opc/carix` 자체를 마운트하면 git이 그 디렉터리를 건드리지 않아 안전하다.
>
> `carix_caddy`는 `./deploy/caddy/Caddyfile`을 파일째 마운트하므로 같은 함정에 걸린다.
> 이쪽은 배포 시 `docker compose up -d --force-recreate carix-caddy`로 재생성해 해결한다.
> **`caddy reload`로 바꾸면 안 된다** — 구버전을 읽고도 성공을 보고해 배포가 초록불인 채
> 라우팅만 반영되지 않는다.

- [ ] **Step 3: `~/infra/docker-compose.yml`의 caddy 서비스**

```yaml
  caddy:
    image: caddy:2-alpine
    container_name: infra_caddy
    environment:
      PULSE_HOST: ${PULSE_HOST}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - /home/opc/carix:/etc/carix-repo:ro
      - caddy_data:/data
      - caddy_config:/config
    networks: [default, edge]
    restart: unless-stopped

networks:
  edge:
    external: true

volumes:
  # asset-pulse가 만든 인증서 볼륨을 재사용한다. 재발급은 LE rate limit을 소모한다.
  caddy_data:
    external: true
    name: asset_pulse_caddy_data
  caddy_config:
    external: true
    name: asset_pulse_caddy_config
```

`~/infra/.env`에 `PULSE_HOST=pulse.144.24.90.88.sslip.io`.

- [ ] **Step 4: asset-pulse에 자기 Caddy 복원**

`/home/opc/.codex/worktrees/asset-pulse-hardening/`:

- `caddy` 서비스를 되살리되 **호스트 포트 바인딩은 없앤다**(라우터가 80/443을 소유)
- `networks: [default, edge]` 추가
- 그쪽 `Caddyfile`은 `{$PUBLIC_HOST}` 대신 `:80` 사이트로 바꾼다 (TLS는 라우터가 종료)

- [ ] **Step 5: Carix 전용 Caddy**

`docker-compose.yml`의 `carix-caddy` 서비스. `deploy/caddy/Caddyfile`을 마운트하고 `edge`에 붙는다.
애플리케이션 이미지가 없어도 단독 기동된다.

```bash
ssh <oci-server-alias> 'cd /home/opc/carix && docker compose up -d carix-caddy'
```

- [ ] **Step 6: 검증**

```bash
ssh <oci-server-alias> 'docker exec infra_caddy caddy validate --config /etc/caddy/Caddyfile'
curl -sS -o /dev/null -w "pulse=%{http_code} tls=%{ssl_verify_result}\n" https://pulse.144.24.90.88.sslip.io/
curl -sS -o /dev/null -w "carix=%{http_code} tls=%{ssl_verify_result}\n" https://carix.144.24.90.88.sslip.io/
ssh <oci-server-alias> 'docker exec infra_caddy ls /data/caddy/certificates/acme-v02.api.letsencrypt.org-directory/'
```

Expected: pulse 200, carix는 백엔드 전이라 502, 둘 다 `tls=0`(검증 통과),
인증서 디렉터리에 두 호스트 모두 존재.


## Phase 4 — 서버 준비

### Task 4.1: SSH 별칭 — 완료

- [x] **Step 1**

키를 `~/Downloads/keys/`에서 `~/.ssh/<oci-server-alias>.key`(권한 600)로 옮기고 `~/.ssh/config`에 등록했다.

```
Host <oci-server-alias>
  HostName 144.24.90.88
  User opc
  IdentityFile ~/.ssh/<oci-server-alias>.key
  IdentitiesOnly yes
```

Verified: `ssh <oci-server-alias> 'echo CONNECTED'` → `CONNECTED`

### Task 4.2: 저장소 체크아웃

서버는 이미지를 빌드하지 않는다. 체크아웃이 필요한 이유는 `docker-compose.yml`과 `deploy/` 뿐이다.

> **경로가 `/home/opc/carix`인 이유:** 이 호스트는 Dockge(`infra` 스택, 포트 3001)로 컨테이너를
> 관리한다. Dockge는 `DOCKGE_STACKS_DIR=/home/opc` 아래에서 **디렉터리 이름을 스택 이름으로**
> 인식하므로, 그 밖에 있으면 "이 스택은 Dockge에 의해 관리되지 않아요"로 표시된다.
> 심볼릭 링크는 디렉터리로 취급되지 않아 우회가 되지 않는다.

- [ ] **Step 1: deploy key 생성·회수**

```bash
ssh <oci-server-alias> 'ssh-keygen -t ed25519 -f ~/.ssh/carix-deploy -N "" -C carix-deploy@oci'
ssh <oci-server-alias> 'cat ~/.ssh/carix-deploy.pub' > /tmp/carix-deploy.pub
```

- [ ] **Step 2: GitHub 등록** (`--allow-write` 주지 않는다)

```bash
gh repo deploy-key add /tmp/carix-deploy.pub -R SeoHyeokGyu/Carix -t carix-oci
```

> 서버 `~/.ssh/config`에는 이미 `Host github.com` 블록이 `~/.ssh/github_personal`로 잡혀 있다.
> 덮어쓰지 말고 `Host github-carix` 별칭을 추가한 뒤 그 별칭으로 클론한다.

- [ ] **Step 3: 클론**

```bash
ssh <oci-server-alias> '
  printf "\nHost github-carix\n  HostName github.com\n  User git\n  IdentityFile ~/.ssh/carix-deploy\n  IdentitiesOnly yes\n" >> ~/.ssh/config
  mkdir -p /home/opc/carix
  git clone git@github-carix:SeoHyeokGyu/Carix.git /home/opc/carix
'
```

Verify: `ssh <oci-server-alias> 'git -C /home/opc/carix log --oneline -1'`

### Task 4.3: 시크릿과 `.env`

- [ ] **Step 1: 암호화 키 생성 (서버에서)**

```bash
ssh <oci-server-alias> 'openssl rand -base64 32'
```

> **키 보관 경고:** `CARIX_ENCRYPTION_KEY`를 분실하면 AES-256-GCM 암호화 컬럼은 **복구 불가**다.
> 생성 즉시 비밀번호 관리자나 OCI Vault에 별도 보관한다.
> 이 문서와 저장소에 실제 값을 절대 기록하지 않는다.

- [ ] **Step 2: `/home/opc/carix/.env`** — 값 입력은 사용자가 한다

```bash
ssh <oci-server-alias> 'cp /home/opc/carix/.env.example /home/opc/carix/.env && chmod 600 /home/opc/carix/.env'
```

채울 항목: `POSTGRES_PASSWORD`, `CARIX_ENCRYPTION_KEY`,
`KOSIS_API_KEY`, `WORK24_API_KEY`, `SARAMIN_API_KEY`, `WAGE_STAT_API_KEY`.

Verify: `ssh <oci-server-alias> 'ls -l /home/opc/carix/.env; git -C /home/opc/carix status --porcelain'`
Expected: `-rw-------`, git status에 `.env` 미표시.

### Task 4.4: 방화벽 확인 (변경 최소)

80/443은 이미 열려 있고 Caddy가 쓴다. Carix는 새 포트를 공개하지 않는다.

- [ ] **Step 1: DB·내부 포트가 닫혀 있는지 외부에서 확인**

```bash
for p in 5432 8080 3000; do nc -z -G 4 144.24.90.88 $p && echo "$p OPEN — 차단 필요" || echo "$p closed OK"; done
```

Expected: 전부 closed. compose가 `expose`만 쓰므로 새로 열릴 일은 없다.

---

## Phase 5 — 빌드·배포 워크플로

### Mukzzi 패턴 점검 — 그대로 쓰면 안 되는 지점

Mukzzi 워크플로는 **Go** 프로젝트다. Carix는 Gradle/JVM + Node SSR이라 비용 구조가 다르다.

| # | Mukzzi 방식 | Carix에서의 문제 | 채택 |
| --- | --- | --- | --- |
| 1 | `ubuntu-24.04-arm` 네이티브 빌드 (QEMU 없음) | 없음. A1 서버가 aarch64라 정확히 맞다 | **그대로** |
| 2 | GHCR push → 서버 `compose pull` | 없음 | **그대로** |
| 3 | Dockerfile 안에서 전체 빌드 | Gradle 의존성 캐시가 이미지 레이어에만 남는다. 소스 한 줄만 바뀌어도 레이어가 무효화돼 매번 전체 컴파일. Go는 이 비용이 작아 문제가 안 됐다 | **교체** — 러너에서 JAR 빌드, Dockerfile은 COPY만 |
| 4 | 통짜 JAR/바이너리 COPY | 배포마다 60–100MB 레이어가 통째로 바뀐다. **디스크 85%인 서버**에 매번 그만큼 쌓인다 | **교체** — Spring Boot layered jar |
| 5 | `provenance` 기본값(true) | GHCR에 `unknown/unknown` 아키텍처 항목이 생겨 매니페스트가 깨져 보인다 | **교체** — `provenance: false`, `sbom: false` |
| 6 | 서버가 `latest` 태그를 pull | **롤백 불가.** 어떤 SHA가 떠 있는지 알 수 없다 | **교체** — `IMAGE_TAG`로 SHA 고정 |
| 7 | `deploy` 잡에 `permissions` 없음 | Mukzzi는 public이라 통과. **Carix는 private → GHCR 패키지도 private → `compose pull` 403** | **교체** — `packages: read` 추가 |
| 8 | 워크플로 2개(backend/frontend)로 분리 + 각자 `paths` | SHA 고정 배포와 충돌한다. 프런트만 바뀐 커밋에는 백엔드 이미지의 해당 SHA 태그가 없다 | **교체** — 항상 둘 다 빌드 |
| 9 | `set -eux` | `-x`가 `docker login` 라인을 트레이스에 찍는다 | **교체** — `set -eu` |
| 10 | `appleboy/ssh-action@v1` (가변 태그) | 서드파티 액션 공급망 리스크 | **적용됨** — v1.2.5 커밋 SHA로 고정 |
| 11 | `IMAGE_BASE: ghcr.io/${{ github.repository }}` | `github.repository`가 `SeoHyeokGyu/Carix`라 대문자가 섞인다. Docker repository 이름은 소문자여야 한다 | **교체** — 소문자 하드코딩 (첫 배포에서 실패로 드러남) |
| 12 | `cache-to: type=gha` + buildx 셋업 없음 | 기본 `docker` 드라이버는 캐시 export를 지원하지 않는다 | **교체** — `docker/setup-buildx-action` 추가 (첫 배포에서 실패로 드러남) |

### 배포 실패 시 복구와 이미지 보존

**자동 롤백은 두지 않는다 (사용자 결정).** 헬스체크가 실패하면 워크플로만 빨간불이 되고
서버는 그 배포 상태로 남는다. 복구는 수동이다.

```bash
ssh <oci-server-alias>
cd /home/opc/carix
# 되돌릴 커밋은 Actions 배포 실행 목록이나 `git log` 에서 찾는다
sed -i "s|^BACKEND_TAG=.*|BACKEND_TAG=<직전 SHA>|" .env
sed -i "s|^FRONTEND_TAG=.*|FRONTEND_TAG=<직전 SHA>|" .env
git reset --hard <직전 SHA>          # compose 파일·Caddyfile도 함께 되돌릴 때
docker compose up -d --remove-orphans
docker compose up -d --force-recreate carix-caddy
```

아래 이미지 보존 정책이 최근 3개를 남기므로, 직전 배포 이미지는 서버에 남아 있어 pull 없이 뜬다.
그보다 오래된 SHA로 되돌리려면 GHCR에서 다시 pull된다.

**이미지 보존.** `docker image prune -f`는 dangling만 지우므로 SHA 태그 이미지가 무한히 쌓인다.
carix 이미지에 한정해 최근 3개만 남기고 나머지 태그를 지운다.
`prune -a`는 여전히 금지 — 이 호스트엔 레지스트리 없이 로컬 빌드된 타 프로젝트 이미지가 있다.

**이미지 태그를 컴포넌트별로 나눈다.** 백엔드만 바뀐 커밋에서 프런트를 다시 빌드하는 것은 낭비다.

```yaml
spring-api:     image: ...backend:${BACKEND_TAG:-latest}
spring-batch:   image: ...backend:${BACKEND_TAG:-latest}
frontend-node:  image: ...frontend:${FRONTEND_TAG:-latest}
```

**태그 값은 배포 커밋이 아니라 그 컴포넌트를 마지막으로 바꾼 커밋이다.**

```bash
TAG=$(git log -1 --format=%H -- backend/ ':!*.md' .github/workflows/deploy.yml)
docker buildx imagetools inspect "$REPO:$TAG" >/dev/null 2>&1 || build_and_push
```

`.md` 를 빼는 이유: `backend/README.md` 만 고쳐도 태그가 움직이면 내용이 같은 이미지를 새로
만들게 되고, 서버에서도 태그 문자열이 달라져 컨테이너가 불필요하게 재생성된다.
실제로 PR #23(문서 동기화)이 backend 태그를 움직였다.

이 정의 덕분에 "빌드가 필요한가"를 따로 판정할 필요가 없다. **그 코드로 만든 이미지가 레지스트리에
이미 있으면 만들 것이 없다.** 백엔드만 바뀐 커밋에서는 `FRONTEND_TAG` 값이 그대로라 compose가
`frontend-node`를 재생성하지 않는다.

부수 효과로 빌드 실패가 자동 복구된다. 실패하면 그 태그의 이미지가 없으므로 다음 실행이 다시 시도한다.
`github.event.before` 기준으로 변경을 감지하면 이 구멍에 빠진다 — 그것은 "직전 푸시"일 뿐
"직전에 성공한 빌드"가 아니라서, 앞선 실행이 실패했거나 푸시가 `paths`에 걸려 스킵되면 변경을 놓친다.

워크플로 파일이 바뀌면 빌드 방식이 달라졌을 수 있으므로 `git log` 경로에 포함해 양쪽을 다시 만든다.

실제 히스토리로 확인한 동작:

| 커밋 | `BACKEND_TAG` | `FRONTEND_TAG` |
| --- | --- | --- |
| backend 수정 | 갱신 | 고정 |
| backend 또 수정 | 갱신 | 고정 |
| 문서만 수정 | 고정 | 고정 |
| frontend 수정 | 고정 | 갱신 |
| 워크플로 수정 | 갱신 | 갱신 |

> **점검표 6번(`latest` 대신 SHA 고정)은 그대로 지켜진다.** 변수를 하나만 둔 것은 당시 가장 단순한
> 구현이었을 뿐 요구사항이 아니었다. SHA 고정의 목적은 롤백이 아니라 관측성·재현성이며,
> 그것은 변수 두 개로도 유지된다. `.env` 두 줄만 보면 어떤 코드가 돌고 있는지 확정되고,
> 서버를 재기동해도 같은 이미지가 뜬다.
>
> 다만 태그가 배포 커밋과 1:1로 대응하지 않는다. `BACKEND_TAG=abc123`은 "abc123을 배포했다"가
> 아니라 "백엔드를 마지막으로 바꾼 커밋이 abc123"이다. 배포 이력은 Actions 실행 목록에서 본다.

**워크플로 파일은 하나로 유지한다.** 둘로 쪼개면 두 워크플로가 동시에 `git reset --hard`와 `.env`를
건드려 경쟁이 생긴다. 잡만 조건부로 만들면 SSH 로직이 한 곳에 남는다.

**헬스체크는 양쪽을 본다.** 한쪽만 재생성될 수 있으므로 `/api/health`와 `/`를 함께 확인한다.

**보존 정책과의 상호작용.** 컴포넌트별 태그는 서로 다른 커밋에 머물 수 있고, 오래된 쪽은 생성 시각
정렬에서 아래로 밀려 삭제 대상에 들어간다. 서버 `.env`에서 실제 사용 중인 두 값을 읽어 제외한다.

**강제 재빌드가 필요하면** GHCR에서 해당 태그를 지우고 워크플로를 다시 돌린다. 태그가 없으면
자동으로 빌드된다.

**GHCR 쪽 보존도 최신 2개만.** 서버 로컬 정리와 별개로 레지스트리에도 쌓인다. Free 플랜의
private 패키지 스토리지 한도가 있어 방치하면 언젠가 push가 막힌다 — 조용히 깨지지 않고
`Build & push image` 실패로 명확히 멈추고, 그 경우 `deploy` 잡이 스킵되어 서버는 마지막 정상
버전을 유지한다.

`actions/delete-package-versions`(커밋 SHA 핀)로 컴포넌트별 패키지마다 최신 2개를 남긴다.

> **운영 중인 태그를 지울 위험은 없다.** 태그를 컴포넌트별로 나눈 뒤로 `BACKEND_TAG`는 정의상
> 그 패키지의 가장 최근 푸시 버전이다. 최신 N개 보존이 최신을 지울 수 없다.
> 성립하려면 `deploy` 잡이 계속 실패하는 동안 그 컴포넌트 빌드만 2회 더 성공해야 한다.
>
> 그리고 지워진 커밋을 다시 배포해야 하면 태그 존재 확인이 실패해 자동으로 다시 빌드된다.
> 삭제가 되돌릴 수 없는 손실이 아니다.

**문서 커밋으로 배포되지 않게.** `backend/README.md`가 `paths`의 `backend/**`에 걸려
코드 변경 0인 재배포가 한 번 돌았다 ([run 30201703174](https://github.com/SeoHyeokGyu/Carix/actions/runs/30201703174)).
`paths` 끝에 `'!**/*.md'`를 두어 제외한다. 마지막에 매칭된 패턴이 이기므로 순서가 중요하다.
**잡 단위 `paths` 필터로 바꾸면 안 된다** — 위 8번 항목의 SHA 고정 배포와 충돌한다.

추가로 **당시 `ci.yml`과 중복**이 있었다. `ci.yml` backend 잡이 `ubuntu-latest`(x86)에서
`./gradlew build`를 돌리는데, 배포가 또 컴파일했다. 게다가 **테스트는 x86, 배포 이미지는 arm64**라
아키텍처가 어긋났다.

→ 최종적으로 **`ci.yml`을 제거**했다 (사용자 결정). 테스트·빌드는 `deploy.yml`이 ARM 러너에서
한 번만 수행한다. Testcontainers는 ARM 러너의 Docker로 돌고 `postgres:16-alpine`은 arm64 이미지가 있다.

> **대가:** PR 단계 자동 검증이 없다. 백엔드는 머지 후 main에서 드러나고(빌드 실패 시 deploy 잡이
> 스킵되므로 서버는 마지막 정상 버전 유지), **프런트엔드는 lint·typecheck·test가 어디서도 자동으로
> 돌지 않는다** — `deploy.yml`의 frontend 잡은 `docker build`만 하기 때문이다.
> 실제 프런트엔드 스캐폴드 시 Dockerfile 빌드 단계에 넣거나 CI를 되살릴지 재검토한다.

> **GHA 캐시 한도:** 저장소당 10GB이며 Gradle 캐시·buildx 캐시가 이 한도를 공유한다.
> 백엔드를 러너 빌드로 돌리면 buildx 레이어 캐시가 거의 필요 없어져 한도 경쟁이 줄어든다.
> `cache-to: type=gha,mode=max`는 프런트엔드에만 건다.
> 그럼에도 최대 소비자는 프런트엔드의 `mode=max`다. **백엔드 빌드가 갑자기 느려지면 Gradle 캐시가
> 축출된 것**이므로, 캐시를 늘리지 말고 프런트엔드를 `mode=min`으로 낮춘다.
>
> `ci.yml`을 제거한 뒤로는 Gradle 캐시 사용처가 `deploy.yml` 하나뿐이라 키 분기 문제가 없다.

### 워크플로 구성

- [ ] **Step 1: 배포용 SSH 키**

```bash
ssh-keygen -t ed25519 -f ~/.ssh/carix-cd -N "" -C carix-cd@actions
ssh-copy-id -i ~/.ssh/carix-cd.pub <oci-server-alias>
```

- [ ] **Step 2: Secrets 등록**

```bash
gh secret set SSH_HOST -R SeoHyeokGyu/Carix --body "144.24.90.88"
gh secret set SSH_USERNAME -R SeoHyeokGyu/Carix --body "opc"
gh secret set SSH_PRIVATE_KEY -R SeoHyeokGyu/Carix < ~/.ssh/carix-cd
```

- [x] **Step 3: compose 이미지 태그를 변수화** (Task 2.1과 함께) — 아래는 당시 원문. 현재는 컴포넌트별 태그다

```yaml
  spring-api:
    image: ghcr.io/seohyeokgyu/carix/backend:${IMAGE_TAG:-latest}
  spring-batch:
    image: ghcr.io/seohyeokgyu/carix/backend:${IMAGE_TAG:-latest}
  frontend-node:
    image: ghcr.io/seohyeokgyu/carix/frontend:${IMAGE_TAG:-latest}
```

- [x] **Step 4: `.github/workflows/deploy.yml`** — 아래는 당시 원문이다. 실제 워크플로는
      이후 여러 번 바뀌었다(컴포넌트별 태그, 빌드 생략, 이미지 보존, `.md` 제외).
      **현재 동작은 저장소의 `.github/workflows/deploy.yml`과 위 「배포 실패 시 복구와 이미지 보존」이 정본이다.**

```yaml
name: Deploy

on:
  push:
    branches: [main]
    paths:
      - backend/**
      - frontend/**
      - docker-compose.yml
      - deploy/**
      - .github/workflows/deploy.yml
  workflow_dispatch:

concurrency:
  group: deploy-production
  cancel-in-progress: false

env:
  IMAGE_BASE: ghcr.io/${{ github.repository }}

jobs:
  backend:
    name: Test & Build backend
    runs-on: ubuntu-24.04-arm   # private 저장소도 free-tier 대상 (2 vCPU)
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      # Gradle 의존성·빌드 캐시를 GHA 캐시에 보관한다.
      # Dockerfile 안에서 빌드하면 이 캐시를 못 쓴다.
      - uses: gradle/actions/setup-gradle@v4

      # Testcontainers 통합 테스트가 여기서 돈다. 러너에 Docker가 있고 aarch64라
      # 운영 아키텍처와 일치한다.
      - name: Build & test
        working-directory: backend
        run: ./gradlew build

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Dockerfile은 이미 만들어진 JAR을 layered로 풀기만 한다 (Task 2.3 참조).
      - uses: docker/build-push-action@v6
        with:
          context: ./backend
          push: true
          provenance: false   # GHCR unknown/unknown 매니페스트 방지
          sbom: false
          tags: |
            ${{ env.IMAGE_BASE }}/backend:${{ github.sha }}
            ${{ env.IMAGE_BASE }}/backend:latest

  frontend:
    name: Build frontend
    runs-on: ubuntu-24.04-arm
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # 프런트엔드는 Dockerfile 안에서 빌드한다. pnpm은 레이어 캐시가 잘 듣는다.
      - uses: docker/build-push-action@v6
        with:
          context: ./frontend
          push: true
          provenance: false
          sbom: false
          tags: |
            ${{ env.IMAGE_BASE }}/frontend:${{ github.sha }}
            ${{ env.IMAGE_BASE }}/frontend:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    name: Deploy to Oracle Cloud
    runs-on: ubuntu-latest
    needs: [backend, frontend]
    permissions:
      contents: read
      packages: read   # private 저장소 → GHCR 패키지도 private. 없으면 compose pull이 403
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1   # 운영 적용 시 커밋 SHA로 핀 고정 권장
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USERNAME }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          envs: IMAGE_TAG,GHCR_TOKEN,GH_ACTOR
          script: |
            set -eu   # -x 금지: docker login 라인이 트레이스에 남는다
            cd /home/opc/carix
            # SHA를 명시적으로 fetch한다. main 팁이 이미 움직였거나 workflow_dispatch를
            # 다른 ref로 돌리면 `fetch origin main`만으로는 그 객체가 안 올 수 있다.
            git fetch --prune origin main
            git fetch origin "$IMAGE_TAG"
            git reset --hard "$IMAGE_TAG"

            # 순서 주의: reset → .env 갱신 → pull.
            # .env는 gitignore 대상이라 reset에 지워지지 않고, compose pull이 이 값을 읽는다.
            # 이번 배포가 어떤 커밋인지 .env에 고정한다. 롤백은 이 값을 바꿔 재실행.
            if grep -q '^IMAGE_TAG=' .env; then
              sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" .env
            else
              echo "IMAGE_TAG=${IMAGE_TAG}" >> .env
            fi

            printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GH_ACTOR" --password-stdin
            docker compose pull
            docker compose up -d --remove-orphans
            docker logout ghcr.io

            # carix_caddy는 git 관리 파일을 직접 마운트한다. git reset --hard가 inode를 바꾸면
            # reload는 성공 보고하면서 구버전을 읽는다(초록불인데 미반영). 반드시 재생성.
            docker compose up -d --force-recreate carix-caddy

            # infra_caddy는 /home/opc/carix(mkdir 생성, git 미관여)를 마운트하므로 reload로 충분하다.
            docker exec infra_caddy caddy reload --config /etc/caddy/Caddyfile

            # 디스크 85%에서 출발한 호스트다. 매 배포마다 정리한다.
            docker image prune -f
        env:
          IMAGE_TAG: ${{ github.sha }}
          GHCR_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GH_ACTOR: ${{ github.actor }}

  healthcheck:
    runs-on: ubuntu-latest
    needs: deploy
    steps:
      - run: |
          for i in $(seq 1 30); do
            code=$(curl -sS -o /dev/null -w '%{http_code}' https://carix.144.24.90.88.sslip.io/api/health || true)
            [ "$code" = "200" ] && echo healthy && exit 0
            sleep 5
          done
          echo "헬스체크 실패" && exit 1
```

- [x] **Step 5: `ci.yml` 처리** — PR 전용으로 축소했다가 최종적으로 제거했다 (아래는 당시 원문)

```yaml
on:
  pull_request:
    branches: [main]
```

`push: branches: [main]` 트리거를 제거한다. main의 테스트는 `deploy.yml` backend 잡이 담당하므로
같은 Gradle 빌드를 두 번 돌릴 이유가 없다.

설계 메모:

- `git reset --hard "$DEPLOY_SHA"`는 배포 커밋 SHA로 고정한다. `origin/main` 팁을 쓰면 검증하지 않은 후속 커밋이 섞인다.
- **롤백:** 자동 롤백은 두지 않는다. 서버 `.env`의 `BACKEND_TAG`·`FRONTEND_TAG`를 이전 SHA로 바꾸고 `docker compose up -d`.
- ~~프런트만 바뀌어도 백엔드를 빌드한다(약 2–4분 손해).~~ → **해소됨.** `BACKEND_TAG`/`FRONTEND_TAG`를 분리하고 태그 값을 "그 컴포넌트를 마지막으로 바꾼 커밋"으로 두어, 안 바뀐 쪽은 빌드도 재생성도 하지 않는다. 위 「배포 실패 시 복구와 이미지 보존」 참조.
- GHCR 패키지는 첫 푸시 시 private이다. 서버 `docker login`이 필수다.
- `caddy reload`가 실패하면 배포가 실패한다. 의도된 동작 — 라우팅이 깨진 채로 성공 처리되면 안 된다.

- [ ] **Step 6: 수동 실행 검증**

```bash
gh workflow run deploy.yml -R SeoHyeokGyu/Carix
gh run watch -R SeoHyeokGyu/Carix
```

- [ ] **Step 7: 롤백 절차 기록** (`docs/OPERATIONS.md` 신설 또는 `DEVELOPMENT.md`)

---

## Phase 6 — 첫 배포 검증

**차단 조건:**

1. `backend/Dockerfile`, `backend/build.gradle*`, `frontend/Dockerfile`, `frontend/package.json` 존재
2. **Phase 2의 compose 변경이 main에 머지됨** — 어기면 구버전 compose가 `build:`를 시도해 실패하고, initdb 스크립트는 빈 볼륨에서만 도는 기회를 놓친다
3. **Phase 1 디스크 정리 완료**
4. **Phase 3 Caddy 라우팅 적용 완료**

- [ ] **Step 1: 게이트 확인**

```bash
ls backend/Dockerfile backend/build.gradle* frontend/Dockerfile frontend/package.json
rg -n "image: ghcr.io" docker-compose.yml
rg -n "nginx" docker-compose.yml   # 결과 없어야 한다
```

- [ ] **Step 2: 배포 후 상태**

```bash
ssh <oci-server-alias> 'cd /home/opc/carix && docker compose ps && docker compose logs --tail=50 core-api'
# 서비스명은 Spring Cloud 전환 이후 core-api 로 바뀌었다.
curl -sS -o /dev/null -w "%{http_code}\n" https://carix.144.24.90.88.sslip.io/
curl -sS -o /dev/null -w "%{http_code}\n" https://carix.144.24.90.88.sslip.io/api/health
```

Expected: 전 서비스 `running`, 둘 다 200.

- [ ] **Step 3: 기존 서비스 무영향 확인**

```bash
curl -sS -o /dev/null -w "pulse=%{http_code}\n" https://pulse.144.24.90.88.sslip.io/
ssh <oci-server-alias> 'docker ps --format "{{.Names}}\t{{.Status}}" | grep -vc Up'
ssh <oci-server-alias> 'free -m; df -h /'
```

Expected: pulse 200, 비정상 컨테이너 0, 스왑·디스크가 Phase 1 대비 악화되지 않음.

- [ ] **Step 4: 재부팅 내성**

```bash
ssh <oci-server-alias> 'sudo systemctl is-enabled docker'
```

Expected: `enabled` (+ compose의 `restart: unless-stopped`).

---

## Phase 7 — 운영

- [ ] **모니터링:** 이 호스트에 Dozzle(8888)·Dockge(3001)가 이미 돈다. Carix 컨테이너가 거기 보이는지 확인하고, 헬스체크 감시를 붙인다.
- [ ] **디스크 감시:** 85%에서 출발했다. 주간 `docker system prune` 또는 사용률 알림을 건다.
- [ ] **엣지 설정 형상관리:** `~/infra/`는 git 추적 대상이 아닌데 이제 **두 서비스의 엣지를 소유**한다.
  `caddy/Caddyfile`과 `docker-compose.yml`이 날짜 붙은 `.bak` 하나만 있는 단일 사본이다.
  별도 private 저장소로 옮기거나 Carix 저장소 `deploy/infra-edge/`에 참조용 사본을 둔다.
- [ ] **암호화 키 백업:** `CARIX_ENCRYPTION_KEY`가 `/home/opc/carix/.env`에만 있다.
  **아직 암호화된 데이터가 없으므로 지금은 재생성이 자유롭다.** 첫 운영 쓰기 전에 아래로 꺼내
  비밀번호 관리자나 OCI Vault에 보관한다. 그 시점 이후 분실은 복구 불가다.

  ```bash
  ssh <oci-server-alias> 'grep ^CARIX_ENCRYPTION_KEY= /home/opc/carix/.env'
  ```

---

## 완료 기준

1. Phase 1 정리 후 루트 사용률이 70% 아래다.
2. compose가 `image: ghcr.io/...`를 쓰고 `nginx` 서비스가 없다. 서버에서 `docker build`가 일어나지 않는다.
3. `https://carix.144.24.90.88.sslip.io/`가 Let's Encrypt 인증서로 200을 반환한다.
4. `/home/opc/carix`에 main이 체크아웃되고 `.env`가 600 권한으로 있다.
5. 외부에서 5432·8080·3000이 닫혀 있다.
6. main 푸시 → ARM 러너 빌드 → GHCR 푸시 → 서버 pull → 헬스체크 200까지 사람 개입이 없다.
7. `pulse.144.24.90.88.sslip.io` 등 기존 서비스가 영향받지 않는다.

## 미해결 사항

| 항목 | 상태 |
| --- | --- |
| `backend/`, `frontend/` 스캐폴드 | 미완 → Phase 6 게이트. 워크플로는 `backend/build.gradle`과 `frontend/Dockerfile` 존재를 전제한다 |
| Spring Boot 버전 | layered jar 문법이 3.3 기준으로 갈린다 → Task 2.3 |
| Phase 3 실행 시 다운타임 | Caddy 교체 중 `pulse.144.24.90.88.sslip.io` 수 초 중단. asset-pulse compose(코덱스 워크트리)도 함께 수정해야 한다 |
| `/api/health` 구현 | 백엔드 스캐폴드 시 필수. CD 헬스체크 전제 |
| `collector` 실행 형태 | 관리자 화면/API 직접 트리거 방식 |
| 메모리 여유 | 스왑 4GB가 이미 소진됨. Carix 기동 후 재평가. 부족 시 flaresolverr×4 / warp×3 정리 논의 |
| 커스텀 도메인 | sslip.io로 충분. 정식 도메인이 생기면 Caddyfile에 호스트만 추가하면 된다 |
