# Spring Cloud 전환 A — 멀티모듈·Eureka·Config Server 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드를 Gradle 멀티모듈로 바꾸고 Eureka·Config Server를 띄워, 기존 API 서비스가 그 둘을 실제로 사용하게 한다.

**Architecture:** `backend/`를 루트 프로젝트로 두고 서비스마다 서브모듈을 만든다. 배포 워크플로는 모듈마다 이미지를 만들되, 기존의 "마지막 변경 커밋 = 태그, 이미지 있으면 빌드 생략" 규칙을 모듈 단위로 확장한다. Eureka와 Config Server는 각각 서브모듈이며 compose 서비스로 뜬다.

**Tech Stack:** Spring Boot 4.1.0, Spring Cloud 2025.1.2 (Oakwood), Java 21, Gradle 9.5.1, Docker Compose

전체 설계는 [Spring Cloud 아키텍처](../specs/spring-cloud-architecture.md)를 따른다. 이 계획은 그중 인프라 배관만 다룬다. Gateway 도입(계획 B)과 `auth-service` 분리(계획 C)는 이 계획이 끝난 뒤에 진행한다.

## 선행 조건

이 계획을 시작하기 전에 서버에서 1회 수행한다. 브라우저 인증이 필요해 사람이 해야 한다.

```bash
ssh carix-oci
curl -fsSL https://tailscale.com/install.sh | sh
docker exec carix_headscale headscale users create server
docker exec carix_headscale headscale preauthkeys create --user server --expiration 1h
sudo tailscale up --login-server https://headscale.144.24.90.88.sslip.io --authkey <키>
```

Task 2·3 에서 인프라 서비스를 `127.0.0.1` 에 바인딩한 뒤, 로컬 개발에서 쓰려면 tailnet 에
중계한다.

```bash
sudo tailscale serve --bg --tcp=8761 tcp://localhost:8761   # eureka
sudo tailscale serve --bg --tcp=8888 tcp://localhost:8888   # config-server
sudo tailscale serve --bg --tcp=8082 tcp://localhost:8082   # core-api
```

**tailnet 없이도 이 계획은 완료할 수 있다.** 위 중계는 로컬 개발 편의를 위한 것이고,
배포·검증은 서버 안에서 `127.0.0.1` 로 확인한다.

## Global Constraints

