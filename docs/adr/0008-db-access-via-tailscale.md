# 0008. DB 접근 경로는 Headscale (자체 호스팅 Tailscale)

- 상태: 채택
- 날짜: 2026-07-27
- 관련: [ADR 0007](0007-single-database-for-prod-and-dev.md) — 운영과 개발이 같은 DB를 쓴다

## 맥락

팀원들이 로컬에서 OCI의 `carix` DB에 붙어야 한다. 그 DB는 운영 DB이기도 하다.

Postgres는 `127.0.0.1:5432`에만 바인딩돼 있어 외부 경로가 없다. 어떤 방식으로 팀원 노트북을
그 포트에 닿게 할지 결정이 필요했다.

## 결정

**Headscale**을 자체 호스팅한다. 클라이언트는 Tailscale 공식 클라이언트를 그대로 쓴다.

- `docker-compose.yml`에 `headscale` 서비스를 둔다. 호스트 포트를 발행하지 않고
  라우터 Caddy(`headscale.144.24.90.88.sslip.io`)만 닿는다.
- 서버 호스트에 Tailscale 클라이언트를 설치해 이 tailnet에 참여시키고,
  `tailscale serve --bg --tcp=5432 tcp://localhost:5432`로 DB 포트를 tailnet 안에만 중계한다.
- Postgres의 `127.0.0.1:5432:5432` 바인딩은 그대로 둔다.

## 대안

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
요구도 이미 18개 컨테이너가 도는 이 서버엔 과하다.

**SSH 터널을 접은 이유**: 사람마다 키를 만들어 서버 `authorized_keys`에 등록하고 이탈 시
회수해야 한다. 팀 규모에 비례해 수동 서버 작업이 늘고 개인키 파일이 노트북마다 남는다.

**직접 노출을 접은 이유**: 운영 DB다. 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
자격증명과 쿼리·결과가 평문으로 오간다. 팀원 IP가 유동이라 허용목록도 계속 깨진다.

## 대가

- **DB 접근의 단일 장애점이 하나 늘었다.** Headscale이 죽으면 신규 인증과 키 갱신이 막힌다.
  기존 연결은 잠시 유지되고, 긴급 시 SSH 포워딩이 우회로로 남는다.
- **`headscale-data` 볼륨이 백업 대상이다.** 노드 키와 ACL을 담고 있어 잃으면 팀원 전원이
  재인증해야 한다. 현재 백업 체계가 없다.
- Headscale 문서는 대상 사용자를 "self-hosters and hobbyists"로 명시한다. 팀 운영 DB 접근의
  단일 경로로 쓰는 이상 그 문구를 감안한다.
- Carix compose에 함께 두므로 **Carix 배포가 headscale 컨테이너를 재생성**한다. 인프라 성격의
  서비스가 애플리케이션 배포 주기에 묶인다. `~/infra`가 버전 관리·백업 밖에 있어 그쪽에 두는
  것보다는 낫다는 판단이다.
- DERP는 Tailscale 공개 릴레이를 쓴다. 계정은 필요 없지만 NAT 통과 경로가 외부 인프라에
  의존한다. 내장 DERP로 바꾸면 STUN 포트를 추가로 열어야 한다.

## 결과

- 구성: [`deploy/headscale/config.yaml`](../../deploy/headscale/config.yaml)
- 라우터 항목: [`deploy/caddy/router.caddy`](../../deploy/caddy/router.caddy)
- 접근 절차와 개인별 DB 롤 발급: [`DEVELOPMENT.md`](../DEVELOPMENT.md) §3
- 팀이 6명 이하로 유지되고 자체 운영 부담이 문제가 되면 Tailscale SaaS로 되돌릴 수 있다.
  클라이언트가 같아 전환 비용은 `--login-server` 변경뿐이다.
