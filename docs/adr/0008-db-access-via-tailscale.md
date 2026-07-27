# 0008. DB 접근 경로는 Tailscale

- 상태: 채택
- 날짜: 2026-07-27
- 관련: [ADR 0007](0007-single-database-for-prod-and-dev.md) — 운영과 개발이 같은 DB를 쓴다

## 맥락

팀원들이 로컬에서 OCI의 `carix` DB에 붙어야 한다. 그 DB는 운영 DB이기도 하다.

Postgres는 `127.0.0.1:5432`에만 바인딩돼 있어 외부 경로가 없다. 어떤 방식으로 팀원 노트북을
그 포트에 닿게 할지 결정이 필요했다.

## 결정

**Tailscale**(메시 VPN)을 쓴다.

```bash
# 서버 1회
sudo tailscale up
sudo tailscale serve --bg --tcp=5432 tcp://localhost:5432
```

`serve`는 tailnet 내부에만 공개한다. 인터넷에 여는 `funnel`은 쓰지 않는다.
팀원은 클라이언트를 설치하고 SSO로 로그인하면 터널 명령 없이 붙는다.

Postgres의 `127.0.0.1:5432:5432` 바인딩은 그대로 둔다. `serve`가 그 루프백 포트를 중계한다.

## 대안

| 방식 | 클라이언트 설치 | 자격증명 배포 | 회수 | 공개 노출 |
| --- | --- | --- | --- | --- |
| **Tailscale (채택)** | 필요 | 없음 — SSO | 콘솔에서 사용자 제거 | 없음 |
| SSH 터널 | 없음 | **개인키 파일** + 서버 `authorized_keys` 등록 | 수동 | 없음 |
| 직접 노출 + TLS | 없음 | 비밀번호 | 롤 삭제 | **5432** |
| 자체 호스팅 VPN (Headscale·NetBird) | 필요 | 있음 | 수동 | 없음 |

**SSH 터널을 접은 이유**: 사람마다 키를 만들어 서버 `authorized_keys`에 등록하고 이탈 시
회수해야 한다. 팀 규모에 비례해 수동 서버 작업이 늘고, 개인키 파일이 노트북마다 남는다.

**직접 노출을 접은 이유**: 운영 DB다. 현재 Postgres는 TLS가 꺼져 있어(SSLRequest에 `N` 응답)
자격증명과 쿼리·결과가 평문으로 오간다. TLS를 켜더라도 팀원 IP가 유동이라 OCI Security List
허용목록이 계속 깨진다.

**자체 호스팅 VPN을 접은 이유**: 같은 결과를 얻으면서 설치·키 관리·업그레이드를 직접 진다.
이 호스트는 컨테이너 18개에 메모리·디스크가 빠듯하다.

## 대가

- 팀원 노트북마다 클라이언트 설치가 필요하다.
- 외부 서비스(Tailscale 계정)에 의존한다. 무료 Personal 플랜은 **사용자 6명**,
  사용자 기기 무제한, 태그된 리소스 50개다 (2026-04 개편 기준). 팀이 6명을 넘으면 유료 전환
  또는 자체 호스팅(Headscale) 재검토가 필요하다.
- Tailscale 장애 시 DB 접근이 끊긴다. 서버 자체는 SSH로 여전히 접근 가능하므로
  긴급 시 SSH 포워딩이 우회로가 된다.

## 결과

- `docker-compose.yml`의 postgres 바인딩은 변경 없음 (`127.0.0.1:5432:5432`).
- 접근 절차와 개인별 DB 롤 발급은 [`DEVELOPMENT.md`](../DEVELOPMENT.md) §3.
- `.env`의 `SPRING_DATASOURCE_URL` 호스트는 서버의 tailnet 이름을 쓴다.
- 서버의 `tailscale up`은 브라우저 인증이 필요해 사람이 1회 수행한다.