- Spring Boot `4.1.0`, Spring Cloud BOM `2025.1.2`. 2025.1.2부터 Boot 4.1.x를 지원한다.
- Java 툴체인 `21`. `settings.gradle`의 foojay resolver를 유지한다.
- 모든 JVM 서비스에 `mem_limit`와 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50`을 건다. 없으면 힙을 호스트 전체 메모리 기준으로 잡는다.
- 포트는 **컨테이너 내부**와 **호스트 바인딩**을 구분한다.
  - 내부: `config-server` 8888, `eureka` 8761, `core-api` 8080
  - 호스트 바인딩(`127.0.0.1:`): `config-server` 8888, `eureka` 8761, `core-api` 8082
  - Eureka 에 등록되는 것은 내부 포트다. Gateway 는 compose 네트워크 안에서 그 포트로 붙는다.
- 도메인 서비스는 호스트 포트를 발행하지 않는다. 로컬 접근이 필요한 인프라 서비스만 `127.0.0.1:`에 바인딩한다.
- 모든 모듈의 Dockerfile은 컴파일하지 않는다. 러너가 만든 JAR을 layered로 풀어 담기만 한다.
- 각 모듈의 `jar` 태스크를 비활성화한다. plain jar가 남으면 `build/libs/*.jar` 글롭이 둘을 집어 `COPY`가 실패한다.
- 커밋 메시지는 한글, Conventional Commits 형식. `Co-Authored-By` 태그를 쓰지 않는다.
- 워크플로 잡 ID 에 하이픈을 쓰지 않는다. `needs.<id>` 표현식에서 하이픈이 뺄셈으로 파싱된다.
- `main`에 직접 커밋하지 않는다. 브랜치 + PR.

---

## 파일 구조

```
backend/
├── settings.gradle              루트 — 서브모듈 include
├── build.gradle                 루트 — 공통 플러그인·의존성
├── gradle/, gradlew             (이동 없음)
├── core-api/
│   ├── build.gradle
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/                     기존 backend/src 를 이동
├── eureka/
│   ├── build.gradle
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/main/{java,resources}
└── config-server/
    ├── build.gradle
    ├── Dockerfile
    ├── .dockerignore
    └── src/main/{java,resources}

config/spring/                   Config Server 가 제공할 설정
├── application.yml
└── core-api.yml
```

책임 분리: 루트 `build.gradle`은 모든 모듈에 공통인 것(Java 툴체인, Boot·Cloud BOM, plain jar 비활성화)만 갖는다. 모듈별 `build.gradle`은 그 모듈 고유 의존성만 선언한다.

---

## Task 1: Gradle 멀티모듈로 전환

기존 단일 프로젝트를 루트 + `core-api` 모듈 구조로 바꾼다. **동작은 그대로**이고 구조만 바뀐다. 배포 워크플로가 모듈 경로를 쓰도록 함께 고친다.

**Files:**
- Modify: `backend/settings.gradle`
- Modify: `backend/build.gradle`
- Create: `backend/core-api/build.gradle`
- Move: `backend/src/` → `backend/core-api/src/`
- Move: `backend/Dockerfile` → `backend/core-api/Dockerfile`
- Move: `backend/.dockerignore` → `backend/core-api/.dockerignore`
- Modify: `.github/workflows/deploy.yml`
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: Gradle 모듈 `:core-api`가 `core-api/build/libs/core-api-0.0.1-SNAPSHOT.jar`를 만든다. 이미지 이름은 `ghcr.io/seohyeokgyu/carix/core-api`.

- [ ] **Step 1: 브랜치 생성**

```bash
cd /Users/seohyeokgyu/IdeaProjects/Carix
git checkout main && git pull --ff-only
git checkout -b feat/spring-cloud-multimodule
```

- [ ] **Step 2: 소스와 Docker 파일을 모듈 디렉터리로 이동**

```bash
cd backend
mkdir -p core-api
git mv src core-api/src
git mv Dockerfile core-api/Dockerfile
git mv .dockerignore core-api/.dockerignore
```

- [ ] **Step 3: `backend/settings.gradle` 작성**

```gradle
plugins {
	// 툴체인 JDK(21)가 없는 환경에서 Gradle이 직접 내려받게 한다.
	// CI는 setup-java가 21을 깔아주므로 다운로드가 일어나지 않는다.
	id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'carix'

include 'core-api'
```

- [ ] **Step 4: `backend/build.gradle` 을 루트 전용으로 교체**

```gradle
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.0' apply false
	id 'io.spring.dependency-management' version '1.1.7' apply false
}

// Spring Cloud 2025.1.2(Oakwood)부터 Boot 4.1.x 를 지원한다.
ext {
	springCloudVersion = '2025.1.2'
}

subprojects {
	apply plugin: 'java'
	apply plugin: 'org.springframework.boot'
	apply plugin: 'io.spring.dependency-management'

	group = 'com.carix'
	version = '0.0.1-SNAPSHOT'

	java {
		toolchain {
			languageVersion = JavaLanguageVersion.of(21)
		}
	}

	repositories {
		mavenCentral()
	}

	dependencyManagement {
		imports {
			mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
		}
	}

	dependencies {
		testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	}

	// Dockerfile이 build/libs/*.jar 를 글롭으로 집는다.
	// plain jar까지 남으면 두 개가 잡혀 COPY가 실패한다.
	tasks.named('jar') {
		enabled = false
	}

	tasks.named('test') {
		useJUnitPlatform()
	}
}
```

- [ ] **Step 5: `backend/core-api/build.gradle` 작성**

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}
```

- [ ] **Step 6: 빌드가 통과하는지 확인**

Run:
```bash
cd backend && ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. `core-api/build/libs/` 에 `core-api-0.0.1-SNAPSHOT.jar` 하나만 있어야 한다.

```bash
ls backend/core-api/build/libs/
```

Expected: `core-api-0.0.1-SNAPSHOT.jar` (plain jar 없음)

- [ ] **Step 7: 애플리케이션이 뜨고 기존 엔드포인트가 동작하는지 확인**

```bash
cd backend/core-api && java -jar build/libs/core-api-0.0.1-SNAPSHOT.jar &
sleep 15
curl -sS -w '\n%{http_code}\n' http://localhost:8080/api/health
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/swagger-ui/index.html
pkill -f 'core-api-0.0.1-SNAPSHOT.jar'
```

Expected: `{"status":"UP"}` / `200`, 그리고 `200`

- [ ] **Step 8: `docker-compose.yml` 의 `spring-api` 를 `core-api` 로 교체**

`spring-api` 서비스 블록을 아래로 바꾼다. 이미지 이름과 서비스명이 함께 바뀐다.

```yaml
  core-api:
    image: ghcr.io/seohyeokgyu/carix/core-api:${CORE_API_TAG:-latest}
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - CARIX_ENCRYPTION_KEY=${CARIX_ENCRYPTION_KEY}
      - JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50
    expose:
      - "8080"
    depends_on:
      - postgres
    mem_limit: 1500m
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
    restart: unless-stopped
```

- [ ] **Step 9: `deploy/caddy/Caddyfile` 의 업스트림 이름 변경**

`reverse_proxy spring-api:8080` 을 `reverse_proxy core-api:8080` 으로 바꾼다.

```bash
sed -i '' 's/reverse_proxy spring-api:8080/reverse_proxy core-api:8080/' deploy/caddy/Caddyfile
grep -n 'core-api' deploy/caddy/Caddyfile
```

Expected: `reverse_proxy core-api:8080` 한 줄

- [ ] **Step 10: `.env.example` 의 태그 변수명 변경**

`BACKEND_TAG` 를 `CORE_API_TAG` 로 바꾼다.

```bash
sed -i '' 's/^BACKEND_TAG=/CORE_API_TAG=/' .env.example
grep -n '_TAG=' .env.example
```

Expected: `CORE_API_TAG=latest`, `FRONTEND_TAG=latest`

- [ ] **Step 11: 배포 워크플로의 backend 잡을 core-api 로 변경**

`.github/workflows/deploy.yml` 의 `backend:` 잡에서 아래 다섯 곳을 바꾼다.

1. 잡 이름: `name: Test & Build backend` → `name: Test & Build core-api`
2. 태그 계산 경로:

```bash
TAG=$(git log -1 --format=%H -- backend/core-api/ backend/build.gradle backend/settings.gradle ':!*.md' .github/workflows/deploy.yml)
```

3. 이미지 존재 확인과 로그의 `backend` → `core-api`
4. 빌드 명령:

```yaml
      - name: Build & test
        if: steps.tag.outputs.build == 'true'
        working-directory: backend
        run: ./gradlew :core-api:build
```

5. 이미지 빌드 컨텍스트와 태그, GHCR 정리 패키지명:

```yaml
        with:
          context: ./backend/core-api
          push: true
          provenance: false
          sbom: false
          tags: |
            ${{ env.IMAGE_BASE }}/core-api:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_BASE }}/core-api:latest
```

```yaml
        with:
          package-name: carix/core-api
          package-type: container
          min-versions-to-keep: 2
```

- [ ] **Step 12: deploy 잡의 태그 변수 변경**

`.github/workflows/deploy.yml` 의 `deploy:` 잡에서 `BACKEND_TAG` 를 `CORE_API_TAG` 로 바꾼다. `env:` 블록, `envs:` 목록, `set_tag` 호출 세 곳이다.

```yaml
        env:
          DEPLOY_SHA: ${{ github.sha }}
          CORE_API_TAG: ${{ needs.backend.outputs.tag }}
          FRONTEND_TAG: ${{ needs.frontend.outputs.tag }}
```

```yaml
          envs: DEPLOY_SHA,CORE_API_TAG,FRONTEND_TAG,IMAGE_BASE,GHCR_TOKEN,GH_ACTOR
```

```bash
            set_tag CORE_API_TAG "$CORE_API_TAG"
            set_tag FRONTEND_TAG "$FRONTEND_TAG"
```

이미지 정리 루프의 `LIVE_BACKEND` 도 함께 바꾼다.

```bash
            LIVE_CORE_API=$(grep '^CORE_API_TAG=' .env | head -1 | cut -d= -f2- || true)
            LIVE_FRONTEND=$(grep '^FRONTEND_TAG=' .env | head -1 | cut -d= -f2- || true)
            prune_repo "${IMAGE_BASE}/core-api" "$LIVE_CORE_API"
            prune_repo "${IMAGE_BASE}/frontend" "$LIVE_FRONTEND"
```

- [ ] **Step 13: 워크플로 YAML 이 파싱되는지 확인**

```bash
npx --yes js-yaml .github/workflows/deploy.yml > /dev/null && echo "YAML OK"
npx --yes js-yaml docker-compose.yml > /dev/null && echo "compose OK"
grep -c 'BACKEND_TAG\|spring-api' .github/workflows/deploy.yml docker-compose.yml deploy/caddy/Caddyfile
```

Expected: `YAML OK`, `compose OK`, 그리고 세 파일 모두 `0`

- [ ] **Step 14: 커밋**

```bash
git add -A
git commit -m "refactor: 백엔드를 Gradle 멀티모듈로 전환

Spring Cloud 서비스를 추가하기 위한 사전 작업이다. 동작은 그대로이고 구조만 바뀐다.

- backend/src 를 backend/core-api/src 로 이동. Dockerfile 과 .dockerignore 도 함께
- 루트 build.gradle 은 공통 설정(Java 툴체인, Boot·Cloud BOM, plain jar 비활성화)만 갖고
  모듈별 build.gradle 이 고유 의존성을 선언한다
- Spring Cloud BOM 2025.1.2 추가. 2025.1.2 부터 Boot 4.1.x 를 지원한다
- 서비스명과 이미지명을 spring-api 에서 core-api 로. compose, Caddyfile,
  배포 워크플로, .env.example 을 함께 갱신
- 태그 계산 경로가 backend/core-api/ 와 루트 빌드 스크립트를 본다"
```

- [ ] **Step 15: 푸시하고 PR 생성**

```bash
git push -u origin feat/spring-cloud-multimodule
gh pr create --base main --head feat/spring-cloud-multimodule \
  --title "refactor: 백엔드를 Gradle 멀티모듈로 전환" \
  --body "Spring Cloud 서비스 추가를 위한 사전 작업. 동작은 그대로이고 구조만 바뀐다.

서비스명이 \`spring-api\` 에서 \`core-api\` 로 바뀌므로 컨테이너가 재생성된다.
\`--remove-orphans\` 가 기존 \`spring-api\` 를 제거한다.

배포 후 확인: /api/health, /api/swagger-ui/index.html 이 200"
```

- [ ] **Step 16: 머지 후 배포 검증**

머지하고 배포가 끝나면 확인한다.

```bash
B=https://carix.144.24.90.88.sslip.io
for p in /api/health /api/swagger-ui/index.html /; do
  printf '%-32s %s\n' "$p" "$(curl -sS -o /dev/null -w '%{http_code}' $B$p)"
done
ssh carix-oci 'docker ps --filter "name=carix" --format "{{.Names}}\t{{.Status}}"'
```

Expected: 세 경로 모두 `200`. 컨테이너 목록에 `carix-core-api-1` 이 있고 `carix-spring-api-1` 은 없다.

---

## Task 2: Eureka 서버 추가

서비스 레지스트리를 띄운다. 아직 등록하는 클라이언트는 없다 — Task 4에서 붙인다.

**Files:**
- Create: `backend/eureka/build.gradle`
- Create: `backend/eureka/Dockerfile`
- Create: `backend/eureka/.dockerignore`
- Create: `backend/eureka/src/main/java/com/carix/eureka/EurekaApplication.java`
- Create: `backend/eureka/src/main/resources/application.yml`
- Modify: `backend/settings.gradle`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/deploy.yml`
- Modify: `.env.example`

**Interfaces:**
- Consumes: Task 1의 루트 `build.gradle` 공통 설정과 Spring Cloud BOM.
- Produces: `http://eureka:8761/eureka/` 에서 레지스트리를 제공한다. 이미지 `ghcr.io/seohyeokgyu/carix/eureka`.

- [ ] **Step 1: 브랜치 생성**

```bash
git checkout main && git pull --ff-only
git checkout -b feat/eureka-server
```

- [ ] **Step 2: 모듈 등록**

`backend/settings.gradle` 의 include 를 늘린다.

```gradle
include 'core-api'
include 'eureka'
```

- [ ] **Step 3: `backend/eureka/build.gradle` 작성**

```gradle
dependencies {
	implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

- [ ] **Step 4: 진입점 작성**

`backend/eureka/src/main/java/com/carix/eureka/EurekaApplication.java`

```java
package com.carix.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaApplication.class, args);
	}
}
```

- [ ] **Step 5: 설정 작성**

`backend/eureka/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: eureka

server:
  port: 8761

eureka:
  client:
    # 자기 자신은 등록하지도 조회하지도 않는다. 단일 인스턴스이므로 피어 복제가 없다.
    register-with-eureka: false
    fetch-registry: false
  server:
    # 기본 60초는 개발 중 죽은 인스턴스가 오래 남아 혼란을 준다.
    eviction-interval-timer-in-ms: 15000
    # 자기보호 모드. 인스턴스가 적을 때 오탐으로 만료를 막아 낡은 등록이 남는다.
    enable-self-preservation: false

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 6: Dockerfile 작성**

`backend/eureka/Dockerfile` — `core-api/Dockerfile` 과 같은 구조다. 포트만 다르다.

```dockerfile
# 컴파일하지 않는다. GitHub Actions 러너가 Gradle 캐시를 써서 만든 JAR을
# layered로 풀어 담기만 한다.
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract \
      --layers \
      --application-filename application.jar \
      --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "application.jar"]
```

- [ ] **Step 7: `.dockerignore` 작성**

`backend/eureka/.dockerignore`

```
# 빌드 컨텍스트는 build/libs/*.jar 하나면 된다.
.gradle/
.git/
src/
build.gradle
build/classes/
build/resources/
build/tmp/
build/reports/
build/test-results/
build/generated/
```

- [ ] **Step 8: 빌드하고 실제로 뜨는지 확인**

```bash
cd backend && ./gradlew :eureka:build --no-daemon
cd eureka && java -jar build/libs/eureka-0.0.1-SNAPSHOT.jar &
sleep 25
curl -sS -o /dev/null -w 'UI  %{http_code}\n' http://localhost:8761/
curl -sS -H 'Accept: application/json' http://localhost:8761/eureka/apps
pkill -f 'eureka-0.0.1-SNAPSHOT.jar'
```

Expected: `UI  200`, 그리고 등록된 앱이 없으므로 빈 응답(`{"applications":{"versions__delta":"1","apps__hashcode":"","application":[]}}` 형태)

- [ ] **Step 9: compose 에 서비스 추가**

`docker-compose.yml` 의 `core-api` 앞에 넣는다. **127.0.0.1 에 바인딩한다** — 로컬 개발에서 tailnet 으로 조회해야 한다.

```yaml
  eureka:
    image: ghcr.io/seohyeokgyu/carix/eureka:${EUREKA_TAG:-latest}
    environment:
      - JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50
    ports:
      - "127.0.0.1:8761:8761"
    mem_limit: 700m
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
    restart: unless-stopped
```

- [ ] **Step 10: `.env.example` 에 태그 변수 추가**

`CORE_API_TAG=latest` 아래에 추가한다.

```
EUREKA_TAG=latest
```

- [ ] **Step 11: 배포 워크플로에 eureka 잡 추가**

`.github/workflows/deploy.yml` 의 `frontend:` 잡 앞에 넣는다. `core-api` 잡과 같은 구조이며 이름·경로·패키지명만 다르다.

```yaml
  eureka:
    name: Build eureka
    runs-on: ubuntu-24.04-arm
    timeout-minutes: 20
    permissions:
      contents: read
      packages: write
    outputs:
      tag: ${{ steps.tag.outputs.tag }}
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: docker/setup-buildx-action@v3

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 태그 결정과 이미지 존재 확인
        id: tag
        run: |
          TAG=$(git log -1 --format=%H -- backend/eureka/ backend/build.gradle backend/settings.gradle ':!*.md' .github/workflows/deploy.yml)
          [ -n "$TAG" ] || TAG="$GITHUB_SHA"
          echo "tag=$TAG" >> "$GITHUB_OUTPUT"

          if docker buildx imagetools inspect "${IMAGE_BASE}/eureka:${TAG}" >/dev/null 2>&1; then
            echo "build=false" >> "$GITHUB_OUTPUT"
            echo "eureka:${TAG} 이미 있음 — 빌드 건너뜀"
          else
            echo "build=true" >> "$GITHUB_OUTPUT"
            echo "eureka:${TAG} 없음 — 빌드"
          fi

      - uses: actions/setup-java@v4
        if: steps.tag.outputs.build == 'true'
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v4
        if: steps.tag.outputs.build == 'true'

      - name: Build & test
        if: steps.tag.outputs.build == 'true'
        working-directory: backend
        run: ./gradlew :eureka:build

      - name: Build & push image
        if: steps.tag.outputs.build == 'true'
        uses: docker/build-push-action@v6
        with:
          context: ./backend/eureka
          push: true
          provenance: false
          sbom: false
          tags: |
            ${{ env.IMAGE_BASE }}/eureka:${{ steps.tag.outputs.tag }}
            ${{ env.IMAGE_BASE }}/eureka:latest

      - name: 오래된 GHCR 버전 정리
        if: steps.tag.outputs.build == 'true'
        uses: actions/delete-package-versions@e5bc658cc4c965c472efe991f8beea3981499c55   # v5.0.0
        with:
          package-name: carix/eureka
          package-type: container
          min-versions-to-keep: 2
```

- [ ] **Step 12: deploy 잡이 eureka 를 기다리고 태그를 전달하게 한다**

`deploy:` 잡의 `needs` 와 태그 전달을 고친다.

```yaml
    needs: [backend, frontend, eureka]
```

```yaml
        env:
          DEPLOY_SHA: ${{ github.sha }}
          CORE_API_TAG: ${{ needs.backend.outputs.tag }}
          FRONTEND_TAG: ${{ needs.frontend.outputs.tag }}
          EUREKA_TAG: ${{ needs.eureka.outputs.tag }}
```

```yaml
          envs: DEPLOY_SHA,CORE_API_TAG,FRONTEND_TAG,EUREKA_TAG,IMAGE_BASE,GHCR_TOKEN,GH_ACTOR
```

```bash
            set_tag CORE_API_TAG "$CORE_API_TAG"
            set_tag FRONTEND_TAG "$FRONTEND_TAG"
            set_tag EUREKA_TAG "$EUREKA_TAG"
```

```bash
            LIVE_EUREKA=$(grep '^EUREKA_TAG=' .env | head -1 | cut -d= -f2- || true)
            prune_repo "${IMAGE_BASE}/eureka" "$LIVE_EUREKA"
```

- [ ] **Step 13: YAML 검증**

```bash
npx --yes js-yaml .github/workflows/deploy.yml > /dev/null && echo "YAML OK"
npx --yes js-yaml docker-compose.yml > /dev/null && echo "compose OK"
npx --yes js-yaml backend/eureka/src/main/resources/application.yml > /dev/null && echo "eureka config OK"
```

Expected: 세 줄 모두 OK

- [ ] **Step 14: 커밋하고 PR**

```bash
git add -A
git commit -m "feat: Eureka 서비스 레지스트리 추가

Spring Cloud 전환의 인프라 배관이다. 아직 등록하는 클라이언트는 없다.

- 단일 인스턴스이므로 register-with-eureka·fetch-registry 를 끈다
- 자기보호 모드를 끈다. 인스턴스가 적을 때 오탐으로 만료를 막아 낡은 등록이 남는다
- 127.0.0.1:8761 에 바인딩한다. 로컬 개발에서 tailscale serve 로 조회한다
- 배포 워크플로에 eureka 잡 추가. core-api 와 같은 태그 규칙을 쓴다"
git push -u origin feat/eureka-server
gh pr create --base main --head feat/eureka-server \
  --title "feat: Eureka 서비스 레지스트리 추가" \
  --body "인프라 배관. 등록하는 클라이언트는 다음 작업에서 붙인다.

배포 후 확인: 서버에서 curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8761/ 가 200"
```

- [ ] **Step 15: 머지 후 배포 검증**

```bash
ssh carix-oci 'docker ps --filter "name=eureka" --format "{{.Names}}\t{{.Status}}"'
ssh carix-oci 'curl -sS -o /dev/null -w "eureka UI %{http_code}\n" http://127.0.0.1:8761/'
ssh carix-oci 'curl -sS -H "Accept: application/json" http://127.0.0.1:8761/eureka/apps'
```

Expected: 컨테이너 `Up`, UI `200`, 등록 앱 없음

---

## Task 3: Config Server 추가

git 백엔드로 설정을 제공한다. **서버에 이미 있는 저장소 체크아웃을 읽는다** — 자격증명이 필요 없다.

**Files:**
- Create: `backend/config-server/build.gradle`
- Create: `backend/config-server/Dockerfile`
- Create: `backend/config-server/.dockerignore`
- Create: `backend/config-server/src/main/java/com/carix/configserver/ConfigServerApplication.java`
- Create: `backend/config-server/src/main/resources/application.yml`
- Create: `config/spring/application.yml`
- Create: `config/spring/core-api.yml`
- Modify: `backend/settings.gradle`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/deploy.yml`
- Modify: `.env.example`

**Interfaces:**
- Consumes: Task 1의 루트 `build.gradle`, Task 2의 배포 워크플로 패턴.
- Produces: `http://config-server:8888/{application}/{profile}` 에서 설정을 제공한다. 이미지 `ghcr.io/seohyeokgyu/carix/config-server`.

- [ ] **Step 1: 브랜치 생성**

```bash
git checkout main && git pull --ff-only
git checkout -b feat/config-server
```

- [ ] **Step 2: 모듈 등록**

`backend/settings.gradle`

```gradle
include 'core-api'
include 'eureka'
include 'config-server'
```

- [ ] **Step 3: `backend/config-server/build.gradle` 작성**

```gradle
dependencies {
	implementation 'org.springframework.cloud:spring-cloud-config-server'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

- [ ] **Step 4: 진입점 작성**

`backend/config-server/src/main/java/com/carix/configserver/ConfigServerApplication.java`

```java
package com.carix.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigServerApplication.class, args);
	}
}
```

- [ ] **Step 5: 설정 작성**

`backend/config-server/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          # 서버에 이미 있는 저장소 체크아웃을 읽는다. 같은 저장소를 네트워크로 다시
          # 받을 이유가 없고 자격증명도 필요 없다. 배포 스크립트의 git reset --hard 가
          # 이 내용을 갱신한다.
          #
          # GitHub 을 SSH 로 직접 읽지 않는 이유: JGit 은 RSA·PEM 형식 키만 받는데
          # 서버의 기존 deploy key 는 ED25519·OpenSSH 형식이라 쓸 수 없다 (2026-07-28 실측).
          uri: file:///etc/carix-repo
          search-paths: config/spring
          default-label: main

server:
  port: 8888

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 6: 제공할 설정 파일 작성**

`config/spring/application.yml` — 모든 서비스 공통

```yaml
# Spring Cloud Config Server 가 제공하는 공통 설정.
# 시크릿은 여기 두지 않는다. DB 자격증명·API 키는 .env 로 주입한다.
management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
  endpoint:
    health:
      show-details: never

logging:
  level:
    root: INFO
    com.carix: INFO
```

`config/spring/core-api.yml`

```yaml
# core-api 전용 설정.
carix:
  index:
    # 지수 계산 임계값. 재배포 없이 /actuator/refresh 로 갱신할 수 있도록
    # @RefreshScope 빈에서 읽는다.
    purchasing-power-warning-threshold: 0.95
    market-value-gap-warning-threshold: 0.90
```

- [ ] **Step 7: Dockerfile 과 .dockerignore 작성**

`backend/config-server/Dockerfile` — `EXPOSE` 만 다르다.

```dockerfile
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract \
      --layers \
      --application-filename application.jar \
      --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "application.jar"]
```

`backend/config-server/.dockerignore`

```
# 빌드 컨텍스트는 build/libs/*.jar 하나면 된다.
.gradle/
.git/
src/
build.gradle
build/classes/
build/resources/
build/tmp/
build/reports/
build/test-results/
build/generated/
```

- [ ] **Step 8: 빌드 확인**

```bash
cd backend && ./gradlew :config-server:build --no-daemon
ls config-server/build/libs/
```

Expected: `BUILD SUCCESSFUL`, `config-server-0.0.1-SNAPSHOT.jar` 하나

로컬 실행은 `/etc/carix-repo` 마운트가 없어 기동에 실패한다. 배포에서 검증한다.

- [ ] **Step 9: compose 에 서비스 추가**

`docker-compose.yml` 의 `eureka` 앞에 넣는다. 서버의 저장소 체크아웃을 읽기 전용으로 마운트한다.

```yaml
  config-server:
    image: ghcr.io/seohyeokgyu/carix/config-server:${CONFIG_SERVER_TAG:-latest}
    environment:
      - JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50
    volumes:
      # 서버의 저장소 체크아웃. 배포 스크립트의 git reset --hard 가 이 내용을 갱신한다.
      # 라우터 Caddy 가 같은 경로를 마운트하는 것과 같은 방식이다.
      - /home/opc/carix:/etc/carix-repo:ro
    ports:
      - "127.0.0.1:8888:8888"
    mem_limit: 700m
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
    restart: unless-stopped
```

- [ ] **Step 10: 배포 `paths` 에 `config/**` 추가**

설정 변경이 서버 체크아웃까지 도달해야 한다. `.github/workflows/deploy.yml` 의 `paths` 에 넣는다. 부정 패턴(`'!**/*.md'`)보다 **앞**에 둔다 — 마지막에 매칭된 패턴이 이긴다.

```yaml
    paths:
      - backend/**
      - frontend/**
      - config/**
      - docker-compose.yml
      - deploy/**
      - .github/workflows/deploy.yml
      - '!**/*.md'
```

태그 계산 경로에는 `config/` 가 없으므로 설정만 바꾼 커밋은 이미지를 다시 빌드하지 않는다. `git reset --hard` 와 `compose up` 만 돈다.

- [ ] **Step 11: `.env.example` 에 태그 변수 추가**

```
CONFIG_SERVER_TAG=latest
```

- [ ] **Step 12: 배포 워크플로에 config-server 잡 추가**

Task 2 Step 11 의 `eureka` 잡을 그대로 복제하고 아래를 바꾼다.

- 잡 키와 이름: **`configserver:`** / `name: Build config-server`

> **잡 ID 에 하이픈을 쓰지 않는다.** GitHub Actions 표현식에서 `needs.config-server` 는
> `needs.config` 빼기 `server` 로 파싱된다. 대괄호 표기(`needs['config-server']`)로
> 우회할 수 있지만, 하이픈 없는 ID 가 단순하다.
- 태그 경로: `backend/config-server/`
- 이미지 이름: `${IMAGE_BASE}/config-server`
- 빌드 명령: `./gradlew :config-server:build`
- 컨텍스트: `./backend/config-server`
- 정리 패키지명: `carix/config-server`

`deploy:` 잡의 `needs` 와 태그 전달도 함께 늘린다.

```yaml
    needs: [backend, frontend, eureka, configserver]
```

```yaml
          CONFIG_SERVER_TAG: ${{ needs.configserver.outputs.tag }}
```

```yaml
          envs: DEPLOY_SHA,CORE_API_TAG,FRONTEND_TAG,EUREKA_TAG,CONFIG_SERVER_TAG,IMAGE_BASE,GHCR_TOKEN,GH_ACTOR
```

```bash
            set_tag CONFIG_SERVER_TAG "$CONFIG_SERVER_TAG"
```

```bash
            LIVE_CONFIG_SERVER=$(grep '^CONFIG_SERVER_TAG=' .env | head -1 | cut -d= -f2- || true)
            prune_repo "${IMAGE_BASE}/config-server" "$LIVE_CONFIG_SERVER"
```

- [ ] **Step 13: YAML 검증하고 커밋**

```bash
npx --yes js-yaml .github/workflows/deploy.yml > /dev/null && echo "YAML OK"
npx --yes js-yaml docker-compose.yml > /dev/null && echo "compose OK"
npx --yes js-yaml config/spring/application.yml > /dev/null && echo "common config OK"
npx --yes js-yaml config/spring/core-api.yml > /dev/null && echo "core-api config OK"

git add -A
git commit -m "feat: Spring Cloud Config Server 추가

설정을 git 으로 외부화한다. 시크릿은 담지 않는다.

- 서버 체크아웃을 읽으므로 설정 변경이 배포를 거친다. 이미지 재빌드도 컨테이너 재생성도
  없는 40초짜리 배포다
- 서버의 저장소 체크아웃을 읽기 전용으로 마운트한다. 자격증명이 필요 없다
- 그 대가로 설정 변경이 배포를 거친다. config/** 를 배포 paths 에 넣되 태그 계산
  경로에는 없으므로 이미지는 다시 빌드되지 않는다
- config/spring/ 에 공통 설정과 core-api 설정을 둔다. 지수 임계값처럼 재배포 없이
  바꾸고 싶은 값이 대상이다"
git push -u origin feat/config-server
gh pr create --base main --head feat/config-server \
  --title "feat: Spring Cloud Config Server 추가" \
  --body "설정을 git 으로 외부화한다. 시크릿은 .env 가 계속 담당한다.

로컬에 /etc/carix-repo 마운트가 없어 기동 검증을 하지 못했다. 배포에서 처음 확인된다.
실패해도 다른 서비스에는 영향이 없다."
```

- [ ] **Step 14: 머지 후 배포 검증**

```bash
ssh carix-oci 'docker ps --filter "name=config-server" --format "{{.Names}}\t{{.Status}}"'
ssh carix-oci 'docker logs --tail 20 $(docker ps -qf name=config-server)'
ssh carix-oci 'curl -sS http://127.0.0.1:8888/core-api/prod | head -c 400'
```

Expected: 컨테이너 `Up`, 로그에 git clone 성공, 설정 JSON 에 `purchasing-power-warning-threshold` 가 보인다

---

## Task 4: core-api 를 Config·Eureka 클라이언트로

기존 서비스가 Config Server 에서 설정을 읽고 Eureka 에 등록되게 한다. 이 시점에 배관이 실제로 쓰인다.

**Files:**
- Modify: `backend/core-api/build.gradle`
- Create: `backend/core-api/src/main/java/com/carix/config/IndexThresholdProperties.java`
- Create: `backend/core-api/src/main/java/com/carix/demo/ThresholdController.java`
- Create: `backend/core-api/src/test/java/com/carix/config/IndexThresholdPropertiesTest.java`
- Modify: `backend/core-api/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Modify: `config/spring/core-api.yml`

**Interfaces:**
- Consumes: Task 3의 `config/spring/core-api.yml` 의 `carix.index.*`, Task 2의 Eureka 레지스트리.
- Produces: `GET /api/v1/demo/thresholds` 가 Config Server 에서 읽은 값을 반환한다.

- [ ] **Step 1: 브랜치 생성**

```bash
git checkout main && git pull --ff-only
git checkout -b feat/core-api-cloud-client
```

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/core-api/src/test/java/com/carix/config/IndexThresholdPropertiesTest.java`

```java
package com.carix.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"carix.index.purchasing-power-warning-threshold=0.80",
		"carix.index.market-value-gap-warning-threshold=0.70"
})
class IndexThresholdPropertiesTest {

	@Autowired
	private IndexThresholdProperties properties;

	@Test
	void 설정값을_바인딩한다() {
		assertThat(properties.purchasingPowerWarningThreshold()).isEqualTo(0.80);
		assertThat(properties.marketValueGapWarningThreshold()).isEqualTo(0.70);
	}
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run:
```bash
cd backend && ./gradlew :core-api:test --tests '*IndexThresholdPropertiesTest*' --no-daemon
```

Expected: FAIL — `IndexThresholdProperties` 클래스가 없어 컴파일 오류

- [ ] **Step 4: 설정 프로퍼티 클래스 작성**

`backend/core-api/src/main/java/com/carix/config/IndexThresholdProperties.java`

```java
package com.carix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지수 경고 임계값. Config Server 가 제공하며 재배포 없이 바꿀 수 있다.
 *
 * <p>갱신하려면 설정 저장소에 커밋한 뒤 이 서비스의 {@code /actuator/refresh} 를 호출한다.
 * Spring Cloud Bus 를 쓰지 않으므로 자동 전파되지 않는다.
 */
@ConfigurationProperties(prefix = "carix.index")
public record IndexThresholdProperties(
		double purchasingPowerWarningThreshold,
		double marketValueGapWarningThreshold) {
}
```

- [ ] **Step 5: 프로퍼티 스캔 활성화**

`backend/core-api/src/main/java/com/carix/CarixApplication.java` 에 애너테이션을 더한다.

```java
package com.carix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CarixApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarixApplication.class, args);
	}

}
```

- [ ] **Step 6: 테스트가 통과하는지 확인**

Run:
```bash
cd backend && ./gradlew :core-api:test --tests '*IndexThresholdPropertiesTest*' --no-daemon
```

Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add -A
git commit -m "feat: 지수 임계값을 설정 프로퍼티로 분리

Config Server 가 제공할 값이다. @ConfigurationPropertiesScan 으로 바인딩한다."
```

- [ ] **Step 8: 값을 확인할 엔드포인트 추가**

Config Server 연동이 실제로 되는지 눈으로 볼 수단이 필요하다.

`backend/core-api/src/main/java/com/carix/demo/ThresholdController.java`

```java
package com.carix.demo;

import com.carix.config.IndexThresholdProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Config Server 연동 확인용 임시 엔드포인트.
 *
 * <p>TODO: (cjkang) 지수 계산이 구현되면 이 컨트롤러를 제거한다.
 */
@RestController
@RequestMapping("/v1/demo")
@RefreshScope
@Tag(name = "demo", description = "임시 확인용. 도메인 API 도입 시 제거한다")
public class ThresholdController {

	private final IndexThresholdProperties properties;

	public ThresholdController(IndexThresholdProperties properties) {
		this.properties = properties;
	}

	@Operation(
			summary = "현재 적용 중인 지수 임계값",
			description = "Config Server 에서 읽은 값이다. 설정 커밋 후 /actuator/refresh 를 호출하면 바뀐다.")
	@GetMapping("/thresholds")
	public IndexThresholdProperties thresholds() {
		return properties;
	}
}
```

- [ ] **Step 9: Cloud 클라이언트 의존성 추가**

`backend/core-api/build.gradle`

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.cloud:spring-cloud-starter-config'
	implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'
	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}
```

- [ ] **Step 10: `application.yml` 에 Config·Eureka 설정 추가**

`backend/core-api/src/main/resources/application.yml` 을 아래로 교체한다.

```yaml
spring:
  application:
    # Config Server 가 이 이름으로 config/spring/core-api.yml 을 찾는다.
    name: core-api
  config:
    # Config Server 를 쓰되, 로컬 프로파일에서는 끌 수 있게 import 를 optional 로 두지 않는다.
    # 운영에서 설정 없이 기본값으로 뜨면 잘못된 임계값으로 계산해 조용히 틀린 결과를 낸다.
    import: configserver:${CONFIG_SERVER_URL:http://config-server:8888}
  cloud:
    config:
      fail-fast: true
      retry:
        max-attempts: 6
        initial-interval: 2000

# 엣지 Caddy 가 /api/* 를 경로 그대로 넘긴다. 컨텍스트 패스를 /api 로 두면
# 헬스체크·OpenAPI 문서·Swagger UI 정적 자원이 전부 /api 아래로 들어온다.
server:
  servlet:
    context-path: /api

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://eureka:8761/eureka/}
  instance:
    # 컨테이너 호스트명 대신 서비스명으로 등록해 Gateway 가 찾기 쉽게 한다.
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,refresh
```

- [ ] **Step 11: 로컬 프로파일 추가**

`backend/core-api/src/main/resources/application-local.yml`

```yaml
# 로컬 개발용. 서버의 config·eureka 를 tailnet 으로 사용한다.
#
# register-with-eureka 를 반드시 false 로 둔다. 등록하면 서버 Gateway 가
# 이 노트북으로 운영 트래픽을 보낸다.
eureka:
  client:
    register-with-eureka: false
    fetch-registry: true
```

- [ ] **Step 12: 전체 빌드 확인**

```bash
cd backend && ./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

**테스트는 Config Server 에 닿을 수 없다.** `@SpringBootTest` 가
`ConfigClientFailFastException` 으로 실패한다.

`@TestPropertySource` 로는 못 고친다. `spring.config.import` 는 `SpringApplication`
의 `prepareEnvironment()` 단계에서 해석되는데, `@TestPropertySource` 값은 그보다 뒤인
컨텍스트 로딩 시점에 주입된다.

**시스템 프로퍼티는 `prepareEnvironment()` 단계에서 읽힌다.** Gradle 테스트 태스크에서
`optional:` 접두사로 바꾼다 — 운영 설정(`application.yml`)은 fail-fast 를 유지한다.

`backend/core-api/build.gradle` 에 더한다.

```gradle
tasks.named('test') {
	// spring.config.import 는 prepareEnvironment 단계에서 해석돼 @TestPropertySource 로
	// 덮을 수 없다. 시스템 프로퍼티는 그 단계에서 읽히므로 여기서 optional 로 바꾼다.
	// 운영 application.yml 은 fail-fast 를 유지한다 — 설정 없이 기본값으로 뜨면
	// 잘못된 임계값으로 계산해 조용히 틀린 결과를 낸다.
	systemProperty 'spring.config.import', 'optional:configserver:'
	systemProperty 'eureka.client.enabled', 'false'
}
```

루트 `build.gradle` 의 `subprojects { tasks.named('test') { useJUnitPlatform() } }` 와
설정이 병합되므로 `useJUnitPlatform()` 을 다시 쓰지 않는다.

- [ ] **Step 13: compose 에 의존성과 환경변수 추가**

`docker-compose.yml` 의 `core-api` 블록에 더한다.

```yaml
    depends_on:
      - postgres
      - config-server
      - eureka
```

- [ ] **Step 14: 커밋하고 PR**

```bash
git add -A
git commit -m "feat: core-api 를 Config·Eureka 클라이언트로

인프라 배관이 실제로 쓰이기 시작한다.

- spring-cloud-starter-config 로 Config Server 에서 설정을 읽는다.
  fail-fast 를 유지한다 — 설정 없이 기본값으로 뜨면 잘못된 임계값으로 계산해
  조용히 틀린 결과를 낸다
- spring-cloud-starter-netflix-eureka-client 로 레지스트리에 등록한다
- 연동 확인용 GET /api/v1/demo/thresholds 추가. @RefreshScope 라
  /actuator/refresh 후 값이 바뀐다
- application-local.yml 에 register-with-eureka: false. 없으면 로컬 인스턴스가
  서버 Gateway 로부터 운영 트래픽을 받는다"
git push -u origin feat/core-api-cloud-client
gh pr create --base main --head feat/core-api-cloud-client \
  --title "feat: core-api 를 Config·Eureka 클라이언트로" \
  --body "인프라 배관이 실제로 쓰이기 시작한다.

배포 후 확인:
- GET /api/v1/demo/thresholds 가 Config Server 값을 반환
- Eureka 레지스트리에 CORE-API 가 등록
- config/spring/core-api.yml 을 바꾸고 /actuator/refresh 호출 시 값이 바뀐다"
```

- [ ] **Step 15: 머지 후 배포 검증**

```bash
B=https://carix.144.24.90.88.sslip.io
curl -sS $B/api/v1/demo/thresholds
ssh carix-oci 'curl -sS -H "Accept: application/json" http://127.0.0.1:8761/eureka/apps | head -c 300'
```

Expected: 임계값 JSON(`0.95`, `0.90`), Eureka 응답에 `CORE-API` 등록

- [ ] **Step 16: 재배포 없는 설정 갱신을 실증**

`config/spring/core-api.yml` 의 `purchasing-power-warning-threshold` 를 `0.93` 으로 바꿔 커밋·푸시한다. `config/` 는 배포 `paths` 에 없으므로 배포가 돌지 않는다.

```bash
git checkout main && git pull --ff-only
sed -i '' 's/purchasing-power-warning-threshold: 0.95/purchasing-power-warning-threshold: 0.93/' config/spring/core-api.yml
git add config/spring/core-api.yml
git commit -m "chore: 구매력 경고 임계값을 0.93 으로 조정"
git push
```

60초 기다린 뒤 갱신을 호출하고 값을 확인한다.

```bash
sleep 70
ssh carix-oci 'curl -sS -XPOST http://127.0.0.1:8082/api/actuator/refresh'
curl -sS https://carix.144.24.90.88.sslip.io/api/v1/demo/thresholds
```

Expected: refresh 응답에 `carix.index.purchasing-power-warning-threshold` 가 포함되고, 임계값이 `0.93` 으로 바뀐다. **배포는 돌지 않았다.**

이 단계가 실패하면 `core-api` 를 `127.0.0.1:8082` 에 바인딩하지 않아 접근이 안 되는 경우다. Task 4 Step 13 의 compose 블록에 아래를 더한다.

```yaml
    ports:
      - "127.0.0.1:8082:8080"
```

---

## 완료 기준

- `backend/` 가 멀티모듈이고 `core-api`·`eureka`·`config-server` 세 모듈을 빌드한다.
- 배포 워크플로가 모듈마다 이미지를 만들며, 안 바뀐 모듈은 빌드를 건너뛴다.
- Eureka 에 `CORE-API` 가 등록된다.
- `core-api` 가 Config Server 에서 임계값을 읽는다.
- 설정 저장소 커밋 + `/actuator/refresh` 로 **배포 없이** 값이 바뀐다.
- 기존 엔드포인트(`/api/health`, Swagger UI, 프런트엔드)가 그대로 동작한다.

## 다음 계획

- **B — Gateway 도입**: `carix_caddy` 를 Spring Cloud Gateway 로 대체. 세션 검증 필터, 상관관계 ID, OpenAPI 취합.
- **C — auth-service 분리**: 인증 도메인을 별도 서비스로. `auth` 스키마 분리.

JPA·Flyway 도입은 이 계획들과 독립적이며 언제 넣어도 된다. 배관을 먼저 맞추는 편이 깨질 것이 적다.
